import alxb.flighttraining.game.flight.*;
import com.jme3.scene.Node;
import com.jme3.math.*;
public class StressCheck {
 static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
 public static void main(String[]args){
  for(float command:new float[]{-1,1}){
   var r=PhysicsCheck.rig(300,7000,0,1);r.command().setPitch(command);r.command().setThrottle(1);
   float peak=0,min=0;
   for(int i=0;i<1200;i++){
    r.physics().update(1f/120);peak=Math.max(peak,r.physics().getNormalG());min=Math.min(min,r.physics().getNormalG());
    require(Float.isFinite(r.physics().getSpeed()),"Nonfinite high-G flight");
   }
   System.out.printf("Full pitch %.0f: normal G range %.2f .. %.2f, altitude=%.0f%n",command,min,peak,r.node().getLocalTranslation().y);
   require(command>0?peak>4:min<-1,"Missing G response");
   require(peak<10 && min>-5,"Severe G-limit overshoot");
  }
  var stalled=PhysicsCheck.rig(180,5000,0,1);
  stalled.node().setLocalRotation(new Quaternion().fromAngleAxis((float)Math.toRadians(60),Vector3f.UNIT_X));
  require(stalled.physics().isStalling(),"High AoA not flagged");
  for(int i=0;i<1200;i++)stalled.physics().update(1f/120);
  require(!stalled.physics().isStalling(),"No high-AoA recovery");
  System.out.printf("PASS stall recovery: AoA=%.1f speed=%.0f%n",stalled.physics().getAngleOfAttack(),stalled.physics().getSpeed());
  var cruise=PhysicsCheck.rig(220,3000,0,1);
  Vector3f previous=cruise.physics().getVelocity();cruise.physics().update(Float.NaN);cruise.physics().update(-1);
  require(cruise.physics().getVelocity().equals(previous),"Invalid dt changed state");
  boolean rejected=false;try{cruise.physics().setVelocity(new Vector3f(Float.NaN,0,0));}catch(IllegalArgumentException e){rejected=true;}
  require(rejected,"Invalid velocity accepted");
  System.out.println("PASS high-G, stall recovery and invalid-input checks");
 }
}
