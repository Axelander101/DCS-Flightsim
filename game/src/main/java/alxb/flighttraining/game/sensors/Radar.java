package alxb.flighttraining.game.sensors;

import alxb.flighttraining.game.enemy.Enemy;
import alxb.flighttraining.game.flight.FlightPhysics;
import alxb.flighttraining.game.combat.*;
import com.jme3.math.Vector3f;
import com.jme3.scene.*;
import java.util.*;

/** Bounded APG-73-inspired RWS/STT and independent passive IR seeker; no hidden omnidirectional lock. */
public final class Radar {
    public record Contact(Spatial target,float range,float bearing,float elevation,float closure){}
    private final float radius;private final Node plane;private final Enemy enemies;
    private FlightPhysics physics;private CombatWorld world;
    private List<Contact> contacts=List.of();private Spatial selected,irCandidate;private float lockTime,irTime;
    private boolean lockCommand,locked,autoLock;
    private final ArrayList<Boolean> isInRadius=new ArrayList<>();
    public Radar(float radius,Node plane,Enemy enemies){this.radius=radius;this.plane=plane;this.enemies=enemies;}
    public void configure(FlightPhysics physics,CombatWorld world){this.physics=physics;this.world=world;reset();}
    public void reset(){contacts=List.of();selected=irCandidate=null;lockTime=irTime=0;locked=lockCommand=false;}
    public void setAutoLock(boolean value){autoLock=value;}
    public float range(){return radius;}public List<Contact> contacts(){return contacts;}public Spatial selected(){return selected;}
    public Spatial lockedTarget(){return locked?selected:null;}public boolean isLocked(){return locked;}
    public Spatial irTarget(){return irTime>=.6f?irCandidate:null;}
    public void update(){update(1f/60);}
    public void update(float dt){
        if(!Float.isFinite(dt)||dt<=0)return;dt=Math.min(dt,.25f);
        var found=new ArrayList<Contact>();var forward=plane.getWorldRotation().mult(Vector3f.UNIT_Z.negate());var inverse=plane.getWorldRotation().inverse();
        var ownVelocity=physics==null?new Vector3f():physics.getVelocity();boolean powered=physics==null||physics.getSystems().combat().radarAvailable();
        List<Spatial> candidates=new ArrayList<>();if(world!=null){for(var actor:world.hostiles(plane))candidates.add(actor.plane());}else if(enemies!=null)for(int i=0;i<enemies.getCount();i++)candidates.add(enemies.getPlane(i));
        Spatial bestIr=null;float bestIrScore=Float.POSITIVE_INFINITY;
        for(Spatial target:candidates){if(target.getParent()==null)continue;var offset=target.getWorldTranslation().subtract(plane.getWorldTranslation());float distance=offset.length();if(distance<1||distance>radius)continue;
            var local=inverse.mult(offset);float bearing=(float)Math.toDegrees(Math.atan2(local.x,-local.z)),elevation=(float)Math.toDegrees(Math.atan2(local.y,Math.hypot(local.x,local.z)));
            var actor=DamageableAircraft.find(target);if(actor!=null&&actor.damage().destroyed())continue;
            boolean clear=world==null||world.visible(plane.getWorldTranslation(),target.getWorldTranslation());if(!clear)continue;
            var v=actor==null?new Vector3f():actor.physics().getVelocity();float closure=-v.subtract(ownVelocity).dot(offset.divide(distance));
            if(powered&&Math.abs(bearing)<=60&&Math.abs(elevation)<=30)found.add(new Contact(target,distance,bearing,elevation,closure));
            float alignment=forward.dot(offset.divide(distance));boolean hot=actor==null||actor.physics().getEnginePower()>.08f||actor.damage().engineFire(true)||actor.damage().engineFire(false);
            if((physics==null||physics.getSystems().combat().electrical())&&distance<4000&&alignment>(float)Math.cos(Math.toRadians(15))&&hot){float score=(1-alignment)*1000+distance*.0001f;if(score<bestIrScore){bestIr=target;bestIrScore=score;}}
        }
        found.sort(Comparator.comparingDouble(c->Math.abs(c.bearing())));contacts=List.copyOf(found);
        boolean next=physics!=null&&physics.getSystems().combat().takeNextTarget(),toggle=physics!=null&&physics.getSystems().combat().takeLockRequest();
        if(selected==null||contacts.stream().noneMatch(c->c.target()==selected)){selected=contacts.isEmpty()?null:contacts.getFirst().target();lockTime=0;locked=false;}
        if(next&&!contacts.isEmpty()){int index=0;for(int i=0;i<contacts.size();i++)if(contacts.get(i).target()==selected)index=i;selected=contacts.get((index+1)%contacts.size()).target();lockTime=0;locked=false;lockCommand=false;}
        if(toggle){lockCommand=!lockCommand;lockTime=0;locked=false;}
        if(selected!=null&&(lockCommand||autoLock)){lockTime+=dt;locked=lockTime>=.7f;}else{lockTime=0;locked=false;}
        if(bestIr!=irCandidate){irCandidate=bestIr;irTime=0;}if(irCandidate!=null)irTime+=dt;else irTime=0;
        isInRadius.clear();if(enemies!=null)for(int i=0;i<enemies.getCount();i++){Spatial target=enemies.getPlane(i);isInRadius.add(contacts.stream().anyMatch(c->c.target()==target));}
    }
    /** Relative-motion intercept including inherited own velocity and a gravity-drop correction. */
    public Vector3f gunLead(){
        Spatial target=lockedTarget();if(target==null)return null;var actor=DamageableAircraft.find(target);
        Vector3f r=target.getWorldTranslation().subtract(plane.getWorldTranslation()),v=(actor==null?new Vector3f():actor.physics().getVelocity()).subtract(physics==null?new Vector3f():physics.getVelocity());
        double a=v.lengthSquared()-1_000_000,b=2*r.dot(v),c=r.lengthSquared(),time=Double.POSITIVE_INFINITY;
        if(Math.abs(a)<1e-6){if(b<0)time=-c/b;}else{double disc=b*b-4*a*c;if(disc>=0){double x=(-b-Math.sqrt(disc))/(2*a),y=(-b+Math.sqrt(disc))/(2*a);if(x>0)time=x;if(y>0)time=Math.min(time,y);}}
        if(!Double.isFinite(time)||time>3)return null;return r.add(v.mult((float)time)).addLocal(0,(float)(4.903325*time*time),0);
    }
}
