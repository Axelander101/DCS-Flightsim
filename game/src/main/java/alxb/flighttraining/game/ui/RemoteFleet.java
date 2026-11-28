package alxb.flighttraining.game.ui;

import alxb.flighttraining.game.network.*;
import alxb.flighttraining.game.network.FlightNetwork.*;
import alxb.flighttraining.game.flight.*;
import alxb.flighttraining.game.world.WorldCoordinates;
import com.jme3.asset.AssetManager;
import com.jme3.font.*;
import com.jme3.math.*;
import com.jme3.renderer.Camera;
import com.jme3.scene.*;
import java.util.*;

/** Render-thread-owned replicas. They have no local AI, damage targets or extra physics simulation. */
public final class RemoteFleet {
    private final AssetManager assets;private final Node root,gui;private final BitmapFont font;
    private final Map<String,Aircraft> aircraft=new LinkedHashMap<>();
    private final BitmapText status,statusShadow;
    private alxb.flighttraining.game.combat.CombatWorld combatWorld;
    public void combatWorld(alxb.flighttraining.game.combat.CombatWorld value){combatWorld=value;}
    public Node node(String id){var a=aircraft.get(id);return a==null?null:a.node;}
    public String id(Spatial node){for(var e:aircraft.entrySet())if(e.getValue().node==node)return e.getKey();return "";}
    private long lastSnapshot=-1;
    public RemoteFleet(AssetManager assets,Node root,Node gui,BitmapFont font){this.assets=assets;this.root=root;this.gui=gui;this.font=font;statusShadow=new BitmapText(font);statusShadow.setSize(16);statusShadow.setColor(new ColorRGBA(0,0,0,.9f));gui.attachChild(statusShadow);statusShadow.setCullHint(Spatial.CullHint.Always);status=new BitmapText(font);status.setSize(16);status.setColor(new ColorRGBA(.5f,.88f,1,1));gui.attachChild(status);status.setCullHint(Spatial.CullHint.Always);statusShadow.setCullHint(Spatial.CullHint.Always);}
    public void update(Snapshot snapshot,String selfId,WorldCoordinates origin,Camera camera,Vector3f player,float dt,long ping){
        long now=System.nanoTime();Set<String> present=new HashSet<>();for(Player p:snapshot.lobby().players())if(!p.id().equals(selfId))present.add(p.id());
        var it=aircraft.entrySet().iterator();while(it.hasNext()){var entry=it.next();if(!present.contains(entry.getKey())){entry.getValue().remove();it.remove();}}
        if(snapshot.receivedNanos()!=lastSnapshot){
            int admitted=0;
            for(Pose pose:snapshot.poses())if(!pose.id().equals(selfId)){
                Aircraft remote=aircraft.get(pose.id());
                if(remote==null){if(admitted++>=1)continue;Player info=snapshot.lobby().players().stream().filter(p->p.id().equals(pose.id())).findFirst().orElse(null);if(info==null)continue;remote=new Aircraft(info);if(combatWorld!=null)combatWorld.register(remote.node,remote.physics,snapshot.lobby().combat()?1:0);aircraft.put(pose.id(),remote);}
                remote.history.add(pose,snapshot.receivedNanos());
            }
            lastSnapshot=snapshot.receivedNanos();
        }
        for(Aircraft remote:aircraft.values()){
            Pose pose=remote.history.sample(now);if(pose==null)continue;
            remote.node.setLocalTranslation(origin.local(pose.x(),(float)pose.y(),pose.z()));remote.node.setLocalRotation(new Quaternion(pose.qx(),pose.qy(),pose.qz(),pose.qw()));
            remote.physics.reset(new Vector3f(pose.vx(),pose.vy(),pose.vz()),pose.throttle());
            for(var damage:snapshot.battle().aircraft())if(damage.id().equals(pose.id()))NetworkCombatView.apply(remote.physics,damage);
            if((pose.gear()>.5f)!=remote.systems.isGearDownSelected())remote.systems.command(AircraftSystems.GEAR,180);
            remote.systems.update(Math.max(0,Math.min(.1f,dt)),180,0,0,false);
            Vector3f point=remote.node.getLocalTranslation().add(0,4,0),offset=point.subtract(camera.getLocation());float distance=point.distance(player);
            if(distance<20000&&offset.dot(camera.getDirection())>0){Vector3f screen=camera.getScreenCoordinates(point);boolean on=screen.x>0&&screen.x<camera.getWidth()&&screen.y>0&&screen.y<camera.getHeight();remote.label.setCullHint(on?Spatial.CullHint.Inherit:Spatial.CullHint.Always);remote.label.setText(remote.name+String.format(java.util.Locale.ROOT,"  %.1f km",distance/1000));remote.label.setLocalTranslation(screen.x-remote.label.getLineWidth()/2,screen.y+18,100);}
            else remote.label.setCullHint(Spatial.CullHint.Always);
            remote.shadow.setText(remote.label.getText());remote.shadow.setLocalTranslation(remote.label.getLocalTranslation().add(1,-1,-1));remote.shadow.setCullHint(remote.label.getLocalCullHint());
        }
        status.setCullHint(Spatial.CullHint.Inherit);status.setText((snapshot.lobby().combat()?"COMBAT  ":"FORMATION  ")+snapshot.lobby().players().size()+" / "+snapshot.lobby().maximum()+"   |   "+ping+" ms   |   ESC: FLIGHT MENU");status.setLocalTranslation(20,camera.getHeight()-22,100);statusShadow.setText(status.getText());statusShadow.setLocalTranslation(21,camera.getHeight()-23,99);statusShadow.setCullHint(Spatial.CullHint.Inherit);
    }
    public int count(){return aircraft.size();}
    public void clear(){for(Aircraft remote:aircraft.values())remote.remove();aircraft.clear();lastSnapshot=-1;status.setCullHint(Spatial.CullHint.Always);statusShadow.setCullHint(Spatial.CullHint.Always);}
    private final class Aircraft {
        final Node node=new Node("NetworkAircraft");final BitmapText label=new BitmapText(font),shadow=new BitmapText(font);final String name;final PoseBuffer history=new PoseBuffer();final FlightPhysics physics=new FlightPhysics(node,new FlightCommandAi());final AircraftSystems systems=physics.getSystems();
        Aircraft(Player info){name=info.name();Spatial model=assets.loadModel("Models/FA18C_Swiss_Hornet_Cannon.glb");model.rotate(0,FastMath.PI,0);model.setLocalScale(.5f);node.attachChild(model);systems.reset(true);AircraftRig.attach(node,systems);root.attachChild(node);shadow.setSize(17);shadow.setColor(new ColorRGBA(0,0,0,.9f));gui.attachChild(shadow);label.setSize(17);label.setColor(new ColorRGBA(.5f,.88f,1,1));gui.attachChild(label);}
        void remove(){node.removeFromParent();label.removeFromParent();shadow.removeFromParent();}
    }
}
