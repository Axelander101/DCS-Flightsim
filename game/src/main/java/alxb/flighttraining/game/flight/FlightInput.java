package alxb.flighttraining.game.flight;

import com.jme3.input.InputManager;
import com.jme3.input.Joystick;
import com.jme3.input.JoystickAxis;
import com.jme3.input.JoystickConnectionListener;
import com.jme3.input.KeyInput;
import com.jme3.input.controls.ActionListener;
import com.jme3.input.controls.AnalogListener;
import com.jme3.input.controls.JoyAxisTrigger;
import com.jme3.input.controls.KeyTrigger;

public final class FlightInput {
    private final InputManager inputManager;
    private ActionListener systemListener;

    // Controller
    public float rollInput = 0f;
    public float yawInput = 0f;
    public float pitchInput = 0f;

    public float throttleUpInput = 0f;
    public float throttleDownInput = 0f;

    // Tastatur
    public boolean pitchUp;
    public boolean pitchDown;
    public boolean rollLeft;
    public boolean rollRight;
    public boolean yawLeft;
    public boolean yawRight;

    public boolean throttleUp;
    public boolean throttleDown;

    public boolean wheelBrake;
    private boolean flightControlsEnabled;
    private int systemCommands, navigationCommands;
    public int drainNavigationCommands(){int result=navigationCommands;navigationCommands=0;return result;}
    public void setFlightControlsEnabled(boolean enabled) {
        flightControlsEnabled=enabled;
        if(!enabled){systemCommands=navigationCommands=0;wheelBrake=false;}
    }
    public int peekSystemCommands(){return systemCommands;}
    public int drainSystemCommands(){int value=systemCommands;systemCommands=0;return value;}

    public boolean shoot;
    public boolean infraredMissile;

    public FlightInput(InputManager inputManager) {
        this.inputManager = inputManager;
        configureJoystick();
        configureKeyboard();
        String[] names={"AircraftGear","AircraftAirbrake","AircraftFlaps","AircraftParkingBrake",
                "AircraftCanopy","AircraftHook","AircraftFold","AircraftEngine","AircraftDisplayPage","AircraftDisplayDetail","AircraftWheelBrake","NavZoomIn","NavZoomOut","NavOrientation","NavNextAirfield","AircraftRadar","AircraftMasterArm","AircraftNextTarget","AircraftLockTarget","AircraftCountermeasures","AircraftFireLeft","AircraftFireRight","AircraftDischarge","AircraftEngineLeft","AircraftEngineRight","AircraftDamagePage"};
        int[] keys={KeyInput.KEY_G,KeyInput.KEY_X,KeyInput.KEY_L,KeyInput.KEY_P,
                KeyInput.KEY_C,KeyInput.KEY_H,KeyInput.KEY_K,KeyInput.KEY_I,KeyInput.KEY_V,KeyInput.KEY_TAB,KeyInput.KEY_B,KeyInput.KEY_EQUALS,KeyInput.KEY_MINUS,KeyInput.KEY_O,KeyInput.KEY_N};
        for(int i=0;i<names.length;i++) inputManager.addMapping(names[i],new KeyTrigger(alxb.flighttraining.game.config.GameSettings.get().key(names[i])));
        ActionListener systems=(name,pressed,tpf)->{
            if(name.equals("AircraftWheelBrake")){wheelBrake=pressed && flightControlsEnabled;return;}
            if(!pressed || !flightControlsEnabled)return;
            for(int i=0;i<10;i++)if(names[i].equals(name)){systemCommands|=1<<i;break;}
            for(int i=11;i<15;i++)if(names[i].equals(name)){navigationCommands|=1<<(i-11);break;}
            for(int i=15;i<names.length;i++)if(names[i].equals(name)){systemCommands|=1<<(i-5);break;}
        };
        systemListener=systems;
        inputManager.addListener(systems,names);
    }

    public void reloadKeyboard(){
        pitchUp=pitchDown=rollLeft=rollRight=yawLeft=yawRight=throttleUp=throttleDown=shoot=infraredMissile=wheelBrake=false;
        String[] flight={"PitchUp","PitchDown","RollLeft","RollRight","YawLeft","YawRight","ThrottleUp","ThrottleDown","Shoot","InfraredMissile"};
        String[] systems={"AircraftGear","AircraftAirbrake","AircraftFlaps","AircraftParkingBrake","AircraftCanopy","AircraftHook","AircraftFold","AircraftEngine","AircraftDisplayPage","AircraftDisplayDetail","AircraftWheelBrake","NavZoomIn","NavZoomOut","NavOrientation","NavNextAirfield","AircraftRadar","AircraftMasterArm","AircraftNextTarget","AircraftLockTarget","AircraftCountermeasures","AircraftFireLeft","AircraftFireRight","AircraftDischarge","AircraftEngineLeft","AircraftEngineRight","AircraftDamagePage"};
        var profile=alxb.flighttraining.game.config.GameSettings.get();
        for(String action:alxb.flighttraining.game.config.GameSettings.DEFAULT_KEYS.keySet()){
            if(inputManager.hasMapping(action))inputManager.deleteMapping(action);
            inputManager.addMapping(action,new KeyTrigger(profile.key(action)));
        }
        inputManager.addListener(actionListener,flight);inputManager.addListener(systemListener,systems);resetControllerInput();
    }

