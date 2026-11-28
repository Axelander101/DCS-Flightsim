package alxb.flighttraining.game.ui;

import alxb.flighttraining.game.accounts.PlayerAccounts;
import com.jme3.input.KeyInput;
import com.jme3.input.event.KeyInputEvent;
import java.awt.*;
import java.util.*;
import java.util.List;

/** Desktop account forms. Passwords are masked, never copied out, and cleared on submit/leave. */
public final class AccountPanel {
    private enum Mode { LOGIN,REGISTER,CONFIRM,RESET }
    private final PlayerAccounts accounts;private final ClipboardAccess clipboard;
    private final Map<String,Rectangle> hits=new LinkedHashMap<>();
    private final Map<String,StringBuilder> fields=new HashMap<>();
    private Mode mode=Mode.LOGIN;private String focus="";private boolean command,all,dirty=true;private long revision=-1;
    public AccountPanel(PlayerAccounts accounts){this(accounts,new DesktopClipboard());}
    AccountPanel(PlayerAccounts accounts,ClipboardAccess clipboard){this.accounts=accounts;this.clipboard=clipboard;for(String name:List.of("name","email","password","repeat","code"))fields.put(name,new StringBuilder());}
    public void open(){leave();}
    public void leave(){clear("password");clear("repeat");clear("code");focus="";command=all=false;dirty=true;}
    private void clear(String key){StringBuilder value=fields.get(key);for(int i=0;i<value.length();i++)value.setCharAt(i,'\0');value.setLength(0);}
    private String value(String key){return fields.get(key).toString();}
    private char[] secret(String key){var b=fields.get(key);char[] result=new char[b.length()];b.getChars(0,b.length(),result,0);return result;}
    public boolean update(){boolean changed=dirty||accounts.view().revision()!=revision;revision=accounts.view().revision();dirty=false;return changed;}
    private List<String> visible(){return switch(mode){case LOGIN->List.of("email","password");case REGISTER->List.of("name","email","password","repeat");case CONFIRM->List.of("email","code");case RESET->List.of("email","code","password","repeat");};}
    public void click(float x,float y){
        for(var entry:hits.entrySet())if(entry.getValue().contains(x,y)){String id=entry.getKey();focus="";all=false;
            if(id.startsWith("field:"))focus=id.substring(6);
            else if(id.startsWith("mode:")){leave();mode=Mode.valueOf(id.substring(5));}
            else if(id.equals("submit"))submit();else if(id.equals("resend"))accounts.resend(value("email"));else if(id.equals("recover"))accounts.recover(value("email"));else if(id.equals("logout"))accounts.logout();
            dirty=true;return;
        }focus="";
    }
    private void submit(){
        if(accounts.view().busy()||!accounts.view().configured())return;
        switch(mode){case LOGIN->accounts.login(value("email"),secret("password"));case REGISTER->accounts.register(value("name"),value("email"),secret("password"),secret("repeat"));case CONFIRM->accounts.confirm(value("email"),value("code"));case RESET->accounts.reset(value("email"),value("code"),secret("password"),secret("repeat"));}
        clear("password");clear("repeat");focus="";all=false;dirty=true;
    }
    public boolean key(KeyInputEvent event){
        int code=event.getKeyCode();if(code==KeyInput.KEY_LMETA||code==KeyInput.KEY_RMETA||code==KeyInput.KEY_LCONTROL||code==KeyInput.KEY_RCONTROL){command=event.isPressed();return !focus.isEmpty();}
        if(!event.isPressed())return !focus.isEmpty();
        if(accounts.view().busy())return true;
        if(code==KeyInput.KEY_TAB&&!accounts.view().signedIn()){var ids=visible();focus=ids.get((ids.indexOf(focus)+1)%ids.size());all=false;dirty=true;return true;}
        if(focus.isEmpty())return false;
        if(code==KeyInput.KEY_ESCAPE){leave();return true;}
        if(code==KeyInput.KEY_RETURN){if(!event.isRepeating())submit();return true;}
        if(command&&code==KeyInput.KEY_A)all=true;
        else if(command&&code==KeyInput.KEY_V){try{String text=clipboard.read();if(text!=null)append(text);}catch(Exception ignored){}}
        else if(code==KeyInput.KEY_BACK){var b=fields.get(focus);if(all)clear(focus);else if(!b.isEmpty())b.delete(b.offsetByCodePoints(b.length(),-1),b.length());all=false;}
        else if(!command&&event.getKeyChar()!=0&&!Character.isISOControl(event.getKeyChar()))append(String.valueOf(event.getKeyChar()));
        dirty=true;return true;
    }
    private void append(String text){var b=fields.get(focus);if(all)clear(focus);all=false;int limit=focus.equals("name")?24:focus.equals("code")?10:focus.equals("email")?254:256;
        for(int i=0;i<text.length()&&b.length()<limit;i++){char c=text.charAt(i);if(!Character.isISOControl(c)&&(!focus.equals("code")||c>='0'&&c<='9'))b.append(c);}}
    private static void text(Graphics2D g,String value,int x,int y,int size,Color color,boolean bold){MenuTheme.text(g,value,x,y,size,color,bold);}
    private void button(Graphics2D g,String id,String label,int x,int y,int w,int h,boolean primary,boolean enabled){Rectangle r=new Rectangle(x,y,w,h);if(enabled)hits.put(id,r);MenuTheme.button(g,r,label,primary,false,enabled);}
    private void field(Graphics2D g,String key,String label,int y,boolean enabled){
        text(g,label,572,y-12,12,MenuTheme.MUTED,true);Rectangle r=new Rectangle(568,y,948,48);if(enabled)hits.put("field:"+key,r);g.setColor(new Color(8,16,23));g.fill(r);g.setColor(focus.equals(key)?MenuTheme.ACCENT:MenuTheme.LINE);g.draw(r);
        Graphics2D edit=(Graphics2D)g.create();edit.clipRect(578,y+3,926,42);if(all&&focus.equals(key)){edit.setColor(new Color(52,81,110));edit.fillRect(578,y+4,926,40);}
        boolean secret=key.equals("password")||key.equals("repeat");String shown=secret?"•".repeat(fields.get(key).length()):value(key);text(edit,shown+(focus.equals(key)?"|":""),582,y+31,20,MenuTheme.TEXT,false);edit.dispose();
    }
    public void draw(Graphics2D g){
        update();hits.clear();var view=accounts.view();boolean enabled=view.configured()&&!view.busy();
        text(g,"Player account",54,146,36,MenuTheme.TEXT,true);text(g,"Email sign-in with a separate pilot callsign.",56,179,17,MenuTheme.MUTED,false);
        MenuTheme.panel(g,54,214,462,588);MenuTheme.panel(g,542,214,1004,588);
        text(g,"DSC ONLINE",78,252,13,MenuTheme.ACCENT,true);text(g,view.signedIn()?"Welcome back":"Your pilot identity",78,300,28,MenuTheme.TEXT,true);
        MenuTheme.paragraph(g,"Use the same account on different computers. Flight statistics currently stay on each device.",80,346,394,18,MenuTheme.MUTED);
        MenuTheme.line(g,78,444,414);
        MenuTheme.paragraph(g,"Passwords are managed by Supabase Auth. This game does not save your password or sign-in tokens to disk.",80,481,394,17,MenuTheme.MUTED);
        MenuTheme.paragraph(g,"Local pilots remain available without an internet connection. Steam is not required.",80,621,394,17,MenuTheme.MUTED);
        if(view.signedIn()){
            text(g,"SIGNED IN",570,253,12,MenuTheme.ACCENT,true);text(g,view.name(),570,306,34,MenuTheme.TEXT,true);text(g,view.email(),572,348,19,MenuTheme.MUTED,false);
            var pilot=accounts.profiles().view().active();MenuTheme.line(g,568,397,948);
            text(g,"FLIGHT LOG ON THIS DEVICE",570,437,12,MenuTheme.MUTED,true);text(g,PilotProfilePanel.duration(pilot.airborneMillis()),570,492,32,MenuTheme.TEXT,true);
            text(g,String.format(Locale.ROOT,"%d flights started  ·  %.1f km flown",pilot.flights(),pilot.distanceMetres()/1000),572,535,19,MenuTheme.MUTED,false);
            MenuTheme.paragraph(g,"Your email identifies the account. Pilot callsigns are display names and do not have to be globally unique.",572,592,890,17,MenuTheme.MUTED);
            button(g,"logout",view.busy()?"SIGNING OUT…":"SIGN OUT",1190,706,326,48,false,!view.busy());
        }else{
            Mode[] modes=Mode.values();String[] labels={"SIGN IN","REGISTER","CONFIRM EMAIL","RESET PASSWORD"};for(int i=0;i<modes.length;i++)button(g,"mode:"+modes[i],labels[i],568+i*240,232,228,39,modes[i]==mode,!view.busy());
            var ids=visible();for(int i=0;i<ids.size();i++){String key=ids.get(i);String label=switch(key){case "name"->"PILOT NAME";case "email"->"EMAIL ADDRESS";case "repeat"->"CONFIRM PASSWORD";case "code"->"CODE FROM EMAIL";default->mode==Mode.RESET?"NEW PASSWORD":"PASSWORD";};field(g,key,label,310+i*80,!view.busy());}
            int y=mode==Mode.REGISTER||mode==Mode.RESET?646:mode==Mode.CONFIRM?468:468;
            String label=switch(mode){case LOGIN->"SIGN IN";case REGISTER->"CREATE ACCOUNT";case CONFIRM->"CONFIRM EMAIL";case RESET->"SAVE NEW PASSWORD";};
            button(g,"submit",view.busy()?"PLEASE WAIT…":!view.configured()?"SETUP REQUIRED":label,1190,y,326,48,true,enabled);
            if(mode==Mode.CONFIRM)button(g,"resend","SEND ANOTHER CODE",568,y,350,48,false,enabled);
            if(mode==Mode.RESET)button(g,"recover","SEND RESET CODE",568,y,350,48,false,enabled);
            MenuTheme.paragraph(g,mode==Mode.REGISTER||mode==Mode.RESET?"Use at least 12 characters (maximum 72 UTF-8 bytes). Spaces are allowed.":mode==Mode.CONFIRM?"Enter the confirmation code from your email. If you used a confirmation link, return to Sign in.":"No account yet? Select Register. Forgotten your password? Select Reset password.",572,y+90,900,16,MenuTheme.MUTED);
        }
        MenuTheme.paragraph(g,view.message(),570,770,940,14,MenuTheme.MUTED);
    }
}
