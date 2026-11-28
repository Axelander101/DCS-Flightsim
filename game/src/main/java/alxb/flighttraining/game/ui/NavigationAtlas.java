package alxb.flighttraining.game.ui;

import com.google.gson.Gson;
import com.jme3.asset.AssetKey;
import com.jme3.asset.AssetManager;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import javax.imageio.ImageIO;

/** Packaged offline maps. Decode on one daemon worker, bounded CPU image cache; no network or GPU work here. */
public final class NavigationAtlas implements AutoCloseable {
    public record Sheet(String name,double west,double south,double east,double north,int width,int height) {
        boolean contains(double e,double n,double radius){return e-radius>=west&&e+radius<=east&&n-radius>=south&&n+radius<=north;}
    }
    private final AssetManager assets;
    private final java.util.List<Sheet> sheets;
    private final LinkedHashMap<String,Future<BufferedImage>> images=new LinkedHashMap<>(8,.75f,true);
    private final ExecutorService decoder=Executors.newSingleThreadExecutor(task->{Thread t=new Thread(task,"DSC-navigation-map");t.setDaemon(true);return t;});
    private final Set<String> failed=new HashSet<>();
    private boolean closed;
    public NavigationAtlas(AssetManager assets){
        this.assets=assets;
        java.util.List<Sheet> loaded=java.util.List.of();
        try(var input=open("Navigation/atlas.json");var reader=new InputStreamReader(input,StandardCharsets.UTF_8)){
            loaded=java.util.List.of(new Gson().fromJson(reader,Sheet[].class));
        }catch(IOException|RuntimeException error){System.err.println("Navigation atlas: "+error.getMessage());}
        sheets=loaded;
        if(!sheets.isEmpty())image(sheets.get(0));
    }
    private InputStream open(String name)throws IOException {
        var info=assets.locateAsset(new AssetKey<Object>(name));
        if(info==null)throw new FileNotFoundException(name);
        return info.openStream();
    }
    private BufferedImage image(Sheet sheet){
        if(closed||failed.contains(sheet.name()))return null;
        Future<BufferedImage> future=images.get(sheet.name());
        if(future==null){
            // Keep the overview plus at most three local maps. Do not queue decode work while one is pending.
            if(images.values().stream().anyMatch(f->!f.isDone()))return null;
            while(images.size()>=4){var it=images.entrySet().iterator();while(it.hasNext()){var e=it.next();if(!e.getKey().equals("overview")){it.remove();break;}}}
            future=decoder.submit(()->{try(var input=open("Navigation/"+sheet.name()+".jpg")){
                BufferedImage result=ImageIO.read(input);
                if(result==null||result.getWidth()!=sheet.width()||result.getHeight()!=sheet.height())throw new IOException("Invalid map image "+sheet.name());
                return result;
            }});
            images.put(sheet.name(),future);
        }
        if(!future.isDone())return null;
        try{return future.get();}catch(InterruptedException e){Thread.currentThread().interrupt();return null;}
        catch(ExecutionException|CancellationException e){failed.add(sheet.name());images.remove(sheet.name());System.err.println("Navigation map unavailable: "+sheet.name());return null;}
    }
    public String draw(Graphics2D g,Rectangle view,double east,double north,double widthMetres,double heading){
        g.setColor(new Color(47,62,61));g.fill(view);
        if(sheets.isEmpty())return "MAP UNAVAILABLE";
        double radius=widthMetres*.5*Math.sqrt(1+Math.pow(view.height/(double)view.width,2));
        Sheet selected=sheets.get(0);
        for(Sheet sheet:sheets)if(!sheet.name().equals("overview")&&sheet.contains(east,north,radius)){selected=sheet;break;}
        BufferedImage background=image(sheets.get(0)),detail=selected==sheets.get(0)?background:image(selected);
        Sheet used=detail!=null?selected:sheets.get(0);BufferedImage map=detail!=null?detail:background;
        if(map==null)return "LOADING MAP";
        Graphics2D world=(Graphics2D)g.create();world.clip(view);
        world.translate(view.getCenterX(),view.getCenterY());world.rotate(-Math.toRadians(heading));
        double pixelsPerMetre=view.width/widthMetres;
        world.scale(pixelsPerMetre,pixelsPerMetre);
        AffineTransform transform=new AffineTransform();transform.translate(used.west()-east,north-used.north());
        transform.scale((used.east()-used.west())/map.getWidth(),(used.north()-used.south())/map.getHeight());
        world.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        world.drawImage(map,transform,null);world.dispose();
        if(!sheets.get(0).contains(east,north,0))return "OUTSIDE MAP COVERAGE";
        return used==sheets.get(0)?"OVERVIEW":"LOCAL MAP";
    }
    public boolean isIdle(){return images.values().stream().allMatch(Future::isDone);}
    public int cachedImages(){return images.size();}
    @Override public void close(){closed=true;images.values().forEach(f->f.cancel(true));images.clear();decoder.shutdownNow();}
}
