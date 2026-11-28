package alxb.flighttraining.game.network;

import alxb.flighttraining.game.network.FlightNetwork.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Async lifecycle owner. Published snapshots contain data only; engine mutations happen in FlightTraining. */
public final class MultiplayerSession implements AutoCloseable {
    public enum State { IDLE, CONNECTING, CONNECTED, ERROR }
    public record View(State state,String message,Snapshot snapshot,String selfId,boolean hosting,String address,long pingMillis) {
        public Player self(){return snapshot==null?null:snapshot.lobby().players().stream().filter(p->p.id().equals(selfId)).findFirst().orElse(null);}
        public boolean flight(){return snapshot!=null&&snapshot.lobby().phase()!=Phase.LOBBY;}
    }
    private final Object lock=new Object();
    private final ExecutorService jobs=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"DSC-network-connect");t.setDaemon(true);return t;});
    private long generation;
    private FlightServer server;private FlightClient client;
    private volatile View view=new View(State.IDLE,"Create a lobby or join a host.",null,"",false,"",0);
    private volatile List<LanDiscovery.Found> discovered=List.of();
    private volatile boolean scanning;
    private volatile String discoveryStatus="";
    private boolean closed;
    public View view(){View current=view;FlightClient connection; synchronized(lock){connection=client;}return new View(current.state(),current.message(),current.snapshot(),current.selfId(),current.hosting(),current.address(),connection==null?0:connection.pingMillis());}
    public List<LanDiscovery.Found> discovered(){return discovered;}
    public boolean scanning(){return scanning;}
    public String discoveryStatus(){return discoveryStatus;}
    public void scan(){
        synchronized(lock){if(scanning||closed)return;scanning=true;discoveryStatus="Searching local network…";}
        jobs.execute(()->{try{discovered=LanDiscovery.scan();discoveryStatus=discovered.isEmpty()?"No LAN lobbies found. You can still join by address.":discovered.size()+" local lobby/lobbies found.";}
            catch(IOException error){discoveryStatus="LAN search unavailable. Join by address instead.";}finally{scanning=false;}});
    }
    public String lobbyCode(){synchronized(lock){return client==null?"":client.lobbyCode();}}
    public void joinCode(String code,String name,String password){
        var target=LobbyCode.decode(code);connect(false,null,target.address(),target.port(),name,password,target.lobbyId());
    }
    public void host(Config config,int port,String name,String password){connect(true,config,"127.0.0.1",port,name,password);}
    public record Endpoint(String address,int port){}
    public static Endpoint endpoint(String input,int fallback){
        String address=Wire.text(input,128);int port=fallback;
        if(address.startsWith("[")){
            int end=address.indexOf(']');if(end<0)throw new IllegalArgumentException("Invalid IPv6 address.");
            String tail=address.substring(end+1);if(!tail.isEmpty()){if(!tail.startsWith(":"))throw new IllegalArgumentException("Invalid host address.");port=Integer.parseInt(tail.substring(1));}address=address.substring(1,end);
        }else if(address.indexOf(':')>=0&&address.indexOf(':')==address.lastIndexOf(':')){
            int colon=address.indexOf(':');port=Integer.parseInt(address.substring(colon+1));address=address.substring(0,colon);
        }
        if(address.isBlank()||address.contains("/")||address.chars().anyMatch(Character::isWhitespace)||port<1||port>65535)throw new IllegalArgumentException("Enter a valid host address and port.");
        return new Endpoint(address,port);
    }
    public void join(String address,int port,String name,String password){var target=endpoint(address,port);connect(false,null,target.address(),target.port(),name,password);}
    private void connect(boolean hosting,Config config,String address,int port,String name,String password){
        connect(hosting,config,address,port,name,password,"");
    }
    private void connect(boolean hosting,Config config,String address,int port,String name,String password,String expectedLobbyId){
        Wire.text(name,24);if(port<1||port>65535||password.length()>64)throw new IllegalArgumentException("Enter a valid port and password (up to 64 characters).");
        final long token;
        synchronized(lock){if(closed)return;clear();token=++generation;view=new View(State.CONNECTING,hosting?"Creating lobby…":"Connecting…",null,"",hosting,"",0);}
        jobs.execute(()->{
            FlightServer hosted=null;FlightClient connection=null;
            try{
                synchronized(lock){if(generation!=token||closed)return;}
                if(hosting)hosted=new FlightServer(config,port,password);
                String shown=(hosting?LanDiscovery.localAddress():address)+":"+(hosted==null?port:hosted.port());
                connection=new FlightClient(snapshot->{synchronized(lock){if(generation!=token)return;view=new View(State.CONNECTED,"Connected",snapshot,client==null?"":client.id(),hosting,shown,0);}},
                    message->{synchronized(lock){if(generation==token){var old=view;view=new View(old.state(),message,old.snapshot(),old.selfId(),old.hosting(),old.address(),0);}}},
                    message->failed(token,message));
                synchronized(lock){if(generation!=token||closed){connection.close();if(hosted!=null)hosted.close();return;}server=hosted;client=connection;}
                connection.connect(address,hosted==null?port:hosted.port(),name,password,hosted==null?"":hosted.hostToken(),expectedLobbyId);
                synchronized(lock){if(generation==token&&view.snapshot()==null)view=new View(State.CONNECTED,"Waiting for lobby…",null,connection.id(),hosting,shown,0);}
            }catch(IOException|RuntimeException error){
                if(connection!=null)connection.close();if(hosted!=null)hosted.close();
                String reason=error instanceof java.net.BindException?"Port is already in use. Choose another port.":
                    error instanceof java.net.UnknownHostException?"Host address not found.":error instanceof java.net.ConnectException?(expectedLobbyId.isEmpty()?"Could not connect. Check the address, port and host.":"Lobby not reachable. Check the code, host and network."):
                    error instanceof java.net.SocketTimeoutException?"Connection timed out.":error.getMessage();
                failed(token,reason==null?"Connection failed.":reason);
            }
        });
    }
    private void failed(long token,String message){synchronized(lock){if(generation!=token||closed)return;clear();view=new View(State.ERROR,message,null,"",false,"",0);}}
    public void combat(boolean gun,boolean launch,int commands,String lockTarget,boolean crash){synchronized(lock){if(client!=null)client.combat(gun,launch,commands,lockTarget,crash);}}
    public void ready(boolean value){synchronized(lock){if(client!=null)client.ready(value);}}
    public void startFlight(){synchronized(lock){if(client!=null)client.startFlight();}}
    public void loaded(){synchronized(lock){if(client!=null)client.loaded();}}
    public void pose(Pose pose){synchronized(lock){if(client!=null)client.pose(pose);}}
    public void disconnect(String reason){synchronized(lock){generation++;clear();view=new View(State.ERROR,Wire.text(reason,100),null,"",false,"",0);}}
    public void leave(){synchronized(lock){generation++;clear();view=new View(State.IDLE,"Disconnected. Create or join another lobby.",null,"",false,"",0);}}
    private void clear(){if(client!=null){client.close();client=null;}if(server!=null){server.close();server=null;}}
    @Override public void close(){synchronized(lock){closed=true;generation++;clear();}jobs.shutdownNow();}
}
