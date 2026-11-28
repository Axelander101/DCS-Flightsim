package alxb.flighttraining.game.combat;

import alxb.flighttraining.game.flight.FlightPhysics;
import com.jme3.effect.ParticleEmitter;
import com.jme3.math.*;
import com.jme3.scene.*;
import com.jme3.scene.control.AbstractControl;
import com.jme3.scene.mesh.IndexBuffer;
import com.jme3.util.BufferUtils;
import java.nio.FloatBuffer;
import java.util.*;

/** Removes only triangles in a destroyed structural region, including the asset's combined shell. */
public final class AirframeDamageVisuals extends AbstractControl {
    private record Surface(Geometry geometry,Mesh original,int[] triangles,byte[] zones,Spatial.CullHint cull){}
    private final Node plane;private final FlightPhysics physics;private final DamageEffects effects;
    private final List<Surface> surfaces=new ArrayList<>();private final ParticleEmitter[] smoke=new ParticleEmitter[2],flame=new ParticleEmitter[2];
    private long missingMask;private boolean wasDestroyed;
    AirframeDamageVisuals(Node plane,FlightPhysics physics,DamageEffects effects){
        this.plane=plane;this.physics=physics;this.effects=effects;Spatial scene=plane;while(scene.getParent()!=null)scene=scene.getParent();scene.updateGeometricState();
        plane.depthFirstTraversal(s->{if(!(s instanceof Geometry g)||s instanceof ParticleEmitter||g.getMesh().getMode()!=Mesh.Mode.Triangles)return;
            if(g.getName()!=null&&g.getName().toLowerCase(java.util.Locale.ROOT).contains("transonic"))return;
            Mesh mesh=g.getMesh();FloatBuffer vertices=mesh.getFloatBuffer(VertexBuffer.Type.Position);IndexBuffer indices=mesh.getIndexBuffer();if(vertices==null||indices==null)return;
            int[] triangles=new int[indices.size()];for(int i=0;i<triangles.length;i++)triangles[i]=indices.get(i);byte[] zones=new byte[triangles.length/3];Arrays.fill(zones,(byte)-1);
            for(int t=0;t<zones.length;t++){Vector3f centroid=new Vector3f();for(int k=0;k<3;k++){int offset=triangles[t*3+k]*3;centroid.addLocal(vertices.get(offset),vertices.get(offset+1),vertices.get(offset+2));}centroid.divideLocal(3);centroid=plane.worldToLocal(g.localToWorld(centroid,new Vector3f()),new Vector3f());zones[t]=(byte)zone(centroid);}
            surfaces.add(new Surface(g,mesh,triangles,zones,g.getLocalCullHint()));
        });
        for(int i=0;i<2;i++){Vector3f position=new Vector3f(i==0?-.4f:.4f,-.1f,3.8f);smoke[i]=effects.emitter("Engine damage smoke "+i,plane,position,false);flame[i]=effects.emitter("Engine fire "+i,plane,position,true);}
    }
    private static int zone(Vector3f p){
        if(p.y>.5f&&p.z>2.4f)return (p.x<0?AirframeDamage.Part.LEFT_FIN:AirframeDamage.Part.RIGHT_FIN).ordinal();
        if(Math.abs(p.x)>.65f&&p.z>2.65f&&p.y<.5f)return(p.x<0?AirframeDamage.Part.LEFT_TAIL:AirframeDamage.Part.RIGHT_TAIL).ordinal();
        if(Math.abs(p.x)>.85f&&p.z>-.9f&&p.z<2.2f)return(p.x<0?AirframeDamage.Part.LEFT_WING:AirframeDamage.Part.RIGHT_WING).ordinal();
        return -1;
    }
    protected void controlUpdate(float dt){
        AirframeDamage damage=physics.getDamage();if(damage.destroyed()&&!wasDestroyed)effects.explosion(plane.getWorldTranslation());wasDestroyed=damage.destroyed();long next=0;for(var part:AirframeDamage.Part.values())if(part.ordinal()<=AirframeDamage.Part.RIGHT_FIN.ordinal()&&damage.health(part)<.08f)next|=1L<<part.ordinal();
        if(next!=missingMask){long detached=next&~missingMask;for(var surface:surfaces)rebuild(surface,next,detached);missingMask=next;}
        for(int i=0;i<2;i++){
            boolean fire=damage.engineFire(i==0)&&!damage.destroyed();float h=damage.health(i==0?AirframeDamage.Part.LEFT_ENGINE:AirframeDamage.Part.RIGHT_ENGINE);
            smoke[i].setParticlesPerSec(fire?40:h<.6f?18:0);flame[i].setParticlesPerSec(fire?35:0);
        }
    }
    private void rebuild(Surface surface,long mask,long detached){
        int[] kept=new int[surface.triangles().length],removed=new int[kept.length];int kc=0,rc=0;
        for(int t=0;t<surface.zones().length;t++){int part=surface.zones()[t];boolean missing=part>=0&&(mask&(1L<<part))!=0,justDetached=part>=0&&(detached&(1L<<part))!=0;
            for(int k=0;k<3;k++){int index=surface.triangles()[t*3+k];if(!missing)kept[kc++]=index;if(justDetached)removed[rc++]=index;}}
        if(kc==surface.triangles().length){surface.geometry().setMesh(surface.original());surface.geometry().setCullHint(surface.cull());return;}
        if(rc>0){Mesh mesh=subset(surface.original(),removed,rc);for(var type:new VertexBuffer.Type[]{VertexBuffer.Type.BoneIndex,VertexBuffer.Type.BoneWeight,VertexBuffer.Type.HWBoneIndex,VertexBuffer.Type.HWBoneWeight})mesh.clearBuffer(type);
            Geometry debris=new Geometry(surface.geometry().getName(),mesh);var material=surface.geometry().getMaterial().clone();if(material.getParam("NumberOfBones")!=null)material.clearParam("NumberOfBones");if(material.getParam("BoneMatrices")!=null)material.clearParam("BoneMatrices");debris.setMaterial(material);debris.setLocalTransform(surface.geometry().getWorldTransform().clone());effects.debris(debris,physics.getVelocity());}
        if(kc==0){surface.geometry().setMesh(subset(surface.original(),kept,0));surface.geometry().setCullHint(Spatial.CullHint.Always);return;}
        surface.geometry().setMesh(subset(surface.original(),kept,kc));surface.geometry().setCullHint(surface.cull());
    }
    private static Mesh subset(Mesh original,int[] indices,int count){Mesh mesh=original.clone();mesh.clearBuffer(VertexBuffer.Type.Index);mesh.setBuffer(VertexBuffer.Type.Index,3,BufferUtils.createIntBuffer(Arrays.copyOf(indices,count)));mesh.clearCollisionData();mesh.updateBound();return mesh;}
    protected void controlRender(com.jme3.renderer.RenderManager manager,com.jme3.renderer.ViewPort viewport){}
}
