package alxb.flighttraining.game.network;

import alxb.flighttraining.game.network.FlightNetwork.*;
import com.google.gson.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Opt-in, one-second local broadcast scan. No internet lobby service or port mapping. */
public final class LanDiscovery {
    public record Found(String address,int port,String title,String region,int players,int maximum,boolean locked,String code){}
    private LanDiscovery(){}
    public static List<Found> scan()throws java.io.IOException {
        Map<String,Found> found=new LinkedHashMap<>();String nonce=UUID.randomUUID().toString();byte[] query=("DSC-DISCOVER-1 "+nonce).getBytes(StandardCharsets.UTF_8);
        try(var socket=new DatagramSocket()){
            socket.setBroadcast(true);socket.setSoTimeout(120);
            Set<InetAddress> broadcasts=new LinkedHashSet<>();broadcasts.add(InetAddress.getByName("255.255.255.255"));broadcasts.add(InetAddress.getLoopbackAddress());
            var interfaces=NetworkInterface.getNetworkInterfaces();while(interfaces.hasMoreElements()){var network=interfaces.nextElement();if(network.isUp())for(var address:network.getInterfaceAddresses())if(address.getBroadcast()!=null)broadcasts.add(address.getBroadcast());}
            for(var broadcast:broadcasts)try{socket.send(new DatagramPacket(query,query.length,broadcast,FlightNetwork.DISCOVERY_PORT));}catch(java.io.IOException ignored){}
            long until=System.nanoTime()+1_200_000_000L;
            while(System.nanoTime()<until)try{
                byte[] bytes=new byte[4096];var reply=new DatagramPacket(bytes,bytes.length);socket.receive(reply);
                var packet=JsonParser.parseString(new String(reply.getData(),0,reply.getLength(),StandardCharsets.UTF_8)).getAsJsonObject();
                if(!Wire.string(packet,"type",20).equals("DSC-LOBBY-1")||!Wire.string(packet,"nonce",40).equals(nonce))continue;
                var lobby=Wire.JSON.fromJson(packet.get("lobby"),Lobby.class);int port=packet.get("port").getAsInt();
                if(lobby==null||lobby.region()==null||lobby.phase()!=Phase.LOBBY||port<1||port>65535||lobby.players()==null||lobby.players().size()>8||lobby.maximum()<2||lobby.maximum()>8)continue;
                String title=Wire.text(lobby.title(),40),address=reply.getAddress().getHostAddress();
                String code="";if(packet.has("lobbyId"))try{code=LobbyCode.encode(address,port,Wire.string(packet,"lobbyId",8));}catch(IllegalArgumentException ignored){}
                found.put(address+":"+port,new Found(address,port,title,lobby.region().label,lobby.players().size(),lobby.maximum(),lobby.locked(),code));
            }catch(SocketTimeoutException ignored){}catch(RuntimeException ignored){}
        }
        return List.copyOf(found.values());
    }
    public record LocalNetwork(String label,String address){}
    public static List<LocalNetwork> localNetworks(){
        Map<String,LocalNetwork> found=new LinkedHashMap<>();
        try{var interfaces=NetworkInterface.getNetworkInterfaces();while(interfaces.hasMoreElements()){
            var network=interfaces.nextElement();if(!network.isUp()||network.isLoopback())continue;
            var addresses=network.getInetAddresses();while(addresses.hasMoreElements()){
                var address=addresses.nextElement();if(address instanceof Inet4Address&&!address.isAnyLocalAddress()&&!address.isLoopbackAddress()&&!address.isMulticastAddress()){
                    String label=network.getDisplayName();if(label==null||label.isBlank())label=network.getName();
                    if(label.length()>27)label=label.substring(0,24)+"…";found.putIfAbsent(address.getHostAddress(),new LocalNetwork(label,address.getHostAddress()));
                }
            }
        }}catch(SocketException ignored){}
        found.put("127.0.0.1",new LocalNetwork("This computer only","127.0.0.1"));return List.copyOf(found.values());
    }
    public static String localAddress(){
        try{var interfaces=NetworkInterface.getNetworkInterfaces();while(interfaces.hasMoreElements()){var network=interfaces.nextElement();if(!network.isUp()||network.isLoopback())continue;var addresses=network.getInetAddresses();while(addresses.hasMoreElements()){var address=addresses.nextElement();if(address instanceof Inet4Address&&address.isSiteLocalAddress())return address.getHostAddress();}}}catch(SocketException ignored){}
        return "127.0.0.1";
    }
}
