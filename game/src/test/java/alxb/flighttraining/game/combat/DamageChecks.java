package alxb.flighttraining.game.combat;
import alxb.flighttraining.game.flight.*;
import alxb.flighttraining.game.weapons.*;
import alxb.flighttraining.game.sensors.Radar;
import com.jme3.asset.*;
import com.jme3.asset.plugins.FileLocator;
import com.jme3.math.*;
import com.jme3.scene.*;
import com.jme3.scene.shape.Box;
import static alxb.flighttraining.game.combat.AirframeDamage.Part.*;
public class DamageChecks {
 static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
 static FlightPhysics aircraft(Node root,String name,Vector3f position){Node n=new Node(name);root.attachChild(n);n.setLocalTranslation(position);var p=new FlightPhysics(n,new FlightCommandAi());p.reset(new Vector3f(0,0,-200),.65f);root.updateGeometricState();return p;}
 static Node plane(FlightPhysics p,Node root){for(Spatial s:root.getChildren())if(s.getControl(DamageableAircraft.class)!=null&&s.getControl(DamageableAircraft.class).physics()==p)return(Node)s;throw new AssertionError();}
 static void cannon(AirframeDamage damage,AirframeDamage.Part part){for(int i=0;i<8;i++)damage.hit(part.center(),AirframeDamage.Weapon.CANNON,1);}
 public static void main(String[]args)throws Exception{
  var damage=new AirframeDamage();damage.hit(LEFT_WING.center(),AirframeDamage.Weapon.MISSILE,1);check(damage.health(LEFT_WING)<.08f&&damage.health(RIGHT_WING)>.95f,"localized wing blast");check(damage.health(FUSELAGE)>.5f,"wing hit is not global instant kill");
  damage.reset();cannon(damage,LEFT_ENGINE);check(damage.health(LEFT_ENGINE)==0&&damage.health(RIGHT_ENGINE)==1,"independent engine damage");check(damage.engineFire(true),"engine fire");var combat=new HornetCombatSystems(damage);combat.command(HornetCombatSystems.DISCHARGE);check(damage.engineFire(true),"bottle cannot discharge before FIRE selection");combat.command(HornetCombatSystems.FIRE_LEFT);combat.command(HornetCombatSystems.DISCHARGE);check(!damage.engineFire(true)&&!combat.fireBottle()&&!combat.engineRunning(true)&&combat.engineRunning(false),"isolate then discharge, without repairing engine");
  for(int i=0;i<1200;i++)combat.update(1f/120);check(combat.hydraulic(1)<.01f&&combat.hydraulic(2)>.99f&&combat.electrical(),"remaining engine preserves second hydraulic circuit and electrical supply");
  damage.reset();check(damage.health(LEFT_ENGINE)==1&&damage.fuel()==1,"new-flight damage reset");
  Node root=new Node("world");var healthy=aircraft(root,"healthy",new Vector3f(0,2000,0));var hurt=aircraft(root,"hurt",new Vector3f(100,2000,0));var hn=plane(healthy,root);var dn=plane(hurt,root);
  float alpha=AerodynamicModel.alphaForLift(healthy.getParameters().massKg()*9.80665f/(.5f*healthy.getAirDensity()*200*200*healthy.getParameters().wingAreaM2()),healthy.getMach());hn.setLocalRotation(new Quaternion().fromAngleAxis(alpha,Vector3f.UNIT_X));dn.setLocalRotation(hn.getLocalRotation().clone());root.updateGeometricState();hurt.getDamage().hit(LEFT_WING.center(),AirframeDamage.Weapon.MISSILE,1);
  healthy.update(1f/120);hurt.update(1f/120);check(hurt.getLiftN()<healthy.getLiftN()*.7f&&hurt.getDragN()>healthy.getDragN(),"wing loss lowers lift and increases drag");
  for(int i=0;i<120;i++){healthy.update(1f/120);hurt.update(1f/120);root.updateGeometricState();}
  check(dn.getWorldRotation().mult(Vector3f.UNIT_X).y>.08f,"left-wing loss rolls aircraft left");check(Math.abs(hn.getWorldRotation().mult(Vector3f.UNIT_X).y)<.001f,"undamaged aircraft has no asymmetric roll");
  for(int i=0;i<1200;i++){hurt.update(1f/120);root.updateGeometricState();check(Float.isFinite(hurt.getSpeed())&&Float.isFinite(dn.getWorldTranslation().y)&&Float.isFinite(dn.getLocalRotation().getW()),"damaged dynamics stay finite");}
  var assets=new DesktopAssetManager(true);assets.registerLocator(args[0],FileLocator.class);Node world=new Node("combat");var owner=aircraft(world,"owner",new Vector3f(0,1000,0));var target=aircraft(world,"target",new Vector3f(0,1000,-500));target.setVelocity(new Vector3f());var ownerNode=plane(owner,world);var targetNode=plane(target,world);var registry=new CombatWorld(assets,world);registry.register(ownerNode,owner,0);registry.register(targetNode,target,1);world.updateGeometricState();
  int[] explosions={0};registry.explosionListener(p->explosions[0]++);Node missileModel=new Node("missile");world.attachChild(missileModel);var missile=new InfraredMissile(missileModel,targetNode,new Vector3f(0,1000,-2),new Vector3f(0,0,-1),new Vector3f(0,0,-250),Vector3f.UNIT_Y,registry,DamageableAircraft.find(ownerNode));boolean done=false;float closest=Float.POSITIVE_INFINITY;Vector3f closestPoint=null;
  for(int i=0;i<420&&!done;i++){registry.beginFrame();world.updateGeometricState();done=missile.update(1f/60);float distance=missile.getPosition().distance(targetNode.getWorldTranslation());if(distance<closest){closest=distance;closestPoint=missile.getPosition();}}
  System.out.println("Missile diagnostic: done="+done+" detonations="+explosions[0]+" closest="+closest+" position="+closestPoint);
  check(done&&explosions[0]==1&&target.getDamage().health(FUSELAGE)<1,"actual missile impacts target once");check(owner.getDamage().health(FUSELAGE)==1,"shooter excluded");missile.update(.1f);check(explosions[0]==1,"no double detonation");
  target.getSystems().reset(true);target.setVelocity(new Vector3f(30,0,0));targetNode.setLocalTranslation(0,1000,-1000);ownerNode.setLocalTranslation(0,1000,0);world.updateGeometricState();var radar=new Radar(18520,ownerNode,null);radar.configure(owner,registry);radar.update(.1f);owner.getSystems().combat().command(HornetCombatSystems.LOCK_TARGET);for(int i=0;i<10;i++)radar.update(.1f);check(radar.lockedTarget()==targetNode&&radar.gunLead()!=null,"radar acquisition and computed gun lead");check(radar.gunLead().x>0&&radar.gunLead().y>0,"lead accounts for crossing velocity and gravity");
  owner.getSystems().combat().command(HornetCombatSystems.RADAR);for(int i=0;i<10;i++)radar.update(.1f);check(radar.contacts().isEmpty()&&radar.lockedTarget()==null&&radar.irTarget()==targetNode,"passive IR remains independent of switched-off radar");
  owner.getSystems().combat().command(HornetCombatSystems.RADAR);registry.lineOfSight((a,b)->false);radar.update(.1f);check(radar.contacts().isEmpty()&&radar.irTarget()==null,"terrain occlusion blocks sensors");
  Node bullets=new Node("bullet targets");world.attachChild(bullets);var victim=aircraft(bullets,"victim",new Vector3f(30,1000,0));Node vn=plane(victim,bullets);Geometry panel=new Geometry("left wing target",new Box(.2f,.1f,.3f));panel.setLocalTranslation(LEFT_WING.center());vn.attachChild(panel);world.updateGeometricState();var bullet=new Bullet(new Vector3f(28.3f,1000,5),new Vector3f(0,0,-1000));check(bullet.update(.01f,bullets),"cannon sweep intersects actual geometry");check(victim.getDamage().health(LEFT_WING)<1,"geometry hit resolves to owning aircraft's local component");
  System.out.println("PASS local blast/cannon damage, separate engines and fire procedure, hydraulic/electrical redundancy, asymmetric lift/drag/roll, 10-second finite damaged trajectory, real missile impact and single detonation, shooter exclusion, RWS/STT lead, passive IR independence, terrain occlusion and geometry-resolved cannon hit");
 }
}
