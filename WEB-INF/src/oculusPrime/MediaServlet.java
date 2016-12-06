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
	        
     	str.append("</head><body onload=\"loaded();\">\n");
        str.append("<div style='padding-top: 5px; padding-bottom: 5px; padding-left: 15px; cursor: pointer;'");
        str.append("onclick=\"window.open(document.URL.replace(/#.*$/, ''), '_self'); \" ");
        str.append("<div>Oculus Prime Media Files </div>\n");

		response.setContentType("html");
		PrintWriter out = response.getWriter();

		File[] streams = new File(Settings.streamfolder).listFiles();	
		File[] frames = new File(Settings.framefolder).listFiles();	
		File[] files = new File[frames.length + streams.length];

		int total = 0;
		for(int i = 0; i < streams.length; i++) files[total++] = streams[i];  
		for(int j = 0; j < frames.length;  j++) files[total++] = frames[j];

		Arrays.sort(files, new Comparator<File>() {
		    public int compare(File f1, File f2) {
		        return Long.compare(f2.lastModified(), f1.lastModified());
		    }
		});

		for(int c = 0 ; c < total ; c++){
			
			if(files[c].getName().toLowerCase().endsWith(".jpg"))
				str.append( "<div class=\'"+IMAGE+"\'><a href=\"http://" + state.get(values.externaladdress) + ":" + state.get(values.httpport) 
					+ "/oculusPrime/framegrabs/"+ files[c].getName() + "\">"
					+ files[c].getName() + "</a>      " + files[c].length() + " bytes" + "</div>\n");
			
			else // if(files[c].getName().toLowerCase().endsWith(".flv"))
				str.append( "<div class=\'"+VIDEO+"\'><a href=\"http://" + state.get(values.externaladdress) + ":" + state.get(values.httpport) 
					+ "/oculusPrime/streams/"+ files[c].getName() + "\">"
					+ files[c].getName() + "</a>      " + files[c].length() + " bytes" + "</div>\n");
			
			
		}
		
		/*
		 	for(int i = 0; i < files.length; i++){
			if(files[i].isFile()){
				str.append( "<div class=\'"+VIDEO+"\'><a href=\"http://" + state.get(values.externaladdress) + ":" + state.get(values.httpport) 
					+ "/oculusPrime/streams/"+ files[i].getName() + "\">"
					+ files[i].getName() + "</a>      " + files[i].length() + " bytes" + "</div>\n");
	        }
		} 

		// File[] 
		files  = new File(Settings.framefolder).listFiles();	
		for(int i = 0; i < files.length; i++){
			if(files[i].isFile()){
				str.append( "<div class=\'"+IMAGE+"\'><a href=\"http://" + state.get(values.externaladdress) + ":" + state.get(values.httpport) 
					+ "/oculusPrime/framegrabs/"+ files[i].getName() + "\">"  
					+ files[i].getName() + "</a>      " + files[i].length() + " bytes" + "</div>\n");
	        }
		} 
		 */
	
        out.println(str+ FILEEND);
        out.close();	
	}
}