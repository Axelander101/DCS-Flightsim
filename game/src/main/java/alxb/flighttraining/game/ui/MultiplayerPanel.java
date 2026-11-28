package alxb.flighttraining.game.ui;

import alxb.flighttraining.game.network.*;
import alxb.flighttraining.game.network.FlightNetwork.*;
import alxb.flighttraining.game.config.DogfightSettings.Region;
import com.jme3.input.KeyInput;
import com.jme3.input.event.KeyInputEvent;
import java.awt.*;
import java.util.*;
import java.util.List;

/** Actual lobby controls; all networking is delegated to the asynchronous session. */
public final class MultiplayerPanel {
    private final MultiplayerSession session;
    private final ClipboardAccess clipboard;
    private final alxb.flighttraining.game.profiles.IdentityProvider identityProvider;
    private String identityKey="";
    private final Map<String,Rectangle> fields=new LinkedHashMap<>(),actions=new LinkedHashMap<>();
    private final Map<String,String> values=new HashMap<>();
    private String focus="",status="",signature="";
    private boolean join,command,advanced,selectAll;
    private int regionIndex=3,maximum=4;
    private boolean discoverable=true,combat;
    private boolean dirty;
    private String shareAddress="",networkCode="";
    private List<LanDiscovery.LocalNetwork> localNetworks=List.of();
    public MultiplayerPanel(MultiplayerSession session){this(session,new DesktopClipboard(),null);}
    public MultiplayerPanel(MultiplayerSession session,alxb.flighttraining.game.profiles.IdentityProvider identity){this(session,new DesktopClipboard(),identity);}
    MultiplayerPanel(MultiplayerSession session,ClipboardAccess clipboard){this(session,clipboard,null);}
    private MultiplayerPanel(MultiplayerSession session,ClipboardAccess clipboard,alxb.flighttraining.game.profiles.IdentityProvider identity){this.session=session;this.clipboard=clipboard;this.identityProvider=identity;values.put("code","");values.put("name","Pilot");values.put("title","Formation flight");values.put("address","127.0.0.1");values.put("port",Integer.toString(FlightNetwork.DEFAULT_PORT));values.put("password","");syncIdentity();}
    private void syncIdentity(){
        if(identityProvider==null||session.view().state()==MultiplayerSession.State.CONNECTED||session.view().state()==MultiplayerSession.State.CONNECTING)return;
        var identity=identityProvider.currentIdentity();String key=identity.provider()+":"+identity.subject()+"/"+identity.displayName();
        if(!key.equals(identityKey)){identityKey=key;values.put("name",identity.displayName());dirty=true;}
    }
    public void leave(){focus="";command=false;selectAll=false;}
    public boolean update(){
        syncIdentity();
        var view=session.view();String currentCode=session.lobbyCode();
        if(view.hosting()&&!currentCode.isEmpty()&&!currentCode.equals(networkCode)){
            networkCode=currentCode;localNetworks=LanDiscovery.localNetworks();shareAddress=LobbyCode.decode(currentCode).address();
        }
        String next=view.state()+view.message()+view.selfId()+view.address()+session.lobbyCode()+view.pingMillis()/10+(view.snapshot()==null?"":view.snapshot().lobby().toString())+session.scanning()+session.discoveryStatus()+session.discovered().toString();
        boolean changed=!next.equals(signature)||dirty;signature=next;dirty=false;return changed;
    }
    public boolean click(float x,float y){
        for(var e:fields.entrySet())if(e.getValue().contains(x,y)){focus=e.getKey();selectAll=false;dirty=true;return true;}
        focus="";
        for(var e:actions.entrySet())if(e.getValue().contains(x,y)){activate(e.getKey());dirty=true;return true;}
        return false;
    }
    private void activate(String action){
        try{
            if(action.equals("host-tab")){join=false;status="";return;}if(action.equals("join-tab")){join=true;status="";return;}
            if(action.equals("advanced")){advanced=!advanced;status="";return;}
            if(action.equals("paste-code")){pasteCode();return;}
            if(action.equals("share-network")){
                if(!localNetworks.isEmpty()){int current=-1;for(int i=0;i<localNetworks.size();i++)if(localNetworks.get(i).address().equals(shareAddress))current=i;shareAddress=localNetworks.get((current+1)%localNetworks.size()).address();status="Use the network reachable by your other pilots.";}return;
            }
            if(action.equals("copy-code")){try{clipboard.write(displayCode());status="Lobby code copied.";}catch(Exception error){status="Copy unavailable. You can type the code above.";}return;}
            if(action.equals("scan")){session.scan();return;}if(action.equals("leave")){session.leave();return;}
            if(action.equals("region")){regionIndex=(regionIndex+1)%Region.values().length;return;}
            if(action.equals("maximum")){maximum=maximum>=8?2:maximum+1;return;}
            if(action.equals("combat")){combat=!combat;return;}
            if(action.equals("visibility")){discoverable=!discoverable;return;}
            if(action.startsWith("found:")){
                var list=session.discovered();int i=Integer.parseInt(action.substring(6));if(i>=list.size())return;var found=list.get(i);
                join=true;advanced=found.code().isEmpty();values.put("code",found.code());values.put("address",found.address());values.put("port",Integer.toString(found.port()));values.put("password","");focus=found.locked()?"password":"name";return;
            }
            if(action.equals("ready")){var self=session.view().self();if(self!=null)session.ready(!self.ready());return;}
            if(action.equals("start")){session.startFlight();return;}
            if(action.equals("connect")){
                int port=join&&!advanced?FlightNetwork.DEFAULT_PORT:Integer.parseInt(values.get("port"));String name=values.get("name").strip(),password=values.get("password");
                if(join&&!advanced){session.joinCode(values.get("code"),name,password);}
                else if(join){String address=values.get("address").strip();if(address.contains("/")||address.contains(" "))throw new IllegalArgumentException("Enter an IP address or hostname.");session.join(address,port,name,password);}
                else session.host(new Config(values.get("title"),Region.values()[regionIndex],maximum,discoverable,combat),port,name,password);
                status="";
            }
        }catch(NumberFormatException error){status="Port must be a number between 1 and 65535.";}
        catch(IllegalArgumentException error){status=error.getMessage();}
    }
    public boolean key(KeyInputEvent event){
        int code=event.getKeyCode();
        if(code==KeyInput.KEY_LMETA||code==KeyInput.KEY_RMETA||code==KeyInput.KEY_LCONTROL||code==KeyInput.KEY_RCONTROL){command=event.isPressed();return !focus.isEmpty();}
        if(!event.isPressed()||event.isRepeating()&&code!=KeyInput.KEY_BACK)return !focus.isEmpty();
        if(code==KeyInput.KEY_TAB){var names=new ArrayList<>(fields.keySet());if(!names.isEmpty())focus=names.get((names.indexOf(focus)+1)%names.size());selectAll=false;dirty=true;return true;}
        if(focus.isEmpty())return false;
        if(command&&code==KeyInput.KEY_A){selectAll=true;dirty=true;return true;}
        if(code==KeyInput.KEY_ESCAPE||code==KeyInput.KEY_RETURN){focus="";dirty=true;return true;}
        String text=values.get(focus);
        if(command&&code==KeyInput.KEY_V){
            try{String copied=clipboard.read();if(copied!=null)append(copied.strip());}
            catch(Exception error){status="Paste unavailable; type the code instead.";}
        }else if(code==KeyInput.KEY_BACK){if(selectAll){values.put(focus,"");selectAll=false;}else if(!text.isEmpty())values.put(focus,text.substring(0,text.offsetByCodePoints(text.length(),-1)));}
        else if(!command&&!Character.isISOControl(event.getKeyChar())&&event.getKeyChar()!=0)append(String.valueOf(event.getKeyChar()));
        dirty=true;return true;
    }
    private void append(String text){
        int max=switch(focus){case "port"->5;case "name"->24;case "title"->40;case "password"->64;case "code"->80;default->128;};
        StringBuilder value=new StringBuilder(selectAll?"":values.get(focus));selectAll=false;
        for(int i=0;i<text.length()&&value.length()<max;i++){char c=text.charAt(i);if(!Character.isISOControl(c)&&(!focus.equals("port")||Character.isDigit(c)))value.append(c);}
        values.put(focus,value.toString());
    }
    public String invitationCode(){return displayCode();}
    public void joinInvitation(String code,String password){
        join=true;advanced=false;values.put("code",LobbyCode.canonical(code));values.put("password",password);syncIdentity();session.joinCode(values.get("code"),values.get("name"),password);
    }
    private String displayCode(){
        String code=session.lobbyCode();
        if(!code.isEmpty()&&session.view().hosting()&&!shareAddress.isEmpty()){
            var target=LobbyCode.decode(code);return LobbyCode.encode(shareAddress,target.port(),target.lobbyId());
        }
        return code;
    }
    private void pasteCode(){
        try{String copied=clipboard.read();values.put("code",LobbyCode.canonical(copied));focus="code";advanced=false;selectAll=false;status="Code pasted. Enter your name and join.";}
        catch(Exception error){status="Clipboard does not contain a valid lobby code.";}
    }
    private static void text(Graphics2D g,String value,int x,int y,int size,Color color,boolean bold){MenuTheme.text(g,value,x,y,size,color,bold);}
    private void button(Graphics2D g,String id,String label,int x,int y,int width,int height,boolean primary,boolean enabled){Rectangle r=new Rectangle(x,y,width,height);if(enabled)actions.put(id,r);MenuTheme.button(g,r,label,primary,false,enabled);}
    private void field(Graphics2D g,String id,String label,int x,int y,int width,boolean secret){
        text(g,label,x,y,12,MenuTheme.MUTED,true);Rectangle box=new Rectangle(x,y+12,width,47);fields.put(id,box);
        g.setColor(new Color(9,18,25));g.fill(box);g.setColor(focus.equals(id)?MenuTheme.ACCENT:MenuTheme.LINE);g.draw(box);
        Graphics2D entry=(Graphics2D)g.create();entry.clipRect(x+10,y+13,width-20,45);String value=secret?"•".repeat(values.get(id).length()):values.get(id);if(focus.equals(id))value+="|";
        if(focus.equals(id)&&selectAll){entry.setColor(new Color(52,81,110));entry.fillRect(x+10,y+17,width-20,32);}
        entry.setFont(new Font(MenuTheme.FONT,Font.PLAIN,20));int length=entry.getFontMetrics().stringWidth(value);text(entry,value,x+12-Math.max(0,length-width+24),y+43,20,MenuTheme.TEXT,false);entry.dispose();
    }
    public void draw(Graphics2D g){
        fields.clear();actions.clear();var view=session.view();
        text(g,"Multiplayer",54,146,36,MenuTheme.TEXT,true);text(g,"F/A-18C Swiss Hornet · Formation or host-controlled combat",56,179,17,MenuTheme.MUTED,false);
        if(view.state()==MultiplayerSession.State.CONNECTED&&view.snapshot()!=null){lobby(g,view);return;}
        MenuTheme.panel(g,54,214,660,588);MenuTheme.panel(g,740,214,806,588);
        button(g,"host-tab","CREATE LOBBY",80,239,294,45,!join,true);button(g,"join-tab","JOIN WITH CODE",390,239,294,45,join,true);
        boolean connecting=view.state()==MultiplayerSession.State.CONNECTING||view.state()==MultiplayerSession.State.CONNECTED;
        if(connecting){text(g,"CONNECTING",80,342,25,MenuTheme.ACCENT,true);MenuTheme.paragraph(g,view.message(),80,388,570,20,MenuTheme.MUTED);button(g,"leave","CANCEL",80,714,604,50,false,true);}
        else{
            field(g,"name","PILOT NAME",80,320,join&&!advanced?604:382,false);if(!join||advanced)field(g,"port","TCP PORT",485,320,199,false);
            if(join){
                field(g,advanced?"address":"code",advanced?"HOST ADDRESS (ADVANCED)":"LOBBY CODE",80,405,604,false);field(g,"password","LOBBY PASSWORD (IF REQUIRED)",80,490,604,true);
                button(g,"paste-code","PASTE CODE",80,575,294,44,false,true);button(g,"advanced",advanced?"USE LOBBY CODE":"ADVANCED ADDRESS",390,575,294,44,false,true);
                MenuTheme.paragraph(g,"Use the host's lobby code on the same network or VPN. Address and port are filled in automatically.",82,651,565,18,MenuTheme.MUTED);
            }
            else{
                field(g,"title","LOBBY NAME",80,405,604,false);
                button(g,"region",English.text(Region.values()[regionIndex].label),80,483,382,48,false,true);button(g,"maximum",maximum+" PILOTS MAX",485,483,199,48,false,true);
                field(g,"password","PASSWORD (OPTIONAL)",80,555,604,true);
                button(g,"visibility",discoverable?"LAN DISCOVERY: ON":"LAN DISCOVERY: OFF",80,638,294,42,false,true);button(g,"combat",combat?"MODE: COMBAT":"MODE: FORMATION",390,638,294,42,combat,true);
            }
            button(g,"connect",join?"JOIN LOBBY  →":"CREATE LOBBY  →",80,714,604,50,true,true);
            String message=!status.isEmpty()?status:view.message();MenuTheme.paragraph(g,message,80,785,600,13,view.state()==MultiplayerSession.State.ERROR||!status.isEmpty()?MenuTheme.ACCENT:MenuTheme.MUTED);
        }
        text(g,"LOCAL NETWORK LOBBIES",766,263,18,MenuTheme.ACCENT,true);button(g,"scan",session.scanning()?"SEARCHING…":"REFRESH",1348,237,172,42,false,!session.scanning());
        text(g,"Select a lobby, then enter its password if required.",767,295,15,MenuTheme.MUTED,false);
        var found=session.discovered();for(int i=0;i<Math.min(6,found.size());i++){
            var item=found.get(i);int y=323+i*65;Rectangle row=new Rectangle(766,y,754,58);actions.put("found:"+i,row);g.setColor(new Color(28,39,47));g.fill(row);
            text(g,item.title(),780,y+24,19,MenuTheme.TEXT,true);MenuTheme.right(g,item.players()+" / "+item.maximum()+(item.locked()?"  PASSWORD":"  OPEN"),1506,y+24,13,MenuTheme.ACCENT);
            text(g,English.text(item.region())+(item.code().isEmpty()?"  ·  Direct join":"  ·  Lobby code available"),782,y+46,14,MenuTheme.MUTED,false);
        }
        if(found.isEmpty())MenuTheme.paragraph(g,session.discoveryStatus().isEmpty()?"Press Refresh to find lobbies on your local network. A worldwide lobby directory is not connected.":session.discoveryStatus(),770,370,700,20,MenuTheme.MUTED);
        MenuTheme.paragraph(g,"Lobby codes connect directly, even when LAN discovery is unavailable. Both pilots still need the same network or a reachable VPN.",768,742,730,15,MenuTheme.MUTED);
    }
    private void lobby(Graphics2D g,MultiplayerSession.View view){
        Lobby lobby=view.snapshot().lobby();MenuTheme.panel(g,54,214,1010,588);MenuTheme.panel(g,1090,214,456,588);
        text(g,lobby.title(),80,266,27,MenuTheme.TEXT,true);text(g,English.text(lobby.region().label)+"  ·  "+lobby.players().size()+" / "+lobby.maximum()+" pilots  ·  "+(lobby.locked()?"Password protected":"Open on this network"),82,297,16,MenuTheme.MUTED,false);
        text(g,"PILOT",82,344,12,MenuTheme.MUTED,true);text(g,"AIRCRAFT",465,344,12,MenuTheme.MUTED,true);text(g,"STATUS",874,344,12,MenuTheme.MUTED,true);
        for(int i=0;i<lobby.players().size();i++){
            Player pilot=lobby.players().get(i);int y=359+i*43;g.setColor(new Color(25,36,44));g.fillRect(78,y,960,38);
            text(g,pilot.name()+(pilot.id().equals(view.selfId())?" (YOU)":"")+(pilot.host()?" · HOST":""),90,y+25,17,MenuTheme.TEXT,pilot.host());
            text(g,"F/A-18C / Swiss",465,y+25,16,MenuTheme.MUTED,false);text(g,pilot.ready()?"READY":"WAITING",874,y+25,14,pilot.ready()?new Color(119,213,168):MenuTheme.MUTED,true);
        }
        text(g,"SESSION TYPE",1116,260,12,MenuTheme.ACCENT,true);text(g,lobby.combat()?"COMBAT":"FORMATION",1114,306,31,MenuTheme.TEXT,true);text(g,"FLIGHT",1114,341,31,MenuTheme.TEXT,true);
        MenuTheme.paragraph(g,"Airborne start, 1,200 m above terrain. Every pilot loads the departure area before the flight begins.",1117,390,397,18,MenuTheme.MUTED);
        text(g,"LOBBY CODE",1117,500,12,MenuTheme.MUTED,true);text(g,displayCode(),1117,532,18,MenuTheme.TEXT,true);
        button(g,"copy-code","COPY CODE",1117,550,397,42,false,!session.lobbyCode().isEmpty());
        if(view.hosting()){
            String selected="Automatic";for(var network:localNetworks)if(network.address().equals(shareAddress))selected=network.label();
            button(g,"share-network","NETWORK: "+selected,1117,601,397,34,false,localNetworks.size()>1);
        }
        int pingY=view.hosting()?658:627;
        text(g,"YOUR PING",1117,pingY,12,MenuTheme.MUTED,true);text(g,view.pingMillis()+" ms",1117,pingY+30,24,MenuTheme.TEXT,false);
        if(!status.isEmpty())MenuTheme.paragraph(g,status,1117,view.hosting()?714:688,395,13,MenuTheme.ACCENT);
        MenuTheme.paragraph(g,"ESC opens your in-flight menu. Online flight continues while that menu is open.",1117,view.hosting()?752:721,395,14,MenuTheme.MUTED);
        Player self=view.self();boolean ready=self!=null&&self.ready();
        button(g,"ready",ready?"NOT READY":"READY",80,723,286,50,!ready,true);
        if(self!=null&&self.host())button(g,"start","START FLIGHT  →",382,723,362,50,true,lobby.allReady());
        button(g,"leave","LEAVE LOBBY",760,723,278,50,false,true);
        text(g,lobby.allReady()?"All pilots ready. The host can start.":"At least two pilots must be ready to start.",82,793,14,MenuTheme.MUTED,false);
    }
}
