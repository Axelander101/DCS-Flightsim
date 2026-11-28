package alxb.flighttraining.game.weapons;

/** Finite training load. Unlimited mode pauses consumption; it never bypasses weapon safety. */
public final class GunAmmunition {
    public static final int CAPACITY=500;
    private int remaining=CAPACITY;
    public int remaining(){return remaining;}
    public void accept(int value){remaining=Math.min(remaining,Math.max(0,Math.min(CAPACITY,value)));}
    public void reset(){remaining=CAPACITY;}
    public boolean tryFire(boolean unlimited){if(unlimited)return true;if(remaining==0)return false;remaining--;return true;}
}
