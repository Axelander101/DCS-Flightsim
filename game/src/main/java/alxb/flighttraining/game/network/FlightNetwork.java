package alxb.flighttraining.game.network;

import alxb.flighttraining.game.config.DogfightSettings.Region;
import java.util.List;
import java.util.Set;

/** Transport-independent immutable snapshots. Positions are absolute game coordinates, altitude is MSL. */
public final class FlightNetwork {
    public static final int DEFAULT_PORT=28770, DISCOVERY_PORT=28771, VERSION=2;
    public enum Phase { LOBBY, LOADING, FLYING }
    public record Config(String title,Region region,int maximum,boolean discoverable,boolean combat) {
        public Config(String title,Region region,int maximum,boolean discoverable){this(title,region,maximum,discoverable,false);}
        public Config {title=Wire.text(title,40);if(region==null||maximum<2||maximum>8)throw new IllegalArgumentException("Choose 2–8 players and an airfield.");}
    }
    public record Player(String id,String name,int slot,boolean host,boolean ready,boolean loaded) {}
    public record Lobby(String title,Region region,int maximum,boolean locked,Phase phase,long flightId,List<Player> players,boolean combat) {
        public Lobby(String title,Region region,int maximum,boolean locked,Phase phase,long flightId,List<Player> players){this(title,region,maximum,locked,phase,flightId,players,false);}
        public boolean allReady(){return players.size()>=2&&players.stream().allMatch(Player::ready);}
    }
    public record Pose(String id,long sequence,double x,double y,double z,float qx,float qy,float qz,float qw,
                       float vx,float vy,float vz,float throttle,float gear) {
        public Pose {
            if(id==null||id.length()>40||sequence<0||!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z)
                ||Math.abs(x)>2_000_000||Math.abs(z)>2_000_000||y < -1500||y>30000)throw new IllegalArgumentException("Invalid aircraft position");
            float norm=qx*qx+qy*qy+qz*qz+qw*qw;
            if(!Float.isFinite(norm)||norm<.9f||norm>1.1f||!Float.isFinite(vx)||!Float.isFinite(vy)||!Float.isFinite(vz)
                ||(double)vx*vx+(double)vy*vy+(double)vz*vz>2500.0*2500||!Float.isFinite(throttle)||throttle<0||throttle>1||!Float.isFinite(gear)||gear<0||gear>1)
                throw new IllegalArgumentException("Invalid aircraft state");
        }
        public Pose identify(String player){return new Pose(player,sequence,x,y,z,qx,qy,qz,qw,vx,vy,vz,throttle,gear);}
    }
    public record AircraftDamage(String id,int[] integrity,boolean fireLeft,boolean fireRight,float fuel,
        boolean engineLeft,boolean engineRight,boolean radar,boolean armed,int flares,boolean bottle,int ammunition,int missiles,String radarLock) {
        public AircraftDamage {
            if(id==null||id.length()>40||integrity==null||integrity.length!=alxb.flighttraining.game.combat.AirframeDamage.Part.values().length
                ||!Float.isFinite(fuel)||fuel<0||fuel>1||flares<0||flares>30||ammunition<0||ammunition>500||missiles<0||missiles>4||radarLock==null||radarLock.length()>40)throw new IllegalArgumentException("Invalid combat state");
            integrity=integrity.clone();for(int v:integrity)if(v<0||v>10000)throw new IllegalArgumentException("Invalid damage");
        }
        @Override public int[] integrity(){return integrity.clone();}
    }
    public record Projectile(long id,String owner,double x,double y,double z,float vx,float vy,float vz) {
        public Projectile {if(id<1||owner==null||owner.length()>40||!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z)||Math.abs(x)>2100000||Math.abs(z)>2100000||Math.abs(y)>100000||!Float.isFinite(vx)||!Float.isFinite(vy)||!Float.isFinite(vz)||(double)vx*vx+(double)vy*vy+(double)vz*vz>4000000)throw new IllegalArgumentException("Invalid projectile");}
    }
    public record CombatEvent(long id,String kind,String owner,double x,double y,double z) {
        public CombatEvent {if(id<1||!Set.of("launch","impact","flare","gun").contains(kind)||owner==null||owner.length()>40||!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z)||Math.abs(x)>2100000||Math.abs(z)>2100000||Math.abs(y)>100000)throw new IllegalArgumentException("Invalid combat event");}
    }
    public record Battle(List<AircraftDamage> aircraft,List<Projectile> missiles,List<CombatEvent> events) {
        public Battle {if(aircraft==null||missiles==null||events==null||aircraft.size()>8||missiles.size()>32||events.size()>48)throw new IllegalArgumentException("Invalid battle snapshot");aircraft=List.copyOf(aircraft);missiles=List.copyOf(missiles);events=List.copyOf(events);}
        public static Battle empty(){return new Battle(List.of(),List.of(),List.of());}
    }
    public record Snapshot(Lobby lobby,List<Pose> poses,long receivedNanos,Battle battle) {
        public Snapshot(Lobby lobby,List<Pose> poses,long receivedNanos){this(lobby,poses,receivedNanos,Battle.empty());}
    }
    private FlightNetwork(){}
}
