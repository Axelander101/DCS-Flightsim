package alxb.flighttraining.game.combat;

import alxb.flighttraining.game.flight.FlightPhysics;
import com.jme3.asset.AssetManager;
import com.jme3.math.*;
import com.jme3.scene.*;
import java.util.*;
import java.util.function.Consumer;

/** Frame-relative swept proximity fuzes; keeps target motion and origin shifts consistent. */
public final class CombatWorld {
    public record Impact(DamageableAircraft aircraft,Vector3f position,float fraction){}
    public record Flare(Node node,float brightness){}
    private static final class Body {final DamageableAircraft actor;final int team;final Vector3f start=new Vector3f();Body(DamageableAircraft a,int team){actor=a;this.team=team;start.set(a.plane().getWorldTranslation());}}
    private final List<Body> bodies=new ArrayList<>();private final List<FlareLife> flares=new ArrayList<>();
    private record FlareLife(Node node,Vector3f velocity,float initial,float[] life){}
    private final DamageEffects effects;private Consumer<Vector3f> explosion=p->{};
    public CombatWorld(AssetManager assets,Node root){effects=new DamageEffects(assets,root);}
    public void explosionListener(Consumer<Vector3f> listener){explosion=listener;}
    public void register(Node plane,FlightPhysics physics){register(plane,physics,0);}
    public void register(Node plane,FlightPhysics physics,int team){
        bodies.removeIf(b->b.actor.plane()==plane);var actor=DamageableAircraft.find(plane);if(actor==null)throw new IllegalStateException("Damage receiver missing");
        bodies.add(new Body(actor,team));if(plane.getControl(AirframeDamageVisuals.class)==null)plane.addControl(new AirframeDamageVisuals(plane,physics,effects));
    }
    public List<DamageableAircraft> hostiles(Node plane){int team=-1;for(var b:bodies)if(b.actor.plane()==plane)team=b.team;var list=new ArrayList<DamageableAircraft>();for(var b:bodies)if(b.team!=team&&b.actor.plane().getParent()!=null&&!b.actor.damage().destroyed())list.add(b.actor);return List.copyOf(list);}
    private java.util.function.BiPredicate<Vector3f,Vector3f> clearSight=(a,b)->true;
    public void lineOfSight(java.util.function.BiPredicate<Vector3f,Vector3f> clear){clearSight=clear;}
    public boolean visible(Vector3f a,Vector3f b){return clearSight.test(a,b);}
    public void beginFrame(){bodies.removeIf(b->b.actor.plane().getParent()==null);for(var b:bodies)b.start.set(b.actor.plane().getWorldTranslation());}
    public Impact sweep(DamageableAircraft owner,Vector3f from,Vector3f to,float startFraction,float endFraction){
        Impact best=null;
        for(var b:bodies){var actor=b.actor;if(actor==owner||actor.plane().getParent()==null||actor.damage().destroyed())continue;
            var end=actor.plane().getWorldTranslation();var inverse=actor.plane().getWorldRotation().inverse();
            Vector3f originStart=b.start.clone().interpolateLocal(end,clamp(startFraction)),originEnd=b.start.clone().interpolateLocal(end,clamp(endFraction));
            Vector3f localFrom=inverse.mult(from.subtract(originStart)),localTo=inverse.mult(to.subtract(originEnd));
            for(var part:AirframeDamage.Part.values())if((part.structural()||part==AirframeDamage.Part.RADAR)&&actor.damage().health(part)>.06f){
                var center=part.center();var extent=part.extent().addLocal(.45f,.45f,.45f);
                Vector3f a=localFrom.subtract(center).divideLocal(extent),d=localTo.subtract(localFrom).divideLocal(extent);
                float aa=d.lengthSquared(),bb=2*a.dot(d),cc=a.lengthSquared()-1,fraction=Float.POSITIVE_INFINITY;
                if(cc<=0)fraction=0;else if(aa>1e-8f){float disc=bb*bb-4*aa*cc;if(disc>=0)fraction=(-bb-(float)Math.sqrt(disc))/(2*aa);}
                if(fraction>=0&&fraction<=1&&(best==null||fraction<best.fraction()))best=new Impact(actor,from.clone().interpolateLocal(to,fraction),fraction);
            }
        }return best;
    }
    private static float clamp(float v){return Math.max(0,Math.min(1,v));}
    public void detonate(Impact hit){
        hit.aircraft().hit(hit.position(),AirframeDamage.Weapon.MISSILE,1);
        // Nearby aircraft receive attenuated blast, without applying the direct hit twice.
        for(var b:bodies)if(b.actor!=hit.aircraft()&&b.actor.plane().getParent()!=null){float distance=b.actor.plane().getWorldTranslation().distance(hit.position());if(distance<3)b.actor.hit(hit.position(),AirframeDamage.Weapon.MISSILE,.28f*(1-distance/3));}
        effects.explosion(hit.position());explosion.accept(hit.position());
    }
    public void groundExplosion(Vector3f position){effects.explosion(position);explosion.accept(position);}
    public void dispense(DamageableAircraft aircraft){
        Vector3f pos=aircraft.plane().getWorldTranslation(),velocity=aircraft.physics().getVelocity();
        for(int side:new int[]{-1,1}){Node flare=effects.flare(pos.add(aircraft.plane().getWorldRotation().mult(new Vector3f(side*.5f,-.3f,1.5f))));
            Vector3f v=velocity.add(aircraft.plane().getWorldRotation().mult(new Vector3f(side*9,-3,12)));flares.add(new FlareLife(flare,v,8,new float[]{4}));}
    }
    public List<Flare> flares(){var result=new ArrayList<Flare>();for(var f:flares)if(f.life()[0]>0)result.add(new Flare(f.node(),f.initial()*Math.min(1,f.life()[0]/2)));return result;}
    public void update(float dt){dt=Math.min(Math.max(0,dt),.25f);effects.update(dt);Iterator<FlareLife> it=flares.iterator();while(it.hasNext()){var f=it.next();f.life()[0]-=dt;if(f.life()[0]<=0){f.node().removeFromParent();it.remove();continue;}f.velocity().multLocal((float)Math.exp(-dt*.6f)).addLocal(0,-9.81f*dt,0);f.node().move(f.velocity().mult(dt));}}
    public void rebase(Vector3f delta){for(var b:bodies)b.start.subtractLocal(delta);effects.rebase(delta);}
    public void reset(){effects.clear();for(var f:flares)f.node().removeFromParent();flares.clear();bodies.removeIf(b->b.actor.plane().getParent()==null);}
}
