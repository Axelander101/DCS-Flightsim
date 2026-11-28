package alxb.flighttraining.game.world;

import alxb.flighttraining.game.config.GameSettings;
import alxb.flighttraining.game.config.GameSettings.Option;

/** Texture detail is independent of geometric tessellation. Existing streaming budgets still apply. */
public final class TerrainQuality {
    private TerrainQuality() {}
    public static int meshSize(float distance, int previous, String quality) {
        int near=quality.equals("Low")?129:quality.equals("Standard")?257:513;
        int middle=quality.equals("Low")?65:129;
        if(previous==near && distance<2500)return near;
        if(previous==middle && distance>1900 && distance<5800)return middle;
        if(previous==65 && distance>5100)return 65;
        return distance<2200?near:distance<5500?middle:65;
    }
    public static int textureSize(float distance,int previous,String quality) {
        // Downloaded aerial imagery is 1024px: never request a fictitious 2048px decode.
        int near=quality.equals("Low")?512:1024;
        int middle=quality.equals("Low")?256:quality.equals("High")?1024:512;
        int far=quality.equals("High")?256:128;
        if(previous==near && distance<2500)return near;
        if(previous==middle && distance>1900 && distance<5800)return middle;
        if(previous==far && distance>5100)return far;
        return distance<2200?near:distance<5500?middle:far;
    }
    static int meshSize(float distance,int previous){return meshSize(distance,previous,GameSettings.get().value(Option.TERRAIN_QUALITY));}
    static int textureSize(float distance,int previous){return textureSize(distance,previous,GameSettings.get().value(Option.TEXTURE_QUALITY));}
}
