package alxb.flighttraining.game.ui;

import alxb.flighttraining.game.combat.*;
import alxb.flighttraining.game.flight.FlightPhysics;
import alxb.flighttraining.game.network.FlightNetwork.*;
import alxb.flighttraining.game.world.WorldCoordinates;
import com.jme3.asset.AssetManager;
import com.jme3.math.*;
import com.jme3.scene.*;
import java.util.*;
import java.util.function.BiConsumer;

/** Render-only host projectile replicas. This class never decides or applies an impact. */
public final class NetworkCombatView {
    private final AssetManager assets;private final Node root;private final CombatWorld world;private final BiConsumer<String,Vector3f> sound;
    private final Map<Long,Spatial> missiles=new HashMap<>();private long lastEvent,lastSnapshot;private long flight=-1;
    public NetworkCombatView(AssetManager assets,Node root,CombatWorld world,BiConsumer<String,Vector3f> sound){this.assets=assets;this.root=root;this.world=world;this.sound=sound;}
    public static void apply(FlightPhysics physics,AircraftDamage d){physics.getDamage().accept(d.integrity(),d.fireLeft(),d.fireRight(),d.fuel());physics.getSystems().combat().accept(d.engineLeft(),d.engineRight(),d.radar(),d.armed(),d.flares(),d.bottle());}
    public void update(Snapshot snapshot,String self,Node player,RemoteFleet fleet,WorldCoordinates origin){
        if(flight!=snapshot.lobby().flightId()){clear();flight=snapshot.lobby().flightId();}
        Set<Long> live=new HashSet<>();float extrapolate=Math.max(0,Math.min(.15f,(System.nanoTime()-snapshot.receivedNanos())/1e9f));
        for(Projectile p:snapshot.battle().missiles()){
            live.add(p.id());Spatial model=missiles.computeIfAbsent(p.id(),id->{Spatial s=assets.loadModel("Models/InfraredMissile/FT_IR_Missile_LOD0.glb");s.setLocalScale(.5f);root.attachChild(s);return s;});
            model.setLocalTranslation(origin.local(p.x(),(float)p.y(),p.z()).addLocal(p.vx()*extrapolate,p.vy()*extrapolate,p.vz()*extrapolate));Vector3f velocity=new Vector3f(p.vx(),p.vy(),p.vz());if(velocity.lengthSquared()>1)model.setLocalRotation(new Quaternion().lookAt(velocity.normalizeLocal(),Vector3f.UNIT_Y));
        }
        missiles.entrySet().removeIf(e->{if(live.contains(e.getKey()))return false;e.getValue().removeFromParent();return true;});
        if(lastSnapshot==snapshot.receivedNanos())return;lastSnapshot=snapshot.receivedNanos();
        for(CombatEvent e:snapshot.battle().events())if(e.id()>lastEvent){lastEvent=e.id();Vector3f position=origin.local(e.x(),(float)e.y(),e.z());
            // Old events remain in the bounded ring to survive dropped snapshots; replay only new ones.
            switch(e.kind()){
                case "impact"->world.groundExplosion(position);
                case "launch"->sound.accept("launch",position);
                case "gun"->sound.accept("cannon",position);
                case "flare"->{Node n=e.owner().equals(self)?player:fleet.node(e.owner());if(n!=null){var actor=DamageableAircraft.find(n);if(actor!=null)world.dispense(actor);}}
            }
        }
    }
    public void clear(){for(Spatial s:missiles.values())s.removeFromParent();missiles.clear();lastEvent=lastSnapshot=0;flight=-1;}
}
