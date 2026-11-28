package alxb.flighttraining.game.accounts;

import alxb.flighttraining.game.profiles.PilotProfileStore;
import java.io.*;
import java.net.URI;
import java.nio.file.*;
import java.util.Properties;

/** Only a public project key belongs in a desktop client. */
public record SupabaseConfig(URI projectUrl,String publishableKey) {
    public SupabaseConfig {
        if(projectUrl==null||!"https".equals(projectUrl.getScheme())||projectUrl.getHost()==null
            ||projectUrl.getUserInfo()!=null||projectUrl.getQuery()!=null||projectUrl.getFragment()!=null
            ||(projectUrl.getPort()!=-1&&projectUrl.getPort()!=443)
            ||!(projectUrl.getPath().isEmpty()||projectUrl.getPath().equals("/")))
            throw new IllegalArgumentException("Use an HTTPS Supabase Project URL without a path.");
        if(publishableKey==null||!publishableKey.matches("sb_publishable_[A-Za-z0-9_-]{8,}"))
            throw new IllegalArgumentException("Use a publishable key (sb_publishable_…), never a secret/service_role key.");
        projectUrl=URI.create("https://"+projectUrl.getHost());
    }
    public static Path file(){return Path.of(System.getProperty("dsc.authConfig",PilotProfileStore.defaultDirectory().resolveSibling("supabase.properties").toString()));}
    public static SupabaseConfig load()throws IOException {
        Properties p=new Properties();try(InputStream stream=Files.isRegularFile(file())?Files.newInputStream(file()):SupabaseConfig.class.getResourceAsStream("/Config/supabase.properties")){if(stream!=null)p.load(stream);}
        String url=System.getProperty("dsc.supabaseUrl",System.getenv().getOrDefault("DSC_SUPABASE_URL",p.getProperty("url",""))).strip();
        String key=System.getProperty("dsc.supabaseKey",System.getenv().getOrDefault("DSC_SUPABASE_PUBLISHABLE_KEY",p.getProperty("publishableKey",""))).strip();
        if(url.isEmpty()&&key.isEmpty())return null;
        try{return new SupabaseConfig(URI.create(url),key);}catch(IllegalArgumentException error){throw new IOException("Invalid Supabase configuration. Check the Project URL and publishable key.");}
    }
    @Override public String toString(){return "SupabaseConfig[configured]";}
}
