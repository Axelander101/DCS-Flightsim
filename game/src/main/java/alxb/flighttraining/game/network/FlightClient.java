package alxb.flighttraining.game.network;

import alxb.flighttraining.game.network.FlightNetwork.*;
import com.google.gson.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.Consumer;

/** One connection, bounded reliable control messages and a replaceable latest aircraft state. */
public final class FlightClient implements AutoCloseable {
    private final Socket socket=new Socket();
    private final Consumer<Snapshot> snapshots;private final Consumer<String> notices,disconnected;
    private final ArrayBlockingQueue<JsonObject> control=new ArrayBlockingQueue<>(16);
    private final AtomicReference<Pose> latest=new AtomicReference<>();
    private final AtomicReference<JsonObject> combatLatest=new AtomicReference<>();
    private volatile boolean closed;
    private volatile Snapshot snapshot;
    private volatile long pingMillis;
    private volatile String id,lobbyCode="";
    public String lobbyCode(){return lobbyCode;}
    public FlightClient(Consumer<Snapshot> snapshots,Consumer<String> notices,Consumer<String> disconnected){this.snapshots=snapshots;this.notices=notices;this.disconnected=disconnected;}
    public void connect(String address,int port,String name,String password,String hostToken)throws IOException {
        connect(address,port,name,password,hostToken,"");
    }
    public void connect(String address,int port,String name,String password,String hostToken,String expectedLobbyId)throws IOException {
        name=Wire.text(name,24);if(password.length()>64)throw new IllegalArgumentException("Password is too long");
        if(closed)throw new IOException("Connection cancelled");socket.connect(new InetSocketAddress(address,port),4000);socket.setTcpNoDelay(true);socket.setKeepAlive(true);socket.setSoTimeout(6000);
        var input=new DataInputStream(new BufferedInputStream(socket.getInputStream()));var output=new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
        var challenge=Wire.read(input);if(!Wire.string(challenge,"type",20).equals("challenge")||challenge.get("version").getAsInt()!=FlightNetwork.VERSION)throw new IOException("Different multiplayer version");
        if(!expectedLobbyId.isEmpty()){
            if(!challenge.has("lobbyId"))throw new IOException("This host needs the lobby-code update. Update both games.");
            if(!Wire.matches(expectedLobbyId,Wire.string(challenge,"lobbyId",8)))throw new IOException("This lobby code has expired. Ask the host for a new code.");
        }
        if(challenge.has("lobbyCode")){
            try{lobbyCode=LobbyCode.canonical(Wire.string(challenge,"lobbyCode",80));}catch(IllegalArgumentException invalid){throw new IOException("Invalid lobby invitation from host");}
        }
        if(!expectedLobbyId.isEmpty())lobbyCode=LobbyCode.encode(address,port,expectedLobbyId);
        var hello=Wire.packet("hello","name",name);hello.addProperty("version",FlightNetwork.VERSION);hello.addProperty("proof",Wire.proof(password,Wire.string(challenge,"nonce",64),name));if(!hostToken.isEmpty())hello.addProperty("hostToken",hostToken);Wire.write(output,hello);
        var welcome=Wire.read(input);String type=Wire.string(welcome,"type",20);if(type.equals("error"))throw new IOException(Wire.string(welcome,"message",100));if(!type.equals("welcome"))throw new IOException("Invalid host response");
        id=Wire.string(welcome,"id",40);
        Thread reader=new Thread(()->read(input),"DSC-network-client-read");reader.setDaemon(true);reader.start();Thread writer=new Thread(()->write(output),"DSC-network-client-write");writer.setDaemon(true);writer.start();
    }
    public String id(){return id;}
    public long pingMillis(){return pingMillis;}
    public Snapshot snapshot(){return snapshot;}
    private void read(DataInputStream input){
        String reason="Connection to the host was lost.";
        try{while(!closed){
            var packet=Wire.read(input);String type=Wire.string(packet,"type",20);
            switch(type){
                case "snapshot"->{
                    Lobby lobby=Wire.JSON.fromJson(packet.get("lobby"),Lobby.class);
                    Pose[] states=Wire.JSON.fromJson(packet.get("poses"),Pose[].class);
                    if(lobby==null||lobby.region()==null||lobby.phase()==null||lobby.players()==null||lobby.players().size()>8||lobby.maximum()<2||lobby.maximum()>8||states==null||states.length>8)throw new IOException("Invalid lobby snapshot");
                    Wire.text(lobby.title(),40);Set<String> ids=new HashSet<>();Set<Integer> slots=new HashSet<>();
                    for(Player player:lobby.players()){Wire.text(player.id(),40);Wire.text(player.name(),24);if(!ids.add(player.id())||player.slot()<0||player.slot()>7||!slots.add(player.slot()))throw new IOException("Invalid lobby players");}
                    if(!ids.contains(id))throw new IOException("You were removed from the lobby");for(Pose pose:states)if(!ids.contains(pose.id()))throw new IOException("Invalid aircraft owner");
                    Battle battle=packet.has("battle")?Wire.JSON.fromJson(packet.get("battle"),Battle.class):Battle.empty();
                    if(battle==null)throw new IOException("Missing combat state");Set<String> damaged=new HashSet<>();Set<Long> projectiles=new HashSet<>();long eventId=0;
                    for(AircraftDamage d:battle.aircraft())if(!ids.contains(d.id())||!damaged.add(d.id())||(!d.radarLock().isEmpty()&&!ids.contains(d.radarLock())))throw new IOException("Invalid damage owner");
                    for(Projectile p:battle.missiles())if(!projectiles.add(p.id()))throw new IOException("Duplicate projectile");
                    for(CombatEvent e:battle.events()){if(e.id()<=eventId)throw new IOException("Unordered combat events");eventId=e.id();}
                    if(!lobby.combat()&&(!battle.aircraft().isEmpty()||!battle.missiles().isEmpty()||!battle.events().isEmpty()))throw new IOException("Combat in formation lobby");
                    snapshot=new Snapshot(lobby,List.of(states),System.nanoTime(),battle);snapshots.accept(snapshot);
                }
                case "pong"->{long elapsed=System.nanoTime()-packet.get("sent").getAsLong();if(elapsed>=0&&elapsed<10_000_000_000L)pingMillis=elapsed/1_000_000;}
                case "notice"->notices.accept(Wire.string(packet,"message",100));
                case "error"->throw new IOException(Wire.string(packet,"message",100));
                default->throw new IOException("Unexpected host message");
            }
        }}catch(IOException|RuntimeException error){if(error instanceof SocketTimeoutException)reason="Host timed out.";}
        finally{boolean report=!closed;close();if(report)disconnected.accept(reason);}
    }
    private void write(DataOutputStream output){
        long nextPose=0,nextPing=0;
        try{while(!closed){
            JsonObject packet=control.poll(10,TimeUnit.MILLISECONDS);if(packet!=null)Wire.write(output,packet);
            long now=System.nanoTime();if(now>=nextPose){Pose state=latest.getAndSet(null);if(state!=null)Wire.write(output,Wire.packet("pose","state",state));JsonObject combat=combatLatest.getAndSet(null);if(combat!=null)Wire.write(output,combat);nextPose=now+50_000_000L;}
            if(now>=nextPing){Wire.write(output,Wire.packet("ping","sent",now));nextPing=now+1_000_000_000L;}
        }}catch(IOException|InterruptedException error){try{socket.close();}catch(IOException ignored){}}
    }
    private void send(JsonObject packet){if(!closed&&!control.offer(packet))close();}
    public void combat(boolean gun,boolean launch,int commands,String lock,boolean crash){
        if(closed)return;
        // Preserve edges while replacing held input between network ticks.
        combatLatest.updateAndGet(old->{var p=Wire.packet("combat","gun",gun);p.addProperty("launch",launch||(old!=null&&old.get("launch").getAsBoolean()));p.addProperty("commands",commands|(old==null?0:old.get("commands").getAsInt()));p.addProperty("lock",lock);p.addProperty("crash",crash||(old!=null&&old.get("crash").getAsBoolean()));return p;});
    }
    public void ready(boolean value){send(Wire.packet("ready","value",value));}
    public void startFlight(){send(Wire.packet("start"));}
    public void loaded(){send(Wire.packet("loaded"));}
    public void pose(Pose state){if(!closed)latest.set(state);}
    @Override public void close(){closed=true;try{socket.close();}catch(IOException ignored){}control.clear();latest.set(null);}
}
