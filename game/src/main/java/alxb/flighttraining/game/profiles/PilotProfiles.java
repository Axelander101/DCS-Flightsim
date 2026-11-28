package alxb.flighttraining.game.profiles;

import alxb.flighttraining.game.profiles.PilotProfileStore.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/** Local identity + flight log. Per-frame accounting is memory-only; file operations run on one worker. */
public final class PilotProfiles implements IdentityProvider,AutoCloseable {
    public record View(List<PilotProfile> pilots,UUID activeId,boolean busy,boolean available,long revision,String message){
        public PilotProfile active(){return pilots.stream().filter(p->p.id().equals(activeId)).findFirst().orElseThrow();}
    }
    private final PilotProfileStore store;
    private final ExecutorService writer=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"DSC-pilot-save");t.setDaemon(true);return t;});
    private final Map<UUID,Delta> pending=new HashMap<>();
    private final Thread shutdownHook;
    private volatile View view;
    private UUID flightPilot;
    private double fractionMillis;
    private long nextSave;
    private boolean closed;
    public PilotProfiles(PilotProfileStore store){
        this.store=store;
        try{Data data=store.load();view=new View(data.pilots(),data.activeId(),false,true,0,store.recoveryMessage().isEmpty()?"Saved on this device.":store.recoveryMessage());}
        catch(IOException|RuntimeException error){UUID id=UUID.randomUUID();view=new View(List.of(new PilotProfile(id,"Pilot",System.currentTimeMillis(),0,0,0)),id,false,false,0,"Pilot storage unavailable. Retry before saving a profile.");}
        shutdownHook=new Thread(this::close,"DSC-pilot-final-save");Runtime.getRuntime().addShutdownHook(shutdownHook);
    }
    public View view(){return view;}
    public PilotProfileStore store(){return store;}
    @Override public Identity currentIdentity(){PilotProfile pilot=view.active();return new Identity("local",pilot.id().toString(),pilot.name());}
    private synchronized void publish(Data data,UUID active,String message){
        UUID own=data.pilots().stream().anyMatch(p->p.id().equals(active))?active:data.activeId();
        String status=message.equals("Saved on this device.")&&!store.recoveryMessage().isEmpty()?store.recoveryMessage():message;
        view=new View(data.pilots(),own,false,true,view.revision()+1,status);
    }
    private synchronized void message(String value){message(value,false);}
    private synchronized void message(String value,boolean complete){var old=view;view=new View(old.pilots(),old.activeId(),complete?false:old.busy(),old.available(),old.revision()+1,value);}
    @FunctionalInterface private interface Operation {Data run()throws IOException;}
    private synchronized boolean submit(Operation operation,boolean selectResult){
        if(closed||view.busy())return false;
        if(flightPilot!=null){message("Return to the main menu before changing pilots.");return false;}
        UUID selected=view.activeId();var old=view;view=new View(old.pilots(),old.activeId(),true,old.available(),old.revision()+1,"Saving…");
        writer.execute(()->{try{Data data=operation.run();publish(data,selectResult?data.activeId():selected,"Saved on this device.");}catch(IOException|RuntimeException error){message(error instanceof IllegalArgumentException?error.getMessage():"Pilot data could not be saved. Please retry.",true);}});return true;
    }
    public void create(String name){try{String valid=PilotProfile.validateName(name);submit(()->store.create(valid),true);}catch(IllegalArgumentException error){message(error.getMessage());}}
    public void rename(String name){try{String valid=PilotProfile.validateName(name);UUID id=view.activeId();submit(()->store.rename(id,valid),false);}catch(IllegalArgumentException error){message(error.getMessage());}}
    public void select(UUID id){submit(()->store.select(id),true);}
    public void refresh(){submit(store::load,false);}
    public synchronized void beginFlight(){
        if(closed||!view.available()||view.busy()||flightPilot!=null)return;
        flightPilot=view.activeId();fractionMillis=0;merge(flightPilot,new Delta(0,0,1));nextSave=System.nanoTime()+15_000_000_000L;
    }
    public synchronized void record(float elapsed,boolean airborne,double groundSpeed){
        if(closed||flightPilot==null)return;
        if(Float.isFinite(elapsed)&&elapsed>0&&airborne&&Double.isFinite(groundSpeed)&&groundSpeed>=0&&groundSpeed<=2500){
            double dt=Math.min(elapsed,.25f);fractionMillis+=dt*1000;long whole=(long)fractionMillis;fractionMillis-=whole;
            merge(flightPilot,new Delta(whole,groundSpeed*dt,0));
        }
        if(System.nanoTime()>=nextSave){flush();nextSave=System.nanoTime()+15_000_000_000L;}
    }
    public synchronized void endFlight(){
        if(flightPilot==null)return;
        long finalMillis=Math.round(fractionMillis);if(finalMillis>0)merge(flightPilot,new Delta(finalMillis,0,0));fractionMillis=0;flightPilot=null;flush();
    }
    private void merge(UUID id,Delta delta){pending.merge(id,delta,Delta::plus);}
    public synchronized void flush(){
        if(closed||pending.isEmpty()||!view.available())return;
        Map<UUID,Delta> batch=Map.copyOf(pending);pending.clear();
        writer.execute(()->{
            try{Data data=store.add(batch);synchronized(this){boolean busy=view.busy();publish(data,view.activeId(),busy?"Saving…":"Saved on this device.");if(busy){var v=view;view=new View(v.pilots(),v.activeId(),true,v.available(),v.revision(),v.message());}}}
            catch(IOException|RuntimeException error){synchronized(this){batch.forEach(this::merge);message("Flight log save failed. Pending data will be retried.");}}
        });
    }
    /** Tests and shutdown can wait; the normal frame loop never does. */
    public boolean awaitSaved(long milliseconds)throws InterruptedException{
        try{writer.submit(()->{}).get(milliseconds,TimeUnit.MILLISECONDS);return true;}catch(ExecutionException|TimeoutException|RejectedExecutionException error){return false;}
    }
    @Override public void close(){
        synchronized(this){if(closed)return;endFlight();flush();closed=true;writer.shutdown();}
        try{if(!writer.awaitTermination(5,TimeUnit.SECONDS))System.err.println("Pilot final save is still pending.");}catch(InterruptedException interrupted){Thread.currentThread().interrupt();}
        if(Thread.currentThread()!=shutdownHook)try{Runtime.getRuntime().removeShutdownHook(shutdownHook);}catch(IllegalStateException ignored){}
    }
}
