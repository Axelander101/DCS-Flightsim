package alxb.flighttraining.game.ui;

import alxb.flighttraining.game.enemy.Enemy;
import alxb.flighttraining.game.flight.FlightPhysics;
import com.jme3.asset.AssetManager;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;
import java.awt.*;
import java.util.Locale;

/** Live flight instruments drawn onto the actual 3D display surfaces, not a screen overlay. */
public final class FlightHud {
    private static Color GREEN = new Color(96,255,140);
    private static final Color DIM = new Color(22,89,53);
    private static final Font FONT = new Font(Font.MONOSPACED, Font.BOLD, 36);
    private final CockpitTexture hud, radar, flight, navigation, scratchpad;
    private final NavigationAtlas navigationAtlas;
    private final NavigationDisplay navigationDisplay;
    private alxb.flighttraining.game.sensors.Radar tacticalRadar;
    public void setRadar(alxb.flighttraining.game.sensors.Radar value){tacticalRadar=value;}
    private boolean wasPowered=true,airDataValid=true;
    private int gunRounds=alxb.flighttraining.game.weapons.GunAmmunition.CAPACITY;
    private boolean unlimitedAmmo;
    public void setAmmunition(int rounds,boolean unlimited){gunRounds=rounds;unlimitedAmmo=unlimited;}
    public void navigationCommands(int commands){navigationDisplay.command(commands);if(commands!=0)nextPage=0;}
    public void resetNavigation(float worldX,float worldZ){navigationDisplay.model().reset(2621500.0+worldX,1092500.0-worldZ);}
    public void close(){navigationAtlas.close();}
    private final Vector3f mapPosition=new Vector3f();
    public void setMapPosition(Vector3f value){mapPosition.set(value);}
    private final Node plane;
    private final Enemy enemies;
    private boolean visible, weaponsEnabled = true;
    private int missiles, page;
    private long nextHud, nextPage;

