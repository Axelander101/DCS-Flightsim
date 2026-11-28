package alxb.flighttraining.game.config;

import com.google.gson.*;
import com.jme3.input.KeyInput;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.util.*;

/** Validated user preferences. Only implemented settings are exposed as controls. */
public final class GameSettings {
    public enum Option {
        RESOLUTION("Graphics","Resolution","1920 × 1080","1920 × 1080","2560 × 1440","3840 × 2160"),
        DISPLAY("Graphics","Display mode","Windowed","Windowed","Fullscreen"),
        VSYNC("Graphics","VSync","On","Off","On"),
        FPS("Graphics","FPS limit","60","30","60","90","120","Unlimited"),
        DISTANCE("Graphics","Render distance","8 km","3 km","5 km","8 km","12 km","20 km"),
        FILTER("Graphics","Anisotropic filtering","4x","1x","2x","4x","8x","16x"),
        TEXTURE_QUALITY("Graphics","Terrain textures","Standard","Low","Standard","High"),
        TERRAIN_QUALITY("Graphics","Terrain geometry","High","Low","Standard","High"),
        NAV_RANGE("Interface","Map width","10 km","5 km","10 km","25 km","50 km","100 km"),
        NAV_ORIENTATION("Interface","Map orientation","North up","North up","Heading up"),
        UNLIMITED_AMMO("Gameplay","Unlimited ammunition","Off","Off","On"),
        DEBUG("Interface","Debug statistics","Off","Off","On"),
        PITCH("Controls","Pitch sensitivity","100%","25%","50%","75%","100%","125%","150%"),
        ROLL("Controls","Roll sensitivity","100%","25%","50%","75%","100%","125%","150%"),
        YAW("Controls","Yaw sensitivity","100%","25%","50%","75%","100%","125%","150%"),
        DEADZONE("Controls","Controller deadzone","5%","0%","5%","10%","15%","20%"),
        INVERT_PITCH("Controls","Invert pitch axis","Off","Off","On"),
        INVERT_ROLL("Controls","Invert roll axis","Off","Off","On"),
        INVERT_YAW("Controls","Invert yaw axis","Off","Off","On"),
        MASTER("Audio","Master volume","65%","0%","10%","20%","30%","40%","50%","60%","65%","70%","80%","90%","100%"),
        ENGINE("Audio","Engine volume","100%","0%","25%","50%","75%","100%"),
        WEAPONS("Audio","Weapons volume","100%","0%","25%","50%","75%","100%"),
        ENVIRONMENT("Audio","Environment volume","100%","0%","25%","50%","75%","100%"),
        RADIO("Audio","Radio volume","100%","0%","25%","50%","75%","100%"),
        WARNINGS("Audio","Cockpit warnings","100%","0%","25%","50%","75%","100%"),
        UI("Audio","UI volume","100%","0%","25%","50%","75%","100%"),
        MODEL("Gameplay","Flight model","Standard","Arcade","Standard","Simulation"),
        STABILISATION("Gameplay","Automatic stabilisation","On","Off","On"),
        G_LIMIT("Gameplay","G limiter","On","Off","On"),
        MARKERS("Interface","Enemy markers","On","Off","On"),
        BRIGHTNESS("Interface","Instrument brightness","100%","25%","50%","75%","100%"),
        UNITS("Interface","Instrument units","Metric","Metric","Aviation");
        public final String page,label,initial;public final List<String> choices;
        Option(String page,String label,String initial,String...choices){this.page=page;this.label=label;this.initial=initial;this.choices=label.equals("Master volume")?java.util.stream.IntStream.rangeClosed(0,100).mapToObj(i->i+"%").toList():List.of(choices);}
    }
    public static final Map<String,Integer> DEFAULT_KEYS;
    static {
        Map<String,Integer> keys=new LinkedHashMap<>();
        keys.put("PitchUp",KeyInput.KEY_W);keys.put("PitchDown",KeyInput.KEY_S);keys.put("RollLeft",KeyInput.KEY_A);keys.put("RollRight",KeyInput.KEY_D);
        keys.put("YawLeft",KeyInput.KEY_Q);keys.put("YawRight",KeyInput.KEY_E);keys.put("ThrottleUp",KeyInput.KEY_R);keys.put("ThrottleDown",KeyInput.KEY_F);
        keys.put("Shoot",KeyInput.KEY_SPACE);keys.put("InfraredMissile",KeyInput.KEY_M);
        keys.put("NavZoomIn",KeyInput.KEY_EQUALS);keys.put("NavZoomOut",KeyInput.KEY_MINUS);
        keys.put("NavOrientation",KeyInput.KEY_O);keys.put("NavNextAirfield",KeyInput.KEY_N);
        String[] systems={"AircraftGear","AircraftAirbrake","AircraftFlaps","AircraftParkingBrake","AircraftCanopy","AircraftHook","AircraftFold","AircraftEngine","AircraftDisplayPage","AircraftDisplayDetail","AircraftWheelBrake"};
        int[] codes={KeyInput.KEY_G,KeyInput.KEY_X,KeyInput.KEY_L,KeyInput.KEY_P,KeyInput.KEY_C,KeyInput.KEY_H,KeyInput.KEY_K,KeyInput.KEY_I,KeyInput.KEY_V,KeyInput.KEY_TAB,KeyInput.KEY_B};
        for(int i=0;i<systems.length;i++)keys.put(systems[i],codes[i]);
        String[] combat={"AircraftRadar","AircraftMasterArm","AircraftNextTarget","AircraftLockTarget","AircraftCountermeasures","AircraftFireLeft","AircraftFireRight","AircraftDischarge","AircraftEngineLeft","AircraftEngineRight","AircraftDamagePage"};
        int[] combatKeys={KeyInput.KEY_J,KeyInput.KEY_U,KeyInput.KEY_T,KeyInput.KEY_Y,KeyInput.KEY_F3,KeyInput.KEY_F5,KeyInput.KEY_F6,KeyInput.KEY_F7,KeyInput.KEY_F11,KeyInput.KEY_F12,KeyInput.KEY_F2};
        for(int i=0;i<combat.length;i++)keys.put(combat[i],combatKeys[i]);DEFAULT_KEYS=Collections.unmodifiableMap(keys);
    }
    private static final GameSettings INSTANCE=new GameSettings(defaultPath());
    public static GameSettings get(){return INSTANCE;}
    private static Path defaultPath(){return Path.of(System.getProperty("dsc.settingsFile",Path.of(System.getProperty("user.home"),"Library","Application Support","DSC","settings.json").toString()));}
    private final Path path;private final EnumMap<Option,String> values=new EnumMap<>(Option.class);private final Map<String,Integer> keys=new LinkedHashMap<>();
    private long revision;private String saveError="";
    public GameSettings(Path file){path=file;for(Option o:Option.values())values.put(o,o.initial);keys.putAll(DEFAULT_KEYS);load();}
    private void load(){
        if(!Files.isRegularFile(path))return;
        try(var reader=Files.newBufferedReader(path)){
            JsonObject object=JsonParser.parseReader(reader).getAsJsonObject();JsonObject options=object.getAsJsonObject("options"),keyboard=object.getAsJsonObject("keyboard");
            if(options!=null)for(Option o:Option.values())try{var e=options.get(o.name());if(e!=null&&o.choices.contains(e.getAsString()))values.put(o,e.getAsString());}catch(RuntimeException ignored){}
            if(keyboard!=null){
                Map<String,Integer> loaded=new LinkedHashMap<>();Set<Integer> used=new HashSet<>();
                // Preserve existing user bindings first when new actions are introduced.
                for(String action:DEFAULT_KEYS.keySet())if(keyboard.has(action)){
                    int code=keyboard.get(action).getAsInt();
                    if(!validKey(code)||!used.add(code))throw new IllegalArgumentException("Invalid keyboard profile");
                    loaded.put(action,code);
                }
                for(String action:DEFAULT_KEYS.keySet())if(!loaded.containsKey(action)){
                    int code=DEFAULT_KEYS.get(action);
                    if(used.contains(code)){
                        code=-1;
                        for(int candidate=1;candidate<=255;candidate++)if(validKey(candidate)&&!used.contains(candidate)&&!DEFAULT_KEYS.containsValue(candidate)){code=candidate;break;}
                        if(code<0)throw new IllegalArgumentException("No free key for "+action);
                    }
                    used.add(code);loaded.put(action,code);
                }
                keys.putAll(loaded);
            }

        }catch(IOException|RuntimeException error){saveError="Some saved settings could not be loaded.";System.err.println("Settings load: "+error.getMessage());}
    }
    public synchronized String value(Option o){return values.get(o);}
    public synchronized boolean enabled(Option o){return value(o).equals("On");}
    public synchronized float fraction(Option o){return Float.parseFloat(value(o).replace("%",""))/100f;}
    public synchronized long revision(){return revision;}
    public synchronized String saveError(){return saveError;}
    public synchronized void cycle(Option o,int direction){set(o,o.choices.get(Math.floorMod(o.choices.indexOf(value(o))+direction,o.choices.size())));}
    public synchronized void set(Option o,String value){if(!o.choices.contains(value))throw new IllegalArgumentException("Invalid preference "+o);if(!values.get(o).equals(value)){values.put(o,value);revision++;save();}}
    public synchronized void setMaster(float volume){String closest=Option.MASTER.choices.get(0);float distance=Float.POSITIVE_INFINITY;for(String c:Option.MASTER.choices){float d=Math.abs(Float.parseFloat(c.replace("%",""))/100f-volume);if(d<distance){distance=d;closest=c;}}set(Option.MASTER,closest);}
    public synchronized int key(String action){return keys.get(action);}
    public synchronized String bind(String action,int code){
        if(!keys.containsKey(action)||!validKey(code))return "This key is reserved.";
        for(var e:keys.entrySet())if(e.getValue()==code&&!e.getKey().equals(action))return "Already used by "+label(e.getKey())+".";
        keys.put(action,code);revision++;save();return "";
    }
    private static boolean validKey(int code){return code>0&&code<=255&&code!=KeyInput.KEY_ESCAPE&&code!=KeyInput.KEY_F8&&code!=KeyInput.KEY_F9&&code!=KeyInput.KEY_F10&&code!=KeyInput.KEY_Z&&code!=KeyInput.KEY_LEFT&&code!=KeyInput.KEY_RIGHT&&code!=KeyInput.KEY_UP&&code!=KeyInput.KEY_DOWN;}
    public static String label(String action){return action.replace("Aircraft","").replaceAll("([a-z])([A-Z])","$1 $2");}
    public synchronized void resetPage(String page){for(Option o:Option.values())if(o.page.equals(page))values.put(o,o.initial);if(page.equals("Controls")){keys.clear();keys.putAll(DEFAULT_KEYS);}revision++;save();}
    private void save(){
        Path temporary=null;
        try{
            Path absolute=path.toAbsolutePath();Files.createDirectories(absolute.getParent());temporary=Files.createTempFile(absolute.getParent(),"settings-",".json.tmp");
            JsonObject object=new JsonObject(),options=new JsonObject(),keyboard=new JsonObject();object.addProperty("version",1);values.forEach((k,v)->options.addProperty(k.name(),v));keys.forEach(keyboard::addProperty);object.add("options",options);object.add("keyboard",keyboard);
            Files.writeString(temporary,new GsonBuilder().setPrettyPrinting().create().toJson(object),StandardCharsets.UTF_8);
            try{Files.move(temporary,absolute,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException e){Files.move(temporary,absolute,StandardCopyOption.REPLACE_EXISTING);}saveError="";
        }catch(IOException error){saveError="Settings could not be saved.";System.err.println("Settings save: "+error.getMessage());}
        finally{if(temporary!=null)try{Files.deleteIfExists(temporary);}catch(IOException ignored){}}
    }
}
