package alxb.flighttraining.game.ui;

import alxb.flighttraining.game.FlightTraining;
import alxb.flighttraining.game.config.*;
import com.jme3.asset.AssetManager;
import com.jme3.input.*;
import com.jme3.input.controls.*;
import com.jme3.input.event.KeyInputEvent;
import com.jme3.scene.Node;
import com.jme3.texture.Texture2D;
import com.jme3.texture.plugins.AWTLoader;
import com.jme3.ui.Picture;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;

/** Simulator launcher. Every clickable region comes from the geometry used to draw it. */
public final class Menu {
    private enum Screen { MAIN,MISSIONS,SETTINGS,GRAPHICS_SETTINGS,STEERING_SETTINGS,GAMEPLAY_SETTINGS,AUDIO_SETTINGS,INTERFACE_SETTING,MULTIPLAYER,PILOT_PROFILE,ACCOUNT,FRIENDS,FREE_PLAY,DOGFIGHT_AI }
    private static final String[] AIRPORTS={"Zürich","Genf","Basel-Mulhouse","Bern-Belp","St. Gallen-Altenrhein","Lugano-Agno","Sion","Grenchen","Payerne","Meiringen","Emmen"};
    private static final String[] CODES={"LSZH","LSGG","LFSB","LSZB","LSZR","LSZA","LSGS","LSZG","LSMP","LSMM","LSME"};
    private static final String[] MISSION_NAMES={"Familiarisation","Navigation","Intercept","Dogfight","Patrol","Escort"};
    private static final String[] MISSION_DETAILS={"Learn the Hornet and practise aircraft handling at your chosen airfield.","Practise visual navigation between Swiss airfields.","Identify and intercept an airborne target.","Configure an opponent, starting range and difficulty for air combat training.","Follow a patrol route and monitor the surrounding airspace.","Protect a friendly aircraft along its route."};
    private static final String[] CATEGORIES={"Graphics","Controls","Gameplay","Audio","Interface"};
    private final AssetManager menuAssets;
    private final FlightTraining app;
    private final InputManager input;
    private final Picture picture=new Picture("MainMenu");
    private final SettingsPanel settingsPanel;
    private final MultiplayerPanel multiplayerPanel;
    private final PilotProfilePanel pilotPanel;
    private final AccountPanel accountPanel;
    private final FriendsPanel friendsPanel;
    private long accountRevision=-1;
    private long pilotRevision=-1;
    private final DogfightUI dogfightUI=new DogfightUI();
    private final Map<String,Rectangle> hits=new LinkedHashMap<>();
    private final Map<String,BufferedImage> airportImages=new HashMap<>();
    private Screen screen=Screen.MAIN;
    private boolean menuOffen=true,dirty,quitConfirm;
    private int selectedAirport,selectedMission,menuWidth,menuHeight;
    private float scale=1,offsetX,offsetY,paintClock;
    private String hover="";
    private float pointerX=Float.NaN,pointerY=Float.NaN;
    private long profileRevision=-1;
    public Menu(AssetManager assets,int width,int height,FlightTraining app,InputManager input){
        this.menuAssets=assets;this.app=app;this.input=input;menuWidth=width;menuHeight=height;settingsPanel=new SettingsPanel(app);multiplayerPanel=new MultiplayerPanel(app.getMultiplayer(),app.getIdentityProvider());pilotPanel=new PilotProfilePanel(app.getPilotProfiles());accountPanel=new AccountPanel(app.getAccounts());friendsPanel=new FriendsPanel(app.getFriends(),app.getMultiplayer(),()->go(Screen.ACCOUNT));
        for(int i=0;i<AIRPORTS.length;i++)airportImages.put(CODES[i],MenuImages.load(assets,CODES[i],AIRPORTS[i]));
        if(app.getAccounts().view().configured())screen=Screen.ACCOUNT;
        picture.setPosition(0,0);redraw();
        input.addMapping("MenuClick",new MouseButtonTrigger(MouseInput.BUTTON_LEFT));
        input.addMapping("ScrollUp",new MouseAxisTrigger(MouseInput.AXIS_WHEEL,false));input.addMapping("ScrollDown",new MouseAxisTrigger(MouseInput.AXIS_WHEEL,true));
        input.addListener((ActionListener)(name,pressed,tpf)->{if(!pressed||!menuOffen)return;var cursor=input.getCursorPosition();click((cursor.x-offsetX)/scale,(app.getCamera().getHeight()-cursor.y-offsetY)/scale);},"MenuClick");
        input.addListener((AnalogListener)(name,value,tpf)->{if(menuOffen&&settingsScreen()&&settingsPanel.scroll((name.equals("ScrollDown")?1:-1)*value*50/scale))dirty=true;},"ScrollUp","ScrollDown");
        input.addRawInputListener(new RawInputListenerAdapter(){@Override public void onKeyEvent(KeyInputEvent event){
            if(!menuOffen||event.isConsumed())return;
            if(screen==Screen.FRIENDS&&friendsPanel.key(event)){event.setConsumed();return;}
            if(screen==Screen.ACCOUNT&&accountPanel.key(event)){event.setConsumed();return;}
            if(app.getAccounts().view().busy()){event.setConsumed();return;}
            if(screen==Screen.PILOT_PROFILE&&pilotPanel.key(event)){event.setConsumed();return;}
            if(screen==Screen.MULTIPLAYER&&multiplayerPanel.key(event)){event.setConsumed();return;}
            if(!event.isPressed()||event.isRepeating())return;
            if(event.getKeyCode()==KeyInput.KEY_ESCAPE){event.setConsumed();if(quitConfirm)quitConfirm=false;else if(screen==Screen.MAIN)quitConfirm=true;else go(screen==Screen.FRIENDS&&app.getMultiplayer().view().state()==alxb.flighttraining.game.network.MultiplayerSession.State.CONNECTED?Screen.MULTIPLAYER:settingsScreen()?Screen.SETTINGS:Screen.MAIN);dirty=true;}
            else if(event.getKeyCode()==KeyInput.KEY_TAB){event.setConsumed();List<String> ids=new ArrayList<>(hits.keySet());if(!ids.isEmpty())hover=ids.get((ids.indexOf(hover)+1)%ids.size());dirty=true;}
            else if(event.getKeyCode()==KeyInput.KEY_RETURN&&!hover.isEmpty()){event.setConsumed();activate(hover);}
        }});
    }
    private boolean settingsScreen(){return switch(screen){case GRAPHICS_SETTINGS,STEERING_SETTINGS,GAMEPLAY_SETTINGS,AUDIO_SETTINGS,INTERFACE_SETTING->true;default->false;};}
    private String settingsPage(){return switch(screen){case GRAPHICS_SETTINGS->"Graphics";case STEERING_SETTINGS->"Controls";case GAMEPLAY_SETTINGS->"Gameplay";case AUDIO_SETTINGS->"Audio";default->"Interface";};}
    private void category(String name){go(switch(name){case "Graphics"->Screen.GRAPHICS_SETTINGS;case "Controls"->Screen.STEERING_SETTINGS;case "Gameplay"->Screen.GAMEPLAY_SETTINGS;case "Audio"->Screen.AUDIO_SETTINGS;default->Screen.INTERFACE_SETTING;});}
    private void go(Screen value){if((screen==Screen.MULTIPLAYER||screen==Screen.FRIENDS)&&value!=Screen.MULTIPLAYER&&value!=Screen.FRIENDS)app.getMultiplayer().leave();multiplayerPanel.leave();pilotPanel.leave();accountPanel.leave();friendsPanel.leave();settingsPanel.leave();screen=value;if(value==Screen.PILOT_PROFILE)pilotPanel.open();if(value==Screen.ACCOUNT)accountPanel.open();if(value==Screen.FRIENDS)friendsPanel.open();hover="";quitConfirm=false;redraw();}
    private String hit(float x,float y){for(var entry:hits.entrySet())if(entry.getValue().contains(x,y))return entry.getKey();return "";}
    private void click(float x,float y){
        String action=hit(x,y);
        if(!action.isEmpty()){if(app.getGameAudio()!=null)app.getGameAudio().uiClick();activate(action);}
        else if(screen==Screen.FRIENDS&&!quitConfirm){friendsPanel.click(x,y);dirty=true;}
        else if(screen==Screen.ACCOUNT&&!quitConfirm){accountPanel.click(x,y);dirty=true;}
        else if(screen==Screen.PILOT_PROFILE&&!quitConfirm){pilotPanel.click(x,y);dirty=true;}
        else if(screen==Screen.MULTIPLAYER&&!quitConfirm){multiplayerPanel.click(x,y);dirty=true;}
        else if(settingsScreen()&&!quitConfirm){if(settingsPanel.click(x-310,y-158))go(Screen.SETTINGS);else dirty=true;}
    }
    private void activate(String id){
        if(app.getAccounts().view().busy()&&!id.equals("confirm-quit"))return;
        if(id.equals("home")){go(Screen.MAIN);return;}
        if(id.equals("back")){go(settingsScreen()?Screen.SETTINGS:Screen.MAIN);return;}
        if(id.startsWith("settings:")){category(id.substring(9));return;}
        if(id.startsWith("airport:")){selectedAirport=Integer.parseInt(id.substring(8));dirty=true;return;}
        if(id.startsWith("mission:")){selectedMission=Integer.parseInt(id.substring(8));dirty=true;return;}
        switch(id){
            case "pilot-profile"->go(!app.getAccounts().view().signedIn()&&!app.getAccounts().view().expired()?Screen.PILOT_PROFILE:Screen.ACCOUNT);
            case "account"->go(Screen.ACCOUNT);
            case "friends"->go(Screen.FRIENDS);
            case "free"->go(Screen.FREE_PLAY);case "missions"->go(Screen.MISSIONS);case "dogfight"->go(Screen.DOGFIGHT_AI);case "settings"->go(Screen.SETTINGS);case "multiplayer"->go(Screen.MULTIPLAYER);
            case "quit"->{quitConfirm=true;hover="cancel";dirty=true;}case "cancel"->{quitConfirm=false;dirty=true;}case "confirm-quit"->app.stop();
            case "start-free"->{var region=regionFor(CODES[selectedAirport]);app.startFreePlay(new SpawnSettings(region.x,region.z,selectedAirport==4?398f:0f,0));}
            case "start-mission"->{if(selectedMission==3)go(Screen.DOGFIGHT_AI);else if(selectedMission<2)go(Screen.FREE_PLAY);}
            case "countMinus"->{dogfightUI.count=Math.max(1,dogfightUI.count-1);dirty=true;}
            case "countPlus"->{dogfightUI.count=Math.min(8,dogfightUI.count+1);dirty=true;}
            case "difficulty"->{dogfightUI.difficultyIndex=(dogfightUI.difficultyIndex+1)%3;dirty=true;}
            case "region"->{dogfightUI.regionIndex=(dogfightUI.regionIndex+1)%DogfightSettings.Region.values().length;dirty=true;}
            case "altitude"->{dogfightUI.altitudeIndex=(dogfightUI.altitudeIndex+1)%DogfightUI.HEIGHTS.length;dirty=true;}
            case "distance"->{dogfightUI.distanceIndex=(dogfightUI.distanceIndex+1)%DogfightUI.DISTANCES.length;dirty=true;}
            case "weapons"->{dogfightUI.weapons=!dogfightUI.weapons;dirty=true;}
            case "start-dogfight"->app.startDogfight(dogfightUI.settings());
        }
    }
    private static DogfightSettings.Region regionFor(String code){return switch(code){case "LSZH"->DogfightSettings.Region.ZURICH;case "LSGG"->DogfightSettings.Region.GENEVA;case "LFSB"->DogfightSettings.Region.BASEL;case "LSZB"->DogfightSettings.Region.BERN;case "LSZR"->DogfightSettings.Region.ALTENRHEIN;case "LSZA"->DogfightSettings.Region.LUGANO;case "LSGS"->DogfightSettings.Region.SION;case "LSZG"->DogfightSettings.Region.GRENCHEN;case "LSMP"->DogfightSettings.Region.PAYERNE;case "LSMM"->DogfightSettings.Region.MEIRINGEN;default->DogfightSettings.Region.EMMEN;};}
    public void update(float tpf){
        if(!menuOffen)return;paintClock+=Math.max(0,tpf);
        boolean resize=menuWidth!=app.getCamera().getWidth()||menuHeight!=app.getCamera().getHeight();
        var cursor=input.getCursorPosition();String pointed=hit((cursor.x-offsetX)/scale,(app.getCamera().getHeight()-cursor.y-offsetY)/scale);
        if(cursor.x!=pointerX||cursor.y!=pointerY){pointerX=cursor.x;pointerY=cursor.y;if(!pointed.equals(hover)){hover=pointed;dirty=true;}
            if(settingsScreen())settingsPanel.pointer((cursor.x-offsetX)/scale-310,(app.getCamera().getHeight()-cursor.y-offsetY)/scale-158);
        }
        long revision=GameSettings.get().revision();if(revision!=profileRevision){profileRevision=revision;dirty=true;}
        long pilotChange=app.getPilotProfiles().view().revision();if(pilotChange!=pilotRevision){pilotRevision=pilotChange;dirty=true;}
        long accountChange=app.getAccounts().view().revision();if(accountChange!=accountRevision){accountRevision=accountChange;dirty=true;}
        if(screen==Screen.ACCOUNT)dirty|=accountPanel.update();
        dirty|=friendsPanel.update();
        dirty|=multiplayerPanel.update();app.getFriends().shareCode(multiplayerPanel.invitationCode());
        var invitation=app.getFriends().takeJoin();if(invitation!=null){go(Screen.MULTIPLAYER);multiplayerPanel.joinInvitation(invitation.code(),invitation.password());dirty=true;}
        if(screen==Screen.PILOT_PROFILE)dirty|=pilotPanel.update();
        dirty|=settingsPanel.takeDirty();
        if(resize||dirty&&paintClock>=1f/25f)redraw();
    }
    private void redraw(){drawMenu(app.getCamera().getWidth(),app.getCamera().getHeight(),menuAssets);dirty=false;paintClock=0;}
    private void drawMenu(int width,int height,AssetManager assets){
        menuWidth=Math.max(1,width);menuHeight=Math.max(1,height);scale=Math.min(menuWidth/1600f,menuHeight/900f);offsetX=Math.max(0,(menuWidth-1600*scale)/2);offsetY=Math.max(0,(menuHeight-900*scale)/2);
        BufferedImage image=new BufferedImage(menuWidth,menuHeight,BufferedImage.TYPE_INT_RGB);Graphics2D g=image.createGraphics();
        try{MenuTheme.quality(g);MenuTheme.background(g,MenuTheme.photo(assets),menuWidth,menuHeight,screen==Screen.MAIN);
            g.setColor(new Color(8,14,20,235));if(screen==Screen.MAIN)g.fillRect(Math.round(offsetX+1100*scale),0,menuWidth-Math.round(offsetX+1100*scale),menuHeight);
            else{g.setColor(new Color(8,15,21,235));g.fillRect(0,0,menuWidth,Math.round(offsetY+82*scale));}
            g.translate(offsetX,offsetY);g.scale(scale,scale);hits.clear();
            if(screen==Screen.MAIN)main(g);else{shell(g);switch(screen){case FREE_PLAY->free(g);case MISSIONS->missions(g);case DOGFIGHT_AI->dogfight(g);case SETTINGS->settingsOverview(g);case GRAPHICS_SETTINGS,STEERING_SETTINGS,GAMEPLAY_SETTINGS,AUDIO_SETTINGS,INTERFACE_SETTING->settings(g);case MULTIPLAYER->multiplayer(g);case PILOT_PROFILE->pilotPanel.draw(g);case ACCOUNT->accountPanel.draw(g);case FRIENDS->friendsPanel.draw(g);default->{}}}
            if(quitConfirm)quit(g);
        }finally{g.dispose();}
        Texture2D texture=new Texture2D(new AWTLoader().load(image,true));var old=picture.getMaterial()==null?null:picture.getMaterial().getTextureParam("Texture");com.jme3.texture.Texture previous=old==null?null:old.getTextureValue();picture.setTexture(assets,texture,false);picture.setWidth(menuWidth);picture.setHeight(menuHeight);if(previous!=null&&previous.getImage()!=null)previous.getImage().dispose();
    }
    private void text(Graphics2D g,String value,int x,int y,int size,Color color,boolean bold){MenuTheme.text(g,value,x,y,size,color,bold);}
    private void button(Graphics2D g,String id,String label,int x,int y,int w,int h,boolean primary){Rectangle r=new Rectangle(x,y,w,h);hits.put(id,r);MenuTheme.button(g,r,label,primary,id.equals(hover),true);}
    private void header(Graphics2D g,String title,String subtitle){text(g,title,54,146,36,MenuTheme.TEXT,true);text(g,subtitle,56,179,17,MenuTheme.MUTED,false);}
    private void shell(Graphics2D g){
        g.setColor(new Color(8,15,21,235));g.fillRect(0,0,1600,82);text(g,"DSC",52,50,30,MenuTheme.TEXT,true);text(g,"FLIGHT TRAINING",125,49,16,MenuTheme.MUTED,false);
        String[][] navigation={{"free","FREE FLIGHT"},{"dogfight","DOGFIGHT"},{"missions","MISSIONS"},{"settings","SETTINGS"}};
        for(int i=0;i<navigation.length;i++){String id=navigation[i][0];boolean selected=id.equals("free")&&screen==Screen.FREE_PLAY||id.equals("dogfight")&&screen==Screen.DOGFIGHT_AI||id.equals("missions")&&screen==Screen.MISSIONS||id.equals("settings")&&(screen==Screen.SETTINGS||settingsScreen());Rectangle r=new Rectangle(540+i*185,16,170,50);hits.put(id,r);if(selected||hover.equals(id)){g.setColor(new Color(255,255,255,9));g.fillRect(r.x,r.y,r.width,r.height);}text(g,navigation[i][1],r.x+16,47,15,selected?MenuTheme.ACCENT:MenuTheme.TEXT,selected);if(selected){g.setColor(MenuTheme.ACCENT);g.fillRect(r.x+16,72,r.width-32,2);}}
        button(g,"friends",app.getFriends().view().notifications()>0?"FRIENDS •":"FRIENDS",1274,22,108,38,false);
        button(g,"home","MAIN MENU",1392,22,154,38,false);MenuTheme.line(g,54,837,1492);text(g,"F/A-18C HORNET",54,869,13,MenuTheme.MUTED,true);MenuTheme.right(g,"SWITZERLAND  /  FLIGHT TRAINING",1546,869,13,MenuTheme.MUTED);
    }
    private void main(Graphics2D g){
        var account=app.getAccounts().view();
        if(account.signedIn()){
            button(g,"account","ACCOUNT: "+account.name(),250,48,620,45,false);
        }else{
            String pilotName=app.getIdentityProvider().currentIdentity().displayName();if(pilotName.length()>18)pilotName=pilotName.substring(0,17)+"…";
            button(g,"pilot-profile","PILOT: "+pilotName,250,48,300,45,false);
            button(g,"account","SIGN IN / REGISTER",570,48,300,45,false);
        }
        int requests=app.getFriends().view().notifications();button(g,"friends",requests>0?"FRIENDS ("+requests+")":"FRIENDS",890,48,190,45,false);
        text(g,"DSC",54,76,46,MenuTheme.TEXT,true);text(g,"FLIGHT TRAINING",56,105,14,MenuTheme.ACCENT,true);
        text(g,"F/A-18C",58,705,66,MenuTheme.TEXT,true);text(g,"HORNET",60,758,42,MenuTheme.TEXT,false);text(g,"Switzerland  /  F/A-18C Hornet",62,797,19,MenuTheme.MUTED,false);
        text(g,"OPERATIONS",1150,140,13,MenuTheme.ACCENT,true);MenuTheme.line(g,1150,159,394);
        String[][] items={{"free","FREE FLIGHT","Choose an airfield and take off"},{"dogfight","DOGFIGHT","Set up an air combat session"},{"missions","MISSIONS","Training and mission selection"},{"settings","SETTINGS","Aircraft, controls and display"},{"multiplayer","MULTIPLAYER","Host a lobby or join a formation"},{"quit","EXIT","Close Flight Training"}};
        for(int i=0;i<items.length;i++){int y=190+i*88;String id=items[i][0];Rectangle r=new Rectangle(1134,y,426,77);hits.put(id,r);boolean selected=id.equals(hover);if(selected){g.setColor(new Color(221,171,85,22));g.fillRect(r.x,r.y,r.width,r.height);g.setColor(MenuTheme.ACCENT);g.fillRect(r.x,r.y,3,r.height);}text(g,items[i][1],1154,y+31,23,selected?MenuTheme.ACCENT:MenuTheme.TEXT,true);text(g,items[i][2],1155,y+57,14,MenuTheme.MUTED,false);if(selected)text(g,"›",1523,y+42,29,MenuTheme.ACCENT,false);}
        MenuTheme.line(g,1150,763,394);text(g,"F/A-18C HORNET",1152,798,15,MenuTheme.TEXT,true);text(g,"11 Swiss airfields  ·  Solo / network flight",1152,824,14,MenuTheme.MUTED,false);text(g,"TAB: SELECT    ENTER: OPEN    ESC: EXIT",56,866,12,MenuTheme.MUTED,true);
    }
    private void free(Graphics2D g){
        header(g,"Free flight","Choose your departure airfield. Terrain is prepared before the flight begins.");
        MenuTheme.panel(g,54,214,462,588);text(g,"DEPARTURE AIRFIELD",74,245,12,MenuTheme.MUTED,true);
        for(int i=0;i<AIRPORTS.length;i++){int y=261+i*47;Rectangle row=new Rectangle(64,y,442,45);String id="airport:"+i;hits.put(id,row);if(i==selectedAirport||id.equals(hover)){g.setColor(i==selectedAirport?new Color(221,171,85,25):new Color(255,255,255,8));g.fillRect(row.x,row.y,row.width,row.height);}if(i==selectedAirport){g.setColor(MenuTheme.ACCENT);g.fillRect(row.x,row.y,3,row.height);}text(g,CODES[i],80,y+28,14,i==selectedAirport?MenuTheme.ACCENT:MenuTheme.MUTED,true);text(g,AIRPORTS[i],150,y+28,19,MenuTheme.TEXT,false);}
        String code=CODES[selectedAirport];MenuTheme.cover(g,airportImages.get(code),542,214,1004,416);g.setPaint(new GradientPaint(0,425,new Color(5,11,17,0),0,630,new Color(5,11,17,245)));g.fillRect(542,214,1004,416);
        text(g,code,572,550,16,MenuTheme.ACCENT,true);text(g,AIRPORTS[selectedAirport],570,597,38,MenuTheme.TEXT,true);
        MenuTheme.right(g,MenuImages.credit(menuAssets,code),1532,623,11,MenuTheme.MUTED);
        MenuTheme.panel(g,542,650,1004,152);var region=regionFor(code);text(g,"AIRCRAFT",566,680,12,MenuTheme.MUTED,true);text(g,"F/A-18C Hornet",566,716,24,MenuTheme.TEXT,true);text(g,"START",900,680,12,MenuTheme.MUTED,true);text(g,selectedAirport==4?"398 m above ground":"On ground",900,713,21,MenuTheme.TEXT,false);
        text(g,"AIRFIELD POSITION",566,754,12,MenuTheme.MUTED,true);text(g,String.format(Locale.ROOT,"LV95  %.0f E / %.0f N",2621500+region.x,1092500-region.z),566,778,15,MenuTheme.MUTED,false);button(g,"start-free","START FLIGHT  →",1190,728,328,50,true);
    }
    private void missions(Graphics2D g){
        header(g,"Missions","Choose an activity. More mission scenarios will be added as they become available.");
        MenuTheme.panel(g,54,214,462,588);
        for(int i=0;i<MISSION_NAMES.length;i++){int y=238+i*83;Rectangle r=new Rectangle(66,y,438,72);hits.put("mission:"+i,r);if(i==selectedMission||hover.equals("mission:"+i)){g.setColor(new Color(221,171,85,i==selectedMission?25:12));g.fillRect(r.x,r.y,r.width,r.height);}text(g,String.format("%02d",i+1),83,y+35,13,MenuTheme.MUTED,false);text(g,MISSION_NAMES[i],127,y+35,22,MenuTheme.TEXT,true);text(g,i==3?"AI COMBAT":i<2?"FREE FLIGHT TRAINING":"PLANNED",128,y+57,11,i<=1||i==3?MenuTheme.ACCENT:MenuTheme.MUTED,true);}
        MenuTheme.cover(g,MenuTheme.briefing(menuAssets),542,214,1004,345);MenuTheme.panel(g,542,578,1004,224);text(g,MISSION_NAMES[selectedMission],570,622,31,MenuTheme.TEXT,true);MenuTheme.paragraph(g,MISSION_DETAILS[selectedMission],572,657,900,18,MenuTheme.MUTED);
        boolean available=selectedMission<2||selectedMission==3;
        if(available){text(g,selectedMission==3?"Configure your opponent and starting conditions.":"Choose an airfield next. No scored objectives in this training mode.",572,724,16,MenuTheme.MUTED,false);button(g,"start-mission",selectedMission==3?"CONFIGURE DOGFIGHT  →":"SELECT AIRFIELD  →",1180,736,338,46,true);}
        else text(g,"This scenario is not available in the current build.",572,742,17,MenuTheme.MUTED,false);
    }
    private void dogfight(Graphics2D g){
        header(g,"Dogfight","Configure an air combat training flight.");
        MenuTheme.fit(g,MenuTheme.detail(menuAssets),54,214,462,314);MenuTheme.panel(g,54,546,462,256);text(g,"YOUR AIRCRAFT",78,579,12,MenuTheme.ACCENT,true);text(g,"F/A-18C Hornet",76,626,30,MenuTheme.TEXT,true);text(g,"Swiss Air Force",78,656,18,MenuTheme.MUTED,false);MenuTheme.line(g,78,678,414);text(g,"HEAD-ON ENGAGEMENT",78,710,12,MenuTheme.MUTED,true);text(g,dogfightUI.count+" opponent"+(dogfightUI.count>1?"s":"")+"  /  "+English.text(DogfightSettings.Difficulty.values()[dogfightUI.difficultyIndex].label),78,746,21,MenuTheme.TEXT,false);
        MenuTheme.panel(g,542,214,1004,588);text(g,"ENGAGEMENT SETUP",568,250,12,MenuTheme.ACCENT,true);
        field(g,"OPPONENTS",Integer.toString(dogfightUI.count),568,284,460);button(g,"countMinus","−",820,307,64,46,false);button(g,"countPlus","+",892,307,64,46,false);
        select(g,"difficulty","DIFFICULTY",English.text(DogfightSettings.Difficulty.values()[dogfightUI.difficultyIndex].label),1050,284,466);
        select(g,"region","START REGION",English.text(DogfightSettings.Region.values()[dogfightUI.regionIndex].label),568,391,460);
        select(g,"altitude","HEIGHT ABOVE GROUND",String.format(Locale.ROOT,"%.0f m",DogfightUI.HEIGHTS[dogfightUI.altitudeIndex]),1050,391,466);
        select(g,"distance","INITIAL SEPARATION",String.format(Locale.ROOT,"%.0f km",DogfightUI.DISTANCES[dogfightUI.distanceIndex]/1000),568,498,460);
        select(g,"weapons","WEAPONS",dogfightUI.weapons?"Cannon & IR missiles":"Unarmed",1050,498,466);
        MenuTheme.line(g,568,621,948);text(g,"Terrain and aircraft are prepared before the session starts.",570,659,17,MenuTheme.MUTED,false);text(g,"Use a short starting range for immediate contact.",570,687,15,MenuTheme.MUTED,false);button(g,"start-dogfight","START DOGFIGHT  →",1180,728,336,50,true);
    }
    private void field(Graphics2D g,String label,String value,int x,int y,int width){text(g,label,x,y,12,MenuTheme.MUTED,true);g.setColor(new Color(8,16,23,150));g.fillRect(x,y+17,width,59);MenuTheme.line(g,x,y+76,width);text(g,value,x+17,y+56,22,MenuTheme.TEXT,true);}
    private void select(Graphics2D g,String id,String label,String value,int x,int y,int width){field(g,label,value,x,y,width);Rectangle r=new Rectangle(x,y+17,width,59);hits.put(id,r);if(hover.equals(id)){g.setColor(MenuTheme.ACCENT);g.drawRect(r.x,r.y,r.width,r.height);}text(g,"›",x+width-31,y+56,27,MenuTheme.ACCENT,false);}
    private void settingsOverview(Graphics2D g){
        header(g,"Settings","Configure your display, aircraft controls and instruments.");
        String[] details={"Display mode, resolution and rendering.","Keyboard bindings and controller response.","Flight model and assistance.","Engine, weapons, cockpit warnings and radio.","Instruments, units and target markers."};
        for(int i=0;i<CATEGORIES.length;i++){int y=220+i*108;Rectangle r=new Rectangle(54,y,890,92);String id="settings:"+CATEGORIES[i];hits.put(id,r);MenuTheme.panel(g,r.x,r.y,r.width,r.height);if(id.equals(hover)){g.setColor(MenuTheme.ACCENT);g.fillRect(r.x,r.y,3,r.height);}text(g,CATEGORIES[i],80,y+37,24,MenuTheme.TEXT,true);text(g,details[i],82,y+67,17,MenuTheme.MUTED,false);text(g,"›",895,y+55,28,MenuTheme.ACCENT,false);}
        MenuTheme.panel(g,972,220,574,523);text(g,"YOUR CONFIGURATION",1000,262,12,MenuTheme.ACCENT,true);text(g,"F/A-18C Hornet",998,312,29,MenuTheme.TEXT,true);MenuTheme.line(g,1000,342,516);text(g,"DISPLAY",1000,383,12,MenuTheme.MUTED,true);text(g,GameSettings.get().value(GameSettings.Option.RESOLUTION),1000,418,24,MenuTheme.TEXT,false);text(g,"FLIGHT MODEL",1000,472,12,MenuTheme.MUTED,true);text(g,GameSettings.get().value(GameSettings.Option.MODEL),1000,507,24,MenuTheme.TEXT,false);MenuTheme.paragraph(g,"Changes are saved automatically. Each category can be restored to its defaults.",1000,592,485,18,MenuTheme.MUTED);
    }
    private void settings(Graphics2D g){
        text(g,"SETTINGS",54,139,12,MenuTheme.ACCENT,true);
        for(int i=0;i<CATEGORIES.length;i++){int y=172+i*62;String id="settings:"+CATEGORIES[i];Rectangle r=new Rectangle(54,y,230,53);hits.put(id,r);boolean selected=CATEGORIES[i].equals(settingsPage());if(selected||hover.equals(id)){g.setColor(new Color(221,171,85,selected?25:12));g.fillRect(r.x,r.y,r.width,r.height);}if(selected){g.setColor(MenuTheme.ACCENT);g.fillRect(r.x,r.y,3,r.height);}text(g,CATEGORIES[i],74,y+34,21,selected?MenuTheme.ACCENT:MenuTheme.TEXT,selected);}
        Graphics2D body=(Graphics2D)g.create();body.translate(310,158);body.clipRect(0,0,1236,651);settingsPanel.draw(body,settingsPage(),1236,651);body.dispose();
    }
    private void multiplayer(Graphics2D g){multiplayerPanel.draw(g);}
    private void quit(Graphics2D g){
        hits.clear();g.setColor(new Color(0,0,0,190));g.fillRect(0,0,1600,900);MenuTheme.panel(g,520,310,560,275);text(g,"Exit Flight Training?",552,365,29,MenuTheme.TEXT,true);text(g,"Your settings have been saved.",554,407,18,MenuTheme.MUTED,false);button(g,"cancel","CANCEL",552,494,220,50,false);button(g,"confirm-quit","EXIT",800,494,246,50,true);
    }
    public void showMultiplayer(Node guiNode){screen=Screen.MULTIPLAYER;quitConfirm=false;show(guiNode);}
    public void showMain(Node guiNode){screen=Screen.MAIN;quitConfirm=false;show(guiNode);}
    public void show(Node guiNode){guiNode.attachChild(picture);menuOffen=true;redraw();}
    public void hide(){friendsPanel.leave();accountPanel.leave();friendsPanel.leave();settingsPanel.leave();picture.removeFromParent();menuOffen=false;}
    public boolean getMenuOffen(){return menuOffen;}
    private static final class DogfightUI {
        private int count=1,difficultyIndex=1,regionIndex=0,altitudeIndex=3,distanceIndex=2;private boolean weapons=true;
        private static final float[] HEIGHTS={600,1000,2000,3000,4000,6000},DISTANCES={1000,3000,5000,8000,10000};
        private DogfightSettings settings(){return new DogfightSettings(DogfightSettings.Region.values()[regionIndex],DogfightSettings.Difficulty.values()[difficultyIndex],count,HEIGHTS[altitudeIndex],DISTANCES[distanceIndex],weapons);}
    }
}
