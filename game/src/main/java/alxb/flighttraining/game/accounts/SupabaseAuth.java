package alxb.flighttraining.game.accounts;

import com.google.gson.*;
import java.io.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.nio.ByteBuffer;
import java.util.concurrent.*;
import java.util.concurrent.Flow.*;

/** Supabase Auth REST client. No redirects, request/body logging, or durable credentials. */
public final class SupabaseAuth {
    public static final class AuthFailure extends IOException {
        final boolean expired;
        AuthFailure(String safeMessage,boolean expired){super(safeMessage);this.expired=expired;}
    }
    static final class Session {
        final String access,refresh;final UUID id;final String name,email;final long expires;
        Session(String access,String refresh,UUID id,String name,String email,long expires){this.access=access;this.refresh=refresh;this.id=id;this.name=name;this.email=email;this.expires=expires;}
        @Override public String toString(){return "Session[redacted]";}
    }
    interface Transport {Reply send(HttpRequest request)throws IOException,InterruptedException;}
    record Reply(int status,String body){@Override public String toString(){return "Reply[status="+status+", body=redacted]";}}
    private final SupabaseConfig config;private final Transport transport;
    public SupabaseAuth(SupabaseConfig config){this(config,network());}
    SupabaseAuth(SupabaseConfig config,Transport transport){this.config=config;this.transport=transport;}
    static Transport network(){
        HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).followRedirects(HttpClient.Redirect.NEVER).build();
        return request->{var future=client.sendAsync(request,info->new LimitedBody(request.uri().getPath().startsWith("/rest/v1/rpc/dsc_social_")?262144:65536));
            try{var response=future.get(15,TimeUnit.SECONDS);return new Reply(response.statusCode(),new String(response.body(),StandardCharsets.UTF_8));}
            catch(TimeoutException|ExecutionException error){future.cancel(true);throw new IOException("Account transport failed.");}
            catch(InterruptedException interrupted){future.cancel(true);throw interrupted;}
        };
    }
    private static final class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final int limit;
        LimitedBody(int limit){this.limit=limit;}
        private final CompletableFuture<byte[]> result=new CompletableFuture<>();private final ByteArrayOutputStream bytes=new ByteArrayOutputStream();private Subscription subscription;
        public CompletionStage<byte[]> getBody(){return result;}
        public void onSubscribe(Subscription value){subscription=value;value.request(1);}
        public void onNext(List<ByteBuffer> buffers){for(ByteBuffer b:buffers){if(bytes.size()+b.remaining()>limit){subscription.cancel();result.completeExceptionally(new IOException("Response too large"));return;}byte[] part=new byte[b.remaining()];b.get(part);bytes.writeBytes(part);}subscription.request(1);}
        public void onError(Throwable error){result.completeExceptionally(new IOException("Account response failed."));}
        public void onComplete(){result.complete(bytes.toByteArray());}
    }
    private JsonObject call(String method,String path,JsonObject body,String token)throws IOException {
        HttpRequest.Builder request=HttpRequest.newBuilder(config.projectUrl().resolve("/auth/v1/"+path)).timeout(Duration.ofSeconds(15)).header("apikey",config.publishableKey()).header("Content-Type","application/json").header("Accept","application/json");
        if(token!=null)request.header("Authorization","Bearer "+token);
        request.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(body.toString(),StandardCharsets.UTF_8));
        Reply result;try{result=transport.send(request.build());}catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new IOException("Account request interrupted.");}
        JsonObject json;
        try{json=result.body().isBlank()?new JsonObject():JsonParser.parseString(result.body()).getAsJsonObject();}catch(RuntimeException invalid){throw new IOException("Account service returned an invalid response.");}
        if(result.status()<200||result.status()>=300){String code=string(json,"error_code");if(code.isEmpty())code=string(json,"code");throw new AuthFailure(safeError(result.status(),code),result.status()==401||code.equals("refresh_token_not_found")||code.equals("refresh_token_already_used"));}
        return json;
    }
    private static String safeError(int status,String code){return switch(code){
        case "invalid_credentials"->"Email or password is incorrect.";
        case "email_not_confirmed"->"Confirm your email before signing in.";
        case "weak_password"->"Choose a stronger password that meets the server's requirements.";
        case "otp_expired"->"The code is incorrect or expired. Request a new email.";
        case "email_address_not_authorized"->"Email delivery is restricted. The project owner must configure SMTP.";
        case "user_already_exists","email_exists"->"Unable to create this account. Try signing in or resetting your password.";
        case "captcha_failed","captcha_disabled"->"This project requires browser CAPTCHA, which this client does not yet support.";
        case "signup_disabled"->"Registration is disabled for this project.";
        default->status==429?"Too many attempts. Please wait before trying again.":status==401?"Your session has expired. Please sign in again.":status>=500?"Account service unavailable. Please try again later.":"The account request was rejected. Check your details or the project's Auth settings.";
    };}
    JsonElement socialRpc(Session session,String function,JsonObject parameters)throws IOException {
        if(!Set.of("snapshot","find","friend","send_invite","reply_invite").contains(function))throw new IllegalArgumentException("Unsupported social operation.");
        var request=HttpRequest.newBuilder(config.projectUrl().resolve("/rest/v1/rpc/dsc_social_"+function)).timeout(Duration.ofSeconds(15))
            .header("apikey",config.publishableKey()).header("Authorization","Bearer "+session.access).header("Content-Type","application/json").header("Accept","application/json")
            .POST(HttpRequest.BodyPublishers.ofString(parameters.toString(),StandardCharsets.UTF_8)).build();
        Reply response;try{response=transport.send(request);}catch(InterruptedException error){Thread.currentThread().interrupt();throw new IOException("Friends request interrupted.");}
        JsonElement json;try{json=JsonParser.parseString(response.body());}catch(RuntimeException error){throw new IOException("Friends service returned an invalid response.");}
        if(response.status()<200||response.status()>=300){
            String code=json.isJsonObject()?string(json.getAsJsonObject(),"code"):"",message=json.isJsonObject()?string(json.getAsJsonObject(),"message"):"";
            String safe=code.equals("PGRST202")||code.equals("42883")||code.equals("42P01")?"Friends need server setup. Run account-setup/FRIENDS_SETUP.sql in Supabase.":response.status()==401?"Sign in again to use Friends.":response.status()==429?"Too many requests. Please wait.":switch(message){
                case "DSC_AUTH_REQUIRED"->"Sign in with a confirmed account to use Friends.";
                case "DSC_SELF"->"You cannot add or invite yourself.";
                case "DSC_NOT_FOUND"->"Player not found. Ask them to sign in and open Friends first.";
                case "DSC_PENDING"->"You are already friends or a request is already pending.";
                case "DSC_LIMIT"->"The friend or request limit has been reached.";
                case "DSC_COOLDOWN"->"Please wait before sending another request to this player.";
                case "DSC_NOT_RECIPIENT","DSC_NOT_PENDING"->"This request is no longer available to accept.";
                case "DSC_NOT_FRIENDS"->"You must be friends before exchanging lobby invitations.";
                case "DSC_LOBBY_CLOSED"->"This lobby is no longer available. Ask the host for a new invitation.";
                default->"The Friends request failed. Refresh and try again.";
            };
            throw new IOException(safe);
        }
        return json;
    }
    static JsonObject object(String... entries){JsonObject j=new JsonObject();for(int i=0;i<entries.length;i+=2)j.addProperty(entries[i],entries[i+1]);return j;}
    static String string(JsonObject j,String key){return j.has(key)&&j.get(key).isJsonPrimitive()?j.get(key).getAsString():"";}
    Session login(String email,String password)throws IOException{return session(call("POST","token?grant_type=password",object("email",email,"password",password),null));}
    Session register(String email,String password,String name)throws IOException{
        JsonObject body=object("email",email,"password",password);body.add("data",object("pilot_name",name));JsonObject result=call("POST","signup",body,null);
        return string(result,"access_token").isEmpty()?null:session(result);
    }
    Session verify(String email,String code,String type)throws IOException{return session(call("POST","verify",object("email",email,"token",code,"type",type),null));}
    void resend(String email)throws IOException{call("POST","resend",object("email",email,"type","signup"),null);}
    void recover(String email)throws IOException{call("POST","recover",object("email",email),null);}
    Session reset(String email,String code,String password)throws IOException{
        Session recovery=verify(email,code,"recovery");call("PUT","user",object("password",password),recovery.access);return validate(recovery);
    }
    Session refresh(Session old)throws IOException{Session fresh=session(call("POST","token?grant_type=refresh_token",object("refresh_token",old.refresh),null));if(!old.id.equals(fresh.id))throw new IOException("Account identity changed unexpectedly.");return fresh;}
    void logout(Session session)throws IOException{call("POST","logout?scope=local",new JsonObject(),session.access);}
    private Session session(JsonObject json)throws IOException{
        try{
            String access=string(json,"access_token"),refresh=string(json,"refresh_token");long lifetime=json.get("expires_in").getAsLong();
            UUID id=UUID.fromString(string(json.getAsJsonObject("user"),"id"));
            if(access.isEmpty()||refresh.isEmpty()||lifetime<=0||lifetime>604800)throw new IllegalArgumentException();
            return validate(new Session(access,refresh,id,"","",System.currentTimeMillis()+lifetime*1000));
        }catch(RuntimeException invalid){throw new IOException("Account service returned an invalid session.");}
    }
    private Session validate(Session session)throws IOException{
        JsonObject user=call("GET","user",null,session.access);
        try{
            UUID id=UUID.fromString(string(user,"id"));if(!id.equals(session.id)||string(user,"email_confirmed_at").isEmpty())throw new IllegalArgumentException();
            String name=user.has("user_metadata")&&user.get("user_metadata").isJsonObject()?string(user.getAsJsonObject("user_metadata"),"pilot_name"):"";
            try{name=alxb.flighttraining.game.profiles.PilotProfile.validateName(name);}catch(IllegalArgumentException bad){name="Pilot";}
            return new Session(session.access,session.refresh,id,name,string(user,"email"),session.expires);
        }catch(RuntimeException invalid){throw new IOException("The server did not confirm this account's identity and email.");}
    }
}
