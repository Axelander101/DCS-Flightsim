package alxb.flighttraining.game.ui;

import java.awt.Toolkit;
import java.awt.datatransfer.*;

/** SDL clipboard works with the headless AWT mode required by this game's macOS launcher. */
final class DesktopClipboard implements ClipboardAccess {
    public String read()throws Exception {
        try{return (String)Class.forName("org.lwjgl.sdl.SDLClipboard").getMethod("SDL_GetClipboardText").invoke(null);}
        catch(ClassNotFoundException unavailable){return (String)Toolkit.getDefaultToolkit().getSystemClipboard().getData(DataFlavor.stringFlavor);}
    }
    public void write(String text)throws Exception {
        try{
            boolean ok=(Boolean)Class.forName("org.lwjgl.sdl.SDLClipboard").getMethod("SDL_SetClipboardText",CharSequence.class).invoke(null,text);
            if(!ok)throw new IllegalStateException("Clipboard unavailable");
        }catch(ClassNotFoundException unavailable){Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text),null);}
    }
}
interface ClipboardAccess {String read()throws Exception;void write(String text)throws Exception;}
