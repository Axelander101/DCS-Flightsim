package alxb.flighttraining.game.weapons;

import com.jme3.collision.CollisionResult;
import com.jme3.collision.CollisionResults;
import com.jme3.math.Ray;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;

public final class Bullet {
    private final Vector3f position;
    private final Vector3f velocity;
    private float life = 3f;

    public Bullet(Vector3f position, Vector3f velocity) {
        this.position = position.clone();
        this.velocity = velocity.clone();
    }

    public void rebase(Vector3f delta){position.subtractLocal(delta);}

    private static void collide(com.jme3.scene.Spatial spatial,Ray ray,CollisionResults results){
        if(spatial instanceof com.jme3.effect.ParticleEmitter)return;
        if(spatial instanceof Node node){for(var child:node.getChildren())collide(child,ray,results);}
        else if(spatial instanceof com.jme3.scene.Geometry g&&g.getMesh().getMode()==com.jme3.scene.Mesh.Mode.Triangles&&g.getMesh().getTriangleCount()>0){
            String name=g.getName()==null?"":g.getName().toLowerCase(java.util.Locale.ROOT);if(!name.contains("transonic")&&!name.contains("afterburner core"))g.collideWith(ray,results);
        }
    }
    public boolean update(float tpf,Node targets){return update(tpf,targets,p->{});}
    public boolean update(float tpf, Node targets, java.util.function.Consumer<Vector3f> hitListener) {
        if (tpf <= 0f) return false;
        life -= tpf;

        if (life <= 0f) return true;
        velocity.y-=9.80665f*tpf;
        Vector3f movement = velocity.mult(tpf);
        float distance = movement.length();

        if (distance > 0f) {
            Ray ray = new Ray(position.clone(), movement.normalize());
            ray.setLimit(distance);
            CollisionResults results = new CollisionResults();
            collide(targets,ray,results);

            for (CollisionResult hit : results) {
                if (hit.getDistance() <= distance) {
                    var aircraft=alxb.flighttraining.game.combat.DamageableAircraft.find(hit.getGeometry());
                    if(aircraft!=null)aircraft.hit(hit.getContactPoint(),alxb.flighttraining.game.combat.AirframeDamage.Weapon.CANNON,1);
                    hitListener.accept(hit.getContactPoint().clone());
                    return true;
                }
            }
        }
        position.addLocal(movement);
        return false;
    }
}