    public FlightHud(AssetManager assets, CockpitView cockpit, Node plane, Enemy enemies) {
        this.plane = plane; this.enemies = enemies;
        navigationAtlas=new NavigationAtlas(assets);navigationDisplay=new NavigationDisplay(navigationAtlas);
        hud = new CockpitTexture(assets, cockpit.surface("HUD_Symbols_Surface"), 512, 512, true);
        radar = new CockpitTexture(assets, cockpit.surface("DDI_Left_Surface"), 512, 512, false);
        flight = new CockpitTexture(assets, cockpit.surface("DDI_Right_Surface"), 512, 512, false);
        navigation = new CockpitTexture(assets, cockpit.surface("AMPCD_Surface"), 512, 512, false);
        scratchpad = new CockpitTexture(assets, cockpit.surface("UFC_Scratchpad_Surface"), 512, 64, false);
        showLoading("");
    }
    public void setVisible(boolean value) { if (value && !visible) nextHud = nextPage = 0; visible = value; }
    public void setWeapons(int missiles, boolean enabled) { this.missiles = missiles; weaponsEnabled = enabled; }
    public void showLoading(String status) {
        for (CockpitTexture screen : new CockpitTexture[]{radar, flight, navigation, scratchpad}) {
            Graphics2D g = screen.begin(false); g.setColor(GREEN); g.setFont(FONT);
            if (!status.isEmpty()) g.drawString("LOADING", 25, 45); screen.upload(g);
        }
        hud.upload(hud.begin(true)); nextHud = nextPage = 0;
    }
    public void update(FlightPhysics physics, float altitude) {
        if (!visible) return;
        airDataValid=physics.getDamage().health(alxb.flighttraining.game.combat.AirframeDamage.Part.AIR_DATA)>.25f;
        boolean powered=physics.getSystems().combat().electrical();
        if(!powered){if(wasPowered){showLoading("");wasPowered=false;}return;}
        if(!wasPowered){nextHud=nextPage=0;wasPowered=true;}
        float brightness=alxb.flighttraining.game.config.GameSettings.get().fraction(alxb.flighttraining.game.config.GameSettings.Option.BRIGHTNESS);
        GREEN=new Color(Math.round(96*brightness),Math.round(255*brightness),Math.round(140*brightness));
        long now = System.nanoTime();
        if (now >= nextHud) { paintHud(physics, altitude); nextHud = now + 50_000_000L; }
        // Stagger the large textures; never regenerate all instrument pages in one frame.
        if (now >= nextPage) {
            switch (page++ % 4) {
                case 0 -> paintRadar(physics);
                case 1 -> paintFlight(physics, altitude);
                case 2 -> paintNavigation(physics);
                case 3 -> paintScratchpad(physics);
            }
            nextPage = now + 25_000_000L;
        }
    }
    private static String key(String action){return com.jme3.input.KeyNames.getName(alxb.flighttraining.game.config.GameSettings.get().key(action));}
    private boolean aviation(){return alxb.flighttraining.game.config.GameSettings.get().value(alxb.flighttraining.game.config.GameSettings.Option.UNITS).equals("Aviation");}
    private float indicatedSpeed(float value){return !airDataValid?Float.NaN:aviation()?value*1.9438445f:value;}
    private float indicatedAltitude(float value){return !airDataValid?Float.NaN:aviation()?value*3.28084f:value;}
    private float indicatedVertical(float value){return !airDataValid?Float.NaN:aviation()?value*196.8504f:value;}
    private static String number(float value, String format) {
        return Float.isFinite(value) ? String.format(Locale.ROOT, format, value) : "---";
    }
    private float heading() {
        Vector3f f = plane.getWorldRotation().mult(Vector3f.UNIT_Z.negate());
        return ((float) Math.toDegrees(Math.atan2(f.x, -f.z)) + 360) % 360;
    }
    private static void heading(Graphics2D g, String title) {
        g.setFont(FONT.deriveFont(Font.BOLD, 29)); g.setColor(GREEN); g.drawString(title, 22, 42);
        g.setStroke(new BasicStroke(2)); g.drawLine(20, 57, 492, 57); g.setFont(FONT);
    }
    private static void row(Graphics2D g, int y, String key, String value) {
        g.setColor(GREEN); g.drawString(key, 25, y);
        g.drawString(value, 485 - g.getFontMetrics().stringWidth(value), y);
    }
    private void paintHud(FlightPhysics p, float altitude) {
        Graphics2D g = hud.begin(true); g.setColor(GREEN); g.setStroke(new BasicStroke(2.4f));
        g.setFont(FONT.deriveFont(Font.BOLD, 46));
        g.drawRect(10, 130, 147, 49); g.drawRect(334, 130, 168, 49);
        g.drawString(number(indicatedSpeed(p.getAirspeed()), "%3.0f"), 25, 167);
        String alt = number(indicatedAltitude(altitude), "%5.0f"); g.drawString(alt, 492 - g.getFontMetrics().stringWidth(alt), 167);
        g.setFont(FONT.deriveFont(21f)); g.drawString(aviation()?"TAS kt":"TAS m/s", 18, 119); g.drawString(aviation()?"ALT ft":"ALT m", 364, 119);
        g.setFont(FONT.deriveFont(36f));
        String hdg = number(heading(), "%03.0f"); g.drawString(hdg, 232, 64);
        for (int i = -3; i <= 3; i++) g.drawLine(256 + i * 31, 75, 256 + i * 31, i == 0 ? 94 : 86);
        g.drawOval(243, 239, 26, 26); g.drawLine(222, 252, 243, 252);
        g.drawLine(269, 252, 290, 252); g.drawLine(256, 222, 256, 239);
        // A real attitude horizon in the physical HUD frame.
        Vector3f forward = plane.getWorldRotation().mult(Vector3f.UNIT_Z.negate());
        Vector3f right = plane.getWorldRotation().mult(Vector3f.UNIT_X);
        Vector3f up = plane.getWorldRotation().mult(Vector3f.UNIT_Y);
        float pitch = (float) Math.asin(Math.max(-1, Math.min(1, forward.y)));
        float bank = (float) Math.atan2(right.y, up.y);
        Graphics2D horizon = (Graphics2D) g.create(); horizon.translate(256, 252 + Math.max(-120, Math.min(120, pitch * 190)));
        horizon.rotate(-bank); horizon.drawLine(-112, 0, -30, 0); horizon.drawLine(30, 0, 112, 0); horizon.dispose();
        g.drawString("M " + number(p.getMach(), "%.2f"), 16, 400);
        g.drawString("G " + number(p.getNormalG(), "%+.1f"), 333, 400);
        g.setFont(FONT.deriveFont(23f));
        g.drawString("VS " + number(indicatedVertical(p.getVerticalSpeed()), "%+.0f") + (aviation()?" ft/min":" m/s"), 135, 455);
        if (p.isStalling()) { g.setColor(new Color(255, 195, 75)); g.drawString("HIGH AOA", 196, 345); }
        paintWeaponCues(g,p);
        var cautions=p.getSystems().combat().cautions();if(!cautions.isEmpty()){g.setColor(new Color(255,170,65));g.setFont(FONT.deriveFont(21f));g.drawString(cautions.getFirst(),Math.max(12,256-g.getFontMetrics().stringWidth(cautions.getFirst())/2),315);}
        hud.upload(g);
    }
    private void paintWeaponCues(Graphics2D g,FlightPhysics p){
        if(tacticalRadar==null||!p.getSystems().combat().masterArm())return;
        com.jme3.scene.Spatial target=tacticalRadar.irTarget()!=null?tacticalRadar.irTarget():tacticalRadar.selected();
        if(target!=null){var local=plane.getWorldRotation().inverse().mult(target.getWorldTranslation().subtract(plane.getWorldTranslation()));if(local.z<-.1f){int x=Math.round(256+local.x/-local.z*350),y=Math.round(252-local.y/-local.z*350);if(x>35&&x<477&&y>100&&y<355){g.setColor(GREEN);g.drawRect(x-14,y-14,28,28);if(tacticalRadar.irTarget()==target)g.drawOval(x-20,y-20,40,40);}}}
        Vector3f lead=tacticalRadar.gunLead();if(lead!=null&&p.getDamage().health(alxb.flighttraining.game.combat.AirframeDamage.Part.WEAPONS)>.25f){var local=plane.getWorldRotation().inverse().mult(lead);if(local.z<-.1f){int x=Math.round(256+local.x/-local.z*350),y=Math.round(252-local.y/-local.z*350);if(x>25&&x<487&&y>100&&y<355){g.setColor(GREEN);g.drawOval(x-8,y-8,16,16);g.drawLine(x-12,y,x+12,y);g.drawLine(x,y-12,x,y+12);}}}
    }
    private static void cell(Graphics2D g,int x,int y,String label,String value){
        g.setColor(GREEN);g.setFont(FONT.deriveFont(27f));g.drawString(label,x,y);
        g.setFont(FONT.deriveFont(54f));g.drawString(value,x,y+57);
    }
    private void paintFlight(FlightPhysics p,float altitude){
        Graphics2D g=flight.begin(false);heading(g,"FLIGHT");
        cell(g,22,92,(aviation()?"TAS kt":"TAS m/s"),number(indicatedSpeed(p.getAirspeed()),"%.0f"));
        cell(g,275,92,(aviation()?"ALT ft":"ALT m"),number(indicatedAltitude(altitude),"%.0f"));
        cell(g,22,199,"MACH",number(p.getMach(),"%.2f"));
        cell(g,275,199,"G LOAD",number(p.getNormalG(),"%+.1f"));
        cell(g,22,306,"AOA deg",number(-p.getAngleOfAttack(),"%+.1f"));
        cell(g,275,306,(aviation()?"V/S ft/min":"V/S m/s"),number(indicatedVertical(p.getVerticalSpeed()),"%+.0f"));
        cell(g,22,413,(aviation()?"EAS kt":"EAS m/s"),number(indicatedSpeed(p.getEquivalentAirspeed()),"%.0f"));
        cell(g,275,413,"THR %",number(p.getThrottle()*100,"%.0f"));
        flight.upload(g);
    }
    private static void status(Graphics2D g,int y,String key,String value){
        g.setColor(GREEN);g.setFont(FONT.deriveFont(27f));g.drawString(key,24,y);
        g.setFont(FONT.deriveFont(43f));g.drawString(value,24,y+42);
    }
    private void paintNavigation(FlightPhysics p){
        if(p.getSystems().combat().damagePage()){paintDamage(p);return;}
        if(!p.getSystems().isSystemsPage()){paintNavigationData(p);return;}
        var state=p.getSystems();Graphics2D g=navigation.begin(false);
        heading(g,state.isAirframePage()?"AIRFRAME":"LANDING / SYSTEMS");
        if(state.isAirframePage()){
            status(g,88,"CANOPY ["+key("AircraftCanopy")+"]",state.getCanopy()<.01f?"CLOSED":state.getCanopy()>.99f?"OPEN":"MOVING");
            status(g,164,"HOOK ["+key("AircraftHook")+"]",state.getHook()>.99f?"DOWN":state.getHook()<.01f?"UP":"MOVING");
            status(g,240,"WING FOLD ["+key("AircraftFold")+"]",state.getWingFold()<.01f?"SPREAD":number(state.getWingFold()*100,"%.0f")+" %");
            status(g,316,"IR MISSILES",unlimitedAmmo?"UNLIMITED":missiles+" / 4");
            status(g,392,"WEAPONS",weaponsEnabled?"READY":"SAFE");
        }else{
            String gear=state.getGear()>.99f?"DOWN":state.getGear()<.01f?"UP":"MOVING";
            status(g,88,"GEAR ["+key("AircraftGear")+"]",gear);
            status(g,164,"BRAKE ["+key("AircraftWheelBrake")+"/"+key("AircraftParkingBrake")+"]",state.isParkingBrake()?"PARK":state.getBrake()>.5f?"ON":"OFF");
            status(g,240,"AIRBRAKE ["+key("AircraftAirbrake")+"]",number(state.getAirbrake()*100,"%.0f")+" %");
            status(g,316,"FLAPS ["+key("AircraftFlaps")+"]",state.getFlapMode().toString());
            status(g,392,"ENGINE ["+key("AircraftEngine")+"]",p.isEngineRunning()?"RUN":"OFF");
        }
        g.setFont(FONT.deriveFont(22f));
        if(!state.getNotice().isEmpty()){
            g.setColor(new Color(255,195,75));g.drawString(state.getNotice(),22,466);
        }
        g.setColor(GREEN);g.drawString(key("AircraftDisplayDetail")+": DETAIL  "+key("AircraftDisplayPage")+": NAV  Z: ZOOM",22,496);
        navigation.upload(g);
    }
    private void paintDamage(FlightPhysics p){
        Graphics2D g=navigation.begin(false);heading(g,"F/A-18C / FCS & CAUTIONS");var state=p.getSystems().combat();
        g.setFont(FONT.deriveFont(23f));g.setColor(GREEN);
        g.drawString(String.format(Locale.ROOT,"HYD1 %3.0f%%  HYD2 %3.0f%%",state.hydraulic(1)*100,state.hydraulic(2)*100),22,92);
        g.drawString(String.format(Locale.ROOT,"L ENG %3.0f%%  R ENG %3.0f%%",p.getEnginePower(true)*100,p.getEnginePower(false)*100),22,124);
        g.drawString(String.format(Locale.ROOT,"FUEL %3.0f%%  GEN %s/%s",p.getDamage().fuel()*100,state.generator(true)?"L":"-",state.generator(false)?"R":"-"),22,156);
        g.drawString("FIRE BOTTLE "+(state.fireBottle()?"READY":"DISCHARGED"),22,188);
        g.drawString("FLARES "+state.flares()+"  ARM "+(state.masterArm()?"ARM":"SAFE"),22,220);
        var warnings=state.cautions();g.setColor(warnings.isEmpty()?GREEN:new Color(255,175,70));g.drawString(warnings.isEmpty()?"NO CAUTIONS":"MASTER CAUTION",22,261);
        int i=0;for(String warning:warnings){if(i>=7)break;g.drawString(warning,22,292+i++*26);}
        g.setColor(GREEN);g.setFont(FONT.deriveFont(18f));g.drawString(key("AircraftDamagePage")+": RETURN  "+key("AircraftCountermeasures")+": CM",22,499);navigation.upload(g);
    }
    private void paintNavigationData(FlightPhysics p){
        Graphics2D g=navigation.begin(false);
        if(p.getDamage().health(alxb.flighttraining.game.combat.AirframeDamage.Part.INS)<.25f){heading(g,"HSI / INS FAIL");navigation.upload(g);return;}
        Vector3f velocity=p.getVelocity();
        navigationDisplay.draw(g,2621500.0+mapPosition.x,1092500.0-mapPosition.z,heading(),Math.hypot(velocity.x,velocity.z),alxb.flighttraining.game.config.GameSettings.get());
        navigation.upload(g);
    }
    private void paintRadar(FlightPhysics p) {
        Graphics2D g=radar.begin(false);heading(g,"APG-73 / 10 NM");
        if(!p.getSystems().combat().radarAvailable()){g.drawString(p.getSystems().combat().radarSelected()?"RADAR FAIL":"RADAR OFF",45,150);g.setFont(FONT.deriveFont(22f));g.drawString(rwrStatus(),22,471);radar.upload(g);return;}
        int cx=256,cy=426;float scale=340f/(tacticalRadar==null?18520:tacticalRadar.range());g.setColor(DIM);g.drawLine(38,cy,474,cy);g.drawLine(cx,80,cx,cy);g.drawArc(70,250,372,350,0,180);g.setColor(GREEN);
        if(tacticalRadar!=null)for(var contact:tacticalRadar.contacts()){float bearing=(float)Math.toRadians(contact.bearing());int x=Math.round(cx+(float)Math.sin(bearing)*contact.range()*scale),y=Math.round(cy-(float)Math.cos(bearing)*contact.range()*scale);if(x<25||x>487||y<75||y>440)continue;g.drawRect(x-5,y-5,10,10);if(contact.target()==tacticalRadar.selected())g.drawOval(x-12,y-12,24,24);}
        g.setFont(FONT.deriveFont(22f));g.drawString(tacticalRadar!=null&&tacticalRadar.isLocked()?"STT LOCK":"RWS SEARCH",22,471);
        g.setColor(new Color(255,180,60));g.drawString(rwrStatus(),260,471);
        g.setColor(GREEN);g.setFont(FONT.deriveFont(18f));g.drawString(key("AircraftNextTarget")+": TARGET  "+key("AircraftLockTarget")+": LOCK  "+key("AircraftRadar")+": RADAR",22,500);radar.upload(g);
    }
    private boolean networkSearch,networkLock;
    public void networkRwr(boolean search,boolean lock){networkSearch=search;networkLock=lock;}
    private String rwrStatus(){
        boolean threat=networkSearch,lock=networkLock;for(int i=0;i<enemies.getCount();i++){var enemy=enemies.aircraft(i);var emitter=enemy.getRadar();if(emitter==null||!enemy.getPhysics().getSystems().combat().radarAvailable())continue;var offset=plane.getWorldTranslation().subtract(enemy.getPosition());float distance=offset.length();if(distance>1&&distance<18520&&enemy.getPlane().getWorldRotation().mult(Vector3f.UNIT_Z.negate()).dot(offset.divide(distance))>.5f){threat=true;if(emitter.lockedTarget()==plane)lock=true;}}
        return !threat?"":lock?"RWR 18 STT":"RWR 18 SEARCH";
    }
    private void paintScratchpad(FlightPhysics physics) {
        Graphics2D g=scratchpad.begin(false);g.setColor(GREEN);g.setFont(FONT.deriveFont(Font.BOLD,46));
        g.drawString(!weaponsEnabled ? "MASTER ARM SAFE" : "IR " + (unlimitedAmmo?"∞":missiles) + " GUN " + (unlimitedAmmo?"∞":gunRounds),18,46);
        scratchpad.upload(g);
    }
}
