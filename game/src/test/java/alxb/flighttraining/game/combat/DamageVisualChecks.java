package alxb.flighttraining.game.combat;
import alxb.flighttraining.game.flight.*;
import com.jme3.asset.*;
import com.jme3.asset.plugins.FileLocator;
import com.jme3.math.*;
import com.jme3.scene.*;
import java.util.*;
public class DamageVisualChecks {
 static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
 static Node model(AssetManager assets,Node parent,String name,Vector3f position){Node n=new Node(name);var model=assets.loadModel("Models/FA18C_Swiss_Hornet_Cannon.glb");model.rotate(0,FastMath.PI,0);model.setLocalScale(.5f);n.attachChild(model);parent.attachChild(n);n.setLocalTranslation(position);return n;}
 static int triangles(Node node){int[] count={0};node.depthFirstTraversal(s->{if(s instanceof Geometry g&&!(s instanceof com.jme3.effect.ParticleEmitter))count[0]+=g.getMesh().getTriangleCount();});return count[0];}
 public static void main(String[]args)throws Exception{
  var assets=new DesktopAssetManager(true);assets.registerLocator(args[0],FileLocator.class);Node root=new Node("root");Node a=model(assets,root,"A",new Vector3f(1500,1000,-900)),b=model(assets,root,"B",new Vector3f(1520,1000,-900));
  var pa=new FlightPhysics(a,new FlightCommandAi());var pb=new FlightPhysics(b,new FlightCommandAi());var world=new CombatWorld(assets,root);world.register(a,pa,0);world.register(b,pb,1);root.updateGeometricState();int before=triangles(a),other=triangles(b);
  pa.getDamage().hit(AirframeDamage.Part.LEFT_WING.center(),AirframeDamage.Weapon.MISSILE,1);root.updateLogicalState(.016f);root.updateGeometricState();int after=triangles(a);check(after<before,"actual asset triangles removed from damaged wing");check(triangles(b)==other,"shared asset cache cannot damage other aircraft");
  Node effects=(Node)root.getChild("Combat effects");check(effects!=null&&effects.getQuantity()>0,"real fragments spawned");world.update(.1f);root.updateGeometricState();for(var piece:effects.getChildren())if(piece.getName().startsWith("Detached"))check(piece.getWorldTranslation().distance(a.getWorldTranslation())<10,"fragment stays near aircraft instead of orbiting world origin");
  world.dispense(DamageableAircraft.find(a));root.updateGeometricState();check(world.flares().size()==2,"flare pair spawned");Vector3f flare=world.flares().getFirst().node().getWorldTranslation().clone();Vector3f delta=new Vector3f(1000,0,-1000);for(var child:root.getChildren())child.move(delta.negate());world.rebase(delta);root.updateGeometricState();check(world.flares().getFirst().node().getWorldTranslation().distance(flare.subtract(delta))<.001f,"flare and effects survive floating-origin shift exactly once");
  pa.getSystems().reset(true);root.updateLogicalState(.016f);check(triangles(a)==before,"new flight restores original meshes without editing cached resources");
  for(int i=0;i<30;i++)world.update(.25f);check(world.flares().isEmpty(),"countermeasure lifetime bounded");world.reset();check(effects.getQuantity()==0,"new-flight cleanup removes debris and bursts");
  System.out.println("PASS real GLB wing fracture, shared-mesh isolation, fragment world transforms, flare pair, floating-origin alignment, full mesh restoration and transient-effect cleanup; triangles "+before+" -> "+after);
 }
}
