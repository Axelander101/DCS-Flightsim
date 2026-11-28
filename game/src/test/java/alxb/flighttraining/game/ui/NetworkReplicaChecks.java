package alxb.flighttraining.game.ui;
import alxb.flighttraining.game.combat.*;
import alxb.flighttraining.game.flight.*;
import alxb.flighttraining.game.network.FlightNetwork.*;
import alxb.flighttraining.game.config.DogfightSettings.Region;
import alxb.flighttraining.game.world.WorldCoordinates;
import com.jme3.asset.*;
import com.jme3.asset.plugins.FileLocator;
import com.jme3.math.*;
import com.jme3.scene.*;
import com.jme3.renderer.Camera;
import java.util.*;
public class NetworkReplicaChecks {
 static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
 static int triangles(Node node){int[] n={0};node.depthFirstTraversal(s->{if(s instanceof Geometry g&&!(g instanceof com.jme3.effect.ParticleEmitter))n[0]+=g.getMesh().getTriangleCount();});return n[0];}
 public static void main(String[]args){
  AssetManager assets=new DesktopAssetManager(true);assets.registerLocator(args[0],FileLocator.class);Node root=new Node("World"),gui=new Node("GUI"),player=new Node("Self");root.attachChild(player);FlightPhysics physics=new FlightPhysics(player,new FlightCommandAi());CombatWorld world=new CombatWorld(assets,root);world.register(player,physics,0);
  RemoteFleet fleet=new RemoteFleet(assets,root,gui,assets.loadFont("Interface/Fonts/Default.fnt"));fleet.combatWorld(world);NetworkCombatView view=new NetworkCombatView(assets,root,world,(s,p)->{});WorldCoordinates origin=new WorldCoordinates(670000,-750000);Camera camera=new Camera(1920,1080);camera.setLocation(new Vector3f(0,1000,0));
  AirframeDamage damage=new AirframeDamage();var healthy=new AircraftDamage("two",damage.integrity(),false,false,1,true,true,true,true,30,true,500,4,"");
  Lobby lobby=new Lobby("Battle",Region.values()[0],2,false,Phase.FLYING,1,List.of(new Player("one","Alpha",0,true,true,true),new Player("two","Bravo",1,false,true,true)),true);
  Pose pose=new Pose("two",1,670010,1000,-750100,0,0,0,1,0,0,-180,.7f,0);Snapshot snapshot=new Snapshot(lobby,List.of(pose),System.nanoTime(),new Battle(List.of(healthy),List.of(),List.of()));
  fleet.update(snapshot,"one",origin,camera,Vector3f.ZERO,.016f,20);root.updateLogicalState(.016f);root.updateGeometricState();Node remote=fleet.node("two");int initial=triangles(remote);check(world.hostiles(player).size()==1,"Network opponent registered for player radar");
  damage.hit(AirframeDamage.Part.LEFT_WING.center(),AirframeDamage.Weapon.MISSILE,1);var damaged=new AircraftDamage("two",damage.integrity(),false,false,1,true,true,true,true,28,true,450,3,"one");
  snapshot=new Snapshot(lobby,List.of(new Pose("two",2,670010,1000,-750100,0,0,0,1,0,0,-180,.7f,0)),System.nanoTime(),new Battle(List.of(damaged),List.of(new Projectile(1,"one",670002,1000,-750050,0,0,-400)),List.of(new CombatEvent(2,"flare","two",670010,1000,-750100))));
  fleet.update(snapshot,"one",origin,camera,Vector3f.ZERO,.016f,20);view.update(snapshot,"one",player,fleet,origin);view.update(snapshot,"one",player,fleet,origin);root.updateLogicalState(.016f);root.updateGeometricState();check(triangles(remote)<initial,"Host damage removes actual remote wing triangles");check(world.flares().size()==2,"Repeated snapshots cannot replay flare event");check(DamageableAircraft.find(remote).physics().getSystems().combat().flares()==28,"Replicated remaining countermeasures");
  origin.shift(new Vector3f(1000,0,-1000));fleet.update(snapshot,"one",origin,camera,Vector3f.ZERO,.016f,20);view.update(snapshot,"one",player,fleet,origin);root.updateGeometricState();check(remote.getLocalTranslation().x < -980,"Replica follows rebased origin");
  view.clear();fleet.clear();world.beginFrame();check(world.hostiles(player).isEmpty(),"Leaving removes remote damage targets");
  System.out.println("PASS real remote GLB damage, sensor registration, replicated stocks, deduplicated events, floating-origin alignment and multiplayer cleanup");
 }
}
