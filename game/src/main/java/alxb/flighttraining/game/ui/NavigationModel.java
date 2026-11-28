package alxb.flighttraining.game.ui;

import alxb.flighttraining.game.config.DogfightSettings.Region;
import java.util.List;

/** Navigation in the same absolute LV95/world frame as terrain; unaffected by floating-origin shifts. */
public final class NavigationModel {
    public record Airfield(Region region,String icao) {
        public double east(){return 2621500.0+region.x;}
        public double north(){return 1092500.0-region.z;}
        public String name(){return English.text(region.label);}
    }
    public static final List<Airfield> AIRFIELDS=List.of(
        new Airfield(Region.ZURICH,"LSZH"),new Airfield(Region.GENEVA,"LSGG"),
        new Airfield(Region.BASEL,"LFSB"),new Airfield(Region.BERN,"LSZB"),
        new Airfield(Region.ALTENRHEIN,"LSZR"),new Airfield(Region.LUGANO,"LSZA"),
        new Airfield(Region.SION,"LSGS"),new Airfield(Region.GRENCHEN,"LSZG"),
        new Airfield(Region.PAYERNE,"LSMP"),new Airfield(Region.MEIRINGEN,"LSMM"),new Airfield(Region.EMMEN,"LSME"));
    private int destination;
    public Airfield destination(){return AIRFIELDS.get(destination);}
    public void nextDestination(){destination=(destination+1)%AIRFIELDS.size();}
    public void reset(double east,double north){
        double best=Double.POSITIVE_INFINITY;
        for(int i=0;i<AIRFIELDS.size();i++){Airfield a=AIRFIELDS.get(i);double distance=Math.hypot(a.east()-east,a.north()-north);if(distance<best){best=distance;destination=i;}}
    }
    public double distance(double east,double north){return Math.hypot(destination().east()-east,destination().north()-north);}
    public double bearing(double east,double north){return (Math.toDegrees(Math.atan2(destination().east()-east,destination().north()-north))+360)%360;}
    public static String eta(double metres,double groundSpeed){
        if(!Double.isFinite(metres)||!Double.isFinite(groundSpeed)||groundSpeed<10)return "--:--";
        long seconds=Math.round(Math.max(0,metres)/groundSpeed);
        if(seconds>=3600)return String.format(java.util.Locale.ROOT,"%dh%02dm",seconds/3600,(seconds%3600)/60);
        return String.format(java.util.Locale.ROOT,"%02d:%02d",seconds/60,seconds%60);
    }
    public static double[] latLon(double east,double north){
        double y=(east-2600000)/1e6,x=(north-1200000)/1e6;
        return new double[]{(16.9023892+3.238272*x-.270978*y*y-.002528*x*x-.0447*y*y*x-.014*x*x*x)*100/36,
            (2.6779094+4.728982*y+.791484*y*x+.1306*y*x*x-.0436*y*y*y)*100/36};
    }
}
