import alxb.flighttraining.game.flight.*;
import com.jme3.scene.Node;
import com.jme3.math.*;
public class PhysicsCheck {
 static void check(boolean ok,String msg){if(!ok)throw new AssertionError(msg);}
 record Rig(Node node,FlightCommandAi command,FlightPhysics physics){}
 static Rig rig(float speed,float height,float bank,float load){
  Node n=new Node();n.setLocalTranslation(0,height,0);
  FlightCommandAi c=new FlightCommandAi();FlightPhysics p=new FlightPhysics(n,c);
  float q=0.5f*p.getAirDensity()*speed*speed;
  float cl=p.getParameters().massKg()*9.80665f*load/(q*p.getParameters().wingAreaM2());
  float alpha=AerodynamicModel.alphaForLift(cl,speed/p.getAtmosphere().speedOfSoundMps());
  n.setLocalRotation(new Quaternion().fromAngleAxis(bank,Vector3f.UNIT_Z).mult(new Quaternion().fromAngleAxis(alpha,Vector3f.UNIT_X)));
  p.reset(new Vector3f(0,0,-speed),p.getTrimThrottle(speed,load,0));
  c.setThrottle(p.getThrottle());c.setPitch(p.pitchCommandForLoadFactor(load));
  return new Rig(n,c,p);
 }
 static void fly(Rig r,float seconds,int fps){for(int i=0;i<seconds*fps;i++)r.physics.update(1f/fps);}
 public static void main(String[] args){
  check(Math.abs(FlightAtmosphere.atAltitude(0).densityKgM3()-1.225)<0.002,"ISA sea level");
  check(Math.abs(FlightAtmosphere.atAltitude(11000).densityKgM3()-0.364)<0.003,"ISA 11km");
  var params=AircraftParameters.hornet();
  float c1=AerodynamicModel.liftCoefficient((float)Math.toRadians(23.999),0.5f),c2=AerodynamicModel.liftCoefficient((float)Math.toRadians(24.001),0.5f);
  check(Math.abs(c2-c1)<0.001,"Discontinuous stall");
  check(AerodynamicModel.liftCoefficient((float)Math.toRadians(60),0.5f)<AerodynamicModel.liftCoefficient((float)Math.toRadians(25),0.5f),"No lift loss");
  check(AerodynamicModel.dragCoefficient(0.2f,0,1.2f,0.5f,params)>AerodynamicModel.dragCoefficient(0.02f,0,0.1f,0.5f,params),"No induced drag");
  System.out.println("PASS atmosphere, continuous stall curve, drag polar");
  Rig level=rig(220,3000,0,1);fly(level,60,120);
  System.out.printf("Level: h=%.1f v=%.1f G=%.3f AoA=%.2f%n",level.node.getLocalTranslation().y,level.physics.getSpeed(),level.physics.getNormalG(),level.physics.getAngleOfAttack());
  check(Math.abs(level.node.getLocalTranslation().y-3000)<100,"Level flight sinks/climbs");
  check(Math.abs(level.physics.getNormalG()-1)<0.05,"Level G not 1");
  Rig turn=rig(220,3000,(float)Math.toRadians(45),(float)Math.sqrt(2));fly(turn,40,120);
  System.out.printf("Turn: h=%.1f G=%.2f path=%.2f pos=%s%n",turn.node.getLocalTranslation().y,turn.physics.getNormalG(),turn.physics.getFlightPathAngle(),turn.node.getLocalTranslation());
  check(Math.abs(turn.node.getLocalTranslation().x)>1000,"No banked turn");
  Rig negative=rig(220,5000,0,1);negative.command.setPitch(-0.5f);fly(negative,3,120);
  System.out.println("Negative G="+negative.physics.getNormalG());check(negative.physics.getNormalG()<0,"No negative G");
  Rig low=rig(55,5000,0,1);low.command.setThrottle(0);low.physics.setEngineRunning(false);low.physics.reset(low.physics.getVelocity(),0);fly(low,10,120);
  System.out.printf("Slow: h=%.1f speed=%.1f%n",low.node.getLocalTranslation().y,low.physics.getSpeed());
  check(low.node.getLocalTranslation().y<4900,"Impossible low speed hovering");
  Rig glide=rig(220,5000,0,1);glide.physics.setEngineRunning(false);glide.physics.reset(glide.physics.getVelocity(),0);glide.command.setThrottle(0);
  float e0=0.5f*glide.physics.getSpeed()*glide.physics.getSpeed()+9.80665f*5000;
  fly(glide,30,120);float e1=0.5f*glide.physics.getSpeed()*glide.physics.getSpeed()+9.80665f*glide.node.getLocalTranslation().y;
  check(e1<e0,"Glider gains energy");System.out.printf("Glide energy: %.0f -> %.0f J/kg%n",e0,e1);
  Rig a=rig(230,4000,0,1),b=rig(230,4000,0,1);
  a.command.setRoll(.15f);b.command.setRoll(.15f);a.command.setPitch(.2f);b.command.setPitch(.2f);
  fly(a,8,30);fly(b,8,120);float error=a.node.getLocalTranslation().distance(b.node.getLocalTranslation());
  check(error<1,"Frame dependence "+error);System.out.println("PASS 30/120 FPS position difference="+error+"m");
  Rig wind=rig(220,3000,0,1);wind.physics.setWindVelocity(new Vector3f(0,0,-40));
  check(Math.abs(wind.physics.getAirspeed()-180)<.01,"Wrong relative airspeed");
  check(wind.physics.getEquivalentAirspeed()<wind.physics.getAirspeed(),"EAS altitude correction");
  Rig ground=rig(0,0,0,1);ground.node.setLocalRotation(new Quaternion());ground.physics.reset(new Vector3f(),0);ground.physics.setEngineRunning(false);
  for(int i=0;i<120;i++){ground.physics.update(1f/120);ground.physics.resolveGroundCollision(0);}
  check(Math.abs(ground.physics.getNormalG()-1)<.01,"Ground support G");
  check(ground.node.getLocalTranslation().y==0.5f,"Ground clamp");
  System.out.println("PASS wind, EAS, ground support; all checks passed");
 }
}
