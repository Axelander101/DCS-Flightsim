package alxb.flighttraining.game;

import alxb.flighttraining.game.weapons.Cannon;
import alxb.flighttraining.game.config.DogfightSettings;
import alxb.flighttraining.game.enemy.Enemy;
import alxb.flighttraining.game.ui.FlightHud;
import alxb.flighttraining.game.ui.CockpitView;
import alxb.flighttraining.game.flight.FlightInput;
import alxb.flighttraining.game.flight.FlightPhysics;
import alxb.flighttraining.game.flight.FlightSurfaces;
import alxb.flighttraining.game.weapons.Guns;
import alxb.flighttraining.game.weapons.InfraredMissile;
import alxb.flighttraining.game.ui.Menu;
import alxb.flighttraining.game.sensors.Radar;
import alxb.flighttraining.game.ui.EnemyMarkerDisplay;
import alxb.flighttraining.game.config.SpawnSettings;
import alxb.flighttraining.game.world.TerrainStreaming;

import com.jme3.anim.AnimComposer;
import com.jme3.app.SimpleApplication;
import com.jme3.anim.SkinningControl;
import com.jme3.scene.control.AbstractControl;
import com.jme3.renderer.RenderManager;
import com.jme3.renderer.ViewPort;
import com.jme3.app.state.AppState;
import com.jme3.app.state.AbstractAppState;
import com.jme3.environment.FastLightProbeFactory;
import com.jme3.light.DirectionalLight;
import com.jme3.light.LightProbe;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.util.SkyFactory;

import java.util.ArrayList;
import java.util.List;

public class FlightTraining extends SimpleApplication {
    private alxb.flighttraining.game.audio.GameAudio gameAudio;
    public alxb.flighttraining.game.audio.GameAudio getGameAudio(){return gameAudio;}
    private final alxb.flighttraining.game.network.MultiplayerSession multiplayer=new alxb.flighttraining.game.network.MultiplayerSession();
    public alxb.flighttraining.game.network.MultiplayerSession getMultiplayer(){return multiplayer;}
    private alxb.flighttraining.game.ui.RemoteFleet remoteFleet;
    private alxb.flighttraining.game.ui.NetworkCombatView networkCombatView;
    private int networkCommands;
    private long appliedBattle=-1;
    private String networkFlight="";
    private boolean networkLoaded;
    private long networkSequence;
    private final alxb.flighttraining.game.profiles.PilotProfiles pilotProfiles=new alxb.flighttraining.game.profiles.PilotProfiles(
        new alxb.flighttraining.game.profiles.PilotProfileStore(alxb.flighttraining.game.profiles.PilotProfileStore.defaultDirectory()));
    private final alxb.flighttraining.game.accounts.PlayerAccounts accounts=new alxb.flighttraining.game.accounts.PlayerAccounts(pilotProfiles);
    public alxb.flighttraining.game.accounts.PlayerAccounts getAccounts(){return accounts;}
    private final alxb.flighttraining.game.friends.FriendsService friends=new alxb.flighttraining.game.friends.FriendsService(accounts,multiplayer);
    public alxb.flighttraining.game.friends.FriendsService getFriends(){return friends;}
    public alxb.flighttraining.game.profiles.PilotProfiles getPilotProfiles(){return accounts.profiles();}
    public alxb.flighttraining.game.profiles.IdentityProvider getIdentityProvider(){return accounts;}
    private boolean spawnReady;
    private alxb.flighttraining.game.ui.PauseMenu pauseMenu;
    private Node plane;
    private FlightInput input;
    private FlightPhysics physics;
    private FlightHud hud;
    private alxb.flighttraining.game.ui.LoadingScreen loadingScreen;
    private TerrainStreaming terrain;
    private Guns guns;
    private final alxb.flighttraining.game.weapons.GunAmmunition gunAmmunition=new alxb.flighttraining.game.weapons.GunAmmunition();
    private float shotCooldown;
    private float cannonFlashTimer = 1f;
    private Node targets;
    private AnimComposer composer;
    private Spatial planeModel;
    private FlightSurfaces surfaces;
    private CockpitView cockpit;
    private static final int MISSILE_CAPACITY = 4;
    private final List<Spatial> mountedMissiles = new ArrayList<>();
    private float missileCooldown;
    private Radar radar;
    private alxb.flighttraining.game.combat.CombatWorld combatWorld;
    private EnemyMarkerDisplay enemyMarkers;


    private Enemy enemies;
    private int enemyCount = 0;
    private DogfightSettings dogfightSettings;

    private boolean missileLaunchWasPressed;

    private final List<InfraredMissile> missiles = new ArrayList<>();


    private Menu menu;
    private SpawnSettings spawnSettings = new SpawnSettings(-6548f, -9563f, 0f, enemyCount);

    public FlightTraining() {
    }

    public FlightTraining(AppState... initialStates) {
        super(initialStates);
    }

