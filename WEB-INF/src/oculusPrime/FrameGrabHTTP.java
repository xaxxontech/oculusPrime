package oculusPrime;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.RenderedImage;
import java.awt.image.WritableRaster;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.Vector;

import javax.imageio.ImageIO;
import javax.servlet.ServletException;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.Part;

import developer.Navigation;
import developer.Ros;
import developer.depth.Mapper;
import developer.depth.ScanUtils;
import oculusPrime.State.values;

@SuppressWarnings("serial")
@MultipartConfig(fileSizeThreshold=1024*1024*2,    // 2MB
		maxFileSize=1024*1024*10,                  // 10MB
		maxRequestSize=1024*1024*50)               // 50MB

public class FrameGrabHTTP extends HttpServlet {
	
	private static State state = State.getReference();
	private static BanList ban = BanList.getRefrence();  // TODO: PULL DATA FROM LOG FILES 
	private static BufferedImage batteryImage = null;
	private static RenderedImage cpuImage = null;
	private static BufferedImage radarImage = null;
	private static Application app = null;
	private static int var = 0;

	private static final int MAX_STATE_HISTORY = 100;
	Vector<String> history = new Vector<String>(MAX_STATE_HISTORY);
	
	public static void setApp(Application a) { app = a; }
	
	public void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException { doPost(req,res); }
	public void doPost(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {

		if( ! ban.knownAddress(req.getRemoteAddr())){
			Util.log("unknown address, blocked: "+req.getRemoteAddr(), this);
			return;
		}

        if (req.getParameter("mode") != null) {
        	final String mode = req.getParameter("mode");
            
            if (mode.equals("radar"))  radarGrab(req,res);     
            else if (mode.equals("battery")) batteryGrab(req, res);
            else if (mode.equals("cpu")) cpuGrab(req, res);       	
            else if (mode.equals("processedImg"))  processedImg(req,res);
			else if (mode.equals("processedImgJPG"))  processedImgJPG(req,res);
			else if (mode.equals("videoOverlayImg")) videoOverlayImg(req, res);
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
            else if (mode.equals("map")) {
            	Application.processedImage = ScanUtils.cellsToImage(Mapper.map);         		
//            	if (req.getParameter("scale") != null) {
//            		double scale = Double.parseDouble(req.getParameter("scale"));
//                	Application.processedImage = ScanUtils.byteCellsToImage(Mapper.map, scale);         		
//            	}
            	processedImg(req,res);
            }
         
            else if (mode.equals("rosmap")) {
            	Application.processedImage = Ros.rosmapImg();
				if (!state.exists(State.values.rosmapinfo))
					app.driverCallServer(PlayerCommands.messageclients, "map data unavailable, try starting navigation system");
            	processedImg(req,res);
            }
            else if (mode.equals("rosmapinfo")) { // xmlhttp text
        		res.setContentType("text/html");
        		PrintWriter out = res.getWriter();
        		out.print(Ros.mapinfo());
        		out.close();
            }
            else if (mode.equals("routesload")) {
        		res.setContentType("text/html");
        		PrintWriter out = res.getWriter();
        		out.print(Navigation.routesLoad());
        		out.close();
            }
			else if (mode.equals("rosmapdownload")) {
				res.setContentType("image/x-portable-graymap");
				res.setHeader("Content-Disposition", "attachment; filename=\"map.pgm\"");
				FileInputStream a = new FileInputStream(Ros.getMapFilePath()+Ros.mapfilename);
				while(a.available() > 0)
					res.getWriter().append((char)a.read());
				a.close();
			}
			else if (mode.equals("rosmapupload")) {
				if (!state.get(State.values.navsystemstatus).equals(Ros.navsystemstate.stopped.toString())) {
					app.message("unable to modify map while navigation running", null, null);
					return;
				}
				Part part = req.getParts().iterator().next();
				if (part == null) {
					app.message("problem uploading, map not saved", null, null);
					return;
				}

				File save = new File(Ros.getMapFilePath(), Ros.mapfilename );
				Ros.backUpMappgm();
				part.write(save.getAbsolutePath());
				app.message("map saved as: " + save.getAbsolutePath(), null, null);
			}
        }
		else { frameGrab(req, res); }
	}
	
	private void frameGrab(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
		
		res.setContentType("image/jpeg");
		OutputStream out = res.getOutputStream();

		Application.framegrabimg = null;
		Application.processedImage = null;
		if (app.frameGrab()) {
			
			int n = 0;
			while (state.getBoolean(State.values.framegrabbusy)) {
				Util.delay(5);
				n++;
				if (n> 2000) {  // give up after 10 seconds 
					state.set(State.values.framegrabbusy, false);
					break;
				}
			}

			if (Application.framegrabimg != null) { // TODO: unused?
				for (int i=0; i<Application.framegrabimg.length; i++) {
					out.write(Application.framegrabimg[i]);
				}
			}

			else {
				if (Application.processedImage != null) {
					ImageIO.write(Application.processedImage, "JPG", out);
				}
			}
			
		    out.close();
		}
	}
	
	private void processedImg(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
		
		if (Application.processedImage == null) return;
		
		// send image
		res.setContentType("image/gif");
		OutputStream out = res.getOutputStream();
		ImageIO.write(Application.processedImage, "GIF", out);
	}

	private void processedImgJPG(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {

		if (Application.processedImage == null) return;

		// send image
		res.setContentType("image/jpg");
		OutputStream out = res.getOutputStream();
		ImageIO.write(Application.processedImage, "JPG", out);
	}

	private void videoOverlayImg(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {

		if (Application.videoOverlayImage == null)
			Application.videoOverlayImage= new BufferedImage(640, 480, BufferedImage.TYPE_INT_RGB);

		// send image
		res.setContentType("image/jpg");
		OutputStream out = res.getOutputStream();
		ImageIO.write(Application.videoOverlayImage, "JPG", out);
	}
	
	private void batteryGrab(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
		generateBatteryImage();
		res.setContentType("image/gif");
		OutputStream out = res.getOutputStream();
		ImageIO.write(batteryImage, "GIF", out);
	}
	
	private void cpuGrab(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
		generateCpuImagemage();
		res.setContentType("image/gif");
		OutputStream out = res.getOutputStream();
		ImageIO.write(cpuImage, "GIF", out);
	} 
	
	private void radarGrab(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {

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
//			g2d.setColor(new Color(23,25,0)); 
			g2d.setColor(new Color(20,20,20)); 
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
//				for (int xd=xdepth.length-1; xd>=0; xd--) {
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
//			g2d.setColor(new Color(23,25,0)); // blue opposite comp?
			g2d.setColor(new Color(20,20,20)); 
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

	//TODO: STUB ONLY, FILL IN FROM power HISTORY 
	private void generateBatteryImage() {

			final int w = 500;
			final int h = 200;
			BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
			Graphics2D g2d = image.createGraphics();
			
			//render background
		//	g2d.setColor(new Color(60,60,90));  
		//	g2d.fill(new Rectangle2D.Double(0, 0, w, h));
		//
	    //    g2d.setFont(new Font("Serif", Font.BOLD, 45));
	        String s = "generateBatteryImage";
	        
	        //g2d.drawString(s, 10, h/2);
	        //g2d.drawLine(0, 0, w, h);	
	        g2d.setPaint(Color.red);
	        //g2d.drawLine(0, h/3, w/3, h/3);
	        
			batteryImage = image;
	}

	//TODO: STUB ONLY, FILL IN FROM CPU HISTORY 
	private void generateCpuImagemage() {
 
		final int radius = 6;
		final int w = 500;
		final int h = 100;
		BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
		Graphics2D g2d = image.createGraphics();
	
		g2d.setPaint(Color.yellow);
        drawCenteredCircle(g2d, 8, 8, radius);
        g2d.drawPolyline(new int[]{10, w/3, w/2, w-radius}, new int[]{10, 20, 90, 50}, 4);
        
        g2d.setPaint(Color.green);
        for( int i = 0 ; i < history.size() ; i++ ){
    //    	Util.log(i + " " +  Integer.parseInt(history.get(i)));
        	drawCenteredCircle(g2d, i*5, Integer.parseInt(history.get(i)), radius);
        }

  
    //    g2d.setPaint(Color.red);
    //    drawCenteredCircle(g2d, w/2, h/2, radius);
        
        g2d.setPaint(Color.red);
   //     drawCenteredCircle(g2d, w-radius, h-radius, radius);
   //     drawCenteredCircle(g2d, 1, 1, 8);
        g2d.drawPolyline(new int[]{5, w-5, w-5, 5, 5}, new int[]{5, 5, h-5, h-5, 5}, 5);
         
		cpuImage = image;
	}
	public void drawCenteredCircle(Graphics2D g, int x, int y, int r) {
		x = x-(r/2);
		y = y-(r/2);
		g.fillOval(x,y,r,r);
	}
	
	/**
	 * @param args download url params, can be null
	 * @return returns download url of saved image
	 */
	public static String saveToFile(String args) {	
		String urlString = "http://127.0.0.1:" + state.get(State.values.httpport) + "/oculusPrime/frameGrabHTTP";
		if(args != null) if(args.startsWith("?")) urlString += args; 
		final String url = urlString;
		
		String datetime = Util.getDateStamp();  // && state.get(State.values.navsystemstatus).equals(Ros.navsystemstate.running.toString()))  
		if(state.exists(values.roswaypoint) && state.equals(values.navsystemstatus, Ros.navsystemstate.running.toString()))    
			datetime += "_" + state.get(values.roswaypoint).replaceAll(" ", "_");
		
        final String name = datetime + ".jpg";
		new Thread(new Runnable() {
			public void run() {
				new Downloader().FileDownload(url, name, "webapps/oculusPrime/framegrabs"); // TODO: EVENT ON NULL ?
			}
		}).start();
		return "/oculusPrime/framegrabs/"+name;
	}

	/** add extra text into file name after timestamp */
	public static String saveToFile(final String args, final String optionalname) {
		String urlString = "http://127.0.0.1:" + state.get(State.values.httpport) + "/oculusPrime/frameGrabHTTP";
		if(args != null) if(args.startsWith("?")) urlString += args; 
		final String url = urlString;
		
		String datetime = Util.getDateStamp();  // no spaces in filenames       
		if(state.exists(values.roswaypoint)) //  && state.get(State.values.navsystemstatus).equals(Ros.navsystemstate.running.toString())) 
			datetime += "_" + state.get(values.roswaypoint);
		
		if(state.equals(values.dockstatus, AutoDock.DOCKED)) datetime += "_docked"; 
		
        final String name = (datetime + "_"+optionalname + ".jpg").replaceAll(" ", "_"); // no spaces in filenames       
		new Thread(new Runnable() {
			public void run() {
				new Downloader().FileDownload(url, name, "webapps/oculusPrime/framegrabs");
			}
		}).start();
		return "/oculusPrime/framegrabs/"+name;
	}

}


