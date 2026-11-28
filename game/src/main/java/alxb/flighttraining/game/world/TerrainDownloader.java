package alxb.flighttraining.game.world;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.HashMap;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.FutureTask;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;

/** Sechs Worker laden Höhendaten und Luftbilder in denselben Karten-Cache. */
public final class TerrainDownloader implements AutoCloseable {
    private static final long RETRY_DELAY_NANOS = Duration.ofSeconds(30).toNanos();
    private enum Kind { HEIGHT, TEXTURE }

    private record Resource(TerrainTile tile, Kind kind) {
        String fileName() {
            if (kind == Kind.HEIGHT) {
                return tile.fileName();
            }
            return "texture_" + tile.x() + "-" + tile.z() + ".png";
        }

    }

    private static final class Download {
        final CompletableFuture<Path> result = new CompletableFuture<>();
        FutureTask<Void> task;
        long retryAt;
    }

    private final HttpClient client;
    private final RegionalHeightFallback regionalHeights;
    private final Path cacheDirectory;
    private final ThreadPoolExecutor workers = new ThreadPoolExecutor(
            6, 6, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(),
            Thread.ofPlatform().daemon().name("map-download-", 0).factory()
    );
    private final HashMap<Resource, Download> downloads = new HashMap<>();
    private boolean closed;

    public TerrainDownloader() {
        this(DownloadCache.directory("terrain-cache"), HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build());
    }

    TerrainDownloader(Path cacheDirectory, HttpClient client) {
        this.cacheDirectory = cacheDirectory;
        this.client = client;
        this.regionalHeights = new RegionalHeightFallback(client);
        DownloadCache.register(this);
    }

    public CompletableFuture<Path> downloadTile(int tileX, int tileZ) {
        return request(new Resource(new TerrainTile(tileX, tileZ), Kind.HEIGHT));
    }

    public CompletableFuture<Path> downloadTextureTile(int tileX, int tileZ) {
        return request(new Resource(new TerrainTile(tileX, tileZ), Kind.TEXTURE));
    }

    private synchronized CompletableFuture<Path> request(Resource resource) {
        if (closed) {
            return CompletableFuture.failedFuture(new IllegalStateException("Downloader geschlossen"));
        }
        Download previous = downloads.get(resource);
        if(previous!=null){
            boolean stale=previous.result.isDone() && !previous.result.isCompletedExceptionally()
                    && previous.result.getNow(null)!=null && !Files.isRegularFile(previous.result.getNow(null));
            if(!stale && (!previous.result.isCompletedExceptionally() || System.nanoTime()-previous.retryAt<0))
                return previous.result;
        }
        Download download = new Download();
        download.task = new FutureTask<>(() -> {
            fetch(resource, download);
            return null;
        });
        downloads.put(resource, download);
        workers.execute(download.task);
        return download.result;
    }

    private HttpResponse<Path> fetchFile(URI uri, Path temporary)
            throws IOException, InterruptedException {
        return client.send(HttpRequest.newBuilder(uri)
                        .timeout(Duration.ofSeconds(60)).GET().build(),
                HttpResponse.BodyHandlers.ofFile(temporary,
                        StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING));
    }

    static URI textureUri(TerrainTile tile) {
        int east = tile.x() * 1000;
        int north = tile.z() * 1000;
        return URI.create("https://wms.geo.admin.ch/?SERVICE=WMS&REQUEST=GetMap"
                + "&VERSION=1.3.0&LAYERS=ch.swisstopo.swissimage&STYLES=default"
                + "&CRS=EPSG:2056&BBOX=" + east + "," + north + ","
                + (east + 1000) + "," + (north + 1000)
                + "&WIDTH=1024&HEIGHT=1024&FORMAT=image/png");
    }

    private synchronized boolean isCurrent(Resource resource, Download download) {
        return !closed && downloads.get(resource) == download;
    }