    @Override
    public void simpleInitApp() {
        setDisplayFps(false);
        setDisplayStatView(false);
        flyCam.setEnabled(false);
        // C and M are aircraft controls; disable the engine's overlapping debug shortcuts.
        if(inputManager.hasMapping(INPUT_MAPPING_CAMERA_POS))inputManager.deleteMapping(INPUT_MAPPING_CAMERA_POS);
        if(inputManager.hasMapping(INPUT_MAPPING_MEMORY))inputManager.deleteMapping(INPUT_MAPPING_MEMORY);

        menu = new Menu(
                assetManager,
                cam.getWidth(),
                cam.getHeight(),
                this,
                inputManager
        );

        menu.show(guiNode);
        inputManager.setCursorVisible(true);

        System.out.println("Menü erstellt und angehängt");



        input = new FlightInput(inputManager);
        gameAudio=new alxb.flighttraining.game.audio.GameAudio(assetManager,rootNode,listener,audioRenderer,inputManager);
        loadingScreen = new alxb.flighttraining.game.ui.LoadingScreen(assetManager, guiNode, guiFont);


        viewPort.setBackgroundColor(new ColorRGBA(0.5f, 0.7f, 1f, 1f));
        initializePlane();
        cockpit = new CockpitView(assetManager, inputManager, cam, plane, renderManager);
        stateManager.attach(new AbstractAppState() {
            @Override
            public void render(RenderManager manager) {
                // The cockpit is a separate viewport scene, outside rootNode/guiNode.
                // Refresh it every frame, including the menu and the first startup frame,
                // after all logical updates and before RenderManager checks culling.
                cockpit.prepareRender();
            }
        });
        cockpit.setEnabled(false);
        configureCamera();
        initializeLighting();

        physics = new FlightPhysics(plane, input);
        terrain = new TerrainStreaming(assetManager, rootNode);
        combatWorld=new alxb.flighttraining.game.combat.CombatWorld(assetManager,rootNode);combatWorld.register(plane,physics,0);
        combatWorld.explosionListener(p->{if(gameAudio!=null)gameAudio.at("explosion",p);});
        combatWorld.lineOfSight((a,b)->{for(int i=1;i<12;i++){Vector3f point=a.clone().interpolateLocal(b,i/12f);float h=terrain.getGroundHeight(point);if(Float.isFinite(h)&&h>point.y+2)return false;}return true;});

        composer = findAnimComposer(planeModel);

        if (composer == null) {
            throw new IllegalStateException("Das Flugzeugmodell enthält keinen AnimComposer.");
        }
        targets = new Node("Targets");
        rootNode.attachChild(targets);
        guns = new Cannon(targets);
        enemies = new Enemy(assetManager, targets);
        enemies.configure(plane, physics, terrain);
        enemies.setCombatWorld(combatWorld);
        enemies.setSoundEvents(p->gameAudio.at("cannon",p),p->gameAudio.at("impact",p));
        guns.setHitListener(p->gameAudio.at("impact",p));
        radar = new Radar(18520f, plane, enemies);radar.configure(physics,combatWorld);
        hud = new FlightHud(assetManager, cockpit, plane, enemies);hud.setRadar(radar);
        enemyMarkers = new EnemyMarkerDisplay(assetManager, guiNode, cam, enemies);
        remoteFleet=new alxb.flighttraining.game.ui.RemoteFleet(assetManager,rootNode,guiNode,guiFont);remoteFleet.combatWorld(combatWorld);
        networkCombatView=new alxb.flighttraining.game.ui.NetworkCombatView(assetManager,rootNode,combatWorld,(key,pos)->{if(gameAudio!=null)gameAudio.at(key,pos);});
        inputManager.deleteMapping(SimpleApplication.INPUT_MAPPING_EXIT);
        pauseMenu=new alxb.flighttraining.game.ui.PauseMenu(assetManager,inputManager,guiNode,cam,
            ()->(spawnReady||multiplayer.view().flight())&&!menu.getMenuOffen(),()->{
                getPilotProfiles().endFlight();
                leaveMultiplayer();
                enemies.clear();for(InfraredMissile missile:missiles)missile.cancel();missiles.clear();
                menu.showMain(guiNode);inputManager.setCursorVisible(true);
            });


        surfaces = new FlightSurfaces(plane, input, composer, physics, assetManager, plane);
        // Procedural surfaces must run after clips have set the pose.
        SkinningControl skin = findSkinningControl(planeModel);
        if (skin == null) {
            throw new IllegalStateException("Das Flugzeugmodell enthält kein SkinningControl.");
        }
        composer.getSpatial().addControl(new AbstractControl() {
            @Override
            protected void controlUpdate(float tpf) {
                if (spawnReady && !menu.getMenuOffen()) {
                    surfaces.update(tpf);
                    skin.getArmature().update();
                }
                input.resetControllerInput();
            }

            @Override
            protected void controlRender(RenderManager rm, ViewPort vp) {
            }
        });
        applyUserSettings();
    }

