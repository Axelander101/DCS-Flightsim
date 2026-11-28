package alxb.flighttraining.game.profiles;

import com.google.gson.*;
import java.io.*;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.function.UnaryOperator;

/** Small local database. Cross-process lock + read/merge/write prevents two running games losing each other's stats. */
public final class PilotProfileStore {
    public static final int MAX_PROFILES=8;
    public record Data(int version,UUID activeId,List<PilotProfile> pilots){
        public Data {
            if(version!=1||activeId==null||pilots==null||pilots.isEmpty()||pilots.size()>MAX_PROFILES)throw new IllegalArgumentException("Invalid pilot database");
            pilots=List.copyOf(pilots);Set<UUID> ids=new HashSet<>();for(PilotProfile p:pilots)if(p==null||!ids.add(p.id()))throw new IllegalArgumentException("Duplicate pilot ID");
            if(!ids.contains(activeId))throw new IllegalArgumentException("Missing active pilot");
        }
        public PilotProfile pilot(UUID id){return pilots.stream().filter(p->p.id().equals(id)).findFirst().orElseThrow(()->new IllegalArgumentException("Pilot not found."));}
    }
    public record Delta(long millis,double metres,long flights){
        public Delta {if(millis<0||!Double.isFinite(metres)||metres<0||flights<0)throw new IllegalArgumentException("Invalid statistics");}
        Delta plus(Delta other){return new Delta(Math.addExact(millis,other.millis),metres+other.metres,Math.addExact(flights,other.flights));}
    }
    private static final Gson JSON=new GsonBuilder().setPrettyPrinting().create();
    private final Path directory,file,backup,lock;
    private volatile String recovery="";
    public PilotProfileStore(Path directory){this.directory=directory.toAbsolutePath().normalize();file=this.directory.resolve("pilots.json");backup=this.directory.resolve("pilots.backup.json");lock=this.directory.resolve("pilots.lock");}
    public static Path defaultDirectory(){
        String configured=System.getProperty("dsc.profileDirectory");if(configured!=null)return Path.of(configured);
        String testSettings=System.getProperty("dsc.settingsFile");if(testSettings!=null){Path settings=Path.of(testSettings).toAbsolutePath();return settings.resolveSibling(settings.getFileName()+"-pilots");}
        Path home=Path.of(System.getProperty("user.home"));String os=System.getProperty("os.name","").toLowerCase(Locale.ROOT);
        if(os.contains("mac"))return home.resolve("Library/Application Support/DSC/pilots");
        if(os.contains("win")){String appData=System.getenv("APPDATA");return(appData==null?home.resolve("AppData/Roaming"):Path.of(appData)).resolve("DSC/pilots");}
        String data=System.getenv("XDG_DATA_HOME");return(data==null?home.resolve(".local/share"):Path.of(data)).resolve("dsc/pilots");
    }
    public Path directory(){return directory;}
    public String recoveryMessage(){return recovery;}
    public Data load()throws IOException{return transaction(UnaryOperator.identity(),false);}
    public Data create(String name)throws IOException{
        String valid=PilotProfile.validateName(name);
        return transaction(data->{if(data.pilots().size()>=MAX_PROFILES)throw new IllegalArgumentException("Up to eight local pilots can be saved.");available(data,valid,null);List<PilotProfile> list=new ArrayList<>(data.pilots());UUID id=UUID.randomUUID();list.add(new PilotProfile(id,valid,Instant.now().toEpochMilli(),0,0,0));return new Data(1,id,list);},true);
    }
    public Data rename(UUID id,String name)throws IOException{
        String valid=PilotProfile.validateName(name);return transaction(data->{data.pilot(id);available(data,valid,id);return new Data(1,data.activeId(),data.pilots().stream().map(p->p.id().equals(id)?p.rename(valid):p).toList());},true);
    }
    public Data select(UUID id)throws IOException{return transaction(data->{data.pilot(id);return new Data(1,id,data.pilots());},true);}
    public Data add(Map<UUID,Delta> changes)throws IOException{
        Map<UUID,Delta> immutable=Map.copyOf(changes);
        return transaction(data->{for(UUID id:immutable.keySet())data.pilot(id);return new Data(1,data.activeId(),data.pilots().stream().map(p->{Delta d=immutable.get(p.id());return d==null?p:p.add(d.millis(),d.metres(),d.flights());}).toList());},true);
    }
    private static void available(Data data,String name,UUID except){if(data.pilots().stream().anyMatch(p->!p.id().equals(except)&&p.name().equalsIgnoreCase(name)))throw new IllegalArgumentException("That pilot name is already in use.");}
    private static final class UnsupportedVersion extends IOException {UnsupportedVersion(){super("Pilot data requires a different game version.");}}
    private Data read(Path path)throws IOException{
        if(Files.size(path)>131072)throw new IOException("Pilot file is too large");
        try(var reader=Files.newBufferedReader(path,StandardCharsets.UTF_8)){
            JsonElement parsed=JsonParser.parseReader(reader);
            if(parsed.isJsonObject()&&parsed.getAsJsonObject().has("version")&&parsed.getAsJsonObject().get("version").getAsInt()!=1)throw new UnsupportedVersion();
            Data data=JSON.fromJson(parsed,Data.class);if(data==null)throw new IOException("Empty pilot file");return data;
        }catch(JsonParseException|IllegalArgumentException error){throw new IOException("Pilot file could not be read",error);}
    }
    private Data transaction(UnaryOperator<Data> operation,boolean save)throws IOException{
        Files.createDirectories(directory);
        try(var channel=FileChannel.open(lock,StandardOpenOption.CREATE,StandardOpenOption.WRITE)){
            FileLock acquired=null;long until=System.nanoTime()+2_000_000_000L;
            while(acquired==null){try{acquired=channel.tryLock();}catch(OverlappingFileLockException busy){}
                if(acquired!=null)break;if(System.nanoTime()>until)throw new IOException("Pilot data is busy in another game instance.");
                try{Thread.sleep(10);}catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new IOException("Pilot operation interrupted",interrupted);}}
            try{
                Data data;boolean created=false;
                if(Files.isRegularFile(file))try{data=read(file);}catch(UnsupportedVersion newer){throw newer;}catch(IOException corrupt){
                    Data recovered=null;if(Files.isRegularFile(backup))try{recovered=read(backup);}catch(IOException ignored){}
                    Path preserved=directory.resolve("pilots-unreadable-"+UUID.randomUUID()+".json");Files.move(file,preserved);
                    data=recovered==null?initial():recovered;created=true;
                    recovery=recovered==null?"Unreadable pilot data was preserved. A new local pilot was created.":"Pilot data recovered from the previous saved version.";
                }else{data=null;if(Files.isRegularFile(backup))try{data=read(backup);}catch(UnsupportedVersion newer){throw newer;}catch(IOException ignored){}
                    if(data==null)data=initial();else recovery="Pilot data restored from the previous saved version.";created=true;}
                if(created)write(data,false);
                Data updated=operation.apply(data);if(save)write(updated,true);return updated;
            }finally{acquired.release();}
        }
    }
    private static Data initial(){UUID id=UUID.randomUUID();return new Data(1,id,List.of(new PilotProfile(id,"Pilot",Instant.now().toEpochMilli(),0,0,0)));}
    private void write(Data data,boolean keepBackup)throws IOException{
        Path temporary=Files.createTempFile(directory,"pilots-",".tmp");
        try{
            Files.writeString(temporary,JSON.toJson(data),StandardCharsets.UTF_8);
            if(keepBackup){Path copy=Files.createTempFile(directory,"pilots-backup-",".tmp");try{Files.copy(file,copy,StandardCopyOption.REPLACE_EXISTING);replace(copy,backup);}finally{Files.deleteIfExists(copy);}}
            if(!keepBackup&&!Files.exists(backup)){Path copy=Files.createTempFile(directory,"pilots-backup-",".tmp");try{Files.copy(temporary,copy,StandardCopyOption.REPLACE_EXISTING);replace(copy,backup);}finally{Files.deleteIfExists(copy);}}
            replace(temporary,file);
        }finally{Files.deleteIfExists(temporary);}
    }
    private static void replace(Path source,Path target)throws IOException{try{Files.move(source,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException unsupported){Files.move(source,target,StandardCopyOption.REPLACE_EXISTING);}}
}
