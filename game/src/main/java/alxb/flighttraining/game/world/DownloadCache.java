package alxb.flighttraining.game.world;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** One temporary download cache per JVM, shared across mission changes. */
public final class DownloadCache {
    private static final Path ROOT = create();
    private static final Set<AutoCloseable> OWNERS = Collections.newSetFromMap(new IdentityHashMap<>());
    private static boolean closed;
    static {
        Runtime.getRuntime().addShutdownHook(new Thread(DownloadCache::shutdown, "download-cache-cleanup"));
    }
    private DownloadCache() {}
    private static Path create() {
        try { return Files.createTempDirectory("flighttraining-map-"); }
        catch (IOException e) { throw new IllegalStateException("Cannot create temporary map cache", e); }
    }
    static synchronized Path directory(String name) {
        if (closed) throw new IllegalStateException("Map cache closed");
        Path path=ROOT.resolve(name);
        try {
            Files.createDirectories(path);
            // Import old caches once by moving, never copying. Only known downloaded formats.
            Path project=Path.of("").toAbsolutePath().normalize();
            while(project!=null && !Files.isRegularFile(project.resolve("settings.gradle"))) project=project.getParent();
            if(project!=null) for(Path parent:List.of(project,project.resolve("desktop"),project.resolve("game"))) {
                Path old=parent.resolve(name);
                if(!Files.isDirectory(old,LinkOption.NOFOLLOW_LINKS)) continue;
                try(var entries=Files.newDirectoryStream(old)) {
                    for(Path file:entries) if(Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS) && owned(file.getFileName().toString(),name)) {
                        Path target=path.resolve(file.getFileName());
                        if(Files.exists(target)) Files.delete(file);
                        else Files.move(file,target);
                    }
                }
            }
            return path;
        } catch(IOException e) { throw new IllegalStateException("Cannot prepare map cache",e); }
    }
    private static boolean owned(String file,String cache) {
        return cache.equals("terrain-cache") ? file.matches("(?:terrain_[0-9]+-[0-9]+[.]tif|texture_[0-9]+-[0-9]+[.]png)(?:-.*[.]part)?")
                : file.matches("[0-9]+_[0-9]+[.]osm|osm-.*[.]part");
    }
    static synchronized void register(AutoCloseable owner) { if(closed) throw new IllegalStateException("Map cache closed"); OWNERS.add(owner); }
    static synchronized void unregister(AutoCloseable owner) { OWNERS.remove(owner); }
    /** Stop writers first, then remove only this JVM's temporary directory. Idempotent. */
    public static void shutdown() {
        List<AutoCloseable> owners;
        synchronized(DownloadCache.class) { if(closed)return;closed=true;owners=new ArrayList<>(OWNERS);OWNERS.clear(); }
        for(var owner:owners) try { owner.close(); } catch(Exception e) { System.err.println("Cache writer shutdown: "+e); }
        try(var files=Files.walk(ROOT)) {
            for(Path file:files.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(file);
        } catch(IOException e) { System.err.println("Cannot remove temporary map cache: "+e); }
    }
}