    @Override
    public void simpleRender(RenderManager manager) {
        if (terrain != null) terrain.prewarm(manager);
    }

    @Override
    public void destroy() {
        multiplayer.close();
        friends.close();
        accounts.close();
        pilotProfiles.close();
        if(remoteFleet!=null)remoteFleet.clear();if(networkCombatView!=null)networkCombatView.clear();
        if(hud!=null)hud.close();
        if(gameAudio!=null)gameAudio.close();
        if (terrain != null) {
            terrain.close();
        }
        alxb.flighttraining.game.world.DownloadCache.shutdown();
        super.destroy();
    }

    @Override
    public void simpleUpdate(float tpf) {
        friends.tick();
        pollMultiplayer();
        boolean online=multiplayer.view().flight();
        pauseMenu.setNetworked(online);
        pauseMenu.update();
        if(pauseMenu.isPaused()) {
            gameAudio.setActive(false);input.setFlightControlsEnabled(false);
            input.shoot=false;input.infraredMissile=false;input.resetControllerInput();enemyMarkers.hide();
            if(!online)return;
            input.pitchUp=input.pitchDown=input.rollLeft=input.rollRight=input.yawLeft=input.yawRight=input.throttleUp=input.throttleDown=false;
        }
        float renderDistance=alxb.flighttraining.game.world.WorldDetailSettings.renderDistance();
        if(cam.getFrustumFar()!=renderDistance)cam.setFrustumFar(renderDistance);
        menu.update(tpf);
        gameAudio.setActive(!menu.getMenuOffen() && spawnReady);
        input.setFlightControlsEnabled(!menu.getMenuOffen() && spawnReady && !pauseMenu.isPaused());
        loadingScreen.update(tpf,cam.getWidth(),cam.getHeight());
        cockpit.setEnabled(!menu.getMenuOffen() && spawnReady);
        hud.setVisible(!menu.getMenuOffen() && spawnReady);
        if (menu.getMenuOffen() || !spawnReady || dogfightSettings == null) enemyMarkers.hide();
        if (!menu.getMenuOffen()) {

            if (!spawnReady) {
                updateSpawn();
                input.resetControllerInput();
                return;
            }

            combatWorld.beginFrame();
            if(online){applyNetworkDamage();networkCommands|=input.peekSystemCommands() & ((1<<10)|(1<<11)|(1<<14)|(1<<15)|(1<<16)|(1<<17)|(1<<18)|(1<<19)|128);}
            physics.update(tpf);
            Vector3f delta=terrain.recenter(plane.getLocalTranslation(),cam);
            if(delta.lengthSquared()>0){
                guns.rebase(delta);enemies.rebase(delta);gameAudio.rebase(delta);combatWorld.rebase(delta);
                for (InfraredMissile missile : missiles) missile.rebase(delta);
            }
            // Streaming/LOD must inspect this frame's camera, after physics and any origin shift.
            cockpit.update(tpf);
            Vector3f position = plane.getLocalTranslation();
            terrain.update(position, cam);
            float playerGround=terrain.getGroundHeight(position);
            physics.resolveGroundCollision(playerGround);
            getPilotProfiles().beginFlight();
            Vector3f groundVelocity=physics.getVelocity();
            getPilotProfiles().record(tpf,Float.isFinite(playerGround)&&position.y-playerGround>3&&!physics.getSystems().isOnGround(),Math.hypot(groundVelocity.x,groundVelocity.z));
            plane.updateGeometricState();
            enemies.update(tpf);

            plane.updateGeometricState();
            targets.updateGeometricState();

            radar.update(Math.min(tpf,.25f));
            if(physics.getSystems().combat().takeCountermeasures()&&!online)combatWorld.dispense(alxb.flighttraining.game.combat.DamageableAircraft.find(plane));
            for(int i=0;i<enemies.getCount();i++)if(enemies.aircraft(i).takeMissileShot(tpf,plane))launchEnemyMissile(enemies.aircraft(i));
            mountedMissiles.removeIf(model->{boolean left=plane.worldToLocal(model.getWorldTranslation(),new Vector3f()).x<0;if(physics.getDamage().health(left?alxb.flighttraining.game.combat.AirframeDamage.Part.LEFT_WING:alxb.flighttraining.game.combat.AirframeDamage.Part.RIGHT_WING)<.08f){model.removeFromParent();return true;}return false;});
            cockpit.updateControls(input, physics.getThrottle(), tpf);
            cockpit.updateSystems(physics.getSystems());
            // Ground correction may have moved the plane; do not integrate head input twice.
            cockpit.update(0f);
            enemyMarkers.update(dogfightSettings != null && alxb.flighttraining.game.config.GameSettings.get().enabled(alxb.flighttraining.game.config.GameSettings.Option.MARKERS));


            int navigationCommands=input.drainNavigationCommands();
            if(navigationCommands!=0 && physics.getSystems().isSystemsPage())
                physics.getSystems().command(alxb.flighttraining.game.flight.AircraftSystems.DISPLAY_PAGE,physics.getAirspeed());
            hud.navigationCommands(navigationCommands);
            updateGuns(tpf);
            missiles.removeIf(missile -> {
                if (missile.update(tpf)) return true;
                Vector3f p = missile.getModel().getLocalTranslation();
                float ground = terrain.getGroundHeight(p);
                if (Float.isFinite(ground) && p.y <= ground) {
                    combatWorld.groundExplosion(p);
                    missile.cancel();
                    return true;
                }
                return false;
            });

            combatWorld.update(tpf);
            gameAudio.update(tpf,physics,enemies,plane,cam,getMapPosition(),terrain.getGroundHeight(plane.getLocalTranslation()),unlimitedAmmo()?MISSILE_CAPACITY:mountedMissiles.size(),weaponsEnabled());
            hud.setWeapons(mountedMissiles.size(), weaponsEnabled()&&physics.getSystems().combat().masterArm());
            hud.setAmmunition(gunAmmunition.remaining(),unlimitedAmmo());
            hud.setMapPosition(getMapPosition());
            hud.update(physics, plane.getLocalTranslation().y);
            updateNetworkAircraft(tpf);
        }
    }

