package alxb.flighttraining.game.ui;

import alxb.flighttraining.game.config.GameSettings;
import alxb.flighttraining.game.config.GameSettings.Option;
import java.awt.*;
import java.awt.geom.Point2D;
import java.util.Locale;

/** Pure 512px map page renderer, shared by the cockpit and headless visual checks. */
public final class NavigationDisplay {
    public static final Rectangle MAP=new Rectangle(16,112,480,270);
    private static final Font FONT=new Font(Font.MONOSPACED,Font.BOLD,22);
    private static final Color AMBER=new Color(255,212,98);
    private final NavigationAtlas atlas;
    private final NavigationModel model=new NavigationModel();
    public NavigationDisplay(NavigationAtlas atlas){this.atlas=atlas;}
    public NavigationModel model(){return model;}
    public void command(int commands){
        var settings=GameSettings.get();
        if((commands&1)!=0)zoom(settings,-1);
        if((commands&2)!=0)zoom(settings,1);
        if((commands&4)!=0)settings.cycle(Option.NAV_ORIENTATION,1);
        if((commands&8)!=0)model.nextDestination();
    }
    private static void zoom(GameSettings settings,int step){
        var choices=Option.NAV_RANGE.choices;int index=choices.indexOf(settings.value(Option.NAV_RANGE));
        settings.set(Option.NAV_RANGE,choices.get(Math.max(0,Math.min(choices.size()-1,index+step))));
    }
    public static Point2D.Double project(double eastOffset,double northOffset,double heading,double widthMetres){
        double angle=Math.toRadians(heading),x=eastOffset,y=-northOffset,scale=MAP.width/widthMetres;
        return new Point2D.Double(MAP.getCenterX()+(x*Math.cos(angle)+y*Math.sin(angle))*scale,
            MAP.getCenterY()+(-x*Math.sin(angle)+y*Math.cos(angle))*scale);
    }
    public void draw(Graphics2D g,double east,double north,float heading,double speed,GameSettings settings){
        float brightness=settings.fraction(Option.BRIGHTNESS);
        Color green=new Color(Math.round(96*brightness),Math.round(255*brightness),Math.round(140*brightness));
        double widthMetres=Double.parseDouble(settings.value(Option.NAV_RANGE).replace(" km",""))*1000;
        double rotation=settings.value(Option.NAV_ORIENTATION).equals("Heading up")?heading:0;
        g.setFont(FONT.deriveFont(26f));g.setColor(green);g.drawString("NAV / "+settings.value(Option.NAV_RANGE),16,29);
        g.setFont(FONT.deriveFont(18f));g.drawString(rotation==0&&settings.value(Option.NAV_ORIENTATION).equals("North up")?"N-UP":"HDG-UP",395,28);
        String status=atlas.draw(g,MAP,east,north,widthMetres,rotation);
        Graphics2D overlay=(Graphics2D)g.create();overlay.clip(MAP);
        // Destination course line, with the same isotropic projection as the map raster.
        var destination=model.destination();var target=project(destination.east()-east,destination.north()-north,rotation,widthMetres);
        overlay.setColor(new Color(20,24,26));overlay.setStroke(new BasicStroke(5f));overlay.drawLine((int)MAP.getCenterX(),(int)MAP.getCenterY(),(int)target.x,(int)target.y);
        overlay.setColor(AMBER);overlay.setStroke(new BasicStroke(2f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND,1,new float[]{8,6},0));overlay.drawLine((int)MAP.getCenterX(),(int)MAP.getCenterY(),(int)target.x,(int)target.y);
        for(var airport:NavigationModel.AIRFIELDS){
            var point=project(airport.east()-east,airport.north()-north,rotation,widthMetres);if(!MAP.contains(point))continue;
            int x=(int)point.x,y=(int)point.y;
            overlay.setStroke(new BasicStroke(4));overlay.setColor(Color.BLACK);overlay.drawRect(x-5,y-5,10,10);
            overlay.setStroke(new BasicStroke(2));overlay.setColor(airport==destination?AMBER:Color.WHITE);overlay.drawRect(x-5,y-5,10,10);
            // Keep labels upright, including heading-up mode. Avoid the own-aircraft symbol and map margins.
            if(point.distance(MAP.getCenterX(),MAP.getCenterY())>23){
                String label=airport.icao();overlay.setFont(FONT.deriveFont(18f));int tx=Math.max(MAP.x+3,Math.min(x+10,MAP.x+MAP.width-59)),ty=Math.max(MAP.y+20,Math.min(y-9,MAP.y+MAP.height-25));
                overlay.setColor(new Color(0,0,0,205));overlay.fillRect(tx-2,ty-17,58,21);overlay.setColor(airport==destination?AMBER:Color.WHITE);overlay.drawString(label,tx,ty);
            }
        }
        if(!MAP.contains(target)){
            double dx=target.x-MAP.getCenterX(),dy=target.y-MAP.getCenterY();double factor=Math.min((MAP.width/2.0-14)/Math.max(.001,Math.abs(dx)),(MAP.height/2.0-14)/Math.max(.001,Math.abs(dy)));
            int x=(int)(MAP.getCenterX()+dx*factor),y=(int)(MAP.getCenterY()+dy*factor);overlay.setColor(AMBER);overlay.setStroke(new BasicStroke(3));overlay.drawOval(x-6,y-6,12,12);
        }
        Graphics2D own=(Graphics2D)overlay.create();own.translate(MAP.getCenterX(),MAP.getCenterY());own.rotate(Math.toRadians(heading-rotation));
        Polygon symbol=new Polygon(new int[]{0,-10,0,10},new int[]{-15,11,5,11},4);own.setStroke(new BasicStroke(5));own.setColor(Color.BLACK);own.draw(symbol);own.setColor(Color.WHITE);own.fill(symbol);own.dispose();
        overlay.setFont(FONT.deriveFont(18f));overlay.setColor(new Color(0,0,0,190));overlay.fillRect(MAP.x+5,MAP.y+5,32,28);overlay.setColor(Color.WHITE);
        Graphics2D compass=(Graphics2D)overlay.create();compass.translate(MAP.x+21,MAP.y+19);compass.rotate(-Math.toRadians(rotation));compass.drawLine(0,9,0,-9);compass.drawLine(0,-9,-4,-2);compass.drawLine(0,-9,4,-2);compass.dispose();overlay.drawString("N",MAP.x+41,MAP.y+25);
        double bar=widthMetres<=10000?1000:widthMetres<=25000?5000:widthMetres<=50000?10000:20000;int barPixels=(int)Math.round(bar/widthMetres*MAP.width),by=MAP.y+MAP.height-16,bx=MAP.x+12;
        overlay.setColor(new Color(0,0,0,200));overlay.fillRect(bx-6,by-27,Math.max(98,barPixels+12),38);overlay.setColor(Color.WHITE);overlay.setStroke(new BasicStroke(2));overlay.drawLine(bx,by,bx+barPixels,by);overlay.drawLine(bx,by-4,bx,by+4);overlay.drawLine(bx+barPixels,by-4,bx+barPixels,by+4);overlay.setFont(FONT.deriveFont(16f));overlay.drawString((int)(bar/1000)+" km",bx,by-8);
        overlay.setFont(FONT.deriveFont(13f));overlay.setColor(new Color(0,0,0,205));overlay.fillRect(MAP.x+MAP.width-105,by-14,102,27);overlay.setColor(Color.WHITE);overlay.drawString("©swisstopo",MAP.x+MAP.width-101,by+4);overlay.dispose();
        // Dim the whole raster and symbols together; this setting must also affect the coloured map.
        if(brightness<1){g.setColor(new Color(0,0,0,1-brightness));g.fill(MAP);}
        g.setColor(green);g.setFont(FONT.deriveFont(16f));g.drawString(status+"  /  HDG "+String.format(Locale.ROOT,"%03.0f",heading%360),16,49);
        g.setFont(FONT.deriveFont(23f));g.drawString(destination.icao()+"  "+destination.name(),16,76);
        boolean aviation=settings.value(Option.UNITS).equals("Aviation");double distance=model.distance(east,north),unit=aviation?1852:1000;
        g.setFont(FONT.deriveFont(22f));g.drawString(String.format(Locale.ROOT,"BRG %03.0f  %.1f %s  ETA %s",model.bearing(east,north)%360,distance/unit,aviation?"NM":"km",NavigationModel.eta(distance,speed)),16,102);
        double[] latlon=NavigationModel.latLon(east,north);g.setFont(FONT.deriveFont(20f));g.drawString(String.format(Locale.ROOT,"%.4f N  %.4f E",latlon[0],latlon[1]),16,423);
        g.setFont(FONT.deriveFont(16f));g.drawString("LV95 "+Math.round(east)+" E / "+Math.round(north)+" N",16,450);
        g.setFont(FONT.deriveFont(16f));g.drawString(key("NavZoomIn")+"/"+key("NavZoomOut")+":ZOOM "+key("NavOrientation")+":UP "+key("NavNextAirfield")+":DEST "+key("AircraftDisplayPage")+":SYS",16,498);
    }
    private static String key(String action){return com.jme3.input.KeyNames.getName(GameSettings.get().key(action));}
}
