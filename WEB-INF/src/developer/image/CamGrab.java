/*
 * need to:
 * port findcenter to java
 * implement image savecenter
 * implement image findcenter
 * try savecenter/findcenter using edge detect instead
 * make image toolkit class: edge detect, blur, convert to grey
 * 
 * interface: swing (so don't have to compile/run red5 every time)
 * need 2-3 320x240 images, series of buttons
 */


package developer.image;
 
 
import java.awt.*;
import java.awt.event.*;
import java.awt.image.*;
import java.io.*;
import java.net.URL;

import javax.imageio.*;
import javax.swing.*;


public class CamGrab extends Component {
           
    BufferedImage img;
 
    public void paint(Graphics g) {
        g.drawImage(img, 0, 0, null);
    }
 
    public CamGrab() {
       try {
//           img = ImageIO.read(new File("strawberry.jpg"));
    	   img = ImageIO.read(new URL("http://127.0.0.1:5080/oculusPrime/frameGrabHTTP"));
       } catch (IOException e) {
       }
 
    }
 
    public Dimension getPreferredSize() {
        if (img == null) {
             return new Dimension(100,100);
        } else {
           return new Dimension(img.getWidth(null), img.getHeight(null));
       }
    }
 
    public static void main(String[] args) {
 
        JFrame f = new JFrame("Load Image Sample");
             
        f.addWindowListener(new WindowAdapter(){
                public void windowClosing(WindowEvent e) {
                    System.exit(0);
                }
            });
 
        f.add(new CamGrab());
        f.pack();
        f.setVisible(true);
    }
}
