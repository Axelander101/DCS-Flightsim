package alxb.flighttraining.game.profiles;

import java.util.UUID;

public record PilotProfile(UUID id,String name,long createdAt,long airborneMillis,double distanceMetres,long flights) {
    public PilotProfile {
        if(id==null||createdAt<0||airborneMillis<0||!Double.isFinite(distanceMetres)||distanceMetres<0||flights<0)throw new IllegalArgumentException("Invalid pilot data");
        name=validateName(name);
    }
    public static String validateName(String value){
        if(value==null)throw new IllegalArgumentException("Enter a pilot name.");String name=value.strip();
        if(name.isEmpty()||name.length()>24||name.chars().anyMatch(Character::isISOControl))throw new IllegalArgumentException("Use a pilot name with 1–24 characters.");
        return name;
    }
    PilotProfile add(long millis,double metres,long count){return new PilotProfile(id,name,createdAt,Math.addExact(airborneMillis,millis),distanceMetres+metres,Math.addExact(flights,count));}
    PilotProfile rename(String value){return new PilotProfile(id,value,createdAt,airborneMillis,distanceMetres,flights);}
}