    private void launchInfraredMissile() {
        if (!weaponsEnabled() || !physics.getSystems().combat().masterArm() || (!unlimitedAmmo() && mountedMissiles.isEmpty()) || missileCooldown > 0f) return;
        Vector3f direction = plane.getWorldRotation()
                .mult(Vector3f.UNIT_Z.negate())
                .normalizeLocal();
        Spatial target = radar.irTarget();
        // Release the actual wing-mounted model, preserving its world position and scale.
        Spatial model;
        if(unlimitedAmmo()){
            model=mountedMissiles.isEmpty()?createMountedMissile(0):mountedMissiles.get(0).clone();
            plane.attachChild(model);
        }else model=mountedMissiles.remove(0);
        plane.updateGeometricState();
        Vector3f position = model.getWorldTranslation().clone();
        Vector3f scale = model.getWorldScale().clone();
        model.removeFromParent();
        rootNode.attachChild(model);
        model.setLocalScale(scale);
        InfraredMissile missile = new InfraredMissile(
                model, target, position, direction, physics.getVelocity(),
                plane.getWorldRotation().mult(Vector3f.UNIT_Y),combatWorld,alxb.flighttraining.game.combat.DamageableAircraft.find(plane)
        );
        missiles.add(missile);
        if(gameAudio!=null)gameAudio.at("launch",position);
        missileCooldown = 0.75f;
    }

    private void launchEnemyMissile(alxb.flighttraining.game.enemy.EnemyAircraft enemy){
        Spatial model=assetManager.loadModel("Models/InfraredMissile/FT_IR_Missile_LOD0.glb");model.setLocalScale(.5f);rootNode.attachChild(model);
        Vector3f direction=enemy.getPlane().getWorldRotation().mult(Vector3f.UNIT_Z.negate());Vector3f position=enemy.getPosition().add(direction.mult(2.5f)).add(0,-.3f,0);
        missiles.add(new InfraredMissile(model,plane,position,direction,enemy.getPhysics().getVelocity(),enemy.getPlane().getWorldRotation().mult(Vector3f.UNIT_Y),combatWorld,alxb.flighttraining.game.combat.DamageableAircraft.find(enemy.getPlane())));
        if(gameAudio!=null)gameAudio.at("launch",position);
    }

    private boolean weaponsEnabled() {
        var view=multiplayer.view();return view.flight()?view.snapshot().lobby().combat():(dogfightSettings == null || dogfightSettings.weaponsEnabled);
    }

    private void resetMissileStores() {
        for (Spatial model : mountedMissiles) model.removeFromParent();
        mountedMissiles.clear();
        if (weaponsEnabled()) {
            for (int i = 0; i < MISSILE_CAPACITY; i++) {
                Spatial model=createMountedMissile(i);
                mountedMissiles.add(model);
            }
        }
        missileCooldown = 0f;
        missileLaunchWasPressed = false;
        hud.setWeapons(mountedMissiles.size(), weaponsEnabled()&&physics.getSystems().combat().masterArm());
    }

