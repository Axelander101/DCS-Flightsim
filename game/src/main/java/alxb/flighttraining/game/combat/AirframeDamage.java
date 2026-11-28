package alxb.flighttraining.game.combat;

import com.jme3.math.Vector3f;
import java.util.*;

/** Location-dependent game damage, in the existing aircraft asset's half-scale body coordinates. */
public final class AirframeDamage {
    public enum Part {
        LEFT_WING("L WING",-1.7f,0,.55f,1.12f,.28f,1.30f),RIGHT_WING("R WING",1.7f,0,.55f,1.12f,.28f,1.30f),
        LEFT_TAIL("L STAB",-1.0f,.03f,3.65f,.75f,.23f,.7f),RIGHT_TAIL("R STAB",1.0f,.03f,3.65f,.75f,.23f,.7f),
        LEFT_FIN("L FIN",-.65f,.95f,3.35f,.4f,.7f,.65f),RIGHT_FIN("R FIN",.65f,.95f,3.35f,.4f,.7f,.65f),
        LEFT_ENGINE("L ENG",-.4f,-.12f,2.9f,.32f,.4f,1.45f),RIGHT_ENGINE("R ENG",.4f,-.12f,2.9f,.32f,.4f,1.45f),
        FUSELAGE("FUSELAGE",0,-.03f,.25f,.63f,.55f,2.6f),COCKPIT("COCKPIT",0,.35f,-1.9f,.4f,.5f,.8f),
        RADAR("RADAR",0,0,-3.7f,.32f,.35f,.7f),AIR_DATA("AIR DATA",.1f,.05f,-3.15f,.24f,.2f,.32f),
        HYD_1("HYD 1",-.35f,-.15f,1.65f,.25f,.25f,.35f),HYD_2("HYD 2",.35f,-.15f,1.65f,.25f,.25f,.35f),
        GEN_LEFT("L GEN",-.4f,-.1f,2.1f,.2f,.2f,.3f),GEN_RIGHT("R GEN",.4f,-.1f,2.1f,.2f,.2f,.3f),
        FCS("FCS",0,.13f,-.8f,.3f,.25f,.4f),FUEL_LEFT("L FUEL",-.5f,0,.7f,.3f,.3f,.7f),FUEL_RIGHT("R FUEL",.5f,0,.7f,.3f,.3f,.7f),
        INS("INS",0,.18f,-1.25f,.25f,.22f,.3f),WEAPONS("SMS",0,-.05f,-1.8f,.25f,.3f,.45f),GEAR("GEAR",0,-.5f,.2f,.75f,.3f,1.5f);
        public final String label;private final Vector3f center,extent;
        Part(String label,float x,float y,float z,float rx,float ry,float rz){this.label=label;center=new Vector3f(x,y,z);extent=new Vector3f(rx,ry,rz);}
        public Vector3f center(){return center.clone();}public Vector3f extent(){return extent.clone();}
        public boolean structural(){return ordinal()<=COCKPIT.ordinal();}
    }
    public enum Weapon { CANNON,MISSILE,CRASH }
    private final float[] health=new float[Part.values().length];
    private final boolean[] burning=new boolean[2];
    private float fuel=1;private long revision;
    public AirframeDamage(){reset();}
    public void reset(){Arrays.fill(health,1);Arrays.fill(burning,false);fuel=1;revision++;}
    public int[] integrity(){int[] result=new int[health.length];for(int i=0;i<result.length;i++)result[i]=Math.round(health[i]*10000);return result;}
    /** Only validated host snapshots enter here; repairs require an explicit flight reset. */
    public void accept(int[] integrity,boolean fireLeft,boolean fireRight,float fuelRemaining){
        if(integrity==null||integrity.length!=health.length||!Float.isFinite(fuelRemaining)||fuelRemaining<0||fuelRemaining>1)throw new IllegalArgumentException("Invalid damage state");
        for(int value:integrity)if(value<0||value>10000)throw new IllegalArgumentException("Invalid component integrity");
        for(int i=0;i<health.length;i++)health[i]=Math.min(health[i],integrity[i]/10000f);
        burning[0]=fireLeft;burning[1]=fireRight;fuel=Math.min(fuel,fuelRemaining);revision++;
    }
    public long revision(){return revision;}public float health(Part part){return health[part.ordinal()];}
    public boolean destroyed(){return health(Part.FUSELAGE)<.08f||health(Part.COCKPIT)<.08f;}
    public boolean engineFire(boolean left){return burning[left?0:1];}public float fuel(){return fuel;}
    public void extinguish(boolean left){if(burning[left?0:1]){burning[left?0:1]=false;revision++;}}
    private void reduce(Part part,float amount){if(!Float.isFinite(amount)||amount<=0)return;float old=health(part);health[part.ordinal()]=Math.max(0,old-amount);if(old!=health(part))revision++;}
    public Part nearest(Vector3f point){Part best=Part.FUSELAGE;float minimum=Float.POSITIVE_INFINITY;for(Part p:Part.values())if(p.structural()||p==Part.RADAR){float d=point.distanceSquared(p.center);if(d<minimum){minimum=d;best=p;}}return best;}
    public void hit(Vector3f point,Weapon weapon,float strength){
        if(point==null||(!Float.isFinite(point.x)||!Float.isFinite(point.y)||!Float.isFinite(point.z))||!Float.isFinite(strength)||strength<=0||destroyed())return;
        strength=Math.min(strength,3);Part direct=nearest(point);
        if(weapon==Weapon.CRASH){for(Part p:Part.values())reduce(p,strength);return;}
        if(weapon==Weapon.CANNON){
            reduce(direct,.17f*strength);
            for(Part p:Part.values())if(!p.structural()&&p.center.distance(point)<.6f)reduce(p,.30f*strength);
            if(direct==Part.LEFT_ENGINE||direct==Part.RIGHT_ENGINE){boolean left=direct==Part.LEFT_ENGINE;if(health(direct)<.6f)burning[left?0:1]=true;}
        }else{
            // A continuous falloff damages nearby equipment rather than subtracting global hit points.
            for(Part p:Part.values()){
                float distance=Math.max(0,point.distance(p.center)-p.extent.length()*.3f);
                float amount=1.25f*strength*(float)Math.exp(-distance*distance/(2*.95f*.95f));
                if(p==Part.FUSELAGE)amount*=.48f;
                reduce(p,amount);
            }
            reduce(direct,.55f*strength);
            if(health(Part.LEFT_ENGINE)<.55f&&point.distance(Part.LEFT_ENGINE.center)<2)burning[0]=true;
            if(health(Part.RIGHT_ENGINE)<.55f&&point.distance(Part.RIGHT_ENGINE.center)<2)burning[1]=true;
        }
    }
    public void update(float dt,boolean leftRunning,boolean rightRunning){
        if(!Float.isFinite(dt)||dt<=0)return;dt=Math.min(dt,.25f);
        fuel=Math.max(0,fuel-dt*((leftRunning||rightRunning)?.000035f:0)-dt*(2-health(Part.FUEL_LEFT)-health(Part.FUEL_RIGHT))*.0015f);
        for(int i=0;i<2;i++)if(burning[i]){
            boolean running=i==0?leftRunning:rightRunning;
            reduce(i==0?Part.LEFT_ENGINE:Part.RIGHT_ENGINE,dt*(running?.018f:.006f));
            reduce(i==0?Part.HYD_1:Part.HYD_2,dt*.008f);reduce(i==0?Part.GEN_LEFT:Part.GEN_RIGHT,dt*.008f);
            reduce(Part.FUSELAGE,dt*.0015f);
        }
    }
    public float liftFraction(boolean left){float h=health(left?Part.LEFT_WING:Part.RIGHT_WING);return h<.08f?0:.12f+.88f*h;}
    public float tailAuthority(){return (health(Part.LEFT_TAIL)+health(Part.RIGHT_TAIL))*.5f;}
    public float finAuthority(){return (health(Part.LEFT_FIN)+health(Part.RIGHT_FIN))*.5f;}
    public float extraDrag(){return .035f*(2-health(Part.LEFT_WING)-health(Part.RIGHT_WING))+.025f*(1-health(Part.FUSELAGE))+.012f*(2-health(Part.LEFT_TAIL)-health(Part.RIGHT_TAIL));}
    public List<String> cautions(){
        var result=new ArrayList<String>();
        if(destroyed())result.add("AIRFRAME LOST");
        if(burning[0])result.add("L ENGINE FIRE");if(burning[1])result.add("R ENGINE FIRE");
        for(Part p:Part.values())if(health(p)<.5f)result.add(p.label+(health(p)<.08f?" FAIL":" DEGRADED"));
        if(fuel<.15f)result.add("FUEL LOW");if(health(Part.FUEL_LEFT)<.5f||health(Part.FUEL_RIGHT)<.5f)result.add("FUEL LEAK");
        return List.copyOf(result);
    }
}
