package alxb.flighttraining.game.accounts;

import alxb.flighttraining.game.profiles.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;

/** Online authentication with memory-only sessions and separate, device-local flight logs. */
public final class PlayerAccounts implements IdentityProvider,AutoCloseable {
    public record View(boolean configured,boolean busy,boolean signedIn,boolean expired,String name,String email,String message,long revision){}
    private final PilotProfiles guest;
    private final Path accountDirectory;
    private final SupabaseAuth auth;
    private final ScheduledExecutorService worker=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"DSC-accounts");t.setDaemon(true);return t;});
    private volatile View view;
    private volatile PilotProfiles accountProfiles;
    private volatile SupabaseAuth.Session session;
    private volatile boolean closed;
    private final Set<char[]> ownedSecrets=Collections.newSetFromMap(new IdentityHashMap<>());
    public PlayerAccounts(PilotProfiles guest){this(guest,configured(),null);}
    private static SupabaseConfig configured(){try{return SupabaseConfig.load();}catch(IOException invalid){return null;}}
    PlayerAccounts(PilotProfiles guest,SupabaseConfig config,SupabaseAuth replacement){
        this.guest=guest;accountDirectory=guest.store().directory().resolve("accounts").resolve(config==null?"unconfigured":UUID.nameUUIDFromBytes(config.projectUrl().toString().getBytes(StandardCharsets.UTF_8)).toString());auth=config==null?null:replacement==null?new SupabaseAuth(config):replacement;
        view=new View(auth!=null,false,false,false,"","",auth==null?"Online accounts are not configured. See ACCOUNT_SETUP.txt.":"Sign in, create an account, or continue with a local pilot.",0);
        worker.scheduleWithFixedDelay(this::refreshSession,30,30,TimeUnit.SECONDS);
    }
    public View view(){return view;}
    public UUID onlineAccountId(){var value=session;return value!=null&&value.expires>System.currentTimeMillis()?value.id:null;}
    /** Runs only the allowlisted social RPCs; callers never receive access or refresh tokens. */
    public com.google.gson.JsonElement socialRpc(String function,com.google.gson.JsonObject parameters)throws IOException{
        var value=session;if(value==null||value.expires<=System.currentTimeMillis())throw new IOException("Sign in again to use Friends.");
        var response=auth.socialRpc(value,function,parameters);
        if(!value.id.equals(onlineAccountId()))throw new IOException("Account changed. Refresh Friends.");return response;
    }
    public PilotProfiles profiles(){PilotProfiles value=accountProfiles;return value==null?guest:value;}
    @Override public Identity currentIdentity(){var current=session;if(current!=null&&current.expires>System.currentTimeMillis())return new Identity("supabase",current.id.toString(),current.name);return profiles().currentIdentity();}
    private synchronized void state(boolean busy,boolean signedIn,boolean expired,String name,String email,String message){view=new View(auth!=null,busy,signedIn,expired,name,email,message,view.revision()+1);}
    private void message(String message){var v=view;state(false,v.signedIn(),v.expired(),v.name(),v.email(),message);}
    private interface Task {void run()throws IOException;}
    private synchronized boolean submit(Task action){
        if(closed||view.busy())return false;
        if(auth==null){message("Configure the Supabase Project URL and publishable key first. See ACCOUNT_SETUP.txt.");return false;}
        var v=view;state(true,v.signedIn(),v.expired(),v.name(),v.email(),"Contacting account service…");
        worker.execute(()->{try{action.run();}catch(SupabaseAuth.AuthFailure error){message(error.getMessage());}catch(IOException|RuntimeException error){message("Could not complete the request. Check your internet connection and account configuration, then retry.");}});return true;
    }
    private static String email(String email){String value=email.strip();if(value.length()>254||!value.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))throw new IllegalArgumentException("Enter a valid email address.");return value;}
    private static void password(char[] password,char[] confirmation){
        if(!Arrays.equals(password,confirmation))throw new IllegalArgumentException("The passwords do not match.");
        if(password.length<12||new String(password).getBytes(StandardCharsets.UTF_8).length>72)throw new IllegalArgumentException("Use at least 12 characters, up to 72 UTF-8 bytes.");
    }
    private static String code(String code){String value=code.strip();if(!value.matches("[0-9]{6,10}"))throw new IllegalArgumentException("Enter the numeric code from your email.");return value;}
    private synchronized void own(char[]... values){for(char[] value:values)if(value!=null)ownedSecrets.add(value);}
    private synchronized void wipe(char[]... values){for(char[] value:values)if(value!=null){Arrays.fill(value,'\0');ownedSecrets.remove(value);}}
    private synchronized void reject(String error){if(!view.busy())message(error);}
    /** Takes ownership of password arrays and clears them after processing. */
    public void login(String email,char[] password){
        own(password);try{String address=email(email);if(password.length==0||password.length>256)throw new IllegalArgumentException("Enter your password.");
            if(!submit(()->{try{attach(auth.login(address,new String(password)));}finally{wipe(password);}}))wipe(password);
        }catch(IllegalArgumentException error){wipe(password);reject(error.getMessage());}
    }
    public void register(String name,String email,char[] password,char[] confirmation){
        own(password,confirmation);try{String address=email(email),pilot=PilotProfile.validateName(name);password(password,confirmation);wipe(confirmation);
            if(!submit(()->{try{var result=auth.register(address,new String(password),pilot);if(result!=null)attach(result);else message("If registration is available, check your email for a confirmation code. Existing users can sign in or reset their password.");}finally{wipe(password);}}))wipe(password);
        }catch(IllegalArgumentException error){wipe(password,confirmation);reject(error.getMessage());}
    }
    public void confirm(String email,String code){try{String address=email(email),otp=code(code);submit(()->attach(auth.verify(address,otp,"signup")));}catch(IllegalArgumentException error){reject(error.getMessage());}}
    public void resend(String email){try{String address=email(email);submit(()->{auth.resend(address);message("If confirmation is available, a new code will arrive by email. Please allow time before retrying.");});}catch(IllegalArgumentException error){reject(error.getMessage());}}
    public void recover(String email){try{String address=email(email);submit(()->{auth.recover(address);message("If this address has an account, a reset code will arrive by email.");});}catch(IllegalArgumentException error){reject(error.getMessage());}}
    public void reset(String email,String code,char[] password,char[] confirmation){
        own(password,confirmation);try{String address=email(email),otp=code(code);password(password,confirmation);wipe(confirmation);
            if(!submit(()->{try{attach(auth.reset(address,otp,new String(password)));}finally{wipe(password);}}))wipe(password);
        }catch(IllegalArgumentException error){wipe(password,confirmation);reject(error.getMessage());}
    }
    private void attach(SupabaseAuth.Session result)throws IOException{
        // UUIDs parsed by the Auth client are the only user-controlled path component.
        PilotProfiles next=new PilotProfiles(new PilotProfileStore(accountDirectory.resolve(result.id.toString())));
        if(!next.view().available()){next.close();throw new IOException("Local account flight log unavailable");}
        if(next.view().pilots().size()==1&&!next.view().active().name().equals(result.name)){next.rename(result.name);try{if(!next.awaitSaved(3000)){next.close();throw new IOException("Profile save timed out");}}catch(InterruptedException interrupted){next.close();Thread.currentThread().interrupt();throw new IOException("Interrupted");}}
        synchronized(this){if(closed){next.close();return;}PilotProfiles old=accountProfiles;accountProfiles=next;session=result;if(old!=null)old.close();state(false,true,false,result.name,result.email,"Signed in. This account's flight log is stored on this device.");}
    }
    public void logout(){
        submit(()->{var old=session;session=null;PilotProfiles profiles=accountProfiles;accountProfiles=null;if(profiles!=null)profiles.close();
            try{if(old!=null)auth.logout(old);message("Signed out. Local pilot restored.");}catch(IOException error){message("Signed out on this device. Server sign-out could not be confirmed; this session expires automatically.");}
            state(false,false,false,"","",view.message());});
    }
    private void refreshSession(){
        var old=session;if(closed||old==null||view.busy()||old.expires-System.currentTimeMillis()>120_000)return;
        try{var fresh=auth.refresh(old);synchronized(this){if(!closed&&session==old){session=fresh;state(false,true,false,fresh.name,fresh.email,"Signed in. Flight statistics are saved on this device.");}}}
        catch(IOException|RuntimeException error){synchronized(this){if(session==old){boolean expired=old.expires<=System.currentTimeMillis()||error instanceof SupabaseAuth.AuthFailure f&&f.expired;if(expired){session=null;state(false,false,true,old.name,old.email,"Session expired. Sign in again; your local flight log is preserved.");}}}}
    }
    public boolean awaitIdle(long millis)throws InterruptedException {try{worker.submit(()->{}).get(millis,TimeUnit.MILLISECONDS);return true;}catch(ExecutionException|TimeoutException|RejectedExecutionException error){return false;}}
    @Override public void close(){
        synchronized(this){if(closed)return;closed=true;session=null;for(char[] secret:ownedSecrets)Arrays.fill(secret,'\0');ownedSecrets.clear();worker.shutdownNow();}
        try{worker.awaitTermination(3,TimeUnit.SECONDS);}catch(InterruptedException interrupted){Thread.currentThread().interrupt();}
        PilotProfiles value=accountProfiles;if(value!=null)value.close();
    }
}
