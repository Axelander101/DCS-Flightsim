package alxb.flighttraining.game.ui;

import java.awt.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import com.jme3.asset.*;

/** Shared visual language for the launcher, settings and in-flight overlays. */
final class MenuTheme {
    static final int WIDTH=1600,HEIGHT=900;
    static final Color TEXT=new Color(231,234,236),MUTED=new Color(145,157,165),ACCENT=new Color(221,171,85),LINE=new Color(93,108,119,90),PANEL=new Color(14,23,30,225);
    static final String FONT="Helvetica Neue";
    private static final java.util.Map<String,BufferedImage> photographs=new java.util.HashMap<>();
    static BufferedImage photo(AssetManager assets){return photograph(assets,"hornet");}
    static BufferedImage detail(AssetManager assets){BufferedImage image=photograph(assets,"hornet_detail");return image!=null?image:photo(assets);}
    static BufferedImage briefing(AssetManager assets){BufferedImage image=photograph(assets,"hornet_briefing");return image!=null?image:photo(assets);}
    private static BufferedImage photograph(AssetManager assets,String name){
        if(!photographs.containsKey(name))try{var info=assets.locateAsset(new AssetKey<>("Interface/Menu/"+name+".png"));if(info!=null)try(var stream=info.openStream()){BufferedImage image=ImageIO.read(stream);if(image!=null)photographs.put(name,image);}}catch(Exception e){System.err.println("Menu photograph: "+e.getMessage());}
        return photographs.get(name);
    }
    static void quality(Graphics2D g){g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);}
    static void cover(Graphics2D g,BufferedImage image,int x,int y,int w,int h){
        if(image==null){g.setColor(new Color(31,44,52));g.fillRect(x,y,w,h);return;}
        Graphics2D c=(Graphics2D)g.create();c.clipRect(x,y,w,h);double scale=Math.max(w/(double)image.getWidth(),h/(double)image.getHeight());int iw=(int)Math.ceil(image.getWidth()*scale),ih=(int)Math.ceil(image.getHeight()*scale);c.drawImage(image,x+(w-iw)/2,y+(h-ih)/2,iw,ih,null);c.dispose();
    }
    static void fit(Graphics2D g,BufferedImage image,int x,int y,int w,int h){
        g.setColor(new Color(14,23,30));g.fillRect(x,y,w,h);
        if(image==null)return;
        double scale=Math.min(w/(double)image.getWidth(),h/(double)image.getHeight());
        int iw=(int)Math.round(image.getWidth()*scale),ih=(int)Math.round(image.getHeight()*scale);
        g.drawImage(image,x+(w-iw)/2,y+(h-ih)/2,iw,ih,null);
    }
    static void background(Graphics2D g,BufferedImage image,int w,int h,boolean main){
        cover(g,image,0,0,w,h);g.setColor(new Color(7,14,20,main?30:200));g.fillRect(0,0,w,h);
        g.setPaint(new GradientPaint(0,0,new Color(5,11,17,20),0,h,new Color(5,11,17,main?190:235)));g.fillRect(0,0,w,h);
    }
    static void text(Graphics2D g,String value,int x,int y,int size,Color color,boolean bold){g.setColor(color);g.setFont(new Font(FONT,bold?Font.BOLD:Font.PLAIN,size));g.drawString(English.text(value),x,y);}
    static void right(Graphics2D g,String value,int x,int y,int size,Color color){g.setFont(new Font(FONT,Font.PLAIN,size));g.setColor(color);g.drawString(value,x-g.getFontMetrics().stringWidth(value),y);}
    static void line(Graphics2D g,int x,int y,int w){g.setColor(LINE);g.drawLine(x,y,x+w,y);}
    static void panel(Graphics2D g,int x,int y,int w,int h){g.setColor(PANEL);g.fillRect(x,y,w,h);g.setColor(LINE);g.drawRect(x,y,w,h);}
    static void button(Graphics2D g,Rectangle r,String label,boolean primary,boolean hover,boolean enabled){
        g.setColor(primary?(hover?new Color(239,190,104):ACCENT):(hover?new Color(65,77,85,240):new Color(28,39,48,220)));g.fillRect(r.x,r.y,r.width,r.height);
        g.setColor(primary?ACCENT:LINE);g.drawRect(r.x,r.y,r.width,r.height);g.setFont(new Font(FONT,Font.BOLD,17));g.setColor(!enabled?new Color(103,115,124):primary?new Color(17,24,30):TEXT);
        g.drawString(label,r.x+(r.width-g.getFontMetrics().stringWidth(label))/2,r.y+(r.height-g.getFontMetrics().getHeight())/2+g.getFontMetrics().getAscent());
    }
    static void paragraph(Graphics2D g,String value,int x,int y,int maxWidth,int size,Color color){
        g.setFont(new Font(FONT,Font.PLAIN,size));g.setColor(color);String line="";for(String word:value.split(" ")){String trial=line.isEmpty()?word:line+" "+word;if(g.getFontMetrics().stringWidth(trial)>maxWidth&&!line.isEmpty()){g.drawString(line,x,y);y+=size+9;line=word;}else line=trial;}if(!line.isEmpty())g.drawString(line,x,y);
    }
}
