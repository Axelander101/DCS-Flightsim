package alxb.flighttraining.game.network;
import alxb.flighttraining.game.network.FlightNetwork.*;
import alxb.flighttraining.game.combat.*;
import alxb.flighttraining.game.config.DogfightSettings.Region;
import java.util.*;
public class NetworkCombatChecks {
 static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
 static Pose pose(String id,long seq,double x,double z){return new Pose(id,seq,x,1000,z,0,0,0,1,0,0,0,.7f,0);}
 static NetworkBattle battle(){NetworkBattle b=new NetworkBattle();b.retain(Set.of("one","two"));b.pose("one",pose("one",1,2670000-2000000,1250000-2000000));b.pose("two",pose("two",1,670000,-750800));b.tick(.05f);return b;}
 static void ticks(NetworkBattle b,int n){for(int i=0;i<n;i++){for(var p:b.pilots.values())b.pose(p.id,p.pose);b.tick(.05f);}}
 public static void main(String[] args)throws Exception{
  NetworkBattle b=battle();b.pose("two",pose("two",2,670000,-750300));b.intent("one",true,false,0,"",false);ticks(b,110);
  check(b.pilots.get("one").ammunition==0,"Host enforces 500 rounds");
  check(Arrays.stream(b.pilots.get("two").damage.integrity()).anyMatch(v->v<10000),"Host cannon sweep actually hits other aircraft");
  check(Arrays.stream(b.pilots.get("one").damage.integrity()).allMatch(v->v==10000),"Own projectiles do not hit owner");
  b=battle();ticks(b,15);b.intent("one",false,true,0,"",false);ticks(b,100);
  check(b.pilots.get("one").missiles==3,"One launch edge consumes one missile");
  check(b.snapshot().events().stream().anyMatch(e->e.kind().equals("impact")),"Host IR missile actually intercepts target");
  check(b.pilots.get("two").damage.health(AirframeDamage.Part.LEFT_ENGINE)<1,"Rear impact damages local engine");
  b=battle();for(int i=0;i<20;i++){b.intent("one",false,false,HornetCombatSystems.COUNTERMEASURES,"",false);ticks(b,1);}check(b.pilots.get("one").systems.flares()==0,"Flares finite in pairs");
  var a=b.pilots.get("one");a.damage.hit(AirframeDamage.Part.LEFT_ENGINE.center(),AirframeDamage.Weapon.MISSILE,1);
  b.intent("one",false,false,HornetCombatSystems.FIRE_LEFT,"",false);ticks(b,1);check(a.damage.engineFire(true),"FIRE isolates without magic extinguish");
  b.intent("one",false,false,HornetCombatSystems.DISCHARGE,"",false);ticks(b,1);check(!a.damage.engineFire(true)&&!a.systems.fireBottle(),"Host fire bottle consumed and fire extinguished");
  int stores=a.missiles;b.intent("one",false,true,HornetCombatSystems.MASTER_ARM,"",false);ticks(b,2);check(a.missiles==stores,"SAFE blocks weapon release");
  b=battle();b.intent("one",false,false,0,"two",false);ticks(b,15);check(b.pilots.get("one").lock.equals("two"),"Host validates radar dwell");
  b.intent("one",false,false,0,"one",false);ticks(b,1);check(b.pilots.get("one").lock.isEmpty(),"Invalid radar lock rejected");
  testTransport(false);testTransport(true);
  System.out.println("PASS host cannon and missile hits; local damage; finite stores; fire procedure; SAFE; radar dwell; real TCP combat and formation isolation");
 }
 static void await(java.util.function.BooleanSupplier condition,String message)throws Exception{long end=System.nanoTime()+5_000_000_000L;while(!condition.getAsBoolean()&&System.nanoTime()<end)Thread.sleep(20);check(condition.getAsBoolean(),message);}
 static void testTransport(boolean combat)throws Exception{
  List<String> errors=new java.util.concurrent.CopyOnWriteArrayList<>();
  try(var host=new FlightServer(new Config("Battle",Region.values()[0],2,false,combat),0,"");var a=new FlightClient(s->{},errors::add,errors::add);var c=new FlightClient(s->{},errors::add,errors::add)){
   a.connect("127.0.0.1",host.port(),"Alpha","",host.hostToken());c.connect("127.0.0.1",host.port(),"Bravo","","");
   await(()->a.snapshot()!=null&&a.snapshot().lobby().players().size()==2,"two peers admitted");a.ready(true);c.ready(true);await(()->a.snapshot().lobby().allReady(),"ready barrier");a.startFlight();await(()->a.snapshot().lobby().phase()==Phase.LOADING,"loading");a.loaded();c.loaded();await(()->a.snapshot().lobby().phase()==Phase.FLYING,"flight");
   a.pose(pose(a.id(),1,0,0));c.pose(pose(c.id(),1,0,-300));Thread.sleep(150);a.combat(true,false,0,"",false);Thread.sleep(400);
   await(()->combat?!a.snapshot().battle().aircraft().isEmpty():a.snapshot().poses().size()==2,"replicated battle or formation poses");
   if(combat){check(a.snapshot().battle().aircraft().stream().filter(d->d.id().equals(a.id())).findFirst().orElseThrow().ammunition()<500,"host ammo reaches clients");
    c.combat(false,false,0,"",true);await(()->a.snapshot().battle().aircraft().stream().filter(d->d.id().equals(c.id())).anyMatch(d->Arrays.stream(d.integrity()).allMatch(h->h==0)),"crash replicated");}
   else check(a.snapshot().battle().aircraft().isEmpty(),"Formation never enables damage combat");
   check(errors.isEmpty(),"No disconnect/errors: "+errors);
  }
 }
}
