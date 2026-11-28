package alxb.flighttraining.game.network;

import alxb.flighttraining.game.network.FlightNetwork.*;
import com.google.gson.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Host-owned cooperative flight lobby. All simulation objects stay on their game's render thread. */
public final class FlightServer implements AutoCloseable {
    private final Config config;
    private final String lobbyId=UUID.randomUUID().toString().substring(0,8);
    private final String password,hostToken=UUID.randomUUID().toString();
    private final ServerSocket listener;
    private final ExecutorService workers=Executors.newCachedThreadPool(r->{Thread t=new Thread(r,"DSC-network-host");t.setDaemon(true);return t;});
    private final ScheduledExecutorService ticker=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"DSC-network-tick");t.setDaemon(true);return t;});
    private final Semaphore handshakes=new Semaphore(16);
    private final Map<String,Peer> peers=new LinkedHashMap<>();
    private final Set<Socket> sockets=ConcurrentHashMap.newKeySet();
    private NetworkBattle battle;
    private Phase phase=Phase.LOBBY;
    private long flightId;
    private long loadingSince;
    private volatile boolean closed;
    private DatagramSocket discovery;
    public FlightServer(Config config,int port,String password)throws IOException {
        this.config=config;this.password=Objects.requireNonNull(password);if(password.length()>64)throw new IllegalArgumentException("Password is too long");
        listener=new ServerSocket();listener.setReuseAddress(true);listener.bind(new InetSocketAddress(port));
        workers.execute(this::accept);ticker.scheduleAtFixedRate(this::broadcast,0,50,TimeUnit.MILLISECONDS);
        if(config.discoverable())try{
            discovery=new DatagramSocket(null);discovery.setReuseAddress(true);discovery.bind(new InetSocketAddress(FlightNetwork.DISCOVERY_PORT));discovery.setSoTimeout(1000);workers.execute(this::discover);
        }catch(IOException error){if(discovery!=null)discovery.close();discovery=null;System.err.println("LAN discovery unavailable; direct joining still works.");}
    }
    public int port(){return listener.getLocalPort();}
    public String hostToken(){return hostToken;}
    public String lobbyId(){return lobbyId;}
    public String lobbyCode(){return LobbyCode.encode(LanDiscovery.localAddress(),port(),lobbyId);}
    public synchronized Lobby lobby(){return new Lobby(config.title(),config.region(),config.maximum(),!password.isEmpty(),phase,flightId,
        peers.values().stream().map(p->new Player(p.id,p.name,p.slot,p.host,p.ready,p.loaded)).toList(),config.combat());}
    private void accept(){
        while(!closed)try{
            Socket socket=listener.accept();if(closed){socket.close();return;}if(!handshakes.tryAcquire()){socket.close();continue;}
            sockets.add(socket);try{workers.execute(()->handle(socket));}catch(RejectedExecutionException cancelled){handshakes.release();sockets.remove(socket);socket.close();}
        }catch(IOException error){if(!closed)System.err.println("Host listener stopped: "+error.getClass().getSimpleName());}
    }
    private void handle(Socket socket){
        Peer peer=null;boolean permit=true;
        try{
            socket.setTcpNoDelay(true);socket.setKeepAlive(true);socket.setSoTimeout(6000);
            var input=new DataInputStream(new BufferedInputStream(socket.getInputStream()));var output=new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
            String nonce=Wire.nonce();var challenge=Wire.packet("challenge","nonce",nonce);challenge.addProperty("version",FlightNetwork.VERSION);challenge.addProperty("lobbyId",lobbyId);challenge.addProperty("lobbyCode",lobbyCode());Wire.write(output,challenge);
            var hello=Wire.read(input);
            if(!Wire.string(hello,"type",20).equals("hello")||hello.get("version").getAsInt()!=FlightNetwork.VERSION)throw new IllegalArgumentException("Different multiplayer version");
            String name=Wire.string(hello,"name",24),proof=Wire.string(hello,"proof",64);
            if(!Wire.matches(Wire.proof(password,nonce,name),proof))throw new IllegalArgumentException("Incorrect lobby password");
            boolean host=hello.has("hostToken")&&Wire.matches(hostToken,hello.get("hostToken").getAsString());
            synchronized(this){
                if(closed||phase!=Phase.LOBBY)throw new IllegalArgumentException("This flight has already started");
                if(peers.size()>=config.maximum())throw new IllegalArgumentException("Lobby is full");
                if(host&&peers.values().stream().anyMatch(p->p.host))throw new IllegalArgumentException("Host is already connected");
                int slot=0;Set<Integer> occupied=new HashSet<>();for(Peer p:peers.values())occupied.add(p.slot);while(occupied.contains(slot))slot++;
                peer=new Peer(socket,output,name,slot,host);peers.put(peer.id,peer);
            }
            var welcome=Wire.packet("welcome","id",peer.id);Wire.write(output,welcome);
            Peer active=peer;workers.execute(active::write);handshakes.release();permit=false;
            long window=System.nanoTime();int messages=0;
            while(!closed&&!socket.isClosed()){
                var message=Wire.read(input);long now=System.nanoTime();if(now-window>1_000_000_000L){window=now;messages=0;}if(++messages>120)throw new IOException("Message rate exceeded");
                receive(active,message);
            }
        }catch(IOException|RuntimeException error){
            if(peer==null&&!closed)try{Wire.write(new DataOutputStream(socket.getOutputStream()),Wire.packet("error","message",error instanceof IllegalArgumentException?error.getMessage():"Connection could not be accepted"));}catch(IOException ignored){}
        }finally{
            if(permit)handshakes.release();if(peer!=null)remove(peer);sockets.remove(socket);try{socket.close();}catch(IOException ignored){}
        }
    }
    private synchronized void receive(Peer peer,JsonObject message)throws IOException {
        String type=Wire.string(message,"type",20);
        switch(type){
            case "ready"->{if(phase==Phase.LOBBY)peer.ready=message.get("value").getAsBoolean();}
            case "start"->{
                if(!peer.host){peer.send(Wire.packet("notice","message","Only the host can start the flight"));return;}
                if(phase!=Phase.LOBBY||!lobby().allReady()){peer.send(Wire.packet("notice","message","At least two pilots must be ready"));return;}
                phase=Phase.LOADING;flightId++;loadingSince=System.nanoTime();for(Peer p:peers.values()){p.loaded=false;p.pose=null;}battle=config.combat()?new NetworkBattle():null;if(battle!=null)battle.retain(peers.keySet());
            }
            case "loaded"->{if(phase==Phase.LOADING){peer.loaded=true;beginWhenLoaded();}}
            case "pose"->{
                if(phase!=Phase.FLYING||!peer.loaded)return;
                Pose pose=Wire.JSON.fromJson(message.get("state"),Pose.class);
                if(pose==null)throw new IOException("Missing aircraft state");
                if(peer.pose==null||pose.sequence()>peer.pose.sequence()){peer.pose=pose.identify(peer.id);if(battle!=null)battle.pose(peer.id,peer.pose);}
            }
            case "combat"->{
                if(phase!=Phase.FLYING||!peer.loaded||battle==null)return;
                int commands=message.get("commands").getAsInt();if((commands&~NetworkBattle.COMMANDS)!=0)throw new IOException("Invalid system command");
                String lock=message.has("lock")?message.get("lock").getAsString():"";if(lock.length()>40||(!lock.isEmpty()&&!peers.containsKey(lock)))lock="";
                battle.intent(peer.id,message.get("gun").getAsBoolean(),message.get("launch").getAsBoolean(),commands,lock,message.get("crash").getAsBoolean());
            }
            case "ping"->peer.send(Wire.packet("pong","sent",message.get("sent").getAsLong()));
            case "leave"->peer.close();
            default->throw new IOException("Unknown multiplayer message");
        }
    }
    private void beginWhenLoaded(){if(!peers.isEmpty()&&peers.values().stream().allMatch(p->p.loaded))phase=Phase.FLYING;}
    private synchronized void remove(Peer peer){peers.remove(peer.id);peer.close();if(phase==Phase.LOADING)beginWhenLoaded();}
    private void broadcast(){
        try{
            synchronized(this){
                if(closed)return;
                if(phase==Phase.LOADING&&System.nanoTime()-loadingSince>180_000_000_000L){
                    for(Peer p:new ArrayList<>(peers.values()))if(!p.loaded){p.send(Wire.packet("notice","message","Terrain loading timed out"));p.close();peers.remove(p.id);}beginWhenLoaded();
                }
                if(battle!=null){battle.retain(peers.keySet());if(phase==Phase.FLYING)battle.tick(.05f);}
                var packet=Wire.packet("snapshot","lobby",lobby());packet.add("battle",Wire.JSON.toJsonTree(battle==null?Battle.empty():battle.snapshot()));packet.add("poses",Wire.JSON.toJsonTree(peers.values().stream().map(p->p.pose).filter(Objects::nonNull).toList()));
                for(Peer p:peers.values())p.latest.set(packet);
            }
        }catch(RuntimeException error){System.err.println("Network snapshot skipped: "+error.getClass().getSimpleName());}
    }
    private void discover(){
        while(!closed&&discovery!=null)try{
            byte[] bytes=new byte[1024];var request=new DatagramPacket(bytes,bytes.length);discovery.receive(request);
            String query=new String(request.getData(),0,request.getLength(),java.nio.charset.StandardCharsets.UTF_8);
            if(!query.startsWith("DSC-DISCOVER-1 ")||query.length()>100)continue;
            Lobby current=lobby();if(current.phase()!=Phase.LOBBY)continue;
            var reply=Wire.packet("DSC-LOBBY-1","lobby",current);reply.addProperty("nonce",query.substring(15));reply.addProperty("port",port());reply.addProperty("lobbyId",lobbyId);
            byte[] answer=Wire.JSON.toJson(reply).getBytes(java.nio.charset.StandardCharsets.UTF_8);if(answer.length<=4096)discovery.send(new DatagramPacket(answer,answer.length,request.getAddress(),request.getPort()));
        }catch(SocketTimeoutException ignored){}catch(IOException|RuntimeException error){if(!closed)System.err.println("LAN discovery response skipped");}
    }
    @Override public void close(){
        if(closed)return;closed=true;try{listener.close();}catch(IOException ignored){}if(discovery!=null)discovery.close();
        for(Socket socket:sockets)try{socket.close();}catch(IOException ignored){}synchronized(this){peers.clear();}
        ticker.shutdownNow();workers.shutdownNow();
    }
    private final class Peer {
        final String id=UUID.randomUUID().toString(),name;final int slot;final boolean host;final Socket socket;final DataOutputStream output;
        final ArrayBlockingQueue<JsonObject> control=new ArrayBlockingQueue<>(16);final AtomicReference<JsonObject> latest=new AtomicReference<>();
        boolean ready,loaded;Pose pose;
        Peer(Socket socket,DataOutputStream output,String name,int slot,boolean host){this.socket=socket;this.output=output;this.name=name;this.slot=slot;this.host=host;}
        void send(JsonObject packet){if(!control.offer(packet))close();}
        void write(){try{while(!closed&&!socket.isClosed()){JsonObject message=control.poll(20,TimeUnit.MILLISECONDS);if(message!=null)Wire.write(output,message);message=latest.getAndSet(null);if(message!=null)Wire.write(output,message);}}catch(IOException|InterruptedException error){close();}}
        void close(){try{socket.close();}catch(IOException ignored){}}
    }
}
