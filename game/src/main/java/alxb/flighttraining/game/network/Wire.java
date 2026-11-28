package alxb.flighttraining.game.network;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Bounded, length-prefixed UTF-8 frames; never Java object deserialization. */
public final class Wire {
    static final Gson JSON=new Gson();
    static final int MAX_FRAME=65536;
    private Wire(){}
    static JsonObject packet(String type){var object=new JsonObject();object.addProperty("type",type);return object;}
    static JsonObject packet(String type,String key,Object value){var object=packet(type);object.add(key,JSON.toJsonTree(value));return object;}
    static String string(JsonObject object,String key,int maximum){
        var value=object.get(key);if(value==null||!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isString())throw new IllegalArgumentException("Missing "+key);
        return text(value.getAsString(),maximum);
    }
    public static String text(String value,int maximum){
        if(value==null||value.isBlank()||value.length()>maximum||value.chars().anyMatch(Character::isISOControl))throw new IllegalArgumentException("Invalid text field");
        return value.strip();
    }
    static JsonObject read(DataInputStream input)throws IOException {
        int length=input.readInt();if(length<2||length>MAX_FRAME)throw new IOException("Invalid network frame size");
        byte[] bytes=input.readNBytes(length);if(bytes.length!=length)throw new EOFException("Connection closed");
        try{var value=JsonParser.parseString(new String(bytes,StandardCharsets.UTF_8));if(!value.isJsonObject())throw new IOException("Invalid network frame");return value.getAsJsonObject();}
        catch(JsonParseException|IllegalStateException error){throw new IOException("Invalid network frame",error);}
    }
    static void write(DataOutputStream output,JsonObject object)throws IOException {
        byte[] bytes=JSON.toJson(object).getBytes(StandardCharsets.UTF_8);if(bytes.length>MAX_FRAME)throw new IOException("Network frame too large");
        output.writeInt(bytes.length);output.write(bytes);output.flush();
    }
    static String nonce(){byte[] bytes=new byte[32];new SecureRandom().nextBytes(bytes);return HexFormat.of().formatHex(bytes);}
    static String proof(String password,String nonce,String name){
        try{byte[] key=MessageDigest.getInstance("SHA-256").digest(password.getBytes(StandardCharsets.UTF_8));var mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(key,"HmacSHA256"));return HexFormat.of().formatHex(mac.doFinal((nonce+"\n"+name+"\n"+FlightNetwork.VERSION).getBytes(StandardCharsets.UTF_8)));}
        catch(GeneralSecurityException error){throw new IllegalStateException(error);}
    }
    static boolean matches(String a,String b){return MessageDigest.isEqual(a.getBytes(StandardCharsets.US_ASCII),b.getBytes(StandardCharsets.US_ASCII));}
}
