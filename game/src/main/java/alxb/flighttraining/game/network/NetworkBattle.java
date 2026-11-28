package alxb.flighttraining.game.network;

import alxb.flighttraining.game.combat.*;
import alxb.flighttraining.game.network.FlightNetwork.*;
import com.jme3.math.*;
import java.util.*;

/** Host-owned weapons and component damage. Clients submit intent, never hits or opponent health.
 * Aircraft movement remains client simulated; this is not a competitive anti-cheat server. */
final class NetworkBattle {
    static final int COMMANDS=HornetCombatSystems.RADAR|HornetCombatSystems.MASTER_ARM|HornetCombatSystems.COUNTERMEASURES|
        HornetCombatSystems.FIRE_LEFT|HornetCombatSystems.FIRE_RIGHT|HornetCombatSystems.DISCHARGE|HornetCombatSystems.ENGINE_LEFT|HornetCombatSystems.ENGINE_RIGHT|128;
    static final class Pilot {
        final String id;final AirframeDamage damage=new AirframeDamage();final HornetCombatSystems systems=new HornetCombatSystems(damage);
        Pose pose;Vector3f position=new Vector3f(),previous=new Vector3f();Quaternion rotation=new Quaternion();
        boolean gun,launch,crashed;String requestedLock="",lock="",heatTarget="";float acquisition,stt,cooldown,gunTime,eventCooldown,hitEventTime=-1;long lastPose;
        int ammunition=500,missiles=4;
        Pilot(String id){this.id=id;systems.reset();systems.setMasterArm(true);}
    }
    static final class Shot {
        long id;Pilot owner;Vector3f position,velocity;String target="";float life;boolean missile;
        Shot(long id,Pilot owner,Vector3f position,Vector3f velocity,boolean missile){this.id=id;this.owner=owner;this.position=position;this.velocity=velocity;this.missile=missile;}
    }
    record Flare(Vector3f position,Vector3f velocity,float[] life){}
    final Map<String,Pilot> pilots=new LinkedHashMap<>();private final List<Shot> shots=new ArrayList<>();private final List<Flare> flares=new ArrayList<>();
    private final ArrayDeque<CombatEvent> events=new ArrayDeque<>();private double originX,originZ;private boolean located;private long sequence;private float clock;
    void retain(Set<String> ids){pilots.keySet().retainAll(ids);for(String id:ids)pilots.computeIfAbsent(id,Pilot::new);shots.removeIf(s->!pilots.containsKey(s.owner.id));}
    void pose(String id,Pose pose){Pilot p=pilots.get(id);if(p==null)return;if(!located){originX=pose.x();originZ=pose.z();located=true;}p.pose=pose;p.lastPose=System.nanoTime();}
    void intent(String id,boolean gun,boolean launch,int commands,String lock,boolean crash){
        Pilot p=pilots.get(id);if(p==null)return;p.gun=gun;p.launch|=launch;if(!p.requestedLock.equals(lock)){p.stt=0;p.lock="";}p.requestedLock=lock;
        if((commands&128)!=0)p.systems.setBothEngines(!(p.systems.engineSelected(true)||p.systems.engineSelected(false)));
        p.systems.command(commands&COMMANDS&~128);
        // A client may concede its own crash, but cannot supply a damage magnitude or another victim.
        if(crash&&!p.crashed){p.damage.hit(Vector3f.ZERO,AirframeDamage.Weapon.CRASH,1);p.crashed=true;}
    }
    private Vector3f velocity(Pilot p){return new Vector3f(p.pose.vx(),p.pose.vy(),p.pose.vz());}
    private Vector3f forward(Pilot p){return p.rotation.mult(new Vector3f(0,0,-1));}
    private Pilot heatTarget(Pilot p){
        Pilot best=null;float score=Float.POSITIVE_INFINITY;
        for(Pilot t:pilots.values())if(t!=p&&t.pose!=null&&!t.damage.destroyed()&&(((t.systems.engineRunning(true)||t.systems.engineRunning(false))&&t.pose.throttle()>.08f)||t.damage.engineFire(true)||t.damage.engineFire(false))){
            Vector3f d=t.position.subtract(p.position);float distance=d.length();if(distance>1&&distance<4000){float alignment=forward(p).dot(d.divide(distance)),candidate=(1-alignment)*1000+distance*.0001f;if(alignment>FastMath.cos(15*FastMath.DEG_TO_RAD)&&candidate<score){best=t;score=candidate;}}
        }return best;
    }
    private boolean radarCanSee(Pilot p,Pilot t){if(t==null||t==p||t.pose==null||t.damage.destroyed()||!p.systems.radarAvailable())return false;Vector3f d=p.rotation.inverse().mult(t.position.subtract(p.position));float r=d.length();return r>1&&r<18520&&d.z<0&&Math.abs(Math.atan2(d.x,-d.z))<Math.PI/3&&Math.abs(Math.asin(d.y/r))<Math.PI/6;}
    void tick(float dt){
        clock+=dt;
        for(Pilot p:pilots.values())if(p.pose!=null){p.previous.set(p.position);p.position.set((float)(p.pose.x()-originX),(float)p.pose.y(),(float)(p.pose.z()-originZ));p.rotation.set(p.pose.qx(),p.pose.qy(),p.pose.qz(),p.pose.qw());if(p.previous.lengthSquared()==0)p.previous.set(p.position);}
        for(Pilot p:pilots.values()){
            p.systems.update(dt);if(p.pose==null)continue;
            p.missiles=Math.min(p.missiles,2*((p.damage.health(AirframeDamage.Part.LEFT_WING)>.08f?1:0)+(p.damage.health(AirframeDamage.Part.RIGHT_WING)>.08f?1:0)));
            boolean fresh=System.nanoTime()-p.lastPose<500_000_000L;p.cooldown=Math.max(0,p.cooldown-dt);p.eventCooldown-=dt;
            Pilot heat=heatTarget(p);String id=heat==null?"":heat.id;if(!id.equals(p.heatTarget))p.acquisition=0;p.heatTarget=id;p.acquisition+=dt;
            Pilot selected=pilots.get(p.requestedLock);if(radarCanSee(p,selected)){p.stt+=dt;p.lock=p.stt>=.7f?p.requestedLock:"";}else{p.stt=0;p.lock="";}
            if(p.systems.takeCountermeasures()){
                for(int side:new int[]{-1,1})flares.add(new Flare(p.position.add(p.rotation.mult(new Vector3f(side*.5f,-.3f,1.5f))),velocity(p).add(p.rotation.mult(new Vector3f(side*9,-3,12))),new float[]{4}));
                event("flare",p,p.position);
            }
            if(fresh&&p.launch&&p.cooldown<=0&&p.missiles>0&&p.systems.masterArm()&&shots.stream().filter(s->s.missile).count()<32){
                Vector3f v=velocity(p).add(forward(p).mult(55));Shot s=new Shot(++sequence,p,p.position.add(p.rotation.mult(new Vector3f((p.damage.health(AirframeDamage.Part.LEFT_WING)<=.08f?1:p.damage.health(AirframeDamage.Part.RIGHT_WING)<=.08f?-1:(p.missiles%2==0?-1:1))*1.175f,-.2f,1))),v,true);
                if(p.acquisition>=.6f)s.target=p.heatTarget;shots.add(s);p.missiles--;p.cooldown=.75f;event("launch",p,s.position);
            }p.launch=false;
            if(fresh&&p.gun&&p.systems.masterArm()&&p.ammunition>0){
                p.gunTime+=dt;int n=Math.min(5,(int)(p.gunTime*100+.001f));p.gunTime-=n*.01f;
                for(int i=0;i<n&&p.ammunition>0;i++){shots.add(new Shot(++sequence,p,p.position.add(forward(p).mult(4.4f)),velocity(p).add(forward(p).mult(1000)),false));p.ammunition--;}
                if(p.eventCooldown<=0){event("gun",p,p.position);p.eventCooldown=.1f;}
            }else p.gunTime=0;
        }
        int steps=6;float h=dt/steps;
        for(int i=0;i<steps;i++){
            for(Iterator<Flare> it=flares.iterator();it.hasNext();){Flare f=it.next();f.life[0]-=h;if(f.life[0]<=0){it.remove();continue;}f.velocity.multLocal((float)Math.exp(-h*.6f)).addLocal(0,-9.81f*h,0);f.position.addLocal(f.velocity.mult(h));}
            for(Iterator<Shot> it=shots.iterator();it.hasNext();){Shot s=it.next();s.life+=h;if(s.life>(s.missile?14:3)||s.position.y< -1500){it.remove();continue;}
                if(s.missile){
                    Vector3f aim=null;Pilot t=pilots.get(s.target);if(s.life>.55f&&t!=null&&t.pose!=null&&!t.damage.destroyed()){
                        Vector3f delta=t.position.subtract(s.position);float time=Math.min(2,delta.length()/Math.max(80,s.velocity.length()));aim=t.position.add(velocity(t).mult(time*.8f));
                        float signal=3/Math.max(1,delta.lengthSquared());for(Flare f:flares){Vector3f d=f.position.subtract(s.position);float score=8*Math.min(1,f.life[0]/2)/Math.max(1,d.lengthSquared());if(score>signal&&s.velocity.normalize().dot(d.normalize())>.94f){signal=score;aim=f.position;}}
                    }
                    float speed=s.velocity.length();if(aim!=null){Vector3f desired=aim.subtract(s.position).normalizeLocal().mult(speed);Vector3f change=desired.subtract(s.velocity);float limit=Math.min(220,speed*1.2f)*h;if(change.length()>limit)change.normalizeLocal().multLocal(limit);s.velocity.addLocal(change).normalizeLocal().multLocal(speed);}
                    if(s.life<3)s.velocity.addLocal(s.velocity.normalize().mult(170*h));float v=s.velocity.length();if(v>750)s.velocity.multLocal(750/v);
                }
                s.velocity.addLocal(0,-9.80665f*h,0);Vector3f end=s.position.add(s.velocity.mult(h));Hit hit=trace(s,s.position,end,(float)i/steps,(float)(i+1)/steps);
                if(hit!=null&&(!s.missile||s.life>=.65f)){hit.p.damage.hit(hit.local,s.missile?AirframeDamage.Weapon.MISSILE:AirframeDamage.Weapon.CANNON,1);if(s.missile||clock-hit.p.hitEventTime>.15f){event("impact",s.owner,hit.world);if(!s.missile)hit.p.hitEventTime=clock;}it.remove();}
                else s.position.set(end);
            }
        }
    }
    private record Hit(Pilot p,Vector3f local,Vector3f world,float fraction){}
    private Hit trace(Shot shot,Vector3f from,Vector3f to,float aTime,float bTime){
        Hit best=null;
        for(Pilot p:pilots.values())if(p!=shot.owner&&p.pose!=null&&!p.damage.destroyed()){
            Vector3f a=p.previous.clone().interpolateLocal(p.position,aTime),b=p.previous.clone().interpolateLocal(p.position,bTime);
            Vector3f relativeFrom=from.subtract(a),relativeTo=to.subtract(b),segment=relativeTo.subtract(relativeFrom);float denom=segment.lengthSquared();float near=denom>0?FastMath.clamp(-relativeFrom.dot(segment)/denom,0,1):0;
            if(relativeFrom.add(segment.mult(near)).lengthSquared()>64)continue;
            Quaternion inverse=p.rotation.inverse();Vector3f localFrom=inverse.mult(relativeFrom),localTo=inverse.mult(relativeTo);
            for(var part:AirframeDamage.Part.values())if((part.structural()||part==AirframeDamage.Part.RADAR)&&p.damage.health(part)>.06f){
                Vector3f extent=part.extent().addLocal(shot.missile?.45f:.015f,shot.missile?.45f:.015f,shot.missile?.45f:.015f);
                Vector3f aa=localFrom.subtract(part.center()).divideLocal(extent),d=localTo.subtract(localFrom).divideLocal(extent);float A=d.lengthSquared(),B=2*aa.dot(d),C=aa.lengthSquared()-1,frac=Float.POSITIVE_INFINITY;
                if(C<=0)frac=0;else if(A>1e-10f){float disc=B*B-4*A*C;if(disc>=0)frac=(-B-(float)Math.sqrt(disc))/(2*A);}
                if(frac>=0&&frac<=1&&(best==null||frac<best.fraction))best=new Hit(p,localFrom.clone().interpolateLocal(localTo,frac),from.clone().interpolateLocal(to,frac),frac);
            }
        }return best;
    }
    private void event(String kind,Pilot p,Vector3f pos){events.addLast(new CombatEvent(++sequence,kind,p.id,pos.x+originX,pos.y,pos.z+originZ));while(events.size()>48)events.removeFirst();}
    Battle snapshot(){
        var states=new ArrayList<AircraftDamage>();for(Pilot p:pilots.values())states.add(new AircraftDamage(p.id,p.damage.integrity(),p.damage.engineFire(true),p.damage.engineFire(false),p.damage.fuel(),p.systems.engineSelected(true),p.systems.engineSelected(false),p.systems.radarSelected(),p.systems.armSelected(),p.systems.flares(),p.systems.fireBottle(),p.ammunition,p.missiles,p.lock));
        var missiles=new ArrayList<Projectile>();for(Shot s:shots)if(s.missile)missiles.add(new Projectile(s.id,s.owner.id,s.position.x+originX,s.position.y,s.position.z+originZ,s.velocity.x,s.velocity.y,s.velocity.z));
        return new Battle(states,missiles,List.copyOf(events));
    }
}