    private boolean unlimitedAmmo(){
        if(multiplayer.view().flight())return false;
        return alxb.flighttraining.game.config.GameSettings.get().enabled(alxb.flighttraining.game.config.GameSettings.Option.UNLIMITED_AMMO);
    }
    private Spatial createMountedMissile(int i){
        Spatial model = assetManager.loadModel("Models/InfraredMissile/FT_IR_Missile_LOD0.glb");
        model.setLocalScale(0.5f);
        float side = i % 2 == 0 ? -1f : 1f;
        // Centres of the existing inboard pylons, in aircraft space at model scale 0.5.
        // Pylon underside y=-0.1575; missile body radius=0.04 -> centre y=-0.20.
        model.setLocalTranslation(side * (i < 2 ? 1.175f : 1.8f), -0.20f, i < 2 ? 1.0f : 1.175f);
        model.setLocalRotation(new com.jme3.math.Quaternion().fromAngleAxis(
                (float) Math.PI, Vector3f.UNIT_Y));
        plane.attachChild(model);
        return model;
    }

    private void updateGuns(float tpf) {
        if(multiplayer.view().flight()){
            multiplayer.combat(input.shoot&&!pauseMenu.isPaused(),input.infraredMissile&&!missileLaunchWasPressed,networkCommands,remoteFleet.id(radar.lockedTarget()),physics.getDamage().destroyed());
            networkCommands=0;missileLaunchWasPressed=input.infraredMissile;return;
        }
        // Auch nach dem Loslassen fliegen bestehende Geschosse weiter.
        guns.update(tpf);
        cannonFlashTimer += tpf;
        missileCooldown = Math.max(0f, missileCooldown - tpf);
        //Missile
        if (input.infraredMissile && !missileLaunchWasPressed) {
            launchInfraredMissile();
        }

        missileLaunchWasPressed = input.infraredMissile;

        //BordCwannon
        if (!input.shoot || !weaponsEnabled() || !physics.getSystems().combat().masterArm()) {
            shotCooldown = 0f;
            return;
        }

        shotCooldown -= tpf;
        Vector3f direction = plane.getWorldRotation()
                .mult(Vector3f.UNIT_Z.negate()).normalizeLocal();
        // Vorläufiger Mündungspunkt vor der Nase; später durch den echten Mündungsknoten ersetzen.
        Vector3f muzzle = plane.getWorldTranslation().add(direction.mult(8f));
        int shots = 0;
        while (shotCooldown <= 0f && shots < 16) {
            if(!gunAmmunition.tryFire(unlimitedAmmo())){shotCooldown=0f;return;}
            guns.fire(muzzle, direction, physics.getVelocity());
            gameAudio.cannonFire();
            shotCooldown += 1f / 100f;
            shots++;
        }
        // Nach einem langen Hänger keine aufgestauten Salven nachholen.
        if (shotCooldown < 0f) shotCooldown = 0f;

        if (cannonFlashTimer >= 0.1f && composer != null
                && composer.hasAnimClip("07_Cannon_Burst")) {
            composer.setCurrentAction("07_Cannon_Burst", AnimComposer.DEFAULT_LAYER, false);
            cannonFlashTimer = 0f;
        }
    }

    public String resolutionDescription(){
        String requested=alxb.flighttraining.game.config.GameSettings.get().value(alxb.flighttraining.game.config.GameSettings.Option.RESOLUTION);
        if(context==null)return requested;
        int width=context.getFramebufferWidth(),height=context.getFramebufferHeight();
        return requested+(requested.equals(width+" × "+height)?"":" [actual "+width+" × "+height+"]");
    }
    public void applyUserSettings(){
        var profile=alxb.flighttraining.game.config.GameSettings.get();
        alxb.flighttraining.game.world.WorldDetailSettings.setRenderDistance(Float.parseFloat(profile.value(alxb.flighttraining.game.config.GameSettings.Option.DISTANCE).replace(" km",""))*1000);
        if(input!=null)input.reloadKeyboard();
        if(gameAudio!=null)gameAudio.setVolume(profile.fraction(alxb.flighttraining.game.config.GameSettings.Option.MASTER));
        if(physics!=null)physics.setFlightAssistEnabled(profile.enabled(alxb.flighttraining.game.config.GameSettings.Option.STABILISATION)&&!profile.value(alxb.flighttraining.game.config.GameSettings.Option.MODEL).equals("Simulation"));
        boolean debug=profile.enabled(alxb.flighttraining.game.config.GameSettings.Option.DEBUG);setDisplayFps(debug);setDisplayStatView(debug);
        int fps=profile.value(alxb.flighttraining.game.config.GameSettings.Option.FPS).equals("Unlimited")?-1:Integer.parseInt(profile.value(alxb.flighttraining.game.config.GameSettings.Option.FPS));
        if(settings!=null)settings.setFrameRate(fps);
        if(context==null)return;
        context.getSettings().setFrameRate(fps);
        int anisotropy=Integer.parseInt(profile.value(alxb.flighttraining.game.config.GameSettings.Option.FILTER).replace("x",""));
        if(renderer!=null)renderer.setDefaultAnisotropicFilter(anisotropy);
        rootNode.depthFirstTraversal(spatial->{if(spatial instanceof com.jme3.scene.Geometry geometry&&geometry.getMaterial()!=null)
            for(var parameter:geometry.getMaterial().getParams())if(parameter.getValue() instanceof com.jme3.texture.Texture texture)texture.setAnisotropicFilter(anisotropy);
        });
        if(context.getType()==com.jme3.system.JmeContext.Type.Headless)return;
        try{
            long window=(Long)context.getClass().getMethod("getWindowHandle").invoke(context);
            Class<?> video=Class.forName("org.lwjgl.sdl.SDLVideo");
            boolean fullscreen=profile.value(alxb.flighttraining.game.config.GameSettings.Option.DISPLAY).equals("Fullscreen");
            if(fullscreen!=settings.isFullscreen()){
                boolean ok=(Boolean)video.getMethod("SDL_SetWindowFullscreen",long.class,boolean.class).invoke(null,window,fullscreen);
                if(!ok)throw new IllegalStateException("Display mode rejected");settings.setFullscreen(fullscreen);context.getSettings().setFullscreen(fullscreen);
            }
            boolean vsync=profile.enabled(alxb.flighttraining.game.config.GameSettings.Option.VSYNC);
            if(vsync!=settings.isVSync()){
                boolean ok=(Boolean)video.getMethod("SDL_GL_SetSwapInterval",int.class).invoke(null,vsync?1:0);
                if(!ok)throw new IllegalStateException("VSync change rejected");settings.setVSync(vsync);context.getSettings().setVSync(vsync);
            }
            String[] size=profile.value(alxb.flighttraining.game.config.GameSettings.Option.RESOLUTION).split(" × ");applyResolution(Integer.parseInt(size[0]),Integer.parseInt(size[1]));
        }catch(ReflectiveOperationException e){throw new IllegalStateException("Live display settings require the SDL desktop backend",e);}
    }


