package alxb.flighttraining.game.combat;

import static alxb.flighttraining.game.combat.AirframeDamage.Part.*;
import java.util.*;

/** Hornet-inspired independent engines, redundant utilities, master-arm, radar and fire procedure.
 * Component severities and reversion gains are game approximations, not certified aircraft data. */
public final class HornetCombatSystems {
    public static final int RADAR=1<<10,MASTER_ARM=1<<11,NEXT_TARGET=1<<12,LOCK_TARGET=1<<13,
        COUNTERMEASURES=1<<14,FIRE_LEFT=1<<15,FIRE_RIGHT=1<<16,DISCHARGE=1<<17,
        ENGINE_LEFT=1<<18,ENGINE_RIGHT=1<<19,DAMAGE_PAGE=1<<20;
    private final AirframeDamage damage;
    private boolean left=true,right=true,radar=true,arm,damagePage;
    private boolean lockRequest,nextRequest,dispense;
    private int fireSelection=-1,flareCount=30,chaffCount=30;private boolean bottle=true;
    private float hyd1=1,hyd2=1;
    public HornetCombatSystems(AirframeDamage damage){this.damage=damage;}
    public void reset(){left=right=radar=true;arm=damagePage=false;fireSelection=-1;bottle=true;flareCount=chaffCount=30;hyd1=hyd2=1;lockRequest=nextRequest=dispense=false;}
    public void command(int value){
        if((value&ENGINE_LEFT)!=0)left=!left;if((value&ENGINE_RIGHT)!=0)right=!right;
        if((value&MASTER_ARM)!=0)arm=!arm;if((value&RADAR)!=0)radar=!radar;
        if((value&DAMAGE_PAGE)!=0)damagePage=!damagePage;
        if((value&NEXT_TARGET)!=0)nextRequest=true;if((value&LOCK_TARGET)!=0)lockRequest=true;
        if((value&COUNTERMEASURES)!=0)dispense=true;
        // FIRE isolates the engine before the separately armed extinguisher can discharge.
        if((value&FIRE_LEFT)!=0){left=false;fireSelection=0;}
        if((value&FIRE_RIGHT)!=0){right=false;fireSelection=1;}
        if((value&DISCHARGE)!=0&&fireSelection>=0&&bottle){damage.extinguish(fireSelection==0);bottle=false;}
    }
    public boolean engineSelected(boolean leftSide){return leftSide?left:right;}
    public boolean armSelected(){return arm;}
    public void accept(boolean l,boolean r,boolean radarOn,boolean armed,int flaresRemaining,boolean bottleRemaining){
        left=l;right=r;radar=radarOn;arm=armed;flareCount=Math.min(flareCount,Math.max(0,Math.min(30,flaresRemaining)));bottle=bottle&&bottleRemaining;
    }
    public void setBothEngines(boolean enabled){left=right=enabled;}
    public boolean engineRunning(boolean leftSide){return !damage.destroyed()&&damage.fuel()>0&&(leftSide?left:right)&&damage.health(leftSide?LEFT_ENGINE:RIGHT_ENGINE)>.08f;}
    public float engineEffectiveness(boolean leftSide){return engineRunning(leftSide)?damage.health(leftSide?LEFT_ENGINE:RIGHT_ENGINE):0;}
    public void update(float dt){
        boolean l=engineRunning(true),r=engineRunning(false);damage.update(dt,l,r);
        float target1=l?damage.health(HYD_1):0,target2=r?damage.health(HYD_2):0;
        hyd1+=(target1-hyd1)*(1-(float)Math.exp(-dt*2));hyd2+=(target2-hyd2)*(1-(float)Math.exp(-dt*2));
    }
    public float hydraulic(int circuit){return circuit==1?hyd1:hyd2;}
    public float controlHydraulics(){return Math.min(1,Math.max(hyd1,hyd2));}
    public boolean generator(boolean leftSide){return engineRunning(leftSide)&&damage.health(leftSide?GEN_LEFT:GEN_RIGHT)>.25f;}
    public boolean electrical(){return generator(true)||generator(false);}
    public boolean radarAvailable(){return radar&&electrical()&&damage.health(AirframeDamage.Part.RADAR)>.25f;}
    public boolean radarSelected(){return radar;}public boolean masterArm(){return arm&&electrical()&&!damage.destroyed()&&damage.health(WEAPONS)>.25f;}
    public boolean damagePage(){return damagePage;}public void setMasterArm(boolean value){arm=value;}
    public boolean fireBottle(){return bottle;}public int fireSelection(){return fireSelection;}
    public int flares(){return flareCount;}public int chaff(){return chaffCount;}
    public boolean takeNextTarget(){boolean v=nextRequest;nextRequest=false;return v;}public boolean takeLockRequest(){boolean v=lockRequest;lockRequest=false;return v;}
    public boolean takeCountermeasures(){boolean request=dispense;dispense=false;if(!request||!electrical()||flareCount<2)return false;flareCount-=2;return true;}
    public List<String> cautions(){var result=new ArrayList<>(damage.cautions());if(hyd1<.45f)result.add("HYD 1 PRESS");if(hyd2<.45f)result.add("HYD 2 PRESS");if(!generator(true))result.add("L GEN");if(!generator(false))result.add("R GEN");return List.copyOf(result);}
}
