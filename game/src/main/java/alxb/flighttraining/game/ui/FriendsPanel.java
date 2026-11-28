package alxb.flighttraining.game.ui;

import alxb.flighttraining.game.friends.FriendsService;
import alxb.flighttraining.game.friends.FriendsService.*;
import alxb.flighttraining.game.network.MultiplayerSession;
import com.jme3.input.KeyInput;
import com.jme3.input.event.KeyInputEvent;
import java.awt.*;
import java.util.*;
import java.util.List;

/** Friend requests and explicit accept-to-join actions. Lobby passwords never go to Supabase. */
public final class FriendsPanel {
    private final FriendsService service;private final MultiplayerSession network;private final Runnable signIn;private final ClipboardAccess clipboard;
    private final Map<String,Rectangle> hits=new LinkedHashMap<>();
    private String query="",password="",focus="",notice="";private boolean command,selectAll,searching,outgoing,dirty=true;
    private int friendPage,requestPage,invitePage;private UUID selectedPerson,selectedInvite;private long revision=-1;
    public FriendsPanel(FriendsService service,MultiplayerSession network,Runnable signIn){this(service,network,signIn,new DesktopClipboard());}
    FriendsPanel(FriendsService service,MultiplayerSession network,Runnable signIn,ClipboardAccess clipboard){this.service=service;this.network=network;this.signIn=signIn;this.clipboard=clipboard;}
    public void open(){leave();service.refresh();}
    public void leave(){password="";focus="";command=selectAll=false;dirty=true;}
    public boolean update(){service.tick();boolean changed=dirty||revision!=service.view().revision();revision=service.view().revision();dirty=false;return changed;}
    public static String code(String value){return value.length()==12?value.substring(0,4)+"-"+value.substring(4,8)+"-"+value.substring(8):value;}
    private Person selected(){var v=service.view();return (searching?v.results():v.friends()).stream().filter(p->p.id().equals(selectedPerson)).findFirst().orElse(null);}
    private Invitation invitation(){return service.view().invites().stream().filter(i->i.id().equals(selectedInvite)).findFirst().orElse(null);}
    private void accept(){var item=invitation();if(item==null)return;service.reply(item.id(),true,password);password="";focus="";}
    public void click(float x,float y){for(var hit:hits.entrySet())if(hit.getValue().contains(x,y)){
        String id=hit.getKey();focus="";selectAll=false;notice="";
        if(id.equals("query")||id.equals("password"))focus=id;
        else if(id.equals("search")){searching=true;friendPage=0;selectedPerson=null;service.find(query);}
        else if(id.equals("my-friends")){searching=false;friendPage=0;selectedPerson=null;}
        else if(id.equals("refresh"))service.refresh();else if(id.equals("signin"))signIn.run();
        else if(id.equals("copy")){try{clipboard.write(code(service.view().code()));notice="Friend code copied.";}catch(Exception error){notice="Clipboard unavailable. Your friend code is shown above.";}}
        else if(id.startsWith("person:"))selectedPerson=UUID.fromString(id.substring(7));
        else if(id.startsWith("invitation:")){selectedInvite=UUID.fromString(id.substring(11));password="";}
        else if(id.equals("add")){var p=selected();if(p!=null)service.friend(p.id(),"request");}
        else if(id.equals("invite")||id.equals("ask-join")){var p=selected();if(p!=null)service.invite(p.id(),id.equals("ask-join"));}
        else if(id.equals("remove")){var p=selected();if(p!=null)service.friend(p.id(),"remove");}
        else if(id.equals("incoming")||id.equals("outgoing")){outgoing=id.equals("outgoing");requestPage=0;}
        else if(id.startsWith("friend:")){String[] parts=id.split(":");service.friend(UUID.fromString(parts[2]),parts[1]);}
        else if(id.equals("accept-invite"))accept();else if(id.equals("decline-invite")){var item=invitation();if(item!=null)service.reply(item.id(),false,"");password="";}
        else if(id.equals("friends-prev"))friendPage=Math.max(0,friendPage-1);else if(id.equals("friends-next"))friendPage++;
        else if(id.equals("requests-prev"))requestPage=Math.max(0,requestPage-1);else if(id.equals("requests-next"))requestPage++;
        else if(id.equals("invites-prev"))invitePage=Math.max(0,invitePage-1);else if(id.equals("invites-next"))invitePage++;
        dirty=true;return;
    }focus="";}
    public boolean key(KeyInputEvent event){int k=event.getKeyCode();if(k==KeyInput.KEY_LMETA||k==KeyInput.KEY_RMETA||k==KeyInput.KEY_LCONTROL||k==KeyInput.KEY_RCONTROL){command=event.isPressed();return !focus.isEmpty();}
        if(!event.isPressed())return !focus.isEmpty();if(service.view().busy())return true;
        if(k==KeyInput.KEY_TAB&&service.view().owner()!=null){var item=invitation();focus=focus.equals("query")&&item!=null&&item.kind().equals("invite")&&item.locked()?"password":"query";selectAll=false;dirty=true;return true;}
        if(focus.isEmpty())return false;if(k==KeyInput.KEY_ESCAPE){focus="";password="";dirty=true;return true;}
        if(k==KeyInput.KEY_RETURN){if(!event.isRepeating()){if(focus.equals("query")){searching=true;friendPage=0;service.find(query);}else accept();}dirty=true;return true;}
        String value=focus.equals("query")?query:password;
        if(command&&k==KeyInput.KEY_A)selectAll=true;
        else if(command&&k==KeyInput.KEY_V){try{String text=clipboard.read();if(text!=null)value=append(value,text);}catch(Exception ignored){}}
        else if(k==KeyInput.KEY_BACK){value=selectAll?"":value.isEmpty()?"":value.substring(0,value.offsetByCodePoints(value.length(),-1));selectAll=false;}
        else if(!command&&event.getKeyChar()!=0&&!Character.isISOControl(event.getKeyChar()))value=append(value,String.valueOf(event.getKeyChar()));
        if(focus.equals("query"))query=value;else password=value;dirty=true;return true;
    }
    private String append(String value,String text){var b=new StringBuilder(selectAll?"":value);selectAll=false;int max=focus.equals("query")?40:64;for(char c:text.toCharArray())if(b.length()<max&&!Character.isISOControl(c))b.append(c);return b.toString();}
    private static void text(Graphics2D g,String value,int x,int y,int size,Color color,boolean bold){MenuTheme.text(g,value,x,y,size,color,bold);}
    private void button(Graphics2D g,String id,String label,int x,int y,int w,int h,boolean primary,boolean enabled){var r=new Rectangle(x,y,w,h);if(enabled)hits.put(id,r);MenuTheme.button(g,r,label,primary,false,enabled);}
    private void field(Graphics2D g,String id,int x,int y,int w){var r=new Rectangle(x,y,w,42);if(!service.view().busy())hits.put(id,r);g.setColor(new Color(8,16,23));g.fill(r);g.setColor(focus.equals(id)?MenuTheme.ACCENT:MenuTheme.LINE);g.draw(r);var edit=(Graphics2D)g.create();edit.clipRect(x+8,y+3,w-16,36);if(selectAll&&focus.equals(id)){edit.setColor(new Color(52,81,110));edit.fillRect(x+8,y+3,w-16,36);}String value=id.equals("password")?"•".repeat(password.length()):query;text(edit,value+(focus.equals(id)?"|":""),x+12,y+28,18,MenuTheme.TEXT,false);edit.dispose();}
    private static String shortText(Graphics2D g,String value,int width,int size){g.setFont(new Font(MenuTheme.FONT,Font.PLAIN,size));String result=value;while(result.length()>1&&g.getFontMetrics().stringWidth(result+"…")>width)result=result.substring(0,result.length()-1);return result.equals(value)?result:result+"…";}
    private void row(Graphics2D g,String id,int x,int y,int w,int h,boolean selected,boolean enabled){var r=new Rectangle(x,y,w,h);if(enabled)hits.put(id,r);g.setColor(selected?new Color(221,171,85,30):new Color(255,255,255,5));g.fill(r);if(selected){g.setColor(MenuTheme.ACCENT);g.fillRect(x,y,3,h);}}
    private int page(Graphics2D g,String prefix,int current,int count,int size,int x,int y,int width){int pages=Math.max(1,(count+size-1)/size);current=Math.min(current,pages-1);button(g,prefix+"-prev","‹",x,y,44,28,false,current>0);button(g,prefix+"-next","›",x+width-44,y,44,28,false,current+1<pages);MenuTheme.right(g,(current+1)+" / "+pages,x+width/2+24,y+20,13,MenuTheme.MUTED);return current;}
    public void draw(Graphics2D g){update();hits.clear();var v=service.view();boolean enabled=!v.busy();
        text(g,"Friends",54,146,36,MenuTheme.TEXT,true);text(g,"Add pilots, accept requests and join a friend's lobby.",56,179,17,MenuTheme.MUTED,false);
        if(v.owner()==null){MenuTheme.panel(g,54,214,1492,588);text(g,"Sign in to use Friends",86,276,29,MenuTheme.TEXT,true);MenuTheme.paragraph(g,"Friend requests and lobby invitations belong to your online account.",88,325,1100,19,MenuTheme.MUTED);button(g,"signin","SIGN IN / REGISTER",88,382,350,52,true,true);return;}
        button(g,"refresh","REFRESH",864,123,162,40,false,enabled);
        text(g,"YOUR FRIEND CODE",1055,132,11,MenuTheme.MUTED,true);text(g,code(v.code()),1055,159,20,MenuTheme.TEXT,true);button(g,"copy","COPY",1390,123,156,40,false,!v.code().isEmpty());
        MenuTheme.panel(g,54,214,462,588);MenuTheme.panel(g,542,214,484,588);MenuTheme.panel(g,1052,214,494,588);
        text(g,"EXACT PILOT NAME OR FRIEND CODE",78,245,11,MenuTheme.MUTED,true);field(g,"query",78,260,286);button(g,"search","SEARCH",376,260,114,42,true,enabled&&v.available());
        text(g,searching?"SEARCH RESULTS":"YOUR FRIENDS",78,334,12,MenuTheme.ACCENT,true);if(searching)button(g,"my-friends","MY FRIENDS",346,312,144,30,false,true);
        var people=searching?v.results():v.friends();friendPage=Math.min(friendPage,Math.max(0,(people.size()-1)/6));
        for(int i=friendPage*6;i<Math.min(people.size(),friendPage*6+6);i++){var p=people.get(i);int y=350+(i%6)*53;row(g,"person:"+p.id(),68,y,434,48,p.id().equals(selectedPerson),enabled);text(g,shortText(g,p.name(),290,19),82,y+21,19,MenuTheme.TEXT,true);text(g,searching?code(p.code()):p.hosting()?"IN A LOBBY  ·  "+shortText(g,p.title(),240,12):p.online()?"ONLINE":"OFFLINE",84,y+40,12,p.online()?MenuTheme.ACCENT:MenuTheme.MUTED,false);}
        if(people.isEmpty())MenuTheme.paragraph(g,searching?"No results. Search by exact pilot name or friend code.":"No friends yet. Search for another pilot above.",82,384,394,17,MenuTheme.MUTED);
        friendPage=page(g,"friends",friendPage,people.size(),6,78,688,412);var selected=selected();
        if(searching){boolean pending=selected!=null&&(v.friends().stream().anyMatch(p->p.id().equals(selected.id()))||v.incoming().stream().anyMatch(p->p.id().equals(selected.id()))||v.outgoing().stream().anyMatch(p->p.id().equals(selected.id())));button(g,"add",pending?"FRIEND / REQUEST ALREADY EXISTS":"SEND FRIEND REQUEST",78,739,412,43,true,enabled&&selected!=null&&!pending&&v.available());}
        else{button(g,"invite","INVITE",78,739,116,43,true,enabled&&selected!=null&&service.canInvite());button(g,"ask-join","ASK TO JOIN",204,739,170,43,false,enabled&&selected!=null&&selected.hosting());button(g,"remove","REMOVE",384,739,106,43,false,enabled&&selected!=null);}
        text(g,"FRIEND REQUESTS",566,252,12,MenuTheme.ACCENT,true);button(g,"incoming","RECEIVED ("+v.incoming().size()+")",566,270,210,36,!outgoing,true);button(g,"outgoing","SENT ("+v.outgoing().size()+")",790,270,210,36,outgoing,true);
        var requests=outgoing?v.outgoing():v.incoming();requestPage=Math.min(requestPage,Math.max(0,(requests.size()-1)/4));
        for(int i=requestPage*4;i<Math.min(requests.size(),requestPage*4+4);i++){var p=requests.get(i);int y=326+(i%4)*94;text(g,shortText(g,p.name(),410,20),568,y+22,20,MenuTheme.TEXT,true);text(g,code(p.code()),568,y+43,12,MenuTheme.MUTED,false);
            if(outgoing)button(g,"friend:cancel:"+p.id(),"CANCEL REQUEST",568,y+52,430,30,false,enabled);else{button(g,"friend:accept:"+p.id(),"ACCEPT",568,y+52,208,30,true,enabled);button(g,"friend:decline:"+p.id(),"DECLINE",790,y+52,208,30,false,enabled);}}
        if(requests.isEmpty())text(g,outgoing?"No sent requests.":"No new requests.",568,354,18,MenuTheme.MUTED,false);requestPage=page(g,"requests",requestPage,requests.size(),4,566,744,434);
        text(g,"LOBBY INVITATIONS ("+v.invites().size()+")",1076,252,12,MenuTheme.ACCENT,true);invitePage=Math.min(invitePage,Math.max(0,(v.invites().size()-1)/4));
        for(int i=invitePage*4;i<Math.min(v.invites().size(),invitePage*4+4);i++){var item=v.invites().get(i);int y=276+(i%4)*58;row(g,"invitation:"+item.id(),1066,y,466,52,item.id().equals(selectedInvite),enabled);text(g,shortText(g,item.name(),420,19),1080,y+22,19,MenuTheme.TEXT,true);text(g,item.kind().equals("invite")?"INVITED YOU TO A LOBBY":"ASKS TO JOIN YOUR LOBBY",1082,y+43,11,MenuTheme.MUTED,true);}
        if(v.invites().isEmpty())MenuTheme.paragraph(g,"No invitations. Host a multiplayer lobby, then select a friend and press Invite.",1080,301,430,17,MenuTheme.MUTED);
        invitePage=page(g,"invites",invitePage,v.invites().size(),4,1076,520,444);var item=invitation();
        if(item!=null){text(g,shortText(g,item.title(),440,21),1076,584,21,MenuTheme.TEXT,true);text(g,item.kind().equals("join_request")?"Approve to send this friend your lobby invitation.":network.view().state()==MultiplayerSession.State.CONNECTED?"Joining leaves your current lobby.":"Accept to connect. No lobby code to copy.",1078,614,14,MenuTheme.MUTED,false);
            if(item.kind().equals("invite")&&item.locked()){text(g,"LOBBY PASSWORD",1078,644,11,MenuTheme.MUTED,true);field(g,"password",1076,652,444);}
            button(g,"accept-invite",item.kind().equals("join_request")?"ALLOW & INVITE":"ACCEPT & JOIN",1076,730,214,44,true,enabled&&(!item.kind().equals("join_request")||service.canInvite()));button(g,"decline-invite","DECLINE",1304,730,216,44,false,enabled);
        }
        text(g,notice.isEmpty()?v.message():notice,56,823,14,MenuTheme.MUTED,false);
    }
}