    private void fetch(Resource resource, Download download) {
        Path temporary = null;
        try {
            if (!isCurrent(resource, download)) {
                return;
            }
            Files.createDirectories(cacheDirectory);
            Path destination = cacheDirectory.resolve(resource.fileName());
            if (Files.isRegularFile(destination)) {
                try {
                    validate(destination, resource.kind());
                    synchronized (this) {
                        if (isCurrent(resource, download)) {
                            download.result.complete(destination);
                        }
                    }
                    return;
                } catch (IOException e) {
                    synchronized (this) {
                        if (!isCurrent(resource, download)) {
                            return;
                        }
                        Files.deleteIfExists(destination);
                    }
                }
            }

            if (!isCurrent(resource, download)) {
                return;
            }
            URI uri = resource.kind()==Kind.HEIGHT
                    ? HeightAssetCatalog.find(client,resource.tile()) : textureUri(resource.tile());
            if(!isCurrent(resource,download))return;
            temporary = Files.createTempFile(cacheDirectory, resource.fileName() + "-", ".part");
            if(resource.kind()==Kind.HEIGHT && uri==null) {
                regionalHeights.write(resource.tile(),temporary);
                System.out.println("Regionale Ersatz-Höhendaten: " + resource.tile().x()+"-"+resource.tile().z());
            } else {
                HttpResponse<Path> response=fetchFile(uri,temporary);
                // A stale published URL is transient, never a permanent missing tile.
                if(response.statusCode()!=200)throw new IOException("HTTP "+response.statusCode()+" für "+uri);
            }
            if (!isCurrent(resource, download)) {
                return;
            }
            validate(temporary, resource.kind());

            synchronized (this) {
                // Abgeschlossene alte Aufträge dürfen entfernte Dateien nicht wieder veröffentlichen.
                if (!isCurrent(resource, download)) {
                    return;
                }
                try {
                    Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE,
                            StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
                }
                download.result.complete(destination);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            fail(download, e);
        } catch (IOException | RuntimeException e) {
            fail(download, e);
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException e) {
                    System.err.println("Temporäre Kartendatei konnte nicht gelöscht werden: " + temporary);
                }
            }
        }
    }

    private static void validate(Path path, Kind kind) throws IOException {
        int size = kind == Kind.HEIGHT ? 500 : 1024;
        var image = ImageIO.read(path.toFile());
        if (image == null || image.getWidth() != size || image.getHeight() != size) {
            throw new IOException("Ungültige Kartendatei: " + path);
        }
    }

    private synchronized void fail(Download download, Exception error) {
        if (!download.result.isDone()) {
            System.err.println("Karten-Download fehlgeschlagen: " + error.getMessage());
        }
        download.retryAt = System.nanoTime() + RETRY_DELAY_NANOS;
        download.result.completeExceptionally(error);
    }

    /** Entfernt Höhe und Luftbild dieser Kachel. */
    public synchronized void deleteTile(int tileX, int tileZ) {
        if (closed) {
            return;
        }
        TerrainTile tile = new TerrainTile(tileX, tileZ);
        for (Kind kind : Kind.values()) {
            forgetResource(new Resource(tile, kind));
        }
    }

    private void forgetResource(Resource resource) {
        Download removed = downloads.remove(resource);
        if (removed != null) {
            removed.result.cancel(false);
            removed.task.cancel(true);
            workers.remove(removed.task);
        }
        workers.execute(() -> deleteUnusedFile(resource));
    }

    private synchronized void deleteUnusedFile(Resource resource) {
        if (downloads.containsKey(resource)) {
            return;
        }
        try {
            Files.deleteIfExists(cacheDirectory.resolve(resource.fileName()));
        } catch (IOException e) {
            System.err.println("Kartendatei konnte nicht gelöscht werden: " + resource.fileName());
        }
    }

    synchronized void retainTiles(Set<TerrainTile> wanted) {
        if(closed)return;
        // Retention is a memory/job window, not a disk eviction policy. Deleting cached rasters
        // here races running neighbourhood readers and forces the return flight to download again.
        for(Resource resource:Set.copyOf(downloads.keySet()))if(!wanted.contains(resource.tile())){
            Download old=downloads.remove(resource);
            if(old!=null && !old.result.isDone()){
                old.result.cancel(false);old.task.cancel(true);workers.remove(old.task);
            }
        }
    }

    @Override
    public void close() {
        synchronized(this) {
            if(closed)return;
            closed=true;
            for(Download download:downloads.values()) {
                download.result.completeExceptionally(new CancellationException("Game closed"));
                download.task.cancel(true);
            }
            downloads.clear();workers.shutdownNow();client.shutdownNow();regionalHeights.clear();
        }
        boolean interrupted=Thread.interrupted();
        try {
            while(!workers.isTerminated())try { workers.awaitTermination(1,TimeUnit.SECONDS); }
            catch(InterruptedException e){interrupted=true;}
        } finally {
            DownloadCache.unregister(this);
            if(interrupted)Thread.currentThread().interrupt();
        }
    }
}
