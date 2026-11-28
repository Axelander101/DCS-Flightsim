package alxb.flighttraining.game.world;

import java.awt.Transparency;
import java.awt.color.ColorSpace;
import java.awt.image.*;
import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import javax.imageio.ImageIO;

/** Coarser real-world elevations for gaps outside swissALTI3D, reprojected to the existing grid. */
final class RegionalHeightFallback {
    private static final int ZOOM=12, PIXELS=256, WORLD_PIXELS=(1<<ZOOM)*PIXELS;
    private record Key(int x,int y) {}
    private final HttpClient client;
    // One shared request per source tile; bounded memory only, no second disk cache.
    private final Map<Key,CompletableFuture<BufferedImage>> images=new LinkedHashMap<>(64,.75f,true);
    RegionalHeightFallback(HttpClient client){this.client=client;}

    void write(TerrainTile tile,Path destination) throws IOException,InterruptedException {
        var model=new ComponentSampleModel(DataBuffer.TYPE_FLOAT,500,500,1,500,new int[]{0});
        var raster=Raster.createWritableRaster(model,new DataBufferFloat(500*500),null);
        Map<Key,BufferedImage> local=new HashMap<>();
        for(int y=0;y<500;y++) {
            if(Thread.currentThread().isInterrupted())throw new InterruptedException("Höhenvorbereitung abgebrochen");
            for(int x=0;x<500;x++) {
                double[] ll=HeightAssetCatalog.wgs(tile.x()*1000.0+2*x+1,(tile.z()+1)*1000.0-2*y-1);
                double px=(ll[1]+180)/360*WORLD_PIXELS-.5;
                double lat=Math.toRadians(ll[0]);
                double py=(1-Math.log(Math.tan(lat)+1/Math.cos(lat))/Math.PI)/2*WORLD_PIXELS-.5;
                int ix=(int)Math.floor(px),iy=(int)Math.floor(py);double fx=px-ix,fy=py-iy;
                float a=sample(ix,iy,local),b=sample(ix+1,iy,local),c=sample(ix,iy+1,local),d=sample(ix+1,iy+1,local);
                float h=(float)((a+(b-a)*fx)*(1-fy)+(c+(d-c)*fx)*fy);
                if(!Float.isFinite(h)||h<=-9000)throw new IOException("Keine gültigen regionalen Höhendaten für "+tile);
                raster.setSample(x,y,0,h);
            }
        }
        var color=new ComponentColorModel(ColorSpace.getInstance(ColorSpace.CS_GRAY),new int[]{32},false,false,Transparency.OPAQUE,DataBuffer.TYPE_FLOAT);
        if(!ImageIO.write(new BufferedImage(color,raster,false,null),"TIFF",destination.toFile()))throw new IOException("Kein TIFF-Writer verfügbar");
    }
    private float sample(int px,int py,Map<Key,BufferedImage> local) throws IOException,InterruptedException {
        if(py<0||py>=WORLD_PIXELS)throw new IOException("Außerhalb der regionalen Höhenabdeckung");
        Key key=new Key(Math.floorMod(Math.floorDiv(px,PIXELS),1<<ZOOM),Math.floorDiv(py,PIXELS));
        BufferedImage image=local.get(key);if(image==null){image=image(key);local.put(key,image);}
        int rgb=image.getRGB(Math.floorMod(px,PIXELS),Math.floorMod(py,PIXELS));
        return decode(rgb);
    }
    static float decode(int rgb){return ((rgb>>16)&255)*256f+((rgb>>8)&255)+(rgb&255)/256f-32768f;}
    private BufferedImage image(Key key) throws IOException,InterruptedException {
        CompletableFuture<BufferedImage> future;boolean owner;
        synchronized(images){future=images.get(key);owner=future==null;if(owner){future=new CompletableFuture<>();images.put(key,future);}}
        if(owner)try {
            URI uri=URI.create("https://s3.amazonaws.com/elevation-tiles-prod/terrarium/"+ZOOM+"/"+key.x()+"/"+key.y()+".png");
            HttpResponse<InputStream> response=client.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(30)).GET().build(),HttpResponse.BodyHandlers.ofInputStream());
            BufferedImage image;
            try(InputStream body=response.body()) {
                if(response.statusCode()!=200)throw new IOException("Regionale Höhendaten: HTTP "+response.statusCode()+" für "+uri);
                byte[] bytes=body.readNBytes(2_000_001);if(bytes.length>2_000_000)throw new IOException("Regionale Höhenkachel zu groß");
                image=ImageIO.read(new ByteArrayInputStream(bytes));
            }
            if(image==null||image.getWidth()!=256||image.getHeight()!=256)throw new IOException("Ungültige Terrarium-Höhenkachel");
            future.complete(image);
            synchronized(images){var iterator=images.entrySet().iterator();while(images.size()>48&&iterator.hasNext()){var old=iterator.next();if(old.getValue().isDone())iterator.remove();}}
        } catch(IOException|InterruptedException|RuntimeException ex) {
            future.completeExceptionally(ex);synchronized(images){images.remove(key,future);}throw ex;
        }
        try{return future.get();}catch(ExecutionException ex){throw new IOException("Regionale Höhenkachel konnte nicht geladen werden",ex.getCause());}
    }
    void clear(){synchronized(images){images.clear();}}
}
