package alxb.flighttraining.game.ui;

import alxb.flighttraining.game.profiles.*;
import com.jme3.input.KeyInput;
import com.jme3.input.event.KeyInputEvent;
import java.awt.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Local profile editing and real flight statistics, without an online-account login facade. */
public final class PilotProfilePanel {
    private final PilotProfiles profiles;private final ClipboardAccess clipboard;
    private final Map<String,Rectangle> hits=new LinkedHashMap<>();
    private String draft="",loadedKey="";private boolean creating,focused,command,selectAll,dirty;
    private long revision=-1;
    public PilotProfilePanel(PilotProfiles profiles){this(profiles,new DesktopClipboard());}
    PilotProfilePanel(PilotProfiles profiles,ClipboardAccess clipboard){this.profiles=profiles;this.clipboard=clipboard;}
    public void open(){leave();profiles.refresh();update();}
    public void leave(){focused=command=selectAll=creating=false;loadedKey="";dirty=true;}
    public boolean update(){
        var view=profiles.view();String key=view.activeId()+"/"+view.active().name();
        if(!view.busy()&&!key.equals(loadedKey)){loadedKey=key;draft=view.active().name();creating=false;focused=false;dirty=true;}
        boolean changed=dirty||revision!=view.revision();revision=view.revision();dirty=false;return changed;
    }
    public void click(float x,float y){
        var view=profiles.view();for(var hit:hits.entrySet())if(hit.getValue().contains(x,y)){
            String id=hit.getKey();focused=false;selectAll=false;
            if(id.equals("name")){focused=true;}
            else if(id.equals("new")){creating=true;draft="";focused=true;}
            else if(id.equals("save")){if(creating)profiles.create(draft);else profiles.rename(draft);}
            else if(id.equals("refresh")){profiles.refresh();}
            else if(id.startsWith("pilot:")){creating=false;profiles.select(UUID.fromString(id.substring(6)));}
            dirty=true;return;
        }
        focused=false;
    }
    public boolean key(KeyInputEvent event){
        int code=event.getKeyCode();if(code==KeyInput.KEY_LMETA||code==KeyInput.KEY_RMETA||code==KeyInput.KEY_LCONTROL||code==KeyInput.KEY_RCONTROL){command=event.isPressed();return focused;}
        if(!event.isPressed())return focused;
        if(code==KeyInput.KEY_TAB){focused=profiles.view().available()&&!profiles.view().busy();dirty=true;return true;}
        if(!focused)return false;
        if(code==KeyInput.KEY_ESCAPE){draft=profiles.view().active().name();creating=false;focused=false;selectAll=false;dirty=true;return true;}
        if(code==KeyInput.KEY_RETURN){if(!event.isRepeating()){if(creating)profiles.create(draft);else profiles.rename(draft);focused=false;}dirty=true;return true;}
        if(event.isRepeating()&&code!=KeyInput.KEY_BACK)return true;
        if(command&&code==KeyInput.KEY_A){selectAll=true;}
        else if(command&&code==KeyInput.KEY_V){try{String text=clipboard.read();if(text!=null)append(text);}catch(Exception ignored){}}
        else if(code==KeyInput.KEY_BACK){if(selectAll){draft="";selectAll=false;}else if(!draft.isEmpty())draft=draft.substring(0,draft.offsetByCodePoints(draft.length(),-1));}
        else if(!command&&event.getKeyChar()!=0&&!Character.isISOControl(event.getKeyChar()))append(String.valueOf(event.getKeyChar()));
        dirty=true;return true;
    }
    private void append(String text){StringBuilder value=new StringBuilder(selectAll?"":draft);selectAll=false;for(int i=0;i<text.length()&&value.length()<24;i++){char c=text.charAt(i);if(!Character.isISOControl(c))value.append(c);}draft=value.toString();}
    private void button(Graphics2D g,String id,String text,int x,int y,int width,int height,boolean primary,boolean enabled){var box=new Rectangle(x,y,width,height);if(enabled)hits.put(id,box);MenuTheme.button(g,box,text,primary,false,enabled);}
    private static void text(Graphics2D g,String value,int x,int y,int size,Color color,boolean bold){MenuTheme.text(g,value,x,y,size,color,bold);}
    public static String duration(long millis){long seconds=millis/1000;return String.format(Locale.ROOT,"%dh %02dm %02ds",seconds/3600,(seconds/60)%60,seconds%60);}
    public void draw(Graphics2D g){
        update();hits.clear();var view=profiles.view();PilotProfile current=view.active();
        text(g,"Pilot profile",54,146,36,MenuTheme.TEXT,true);text(g,"Your pilots and flight log, saved on this device.",56,179,17,MenuTheme.MUTED,false);
        MenuTheme.panel(g,54,214,462,588);MenuTheme.panel(g,542,214,1004,588);text(g,"LOCAL PILOTS",78,253,13,MenuTheme.ACCENT,true);
        for(int i=0;i<view.pilots().size();i++){
            PilotProfile pilot=view.pilots().get(i);int y=274+i*51;Rectangle box=new Rectangle(68,y,434,46);if(!view.busy()&&view.available())hits.put("pilot:"+pilot.id(),box);
            boolean active=pilot.id().equals(current.id());g.setColor(active?new Color(221,171,85,28):new Color(255,255,255,5));g.fill(box);if(active){g.setColor(MenuTheme.ACCENT);g.fillRect(box.x,box.y,3,box.height);}
            String label=pilot.name();g.setFont(new Font(MenuTheme.FONT,active?Font.BOLD:Font.PLAIN,20));while(label.length()>1&&g.getFontMetrics().stringWidth(label+"…")>270)label=label.substring(0,label.length()-1);if(!label.equals(pilot.name()))label+="…";text(g,label,86,y+29,20,MenuTheme.TEXT,active);MenuTheme.right(g,duration(pilot.airborneMillis()),486,y+29,13,MenuTheme.MUTED);
        }
        button(g,"new","NEW PILOT",78,728,414,49,true,view.available()&&!view.busy()&&view.pilots().size()<PilotProfileStore.MAX_PROFILES);
        text(g,"OFFLINE PROFILE",568,252,12,MenuTheme.ACCENT,true);text(g,creating?"New pilot":current.name(),566,299,34,MenuTheme.TEXT,true);
        text(g,creating?"Choose a callsign to start a separate flight log.":"This callsign is filled in when you join a multiplayer lobby.",570,332,17,MenuTheme.MUTED,false);
        text(g,"PILOT NAME",570,378,12,MenuTheme.MUTED,true);Rectangle field=new Rectangle(568,393,640,55);g.setColor(new Color(8,16,23));g.fill(field);g.setColor(focused?MenuTheme.ACCENT:MenuTheme.LINE);g.draw(field);
        if(view.available()&&!view.busy())hits.put("name",field);Graphics2D edit=(Graphics2D)g.create();edit.clipRect(580,398,616,45);if(focused&&selectAll){edit.setColor(new Color(52,81,110));edit.fillRect(580,400,616,40);}text(edit,draft+(focused?"|":""),584,428,22,MenuTheme.TEXT,false);edit.dispose();
        button(g,"save",view.busy()?"SAVING…":creating?"CREATE PILOT":"SAVE NAME",1230,393,286,55,true,view.available()&&!view.busy());
        MenuTheme.line(g,568,489,948);
        metric(g,"AIRBORNE TIME",creating?"0h 00m":duration(current.airborneMillis()),568,533);
        metric(g,"FLIGHTS STARTED",creating?"0":Long.toString(current.flights()),890,533);
        metric(g,"DISTANCE FLOWN",String.format(Locale.ROOT,"%.1f km",creating?0:current.distanceMetres()/1000),1210,533);
        if(!creating){String since=DateTimeFormatter.ofPattern("dd MMM uuuu",Locale.ENGLISH).format(Instant.ofEpochMilli(current.createdAt()).atZone(ZoneId.systemDefault()));text(g,"Pilot since "+since,570,645,15,MenuTheme.MUTED,false);}
        MenuTheme.paragraph(g,"Local profiles work offline. Online accounts and cloud synchronisation can be connected later.",570,689,900,17,MenuTheme.MUTED);
        button(g,"refresh",view.available()?"REFRESH":"RETRY STORAGE",1250,742,266,36,false,!view.busy());MenuTheme.paragraph(g,view.message(),570,774,650,14,MenuTheme.MUTED);
    }
    private static void metric(Graphics2D g,String label,String value,int x,int y){text(g,label,x,y,12,MenuTheme.MUTED,true);text(g,value,x,y+45,30,MenuTheme.TEXT,true);}
}