    public void applyResolution(int width,int height){
        if(!((width==1920&&height==1080)||(width==2560&&height==1440)||(width==3840&&height==2160)))
            throw new IllegalArgumentException("Unsupported display resolution");
        if(settings.getWidth()==width&&settings.getHeight()==height)return;
        com.jme3.system.AppSettings updated=new com.jme3.system.AppSettings(false);
        updated.copyFrom(settings);updated.setResolution(width,height);
        try {
            // SDL changes the existing window; no GL context recreation or asset re-upload.
            long window=(Long)context.getClass().getMethod("getWindowHandle").invoke(context);
            Class<?> video=Class.forName("org.lwjgl.sdl.SDLVideo");
            float density=(Float)video.getMethod("SDL_GetWindowPixelDensity",long.class).invoke(null,window);
            int w=Math.max(1,Math.round(width/Math.max(1,density))),h=Math.max(1,Math.round(height/Math.max(1,density)));
            boolean changed=(Boolean)video.getMethod("SDL_SetWindowSize",long.class,int.class,int.class).invoke(null,window,w,h);
            if(!changed)throw new IllegalStateException("The display rejected the selected resolution");
            updated.setWindowSize(w,h);setSettings(updated);context.setSettings(updated);
        }catch(ReflectiveOperationException e){throw new IllegalStateException("Live resolution change requires the SDL desktop backend",e);}
    }

    public void startFreePlay(SpawnSettings settings) {
        leaveMultiplayer();
        dogfightSettings = null;
        enemyCount = 0;
        beginFlight(settings);
    }

    public void startDogfight(DogfightSettings settings) {
        leaveMultiplayer();
        java.util.Objects.requireNonNull(settings);
        dogfightSettings = settings;
        enemyCount = settings.enemyCount;
        beginFlight(settings.spawn());
    }

    private void beginFlight(SpawnSettings settings) {
        getPilotProfiles().endFlight();
        if (settings == null || !Float.isFinite(settings.worldX) || !Float.isFinite(settings.worldZ)
                || !Float.isFinite(settings.heightAboveGround) || settings.heightAboveGround < 0)
            throw new IllegalArgumentException("Ungültige Startposition");
        if(gameAudio!=null)gameAudio.reset();
        spawnSettings = settings;
        spawnReady = false;
        cockpit.reset();
        enemies.clear();
        enemyMarkers.hide();
        for (InfraredMissile missile : missiles) missile.cancel();
        missiles.clear();
        resetMissileStores();
        guns.clear();
        gunAmmunition.reset();
        if(combatWorld!=null)combatWorld.reset();if(radar!=null)radar.reset();
        hud.resetNavigation(settings.worldX,settings.worldZ);
        shotCooldown = 0f;
        cannonFlashTimer = 1f;
        plane.setLocalRotation(new com.jme3.math.Quaternion());
        terrain.close();
        terrain = new TerrainStreaming(assetManager,rootNode,settings.worldX,settings.worldZ);
        enemies.configure(plane,physics,terrain);
        plane.setLocalTranslation(0f, settings.heightAboveGround, 0f);
        physics.reset(new Vector3f(), 0f);
        physics.setEngineRunning(true);
        physics.getSystems().reset(settings.heightAboveGround > 3f);
        physics.getSystems().combat().setMasterArm(multiplayer.view().flight()?weaponsEnabled():dogfightSettings!=null&&dogfightSettings.weaponsEnabled);
        input.shoot = input.pitchUp = input.pitchDown = input.rollLeft = input.rollRight = false;
        input.infraredMissile = false;
        input.yawLeft = input.yawRight = input.throttleUp = input.throttleDown = false;
        input.resetControllerInput();
        menu.hide();
        inputManager.setCursorVisible(false);
        hud.showLoading("");
        loadingScreen.begin(cam.getWidth(),cam.getHeight());
        updateCamera();
    }

