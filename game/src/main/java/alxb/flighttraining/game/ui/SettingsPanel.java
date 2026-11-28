package alxb.flighttraining.game.ui;

import alxb.flighttraining.game.FlightTraining;
import alxb.flighttraining.game.config.GameSettings;
import alxb.flighttraining.game.config.GameSettings.Option;
import com.jme3.input.*;
import com.jme3.input.event.KeyInputEvent;
import java.awt.*;
import java.util.*;
import java.util.List;

/** Data-driven settings pages. Rendered bounds are also the source of hit testing. */
public final class SettingsPanel {
    private record Row(String label,String value,Option option,String binding,Runnable action,boolean enabled) {}
    private final FlightTraining app;
    private final GameSettings settings=GameSettings.get();
    private final Map<Rectangle,Row> hits=new LinkedHashMap<>();
    private final Rectangle viewport=new Rectangle(),back=new Rectangle(),reset=new Rectangle();
    private String page="",capture,status="";
    private Rectangle hovered;
    public void pointer(float x,float y){Rectangle next=null;for(Rectangle r:hits.keySet())if(r.contains(x,y)){next=r;break;}if(!java.util.Objects.equals(next,hovered)){hovered=next;dirty=true;}}private float scroll;private int maximum;private boolean dirty;
    public SettingsPanel(FlightTraining app){this.app=app;app.getInputManager().addRawInputListener(new RawInputListenerAdapter(){
        @Override public void onKeyEvent(KeyInputEvent event){
            if(capture==null)return;event.setConsumed();if(!event.isPressed()||event.isRepeating())return;
            if(event.getKeyCode()==KeyInput.KEY_ESCAPE){capture=null;status="Binding cancelled.";dirty=true;return;}
            String error=settings.bind(capture,event.getKeyCode());status=error.isEmpty()?"Binding saved.":error;
            if(error.isEmpty()){capture=null;app.applyUserSettings();}dirty=true;
        }
    });}
    public void leave(){capture=null;}
    public boolean takeDirty(){boolean value=dirty;dirty=false;return value;}
    public boolean scroll(float amount){float previous=scroll;scroll=Math.max(0,Math.min(maximum,scroll+amount));return scroll!=previous;}
    /** true means return to the settings overview. */
    public boolean click(float x,float y){
        if(back.contains(x,y)){leave();return true;}
        if(reset.contains(x,y)){settings.resetPage(page);capture=null;app.applyUserSettings();status="Defaults restored.";dirty=true;return false;}
        if(capture!=null||!viewport.contains(x,y))return false;
        for(var entry:hits.entrySet())if(entry.getKey().contains(x,y)){
            Row row=entry.getValue();if(!row.enabled)return false;
            if(row.binding!=null){capture=row.binding;status="Press a key. Esc cancels.";}
            else if(row.option!=null){
                String old=settings.value(row.option);if(row.option==Option.MASTER)settings.setMaster(settings.fraction(Option.MASTER)>=1?0:Math.min(1,settings.fraction(Option.MASTER)+.1f));else settings.cycle(row.option,1);
                try{app.applyUserSettings();status=settings.saveError().isEmpty()?"Saved. Changes applied.":settings.saveError();}
                catch(RuntimeException error){settings.set(row.option,old);try{app.applyUserSettings();}catch(RuntimeException ignored){}status="This setting is not supported by the current display.";System.err.println("Setting rejected: "+error.getMessage());}
            }
            else if(row.action!=null)try{row.action.run();status="Display updated.";}catch(RuntimeException e){status="Display change unavailable.";System.err.println(e.getMessage());}
            dirty=true;break;
        }
        return false;
    }
    public void draw(Graphics2D g,String newPage,int width,int height){
        if(!page.equals(newPage)){page=newPage;scroll=0;capture=null;status="";}
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(MenuTheme.PANEL);g.fillRect(0,0,width,height);
        g.setColor(Color.WHITE);g.setFont(new Font(MenuTheme.FONT,Font.BOLD,29));g.drawString(page,26,43);
        back.setBounds(width-145,16,118,38);button(g,back,"Back",false);
        reset.setBounds(width-218,height-55,182,35);button(g,reset,"Restore defaults",false);
        List<Row> rows=rows();viewport.setBounds(26,77,Math.max(1,width-52),Math.max(1,height-159));maximum=Math.max(0,rows.size()*55-viewport.height);scroll=Math.min(scroll,maximum);hits.clear();
        Graphics2D body=(Graphics2D)g.create();body.clip(viewport);
        for(int i=0;i<rows.size();i++){
            Row row=rows.get(i);Rectangle bounds=new Rectangle(viewport.x,viewport.y+i*55-Math.round(scroll),viewport.width-15,48);if(!bounds.intersects(viewport))continue;
            hits.put(bounds.intersection(viewport),row);body.setColor(row.binding!=null&&row.binding.equals(capture)?new Color(76,65,43):new Color(25,35,43));body.fillRect(bounds.x,bounds.y,bounds.width,bounds.height);
            if(hovered!=null&&hovered.intersects(bounds)&&row.enabled){body.setColor(MenuTheme.ACCENT);body.fillRect(bounds.x,bounds.y,2,bounds.height);}
            body.setColor(MenuTheme.LINE);body.drawLine(bounds.x,bounds.y+bounds.height,bounds.x+bounds.width,bounds.y+bounds.height);
            body.setColor(row.enabled?new Color(228,235,239):new Color(128,141,150));body.setFont(new Font(MenuTheme.FONT,Font.PLAIN,20));body.drawString(row.label,bounds.x+16,bounds.y+33);
            body.setFont(new Font(MenuTheme.FONT,Font.BOLD,18));String value=row.value+(row.enabled?"  ›":"");int textWidth=body.getFontMetrics().stringWidth(value);body.drawString(value,bounds.x+bounds.width-textWidth-18,bounds.y+33);
        }
        body.dispose();g.setColor(new Color(142,158,168));g.setFont(new Font(MenuTheme.FONT,Font.PLAIN,15));g.drawString(capture!=null?"Press a key for "+GameSettings.label(capture)+" (Esc cancels)":status.isEmpty()?"Click to change · Scroll for more":status,26,height-64);
        if(maximum>0){int thumb=Math.max(22,viewport.height*viewport.height/Math.max(1,rows.size()*55)),y=viewport.y+Math.round(scroll/maximum*(viewport.height-thumb));g.setColor(MenuTheme.ACCENT);g.fillRoundRect(width-45,y,5,thumb,5,5);}
    }
    private static void button(Graphics2D g,Rectangle bounds,String text,boolean selected){g.setColor(new Color(37,48,57));g.fillRect(bounds.x,bounds.y,bounds.width,bounds.height);g.setColor(Color.WHITE);g.setFont(new Font(MenuTheme.FONT,Font.BOLD,16));g.drawString(text,bounds.x+(bounds.width-g.getFontMetrics().stringWidth(text))/2,bounds.y+(bounds.height+11)/2);}
    private List<Row> rows(){
        List<Row> result=new ArrayList<>();
        for(Option option:Option.values())if(option.page.equals(page))result.add(new Row(option.label,(option==Option.RESOLUTION?app.resolutionDescription():settings.value(option)),option,null,null,true));
        if(page.equals("Audio")){result.add(new Row("Mute",app.getGameAudio()!=null&&app.getGameAudio().getVolume()==0?"On":"Off",null,null,()->app.getGameAudio().toggleMute(),true));disabled(result,"Music","No music loaded");}
        if(page.equals("Controls")){
            for(String action:GameSettings.DEFAULT_KEYS.keySet())result.add(new Row(GameSettings.label(action),KeyNames.getName(settings.key(action)),null,action,null,true));
            disabled(result,"Force feedback","Not available");
        }
        if(page.equals("Graphics"))for(String feature:List.of("Anti-aliasing","Shadow quality","Vegetation density","Building quality","Cloud quality","Particle quality","Water quality","Motion blur"))disabled(result,feature,"Not available yet");
        if(page.equals("Gameplay"))for(String feature:List.of("Crash damage","Friendly fire","Aim assistance"))disabled(result,feature,"Not available yet");
        if(page.equals("Interface")){disabled(result,"Language","English");disabled(result,"HUD scale","Fixed cockpit optics");}
        return result;
    }
    private static void disabled(List<Row> rows,String label,String value){rows.add(new Row(label,value,null,null,null,false));}
}
