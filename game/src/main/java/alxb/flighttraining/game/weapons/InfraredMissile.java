package alxb.flighttraining.game.weapons;

import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.scene.Spatial;

/** Simplified game flight: release, boost, then bounded pursuit. */
public final class InfraredMissile {
    private static final float RELEASE_TIME = 0.40f;
    private static final float GUIDANCE_TIME = 0.95f;
    private static final float MOTOR_END = 3.8f;
    private static final float LIFE = 14f;
    private static final float MAX_STEP = 1f / 120f;
    private final Spatial model;
    private Spatial target;
    private alxb.flighttraining.game.combat.CombatWorld combatWorld;
    private alxb.flighttraining.game.combat.DamageableAircraft owner;
    private final Vector3f position;
    private final Vector3f direction;
    private final Vector3f velocity;
    private final Vector3f up;
    private float age;
    private boolean tracking = true;
    private boolean cancelled;

    public InfraredMissile(Spatial model, Spatial target, Vector3f position,
                           Vector3f direction, Vector3f aircraftVelocity, Vector3f aircraftUp) {
        this.model = model;
        this.target = target;
        this.position = position.clone();
        this.direction = direction.normalize();
        this.velocity = aircraftVelocity.subtract(aircraftUp.mult(1.5f));
        this.up = aircraftUp.normalize();
        updateModel();
    }

    public InfraredMissile(Spatial model,Spatial target,Vector3f position,Vector3f direction,Vector3f velocity,Vector3f up,
            alxb.flighttraining.game.combat.CombatWorld world,alxb.flighttraining.game.combat.DamageableAircraft owner){
        this(model,target,position,direction,velocity,up);this.combatWorld=world;this.owner=owner;
    }
    public Spatial getTarget(){return target;}
    public Vector3f getPosition(){return position.clone();}
    public boolean isTracking(){return tracking;}
    private float guidanceStart(){return combatWorld==null?GUIDANCE_TIME:.55f;}
    private void decoys(){
        if(combatWorld==null||!tracking||target==null||age<guidanceStart())return;
        var body=alxb.flighttraining.game.combat.DamageableAircraft.find(target);float heat=body==null?4:Math.max(.1f,body.physics().getEnginePower())+(body.damage().engineFire(true)||body.damage().engineFire(false)?2:0);
        float score=heat/Math.max(1,target.getWorldTranslation().distanceSquared(position));
        for(var flare:combatWorld.flares()){Vector3f offset=flare.node().getWorldTranslation().subtract(position);float distance=offset.lengthSquared();if(distance<1||direction.dot(offset.normalize())<.94f)continue;float other=flare.brightness()/distance;if(other>score*1.5f){target=flare.node();score=other;}}
    }

    public boolean update(float tpf) {
        if (cancelled) return true;
        if (!Float.isFinite(tpf) || tpf <= 0f) return false;
        float remaining = Math.min(combatWorld==null?tpf:Math.min(tpf,.25f), LIFE - age);
        float frame=remaining,elapsed=0;decoys();
        while (remaining > 0.000001f) {
            float dt = Math.min(remaining, MAX_STEP);
            Vector3f previous=position.clone();step(dt);
            if(combatWorld!=null&&age>.65f){var hit=combatWorld.sweep(owner,previous,position,elapsed/frame,(elapsed+dt)/frame);if(hit!=null){position.set(hit.position());combatWorld.detonate(hit);cancel();return true;}}
            elapsed+=dt;remaining -= dt;
        }
        if (age >= LIFE - 0.00001f) {
            cancel();
            return true;
        }
        updateModel();
        return false;
    }

    private void step(float dt) {
        age += dt;
        if (age < RELEASE_TIME) {
            velocity.y -= 9.81f * dt;
        } else {
            float speed = velocity.length();
            if (speed > 1f) direction.set(velocity).normalizeLocal();
            if (age >= guidanceStart() && tracking && target != null) {
                if (target.getParent() == null) {
                    tracking = false;
                } else {
                    Vector3f desired = target.getWorldTranslation().subtract(position);
                    if(combatWorld!=null){var body=alxb.flighttraining.game.combat.DamageableAircraft.find(target);float time=Math.min(2,desired.length()/Math.max(100,speed));if(body!=null)desired.addLocal(body.physics().getVelocity().mult(time*.8f));desired.y+=Math.min(12,4.903325f*time*time);}
                    if (desired.lengthSquared() < 1f) {
                        tracking = false;
                    } else {
                        desired.normalizeLocal();
                        // Passed targets are lost permanently, preventing rearward U-turns.
                        if (direction.dot(desired) < 0.34f) tracking = false;
                        else steer(desired, speed, dt);
                    }
                }
            }
            float ramp = Math.min(1f, (age - RELEASE_TIME) / 0.45f);
            float acceleration = age < MOTOR_END ? 105f * ramp : -5f;
            speed = Math.max(1f, Math.min(600f, speed + acceleration * dt));
            velocity.set(direction).multLocal(speed);
            velocity.y -= 9.81f * dt;
            if (velocity.lengthSquared() > 1f) direction.set(velocity).normalizeLocal();
        }
        position.addLocal(velocity.mult(dt));
    }

    private void steer(Vector3f desired, float speed, float dt) {
        float angle = (float) Math.acos(Math.max(-1f, Math.min(1f, direction.dot(desired))));
        Vector3f axis = direction.cross(desired);
        if (angle < 0.00001f || axis.lengthSquared() < 0.0000001f) return;
        // Game curvature limit: high speed means wider turns, never an instant reversal.
        float rate = combatWorld==null?Math.min(.65f,75f/Math.max(speed,30f)):Math.min(1.2f,220f/Math.max(speed,30f));
        float ramp = Math.min(1f, (age - guidanceStart()) / (combatWorld==null?.6f:.25f));
        new Quaternion().fromAngleAxis(Math.min(angle, rate * ramp * dt), axis.normalizeLocal())
                .mult(direction, direction);
        direction.normalizeLocal();
    }

    private void updateModel() {
        model.setLocalTranslation(position);
        Vector3f referenceUp = Math.abs(direction.dot(up)) > 0.99f ? Vector3f.UNIT_X : up;
        model.setLocalRotation(new Quaternion().lookAt(direction, referenceUp));
    }


    public void rebase(Vector3f delta) {
        position.subtractLocal(delta);
        model.setLocalTranslation(position);
    }

    public Spatial getModel() { return model; }

    public void cancel() {
        cancelled = true;
        model.removeFromParent();
    }
}