    private void updateSpawn() {
        Vector3f position = plane.getLocalTranslation();
        updateCamera();
        terrain.update(position, cam);

        float groundHeight = terrain.getGroundHeight(position);
        if (!Float.isFinite(groundHeight)) {
            loadingScreen.progress(terrain.getGroundLoadStatus(position),0);
            return;
        }

        plane.setLocalTranslation(
                position.x, groundHeight + Math.max(spawnSettings.heightAboveGround, 1.155f), position.z
        );
        plane.updateGeometricState();
        int ready=terrain.spawnObjectProgress(plane.getLocalTranslation());
        int total=terrain.spawnObjectTotal(plane.getLocalTranslation());
        if(!terrain.isSpawnAreaReady(plane.getLocalTranslation())){
            loadingScreen.progress("Umgebung wird vorbereitet: "+ready+" / "+total+" Kacheln",.1f+.9f*ready/Math.max(1,total));return;
        }
        if(multiplayer.view().flight()){
            if(!networkLoaded){
                physics.reset(new Vector3f(0,0,-180),.6f);networkLoaded=true;multiplayer.loaded();
            }
            var view=multiplayer.view();
            if(view.snapshot()==null||view.snapshot().lobby().phase()!=alxb.flighttraining.game.network.FlightNetwork.Phase.FLYING){
                long loaded=view.snapshot()==null?0:view.snapshot().lobby().players().stream().filter(p->p.loaded()).count();
                int pilots=view.snapshot()==null?0:view.snapshot().lobby().players().size();
                loadingScreen.progress("Waiting for pilots: "+loaded+" / "+pilots,.99f);return;
            }
        }
        spawnOpponents();
        targets.updateGeometricState();
        spawnReady = true;
        getPilotProfiles().beginFlight();
        loadingScreen.finish();
        updateCamera();
        hud.update(physics, plane.getLocalTranslation().y);
    }


    public void leaveMultiplayer(){
        if(!networkFlight.isEmpty())getPilotProfiles().endFlight();
        multiplayer.leave();networkFlight="";networkLoaded=false;if(hud!=null)hud.networkRwr(false,false);if(remoteFleet!=null)remoteFleet.clear();if(networkCombatView!=null)networkCombatView.clear();
    }
    private void pollMultiplayer(){
        var view=multiplayer.view();
        if(!networkFlight.isEmpty() && !view.flight()){
            getPilotProfiles().endFlight();
            networkFlight="";networkLoaded=false;remoteFleet.clear();networkCombatView.clear();hud.networkRwr(false,false);spawnReady=false;loadingScreen.finish();pauseMenu.dismiss();
            menu.showMultiplayer(guiNode);inputManager.setCursorVisible(true);return;
        }
        if(!view.flight()||view.self()==null)return;
        String key=view.selfId()+"/"+view.snapshot().lobby().flightId();
        if(key.equals(networkFlight))return;
        networkFlight=key;networkLoaded=false;networkSequence=0;networkCommands=0;appliedBattle=-1;remoteFleet.clear();networkCombatView.clear();pauseMenu.dismiss();
        dogfightSettings=null;enemyCount=0;
        var region=view.snapshot().lobby().region();int slot=view.self().slot();
        // Separate airborne starts avoid overlapping aircraft and runway collisions.
        beginFlight(new SpawnSettings(region.x+slot*180f,region.z+slot*120f,1200f,0));
    }
    private void updateNetworkAircraft(float dt){
        var view=multiplayer.view();if(!view.flight()||view.snapshot()==null)return;
        Vector3f position=getMapPosition(),velocity=physics.getVelocity();var q=plane.getLocalRotation();
        try{multiplayer.pose(new alxb.flighttraining.game.network.FlightNetwork.Pose(view.selfId(),++networkSequence,position.x,position.y,position.z,
            q.getX(),q.getY(),q.getZ(),q.getW(),velocity.x,velocity.y,velocity.z,physics.getThrottle(),physics.getSystems().isGearDownSelected()?1:0));}
        catch(IllegalArgumentException invalid){multiplayer.disconnect("Aircraft state is outside multiplayer flight limits.");return;}
        remoteFleet.update(view.snapshot(),view.selfId(),terrain.coordinates(),cam,plane.getLocalTranslation(),dt,view.pingMillis());
        networkCombatView.update(view.snapshot(),view.selfId(),plane,remoteFleet,terrain.coordinates());
        boolean search=false,lock=false;
        for(var state:view.snapshot().battle().aircraft())if(!state.id().equals(view.selfId())&&state.radar()){
            Node remote=remoteFleet.node(state.id());if(remote==null)continue;var receiver=alxb.flighttraining.game.combat.DamageableAircraft.find(remote);if(receiver==null||!receiver.physics().getSystems().combat().radarAvailable())continue;
            Vector3f offset=plane.getLocalTranslation().subtract(remote.getLocalTranslation());float distance=offset.length();
            if(distance>1&&distance<18520&&remote.getLocalRotation().mult(Vector3f.UNIT_Z.negate()).dot(offset.divide(distance))>.5f){search=true;if(state.radarLock().equals(view.selfId()))lock=true;}
        }hud.networkRwr(search,lock);
    }

