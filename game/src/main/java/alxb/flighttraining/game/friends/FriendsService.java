package alxb.flighttraining.game.friends;

import alxb.flighttraining.game.accounts.PlayerAccounts;
import alxb.flighttraining.game.network.*;
import com.google.gson.*;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

/** Authenticated social messages. No network work or engine mutations run on the render thread. */
public final class FriendsService implements AutoCloseable {
    public record Person(UUID id,String name,String code,boolean online,boolean hosting,String title){}
    public record Invitation(UUID id,UUID from,String name,String kind,String title,boolean locked,String expires){}
    public record View(UUID owner,boolean available,boolean busy,String code,List<Person> friends,List<Person> incoming,List<Person> outgoing,List<Invitation> invites,List<Person> results,String message,long revision){
        public int notifications(){return incoming.size()+invites.size();}
    }
    public record Join(String code,String password){@Override public String toString(){return "Join[redacted]";}}
    interface Gateway {UUID accountId();JsonElement call(String function,JsonObject body)throws IOException;}
    private final Gateway gateway;private final MultiplayerSession multiplayer;
    private final ScheduledExecutorService worker=Executors.newSingleThreadScheduledExecutor(r->{var t=new Thread(r,"DSC-friends");t.setDaemon(true);return t;});
    private volatile View view=empty(null,0);private volatile String selectedCode="";private volatile boolean closed;
    private Join pendingJoin;private boolean refreshQueued;private long nextRetry;
    public FriendsService(PlayerAccounts accounts,MultiplayerSession multiplayer){this(new Gateway(){public UUID accountId(){return accounts.onlineAccountId();}public JsonElement call(String f,JsonObject b)throws IOException{return accounts.socialRpc(f,b);}},multiplayer);}
    FriendsService(Gateway gateway,MultiplayerSession multiplayer){this.gateway=gateway;this.multiplayer=multiplayer;worker.scheduleWithFixedDelay(this::poll,0,15,TimeUnit.SECONDS);}
    private static View empty(UUID id,long revision){return new View(id,false,false,"",List.of(),List.of(),List.of(),List.of(),List.of(),id==null?"Sign in to add friends and exchange lobby invitations.":"Loading Friends…",revision);}
    public View view(){return view;}
    /** Clear all previous-account data immediately, even while an old request is still in flight. */
    public synchronized void tick(){UUID id=gateway.accountId();if(!Objects.equals(id,view.owner())){view=empty(id,view.revision()+1);pendingJoin=null;nextRetry=0;if(id!=null)refresh();}}
    public void shareCode(String code){selectedCode=code==null?"":code;}
    private record LobbyShare(String code,String id,String title,boolean locked){}
    private LobbyShare lobby(){
        var state=multiplayer.view();if(!state.hosting()||state.state()!=MultiplayerSession.State.CONNECTED||state.snapshot()==null)return null;
        var lobby=state.snapshot().lobby();if(lobby.phase()!=FlightNetwork.Phase.LOBBY||lobby.players().size()>=lobby.maximum())return null;
        try{String current=multiplayer.lobbyCode();var target=LobbyCode.decode(current);String code=selectedCode;
            if(code.isEmpty()||!LobbyCode.decode(code).lobbyId().equals(target.lobbyId()))code=current;
            return new LobbyShare(LobbyCode.canonical(code),target.lobbyId(),lobby.title(),lobby.locked());
        }catch(IllegalArgumentException bad){return null;}
    }
    public boolean canInvite(){return lobby()!=null;}
    private static JsonObject params(Object... values){var j=new JsonObject();for(int i=0;i<values.length;i+=2){String key=(String)values[i];Object v=values[i+1];if(v instanceof Boolean b)j.addProperty(key,b);else j.addProperty(key,v==null?null:v.toString());}return j;}
    private JsonObject lobbyParams(){LobbyShare share=lobby();return params("p_hosting",share!=null,"p_lobby_id",share==null?"":share.id(),"p_title",share==null?"":share.title());}
    private boolean current(UUID id){return !closed&&Objects.equals(id,gateway.accountId())&&Objects.equals(id,view.owner());}
    private synchronized void status(UUID id,String message,boolean complete){if(id==null||!current(id))return;var v=view;view=new View(id,v.available(),complete?false:v.busy(),v.code(),v.friends(),v.incoming(),v.outgoing(),v.invites(),v.results(),message,v.revision()+1);}
    private static String text(JsonObject j,String key){return j.has(key)&&j.get(key).isJsonPrimitive()?j.get(key).getAsString():"";}
    private static boolean flag(JsonObject j,String key){return j.has(key)&&j.get(key).isJsonPrimitive()&&j.get(key).getAsBoolean();}
    private static List<Person> people(JsonArray list){if(list.size()>200)throw new IllegalArgumentException();var result=new ArrayList<Person>();for(var e:list){var p=e.getAsJsonObject();String name=alxb.flighttraining.game.profiles.PilotProfile.validateName(text(p,"name")),code=text(p,"code");if(!code.matches("[0-9A-F]{12}"))throw new IllegalArgumentException();result.add(new Person(UUID.fromString(text(p,"id")),name,code,flag(p,"online"),flag(p,"hosting"),text(p,"title")));}return List.copyOf(result);}
    private void snapshot(UUID id)throws IOException{
        var json=gateway.call("snapshot",lobbyParams()).getAsJsonObject();
        try{
            var self=json.getAsJsonObject("self");if(!UUID.fromString(text(self,"id")).equals(id))throw new IllegalArgumentException();String code=text(self,"code");if(!code.matches("[0-9A-F]{12}"))throw new IllegalArgumentException();
            var friends=people(json.getAsJsonArray("friends"));var incoming=people(json.getAsJsonArray("incoming"));var outgoing=people(json.getAsJsonArray("outgoing"));var invitations=new ArrayList<Invitation>();var list=json.getAsJsonArray("invites");if(list.size()>200)throw new IllegalArgumentException();
            for(var e:list){var p=e.getAsJsonObject();String kind=text(p,"kind");if(!Set.of("invite","join_request").contains(kind))throw new IllegalArgumentException();invitations.add(new Invitation(UUID.fromString(text(p,"id")),UUID.fromString(text(p,"from_id")),alxb.flighttraining.game.profiles.PilotProfile.validateName(text(p,"name")),kind,text(p,"title"),flag(p,"locked"),text(p,"expires")));}
            synchronized(this){if(!current(id))return;var old=view;view=new View(id,true,old.busy(),code,friends,incoming,outgoing,List.copyOf(invitations),old.results(),"Friends up to date.",old.revision()+1);nextRetry=0;}
        }catch(RuntimeException bad){throw new IOException("Friends service returned invalid player data.");}
    }
    private void poll(){
        tick();UUID id=view.owner();if(closed||id==null||view.busy()||System.nanoTime()<nextRetry)return;
        try{snapshot(id);}catch(IOException|RuntimeException error){failed(id,error,false);}
    }
    private void failed(UUID id,Exception error,boolean complete){
        String message=error instanceof IOException?error.getMessage():"Friends request failed. Refresh and try again.";
        if(message==null)message="Friends request failed. Check your connection.";
        synchronized(this){if(current(id)){nextRetry=System.nanoTime()+60_000_000_000L;status(id,message,complete);}}
    }
    public synchronized void refresh(){
        if(closed||refreshQueued)return;refreshQueued=true;worker.execute(()->{tick();UUID id=view.owner();try{if(id!=null)snapshot(id);}catch(IOException|RuntimeException error){failed(id,error,false);}finally{synchronized(this){refreshQueued=false;}}});
    }
    private interface Operation {String run(UUID id)throws IOException;}
    private synchronized void submit(Operation action){
        tick();UUID id=view.owner();if(closed||id==null||view.busy())return;var v=view;view=new View(id,v.available(),true,v.code(),v.friends(),v.incoming(),v.outgoing(),v.invites(),v.results(),"Sending request…",v.revision()+1);
        worker.execute(()->{try{if(!current(id))return;String message=action.run(id);if(!current(id))return;snapshot(id);status(id,message,true);}catch(IOException|RuntimeException error){failed(id,error,true);}});
    }
    public void find(String query){String value=query.strip();if(value.length()<1||value.length()>40){status(view.owner(),"Enter an exact pilot name or a friend code.",false);return;}
        submit(id->{var found=people(gateway.call("find",params("p_query",value)).getAsJsonArray());synchronized(this){if(current(id)){var v=view;view=new View(id,v.available(),v.busy(),v.code(),v.friends(),v.incoming(),v.outgoing(),v.invites(),found,v.message(),v.revision()+1);}}return found.isEmpty()?"No player found. Ask them to sign in and open Friends first.":"Select a player and send a friend request.";});
    }
    public void friend(UUID peer,String action){if(!Set.of("request","accept","decline","cancel","remove").contains(action))return;submit(id->{gateway.call("friend",params("p_peer",peer,"p_action",action));return switch(action){case "request"->"Friend request sent.";case "accept"->"Friend request accepted.";case "remove"->"Friend removed.";default->"Request dismissed.";};});}
    private JsonObject invitationParams(UUID peer,String kind)throws IOException{
        var share=lobby();if(kind.equals("invite")&&share==null)throw new IOException("Create a lobby with an empty slot before inviting a friend.");
        return params("p_peer",peer,"p_kind",kind,"p_code",share==null?"":share.code(),"p_lobby_id",share==null?"":share.id(),"p_title",share==null?"":share.title(),"p_locked",share!=null&&share.locked());
    }
    public void invite(UUID peer,boolean requestJoin){submit(id->{snapshot(id);gateway.call("send_invite",invitationParams(peer,requestJoin?"join_request":"invite"));return requestJoin?"Join request sent. The host can approve and invite you.":"Lobby invitation sent. Your friend can accept it in Friends.";});}
    public void reply(UUID invitation,boolean accept,String password){
        if(password==null||password.length()>64){status(view.owner(),"Lobby passwords can contain up to 64 characters.",false);return;}
        submit(id->{Invitation item=view.invites().stream().filter(i->i.id().equals(invitation)).findFirst().orElseThrow(()->new IOException("This invitation is no longer available."));
            snapshot(id);var share=lobby();var body=params("p_invite",invitation,"p_accept",accept,"p_code",share==null?"":share.code(),"p_lobby_id",share==null?"":share.id(),"p_title",share==null?"":share.title(),"p_locked",share!=null&&share.locked());
            var result=gateway.call("reply_invite",body).getAsJsonObject();String code=text(result,"join_code");
            if(accept&&item.kind().equals("invite")){
                if(code.isEmpty())throw new IOException("Invitation did not contain a lobby connection.");code=LobbyCode.canonical(code);
                synchronized(this){if(current(id))pendingJoin=new Join(code,password);}
            }
            return !accept?"Invitation declined.":item.kind().equals("join_request")?"Join request approved. A lobby invitation was sent.":"Invitation accepted. Connecting to the lobby…";
        });
    }
    /** Consume only on the render thread. A user explicitly accepted the invitation first. */
    public synchronized Join takeJoin(){tick();Join result=pendingJoin;pendingJoin=null;return result;}
    public boolean awaitIdle(long millis)throws InterruptedException{try{worker.submit(()->{}).get(millis,TimeUnit.MILLISECONDS);return true;}catch(ExecutionException|TimeoutException|RejectedExecutionException error){return false;}}
    @Override public synchronized void close(){closed=true;pendingJoin=null;worker.shutdownNow();}
}
