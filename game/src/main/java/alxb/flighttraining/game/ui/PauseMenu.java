package alxb.flighttraining.game.ui;

import com.jme3.asset.AssetManager;
import com.jme3.input.*;
import com.jme3.input.controls.*;
import com.jme3.renderer.Camera;
import com.jme3.scene.Node;
import com.jme3.texture.Texture2D;
import com.jme3.texture.plugins.AWTLoader;
import com.jme3.ui.Picture;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.function.BooleanSupplier;

/** Small in-flight overlay. The application suspends simulation while this is open. */
public final class PauseMenu {
 private final AssetManager assets;
 private final InputManager input;
 private final Node gui;
 private final Camera camera;
 private final BooleanSupplier canPause;
 private final Runnable mainMenu;
 private final Picture picture=new Picture("PauseMenu");
 private boolean paused,networked;
 public void setNetworked(boolean value){if(networked!=value){networked=value;if(paused)draw();}}
 public void dismiss(){paused=false;picture.removeFromParent();}
 private int width,height;
 public PauseMenu(AssetManager assets,InputManager input,Node gui,Camera camera,BooleanSupplier canPause,Runnable mainMenu){
  this.assets=assets;this.input=input;this.gui=gui;this.camera=camera;this.canPause=canPause;this.mainMenu=mainMenu;
  input.addMapping("FlightPause",new KeyTrigger(KeyInput.KEY_ESCAPE));
  input.addMapping("PauseClick",new MouseButtonTrigger(MouseInput.BUTTON_LEFT));
  input.addListener((ActionListener)(name,pressed,tpf)->{
   if(!pressed)return;
   if(name.equals("FlightPause")){if(paused)resume();else if(canPause.getAsBoolean()){paused=true;draw();gui.attachChild(picture);input.setCursorVisible(true);}}
   else if(paused){var cursor=input.getCursorPosition();float x=cursor.x-width/2f,y=height-cursor.y-height/2f;
    if(x>=-150&&x<=150){if(y>=-25&&y<=35)resume();else if(y>=55&&y<=115){paused=false;picture.removeFromParent();mainMenu.run();}}
   }
  },"FlightPause","PauseClick");
 }
 public boolean isPaused(){return paused;}
 private void resume(){paused=false;picture.removeFromParent();input.setCursorVisible(false);}
 public void update(){if(paused&&(width!=camera.getWidth()||height!=camera.getHeight()))draw();}
 private void draw(){
  width=camera.getWidth();height=camera.getHeight();
  BufferedImage image=new BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB);Graphics2D g=image.createGraphics();
  try{
   MenuTheme.quality(g);
   g.setColor(new Color(4,9,14,185));g.fillRect(0,0,width,height);int x=width/2,y=height/2;
   MenuTheme.panel(g,x-225,y-150,450,325);
   MenuTheme.text(g,"FLIGHT TRAINING",x-190,y-112,12,MenuTheme.ACCENT,true);
   MenuTheme.text(g,networked?"In-flight menu":"Flight paused",x-190,y-65,32,MenuTheme.TEXT,true);
   MenuTheme.button(g,new Rectangle(x-150,y-25,300,60),"RESUME FLIGHT",true,false,true);
   MenuTheme.button(g,new Rectangle(x-150,y+55,300,60),networked?"LEAVE SESSION":"MAIN MENU",false,false,true);
   MenuTheme.text(g,networked?"ONLINE FLIGHT CONTINUES":"ESC TO RESUME",x-150,y+146,12,MenuTheme.MUTED,false);
  }finally{g.dispose();}
  var previous=picture.getMaterial()==null?null:picture.getMaterial().getTextureParam("Texture");var old=previous==null?null:previous.getTextureValue();
  picture.setTexture(assets,new Texture2D(new AWTLoader().load(image,true)),true);if(old!=null&&old.getImage()!=null)old.getImage().dispose();picture.setWidth(width);picture.setHeight(height);picture.setLocalTranslation(0,0,500);
 }
}
