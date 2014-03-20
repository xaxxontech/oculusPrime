package oculusPrime;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
//import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
//import java.util.Random;
//import java.util.Timer;
//import java.util.TimerTask;



import javax.imageio.ImageIO;
import javax.servlet.*;
import javax.servlet.http.*;

import developer.depth.Mapper;
import developer.depth.ScanUtils;

public class FrameGrabHTTP extends HttpServlet {
	
	private static Application app = null;
	private State state = State.getReference();
	
	private static int var;
	private static BufferedImage radarImage = null;
	private static Settings settings = Settings.getReference();
	
	public static void setApp(Application a) {
		if(app != null) return;
		app = a;
		var = 0;
	}
	
	public void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
		doPost(req,res);
	}
	
	public void doPost(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        if (req.getParameter("mode") != null) {

        	String mode = req.getParameter("mode");
            
            if (mode.equals("radar"))  radarGrab(req,res);            	
            else if(mode.equals("processedImg"))  processedImg(req,res); 
            else if (mode.equals("depthFrame") &&  Application.openNIRead.depthCamGenerating) { 	
            	Application.processedImage = Application.openNIRead.generateDepthFrameImg();
            	processedImg(req,res);
            }
            else if (mode.equals("floorPlane") && Application.openNIRead.depthCamGenerating) {
//            	short[] depthFrame = Application.openNIRead.readFullFrame();
//            	Application.processedImage = Application.scanMatch.floorPlaneImg(depthFrame);
            	Application.processedImage = Application.scanUtils.floorPlaneImg();
            	processedImg(req,res);
            }
            else if (mode.equals("floorPlaneTop") && Application.openNIRead.depthCamGenerating) {
            	Application.processedImage = ScanUtils.floorPlaneTopViewImg();
            	processedImg(req,res);
            }
            else if (mode.equals("map") && Application.openNIRead.depthCamGenerating) {
            	Application.processedImage = ScanUtils.cellsToImage(Mapper.map);         		
//            	if (req.getParameter("scale") != null) {
//            		double scale = Double.parseDouble(req.getParameter("scale"));
//                	Application.processedImage = ScanUtils.byteCellsToImage(Mapper.map, scale);         		
//            	}
            	processedImg(req,res);
            }

        }
		else { frameGrab(req,res); }        
	}
	
	private void frameGrab(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
		
		res.setContentType("image/jpeg");
		OutputStream out = res.getOutputStream();

		Application.framegrabimg = null;
		Application.processedImage = null;
		if (app.frameGrab()) {
			
			int n = 0;
			while (state.getBoolean(State.values.framegrabbusy)) {
//				while (img == null) {
				try {
					Thread.sleep(5);
				} catch (InterruptedException e) {
					e.printStackTrace();
				} 
				n++;
				if (n> 2000) {  // give up after 10 seconds 
					state.set(State.values.framegrabbusy, false);
					break;
				}
			}
			
			
			if (Application.framegrabimg != null) {
				for (int i=0; i<Application.framegrabimg.length; i++) {
					out.write(Application.framegrabimg[i]);
				}
			}
			
			else if (Application.processedImage != null) {
				
				ImageIO.write(Application.processedImage, "JPG", out);
			}
			
		    out.close();
		}
	}
	
	private void processedImg(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
		
		// send image
		res.setContentType("image/gif");
		OutputStream out = res.getOutputStream();
		ImageIO.write(Application.processedImage, "GIF", out);
//		out.close();
	}
	
	private void radarGrab(HttpServletRequest req, HttpServletResponse res) 
		throws ServletException, IOException {

		generateRadarImage();
		
		// send image
		res.setContentType("image/gif");
		OutputStream out = res.getOutputStream();
		ImageIO.write(radarImage, "GIF", out);
	}

	
	private void generateRadarImage() {

			final int w = 240;
			final int h = 320;
			BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
	
			final int voff = 0; // offset
			final double angle = 0.392699082; // 22.5 deg in radians from ctr, or half included view angle
			Graphics2D g2d = image.createGraphics();
			
			//render background
			g2d.setColor(new Color(10,10,10));  
			g2d.fill(new Rectangle2D.Double(0, 0, w, h));
			
			// too close out of range background fill
			g2d.setColor(new Color(23,25,0)); 
			int r = 40;
			g2d.fill(new Ellipse2D.Double( w/2-r, h-1-r*0.95+voff, r*2, r*2*0.95));
			
			// retrieve & render pixel data and shadows
			int maxDepthInMM = 3500;
			if (Application.openNIRead.depthCamGenerating == true) { 	
				WritableRaster raster = image.getRaster();
				int[] xdepth = Application.openNIRead.readHorizDepth(120); 
				/* TODO: need to figure out some way to drop request if taking too long
				 * above line hangs whole servlet?
				 */
				int[] dataRGB = {0,255,0}; // sensor data pixel colour
				g2d.setColor(new Color(0,70,0)); // shadow colour
				int xdctr = xdepth.length/2;
				for (int xd=0; xd < xdepth.length; xd++) {
					int y = (int) ((float)xdepth[xd]/(float)maxDepthInMM*(float)h);
					// x(opposite) = tan(angle)*y(adjacent)
					double xdratio = (double)(xd - xdctr)/ (double) xdctr;
		//			Util.log(Double.toString(xdratio),this);
					int x = (w/2) - ((int) (Math.tan(angle)*(double) y * xdratio));
					int xend = (w/2) - ((int) (Math.tan(angle)*(double) (h-1) * xdratio)); // for shadow fill past point
					if (y<h-voff && y>0+voff && x>=0 && x<w) {
						y = h-y-1+voff; //flip vertically
						g2d.drawLine(x, y, xend, 0);  //fill area behind with line
						raster.setPixel(x,y,dataRGB);
						raster.setPixel(x,y+1,dataRGB);
					}
				}
			}
			else {
				// pulsator
				g2d.setColor(new Color(0,0,155));
				var += 11;
				if (var > h + 50) { var = 0; }
				g2d.draw(new Ellipse2D.Double( w/2-var, h-1-var*0.95+voff, var*2, var*2*0.95));		
			}
			
			
			// dist scale arcs
			g2d.setColor(new Color(100,100,100));
			r = 100;
			g2d.draw(new Ellipse2D.Double( w/2-r, h-1-r*0.95+voff, r*2, r*2*0.95));
			r = 200;
			g2d.draw(new Ellipse2D.Double( w/2-r, h-1-r*0.95+voff, r*2, r*2*0.95));
			r = 300;
			g2d.draw(new Ellipse2D.Double( w/2-r, h-1-r*0.95+voff, r*2, r*2*0.95));	
			
			// outside cone colour fill
			g2d.setColor(new Color(23,25,0)); 
			for (int y= 0-voff; y<h+voff; y++) {
				int x = (int) (Math.tan(angle)*(double)(h-y-1));
				if (x>=0) {
					g2d.drawLine(0, y, (w/2)-x, y);  
					g2d.drawLine(w-1, y, (w/2)+x,y);
				}
	  
			}
			
			// cone perim lines
			g2d.setColor(new Color(100,100,100));
			int x = (int) (Math.tan(angle)*(double)(319));
			g2d.drawLine(w/2, 319, (w/2)-x, 0);
			g2d.drawLine(w/2, 319, (w/2)+x, 0);
			
			// radarImage = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
			radarImage = image;
//			radarImageGenerating = false;
//		} }).start();

	}

	public static void saveToFile(final String str) {
		
		final String urlString = "http://127.0.0.1:" + settings.readRed5Setting("http.port") + "/oculus/frameGrabHTTP";
		new Thread(new Runnable() {
			@Override
			public void run() {
				try {			
					int i = 1;
					if (!str.equals("")) { i = Integer.parseInt(str); }
					Downloader dl = new Downloader();
					String sep = Settings.sep;
					for(; i > 0 ; i--) {
						Util.debug(i + " framegrab save: " + urlString, this);
						DateFormat dateFormat = new SimpleDateFormat("dd-MMM-yyyy_HH-mm-ss");
						Calendar cal = Calendar.getInstance();
						String datetime = dateFormat.format(cal.getTime());

						dl.FileDownload(urlString, datetime + ".jpg", "webapps"+sep+"oculus"+sep+"framegrabs");
//						Util.saveUrl("capture/" + System.currentTimeMillis() + ".jpg", urlString );
					}
				} catch (Exception e) {
					Util.log("can't get image: " + e.getLocalizedMessage(), this);
				}
			}
		}).start();
	}
	

}