    private void applyNetworkDamage(){
        var view=multiplayer.view();if(view.snapshot()==null||appliedBattle==view.snapshot().receivedNanos())return;appliedBattle=view.snapshot().receivedNanos();
        for(var state:view.snapshot().battle().aircraft())if(state.id().equals(view.selfId())){
            alxb.flighttraining.game.ui.NetworkCombatView.apply(physics,state);gunAmmunition.accept(state.ammunition());
            while(mountedMissiles.size()>state.missiles())mountedMissiles.removeLast().removeFromParent();
        }
    }
    private void spawnOpponents() {
        enemies.clear();
        if (dogfightSettings == null) return;
        Vector3f forward = plane.getLocalRotation().mult(new Vector3f(0, 0, -1));
        physics.reset(forward.mult(220f), .6f);
        physics.getSystems().reset(true);
        physics.getSystems().combat().setMasterArm(dogfightSettings.weaponsEnabled);
        enemies.spawn(dogfightSettings.enemyCount, plane, dogfightSettings);
    }


    public Vector3f getMapPosition() {
        return terrain.coordinates().absolute(plane.getLocalTranslation());
    }

    public float getAngleOfAttack() {
        return physics == null ? 0f : physics.getAngleOfAttack();
    }

    public float sideSlip() {
        return physics.sideSlip();
    }

    public float getSideSlipAngle() {
        return physics.getSideSlipAngle();
    }

    private void initializePlane() {
        plane = new Node("Plane");

        planeModel =
                assetManager.loadModel(
                        "Models/FA18C_Swiss_Hornet_Cannon.glb"
                );

        planeModel.rotate(
                0f,
                (float) Math.PI,
                0f
        );

        planeModel.setLocalScale(0.5f);

        plane.attachChild(planeModel);

        plane.setLocalTranslation(spawnSettings.worldX, 0f, spawnSettings.worldZ);

        rootNode.attachChild(plane);
    }

    private void configureCamera() {
        flyCam.setEnabled(false);
        updateCamera();
    }

    private void initializeLighting() {
        // Sonnenlicht von schräg oben
        DirectionalLight sun = new DirectionalLight();
        sun.setDirection(new Vector3f(-0.6f, -1f, -0.5f).normalizeLocal());
        sun.setColor(new ColorRGBA(1.30f, 1.24f, 1.14f, 1f));
        rootNode.addLight(sun);

        Spatial lightingSky = SkyFactory.createSky(
                assetManager,
                "Lighting/daylight.hdr",
                SkyFactory.EnvMapType.EquirectMap
        );
        lightingSky.updateGeometricState();

        LightProbe daylight = FastLightProbeFactory.makeProbe(
                renderManager, assetManager, 128,
                Vector3f.ZERO, 0.1f, 100f, lightingSky
        );
        daylight.getArea().setRadius(2_000_000f);
        rootNode.addLight(daylight);
    }

    private void updateCamera() {
        cockpit.update(0f);
    }

    private SkinningControl findSkinningControl(Spatial spatial) {
        SkinningControl skin = spatial.getControl(SkinningControl.class);
        if (skin != null) {
            return skin;
        }
        if (spatial instanceof Node node) {
            for (Spatial child : node.getChildren()) {
                skin = findSkinningControl(child);
                if (skin != null) {
                    return skin;
                }
            }
        }
        return null;
    }

    private AnimComposer findAnimComposer(Spatial spatial) {

        AnimComposer composer =
                spatial.getControl(AnimComposer.class);

        if (composer != null) {
            return composer;
        }

        if (spatial instanceof Node node) {

            for (Spatial child : node.getChildren()) {

                AnimComposer found = findAnimComposer(child);

                if (found != null) {
                    return found;
                }
            }
        }

        return null;
    }
}
