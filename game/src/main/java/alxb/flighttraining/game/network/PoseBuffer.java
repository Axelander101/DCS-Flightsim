package alxb.flighttraining.game.network;

import alxb.flighttraining.game.network.FlightNetwork.Pose;
import com.jme3.math.Quaternion;
import java.util.ArrayDeque;

/** 100ms interpolation; extrapolate at most 150ms then hold, rather than running a stale aircraft forever. */
public final class PoseBuffer {
    private record Timed(Pose pose,long time){}
    private final ArrayDeque<Timed> states=new ArrayDeque<>();
    public void add(Pose pose,long arrival){
        Timed last=states.peekLast();if(last!=null&&pose.sequence()<=last.pose().sequence())return;
        if(last!=null&&(arrival-last.time()>2_000_000_000L||Math.hypot(pose.x()-last.pose().x(),pose.z()-last.pose().z())>5000))states.clear();
        states.addLast(new Timed(pose,arrival));while(states.size()>8)states.removeFirst();
    }
    public Pose sample(long now){
        if(states.isEmpty())return null;long target=now-100_000_000L;
        while(states.size()>2){var it=states.iterator();it.next();if(it.next().time()>=target)break;states.removeFirst();}
        Timed first=states.peekFirst(),last=states.peekLast();
        if(target<=first.time())return first.pose();
        if(target>=last.time()){
            Pose p=last.pose();double dt=Math.min(.15,(target-last.time())/1e9);
            return new Pose(p.id(),p.sequence(),Math.max(-2e6,Math.min(2e6,p.x()+p.vx()*dt)),Math.max(-1500,Math.min(30000,p.y()+p.vy()*dt)),Math.max(-2e6,Math.min(2e6,p.z()+p.vz()*dt)),p.qx(),p.qy(),p.qz(),p.qw(),p.vx(),p.vy(),p.vz(),p.throttle(),p.gear());
        }
        var it=states.iterator();Timed a=it.next(),b=it.next();while(b.time()<target&&it.hasNext()){a=b;b=it.next();}
        float t=(float)((target-a.time())/(double)Math.max(1,b.time()-a.time()));Pose p=a.pose(),q=b.pose();
        Quaternion rotation=new Quaternion(p.qx(),p.qy(),p.qz(),p.qw());rotation.slerp(new Quaternion(q.qx(),q.qy(),q.qz(),q.qw()),t);rotation.normalizeLocal();
        return new Pose(p.id(),q.sequence(),p.x()+(q.x()-p.x())*t,p.y()+(q.y()-p.y())*t,p.z()+(q.z()-p.z())*t,rotation.getX(),rotation.getY(),rotation.getZ(),rotation.getW(),p.vx()+(q.vx()-p.vx())*t,p.vy()+(q.vy()-p.vy())*t,p.vz()+(q.vz()-p.vz())*t,p.throttle()+(q.throttle()-p.throttle())*t,p.gear()+(q.gear()-p.gear())*t);
    }
}
