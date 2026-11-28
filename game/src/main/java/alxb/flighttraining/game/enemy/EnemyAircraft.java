package alxb.flighttraining.game.enemy;

import alxb.flighttraining.game.weapons.Cannon;
import alxb.flighttraining.game.config.DogfightSettings;
import alxb.flighttraining.game.flight.FlightCommandAi;
import alxb.flighttraining.game.flight.FlightPhysics;
import alxb.flighttraining.game.flight.AircraftRig;
import alxb.flighttraining.game.weapons.Guns;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;
public final class EnemyAircraft {
 private final Node enemyPlane;
 private final FlightCommandAi commands=new FlightCommandAi();
 private final FlightPhysics physics;
 private final EnemyAI ai;
 private final Guns cannon;
 private float cooldown,missileCooldown=10;private int missiles=2;
 private alxb.flighttraining.game.sensors.Radar radar;
 public FlightPhysics getPhysics(){return physics;}
 public alxb.flighttraining.game.sensors.Radar getRadar(){return radar;}
 public void setMissileDelay(float delay){missileCooldown=delay;}
 public void configureCombat(alxb.flighttraining.game.combat.CombatWorld world){world.register(enemyPlane,physics,1);radar=new alxb.flighttraining.game.sensors.Radar(18520,enemyPlane,null);radar.configure(physics,world);radar.setAutoLock(weaponsEnabled);physics.getSystems().combat().setMasterArm(weaponsEnabled);}
 public boolean takeMissileShot(float dt,Node target){
  missileCooldown=Math.max(0,missileCooldown-Math.min(dt,.25f));if(!weaponsEnabled||missiles==0||missileCooldown>0||physics.getDamage().destroyed()||!physics.getSystems().combat().masterArm()||radar==null||radar.irTarget()!=target)return false;
  float distance=enemyPlane.getWorldTranslation().distance(target.getWorldTranslation());if(distance<450||distance>2600)return false;
  missiles--;missileCooldown=18;return true;
 }
 private boolean weaponsEnabled=true;
 public void configure(DogfightSettings settings) {
  ai.setDifficulty(settings.difficulty); weaponsEnabled=settings.weaponsEnabled;physics.getSystems().combat().setMasterArm(weaponsEnabled);
 }

 public EnemyAircraft(Node node,Node weaponTargets){
  enemyPlane=node; node.updateGeometricState();
  physics=new FlightPhysics(node,commands);physics.getSystems().combat().setMasterArm(true);
  physics.reset(node.getLocalRotation().mult(new Vector3f(0,0,-180)), physics.getTrimThrottle(180,1,0));
  AircraftRig.attach(node,physics.getSystems());
  ai=new EnemyAI(commands,node,physics); cannon=new Cannon(weaponTargets);
 }
 public void update(float dt,Node target,Vector3f targetVelocity,float ground){
  if (!Float.isFinite(dt) || dt <= 0) return;
  float remaining=Math.min(dt,0.25f);
  while(remaining>0.000001f){
   float step=Math.min(remaining,1f/120f);
   updateStep(step,target,targetVelocity,ground);
   remaining-=step;
  }
  if(radar!=null)radar.update(Math.min(dt,.25f));
 }
 private void updateStep(float dt,Node target,Vector3f targetVelocity,float ground){
  if(!physics.getDamage().destroyed())ai.update(dt,target,targetVelocity,ground);else{commands.setPitch(0);commands.setRoll(0);commands.setYaw(0);commands.setThrottle(0);}
  physics.update(dt);
  enemyPlane.updateGeometricState(); cannon.update(dt); cooldown-=dt;
  if(weaponsEnabled && physics.getSystems().combat().masterArm() && !physics.getDamage().destroyed() && ai.isFiring() && cooldown<=0){
   Vector3f direction=enemyPlane.getWorldRotation().mult(new Vector3f(0,0,-1));
   cannon.fire(getPosition().add(direction.mult(12)),direction,physics.getVelocity());
   cooldown=0.04f;
  }
 }
 public void setSoundEvents(java.util.function.Consumer<Vector3f> fire,java.util.function.Consumer<Vector3f> hit){cannon.setShotListener(fire);cannon.setHitListener(hit);}
 public void rebase(Vector3f delta){ai.rebase(delta);cannon.rebase(delta);}
 public EnemyAI.State getState(){return ai.getState();}
 public Vector3f getPosition(){return enemyPlane.getWorldTranslation().clone();}
 public Node getPlane(){return enemyPlane;}
 public void remove(){enemyPlane.removeFromParent();}
}
