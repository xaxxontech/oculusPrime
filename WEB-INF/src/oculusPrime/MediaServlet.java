package oculusPrime;

import java.io.*;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;

import javax.servlet.*;
import javax.servlet.http.*;

import oculusPrime.State.values;

/**
 * list the files in framegrabs and streams folders 
 */
public class MediaServlet extends HttpServlet {

	private static final long serialVersionUID = 1L;
	static BanList ban = BanList.getRefrence();
	static State state = State.getReference();
	
    public static final String IMAGE = "IMAGE";
    public static final String VIDEO = "VIDEO";
    public static final String AUDIO = "AUDIO";
    
    // private static final String ITEM = "<!--item-->";
    private static final String FILEEND = "</body></html>";

	public void doGet(final HttpServletRequest req, HttpServletResponse response) throws ServletException, IOException {

		if (!ban.knownAddress(req.getRemoteAddr())) {
			Util.log("unknown address: sending to login: " + req.getRemoteAddr(), this);
			response.sendRedirect("/oculusPrime");   
			return;
		}
		
		StringBuffer str = new StringBuffer();
		str.append("<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Transitional//EN\" \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd\">\n");
		str.append("<!-- DO NOT MODIFY THIS FILE, THINGS WILL BREAK -->\n");
		str.append("<html><head><title>Oculus Prime Media Files</title>\n" +
	                "<meta http-equiv=\"Pragma\" content=\"no-cache\">\n" +
	                "<meta http-equiv=\"Cache-Control\" Content=\"no-cache\">\n" +
	                "<meta http-equiv=\"Expires\" content=\"-1\">\n" +
					"<style type=\"text/css\">\n" +
	                "body { padding-bottom: 10px; margin: 0px; padding: 0px} \n" +
	                "body, p, ol, ul, td, tr {\n" +
	                "font-family: verdana, arial, helvetica, sans-serif;}\n");
		str.append("."+IMAGE+" {background-color: #FF8533; padding-top: 3px; padding-bottom: 3px; padding-left: 15px; padding-right: 10px; border-top: 1px solid #ffffff; }\n");
		str.append("."+VIDEO+" {background-color: #C2EBFF; padding-top: 3px; padding-bottom: 3px; padding-left: 15px; padding-right: 10px; border-top: 1px solid #ffffff; }\n");
		str.append("</style>\n");
	        
     	str.append("</head><body>\n");
        str.append("<div style='padding-left: 15px'>Oculus Prime Media Files </div>\n");

		response.setContentType("html");
		PrintWriter out = response.getWriter();
	
		String filterstr = null;
		try { filterstr = req.getParameter("filter"); } catch (Exception e) {}
		File[] files = null;
					
		final String f = filterstr;
		File[] streams = null;
		File[] frames = null; 	
		
		if(f == null){
			streams = new File(Settings.streamfolder).listFiles();	
			frames = new File(Settings.framefolder).listFiles();		
		} else {
			// not jdk7 compatible! fix?
//			streams = new File(Settings.streamfolder).listFiles((File pathname) -> pathname.getName().contains(f));
//			frames = new File(Settings.framefolder).listFiles((File pathname) -> pathname.getName().contains(f));
		}
		
		// merge lists and sort by date 
		files = new File[frames.length + streams.length];

		int total = 0;
		for(int i = 0; i < streams.length; i++) files[total++] = streams[i];  
		for(int j = 0; j < frames.length;  j++) files[total++] = frames[j];
		
		Arrays.sort(files, new Comparator<File>() {
		    public int compare(File f1, File f2) {
		        return Long.compare(f2.lastModified(), f1.lastModified());
		    }
		});

		for(int c = 0 ; c < files.length ; c++){
			
			if(files[c].getName().toLowerCase().endsWith(".jpg"))
				str.append( "<div class=\'"+IMAGE+"\'><a href=\"/oculusPrime/framegrabs/"+ files[c].getName()
					+ "\" target='_blank'>"
					+ files[c].getName() + "</a>      " + files[c].length() + " bytes" + "</div>\n");
			
			else // if(files[c].getName().toLowerCase().endsWith(".flv"))
				str.append( "<div class=\'"+VIDEO+"\'><a href=\"/oculusPrime/streams/"+ files[c].getName()
					+ "\" target='_blank'>"
					+ files[c].getName() + "</a>      " + files[c].length() + " bytes" + "</div>\n");
			
			
		}
	
        out.println(str+ FILEEND);
        out.close();	
	}
	
}