    private void configureKeyboard() {
        inputManager.addMapping("PitchUp", new KeyTrigger(alxb.flighttraining.game.config.GameSettings.get().key("PitchUp")));
        inputManager.addMapping("PitchDown", new KeyTrigger(alxb.flighttraining.game.config.GameSettings.get().key("PitchDown")));

        inputManager.addMapping("RollLeft", new KeyTrigger(alxb.flighttraining.game.config.GameSettings.get().key("RollLeft")));
        inputManager.addMapping("RollRight", new KeyTrigger(alxb.flighttraining.game.config.GameSettings.get().key("RollRight")));

        inputManager.addMapping("YawLeft", new KeyTrigger(alxb.flighttraining.game.config.GameSettings.get().key("YawLeft")));
        inputManager.addMapping("YawRight", new KeyTrigger(alxb.flighttraining.game.config.GameSettings.get().key("YawRight")));

        inputManager.addMapping("ThrottleUp", new KeyTrigger(alxb.flighttraining.game.config.GameSettings.get().key("ThrottleUp")));
        inputManager.addMapping("ThrottleDown", new KeyTrigger(alxb.flighttraining.game.config.GameSettings.get().key("ThrottleDown")));


        inputManager.addMapping("Shoot", new KeyTrigger(alxb.flighttraining.game.config.GameSettings.get().key("Shoot")));
        inputManager.addMapping("InfraredMissile", new KeyTrigger(alxb.flighttraining.game.config.GameSettings.get().key("InfraredMissile")));

        inputManager.addListener(actionListener,
                "PitchUp",
                "PitchDown",
                "RollLeft",
                "RollRight",
                "YawLeft",
                "YawRight",
                "ThrottleUp",
                "ThrottleDown",
                "Shoot",
                "InfraredMissile"
        );
    }

    private void configureJoystick() {
        inputManager.addListener(controllerListener,"RollLeftController","RollRightController","PitchUpController","PitchDownController","YawLeftController","YawRightController","ThrottleUpController","ThrottleDownController");
        inputManager.addJoystickConnectionListener(new JoystickConnectionListener(){
            public void onConnected(Joystick joystick){bindJoystick(joystick);}
            public void onDisconnected(Joystick joystick){resetControllerInput();}
        });
        Joystick[] devices=inputManager.getJoysticks();if(devices!=null)for(Joystick joystick:devices)bindJoystick(joystick);
    }
    private void bindJoystick(Joystick joystick){
        // Bind each available axis independently: a two-axis stick must not require five axes.
        for(JoystickAxis axis:joystick.getAxes()){
            String id=axis.getLogicalId();
            if(id.equals("x")){axis(joystick,axis,"RollLeftController",false);axis(joystick,axis,"RollRightController",true);}
            else if(id.equals("y")){axis(joystick,axis,"PitchUpController",true);axis(joystick,axis,"PitchDownController",false);}
            else if(id.equals("z")||id.equals("rz")){axis(joystick,axis,"YawLeftController",false);axis(joystick,axis,"YawRightController",true);}
            else if(id.equals("ry"))axis(joystick,axis,"ThrottleUpController",false);
            else if(id.equals("rx"))axis(joystick,axis,"ThrottleDownController",false);
        }
    }
    private void axis(Joystick joystick,JoystickAxis axis,String action,boolean negative){
        inputManager.addMapping(action,new JoyAxisTrigger(joystick.getJoyId(),axis.getAxisId(),negative));
        inputManager.addListener(controllerListener,action);
    }

    private final ActionListener actionListener = (name, isPressed, tpf) -> {
        switch (name) {
            case "PitchUp":
                pitchUp = isPressed;
                break;

            case "PitchDown":
                pitchDown = isPressed;
                break;

            case "RollLeft":
                rollLeft = isPressed;
                break;

            case "RollRight":
                rollRight = isPressed;
                break;

            case "YawLeft":
                yawLeft = isPressed;
                break;

            case "YawRight":
                yawRight = isPressed;
                break;

            case "ThrottleUp":
                throttleUp = isPressed;
                break;

            case "ThrottleDown":
                throttleDown = isPressed;
                break;

            case "Shoot":
                shoot = isPressed;
                break;

            case "InfraredMissile":
                infraredMissile = isPressed;
                break;
        }
    };

    private final AnalogListener controllerListener = (name, value, tpf) -> {
        if(!flightControlsEnabled||!Float.isFinite(tpf)||tpf<=0)return;
        float stickValue=Math.max(0,Math.min(1,value/tpf));

        switch (name) {
            case "RollLeftController":
                rollInput = -stickValue;
                break;

            case "RollRightController":
                rollInput = stickValue;
                break;

            case "YawLeftController":
                yawInput = -stickValue;
                break;

            case "YawRightController":
                yawInput = stickValue;
                break;

            case "PitchUpController":
                pitchInput = stickValue;
                break;

            case "PitchDownController":
                pitchInput = -stickValue;
                break;

            case "ThrottleUpController":
                throttleUpInput = stickValue;
                break;

            case "ThrottleDownController":
                throttleDownInput = stickValue;
                break;
        }
    };

    public void resetControllerInput() {
        rollInput = 0f;
        pitchInput = 0f;
        yawInput = 0f;

        throttleUpInput = 0f;
        throttleDownInput = 0f;
    }
}
