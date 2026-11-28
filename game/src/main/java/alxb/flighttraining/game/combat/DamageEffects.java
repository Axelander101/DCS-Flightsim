package alxb.flighttraining.game.combat;

import com.jme3.asset.AssetManager;
import com.jme3.effect.*;
import com.jme3.material.Material;
import com.jme3.math.*;
import com.jme3.scene.*;
import java.util.*;

/** Bounded transient effects. Existing game particle textures and real aircraft mesh fragments. */
final class DamageEffects {
    private final AssetManager assets;private final Node root=new Node("Combat effects");
    private record Debris(Node node,Vector3f velocity,Vector3f spin,float[] age){}
    private record Burst(ParticleEmitter particles,float[] age){}
    private final List<Debris> debris=new ArrayList<>();private final List<Burst> bursts=new ArrayList<>();
    DamageEffects(AssetManager assets,Node parent){this.assets=assets;parent.attachChild(root);}
    AssetManager assets(){return assets;}
    ParticleEmitter emitter(String name,Node parent,Vector3f local,boolean flame){
        var e=new ParticleEmitter(name,ParticleMesh.Type.Triangle,flame?40:60);var material=new Material(assets,"Common/MatDefs/Misc/Particle.j3md");material.setTexture("Texture",assets.loadTexture(flame?"Textures/Particles/spark.png":"Textures/Particles/vapor.png"));e.setMaterial(material);e.setInWorldSpace(false);e.setParticlesPerSec(0);e.setGravity(0,-.3f,0);e.setLowLife(flame?.15f:.7f);e.setHighLife(flame?.5f:1.6f);e.setStartSize(flame?.18f:.25f);e.setEndSize(flame?.45f:1.5f);e.setStartColor(flame?new ColorRGBA(1,.5f,.08f,.8f):new ColorRGBA(.12f,.13f,.14f,.6f));e.setEndColor(flame?new ColorRGBA(.7f,.08f,0,0):new ColorRGBA(.12f,.13f,.14f,0));e.getParticleInfluencer().setInitialVelocity(new Vector3f(0,.4f,flame?3:4));e.getParticleInfluencer().setVelocityVariation(.35f);e.setLocalTranslation(local);parent.attachChild(e);return e;
    }
    void explosion(Vector3f world){
        if(bursts.size()>=8){bursts.remove(0).particles().removeFromParent();}
        var e=emitter("Impact burst",root,world,true);e.setStartSize(.6f);e.setEndSize(2.5f);e.setLowLife(.2f);e.setHighLife(.65f);e.getParticleInfluencer().setInitialVelocity(new Vector3f(0,2,0));e.getParticleInfluencer().setVelocityVariation(1);e.emitAllParticles();bursts.add(new Burst(e,new float[]{0}));
    }
    Node flare(Vector3f world){Node n=new Node("Defensive flare");n.setLocalTranslation(world);root.attachChild(n);var e=emitter("Flare plume",n,Vector3f.ZERO,true);e.setParticlesPerSec(45);e.setStartSize(.13f);e.setEndSize(.3f);return n;}
    void debris(Geometry geometry,Vector3f velocity){
        if(debris.size()>=32){debris.remove(0).node().removeFromParent();}
        Node node=new Node("Detached "+geometry.getName());node.setLocalTranslation(geometry.getLocalTranslation());geometry.setLocalTranslation(0,0,0);root.attachChild(node);node.attachChild(geometry);debris.add(new Debris(node,velocity.add(new Vector3f(0,1,0)),new Vector3f(.3f,.7f,.5f),new float[]{0}));
    }
    void update(float dt){
        debris.removeIf(d->{d.age()[0]+=dt;if(d.age()[0]>12){d.node().removeFromParent();return true;}d.velocity().addLocal(0,-9.81f*dt,0).multLocal((float)Math.exp(-dt*.08f));d.node().move(d.velocity().mult(dt));d.node().rotate(d.spin().x*dt,d.spin().y*dt,d.spin().z*dt);return false;});
        bursts.removeIf(b->{b.age()[0]+=dt;if(b.age()[0]>2){b.particles().removeFromParent();return true;}return false;});
    }
    void rebase(Vector3f delta){root.setLocalTranslation(0,0,0);for(Spatial s:root.getChildren())s.move(delta.negate());}
    void clear(){root.detachAllChildren();root.setLocalTranslation(0,0,0);debris.clear();bursts.clear();}
}
