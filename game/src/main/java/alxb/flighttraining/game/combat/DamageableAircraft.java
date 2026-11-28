package alxb.flighttraining.game.combat;

import alxb.flighttraining.game.flight.FlightPhysics;
import com.jme3.math.Vector3f;
import com.jme3.scene.*;
import com.jme3.scene.control.AbstractControl;

/** A hit resolves to the aircraft owning the intersected geometry, never to every target. */
public final class DamageableAircraft extends AbstractControl {
    private final FlightPhysics physics;
    public DamageableAircraft(FlightPhysics physics){this.physics=physics;}
    public FlightPhysics physics(){return physics;}
    public AirframeDamage damage(){return physics.getDamage();}
    public Node plane(){return (Node)spatial;}
    public void hit(Vector3f world,AirframeDamage.Weapon weapon,float strength){
        damage().hit(spatial.worldToLocal(world,new Vector3f()),weapon,strength);
    }
    public static DamageableAircraft find(Spatial geometry){for(Spatial s=geometry;s!=null;s=s.getParent()){var body=s.getControl(DamageableAircraft.class);if(body!=null)return body;}return null;}
    protected void controlUpdate(float tpf){}protected void controlRender(com.jme3.renderer.RenderManager manager,com.jme3.renderer.ViewPort viewport){}
}
