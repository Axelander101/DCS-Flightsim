package alxb.flighttraining.game.world;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.regex.Pattern;

/** Finds published 2 m LV95 rasters without guessing a survey year. */
final class HeightAssetCatalog {
    private static final String ENDPOINT = "https://data.geo.admin.ch/api/stac/v1/collections/ch.swisstopo.swissalti3d/items";
    private HeightAssetCatalog() {}

    static double[] wgs(double east, double north) {
        double e=(east-2600000)/1e6, n=(north-1200000)/1e6;
        return new double[]{
            (16.9023892+3.238272*n-.270978*e*e-.002528*n*n-.0447*e*e*n-.014*n*n*n)*100/36,
            (2.6779094+4.728982*e+.791484*e*n+.1306*e*n*n-.0436*e*e*e)*100/36
        };
    }

    static URI query(TerrainTile tile) {
        double minLon=180,minLat=90,maxLon=-180,maxLat=-90;
        // Border tiles can contain Swiss data even when their centre is abroad.
        for(int dx=0;dx<=1;dx++)for(int dz=0;dz<=1;dz++) {
            double[] ll=wgs((tile.x()+dx)*1000.0,(tile.z()+dz)*1000.0);
            minLat=Math.min(minLat,ll[0]);maxLat=Math.max(maxLat,ll[0]);
            minLon=Math.min(minLon,ll[1]);maxLon=Math.max(maxLon,ll[1]);
        }
        return URI.create(ENDPOINT+String.format(Locale.ROOT,"?bbox=%.7f,%.7f,%.7f,%.7f&limit=100",
            minLon-.00001,minLat-.00001,maxLon+.00001,maxLat+.00001));
    }

    static URI find(HttpClient client,TerrainTile tile) throws IOException,InterruptedException {
        URI next=query(tile), newest=null;int year=0;Set<URI> visited=new HashSet<>();
        Pattern filename=Pattern.compile("swissalti3d_([0-9]{4})_"+tile.x()+"-"+tile.z()+"_2_2056_5728[.]tif");
        while(next!=null) {
            if(!visited.add(next)||visited.size()>16)throw new IOException("Ungültige Pagination im Höhenkatalog");
            HttpResponse<String> response=client.send(HttpRequest.newBuilder(next).timeout(Duration.ofSeconds(25)).GET().build(),HttpResponse.BodyHandlers.ofString());
            if(response.statusCode()!=200)throw new IOException("Höhenkatalog antwortet mit HTTP "+response.statusCode());
            JsonObject page;
            try {page=JsonParser.parseString(response.body()).getAsJsonObject();}
            catch(RuntimeException ex){throw new IOException("Ungültige Antwort des Höhenkatalogs",ex);}
            var features=page.getAsJsonArray("features");
            if(features==null)throw new IOException("Höhenkatalog liefert keine Feature-Liste");
            for(var feature:features) {
                JsonObject assets=feature.getAsJsonObject().getAsJsonObject("assets");if(assets==null)continue;
                for(var entry:assets.entrySet()) {
                    var href=entry.getValue().getAsJsonObject().get("href");if(href==null||!href.isJsonPrimitive())continue;
                    URI uri=URI.create(href.getAsString());String path=uri.getPath();
                    if(!trusted(uri)||path==null)continue;
                    var match=filename.matcher(path.substring(path.lastIndexOf('/')+1));
                    if(match.matches()&&Integer.parseInt(match.group(1))>year){year=Integer.parseInt(match.group(1));newest=uri;}
                }
            }
            URI current=next;next=null;var links=page.getAsJsonArray("links");
            if(links!=null)for(var link:links) {
                JsonObject l=link.getAsJsonObject();
                if(l.has("rel")&&"next".equals(l.get("rel").getAsString())&&l.has("href")) {
                    String href=l.get("href").getAsString();
                    next=href.startsWith("?")?URI.create(current.toString().split("[?]",2)[0]+href):current.resolve(href);
                    if(!trusted(next)||!next.getPath().equals(URI.create(ENDPOINT).getPath()))throw new IOException("Ungültiger Höhenkatalog-Link");
                    break;
                }
            }
        }
        return newest;
    }
    private static boolean trusted(URI uri){return "https".equalsIgnoreCase(uri.getScheme())&&"data.geo.admin.ch".equalsIgnoreCase(uri.getHost())&&uri.getUserInfo()==null;}
}
