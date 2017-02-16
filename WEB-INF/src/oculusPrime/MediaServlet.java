package oculusPrime;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Comparator;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

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
	                "a { text-decoration: none; } \n" +
	                "body { padding-bottom: 10px; margin: 0px; padding: 0px} \n" +
	                "body, p, ol, ul, td, tr, td { font-family: verdana, arial, helvetica, sans-serif;}\n");
		str.append("."+IMAGE+" {background-color: #FF8533; padding-top: 3px; padding-bottom: 3px; padding-left: 15px; padding-right: 10px; border-top: 1px solid #ffffff; }\n");
		str.append("."+VIDEO+" {background-color: #C2EBFF; padding-top: 3px; padding-bottom: 3px; padding-left: 15px; padding-right: 10px; border-top: 1px solid #ffffff; }\n");
		str.append("</style>\n");   
     	str.append("</head><body>\n");
        str.append("<div style='padding-top: 5px; padding-bottom: 5px; padding-left: 15px; '><b>Oculus Prime Media Files </b></div>\n");
		response.setContentType("html");
		PrintWriter out = response.getWriter();
		
		String filterstr = null;
		try { filterstr = req.getParameter("filter"); } catch (Exception e) {}
		File[] files = null;
					
		final String f = filterstr;
		File[] streams;
		File[] frames;
		
		if(f == null){
			streams = new File(Settings.streamfolder).listFiles();	
			frames = new File(Settings.framefolder).listFiles();		
		} else {

// not jdk7 compatible! fix?
//			streams = new File(Settings.streamfolder).listFiles((File pathname) -> pathname.getName().contains(f));
//			frames = new File(Settings.framefolder).listFiles((File pathname) -> pathname.getName().contains(f));

			streams = new File(Settings.streamfolder).listFiles(new FileFilter() {
				@Override public boolean accept(File file) { return file.getName().contains(f); }
			});
			
			frames = new File(Settings.framefolder).listFiles(new FileFilter() {
				@Override public boolean accept(File file) { return file.getName().contains(f); }
			});	
		}

		int frameslength = 0;
		if (frames != null) frameslength = frames.length;
		int streamslength = 0;
		if (streams != null) streamslength = streams.length;
		// merge lists and sort by date

		if (streamslength > 0 || frameslength > 0) {
			files = new File[frameslength + streamslength];

			int total = 0;
			for (int i = 0; i < streamslength; i++) files[total++] = streams[i];
			for (int j = 0; j < frameslength; j++) files[total++] = frames[j];

			Arrays.sort(files, new Comparator<File>() {
				public int compare(File f1, File f2) {
					return Long.compare(f2.lastModified(), f1.lastModified());
				}
			});

			str.append("\n <table> ");
			for (int c = 0; c < files.length; c++) {

				if (files[c].getName().toLowerCase().endsWith(".jpg"))
					str.append("<tr><td><div class=\'" + IMAGE + "\'><a href=\"/oculusPrime/framegrabs/" + files[c].getName()
							+ "\" target='_blank'>"
							+ files[c].getName() + "</a></div><td style=\"text-align: right;\">" + files[c].length() + " bytes" + "</tr>\n");

				else // if(files[c].getName().toLowerCase().endsWith(".flv"))
					str.append("<tr><td><div class=\'" + VIDEO + "\'><a href=\"/oculusPrime/streams/" + files[c].getName()
							+ "\" target='_blank'>"
							+ files[c].getName() + "</a></div><td style=\"text-align: right;\">" + files[c].length() + " bytes" + "</tr>\n");
			}

			str.append("\n </table> ");
		}
		else { str.append ("<div style='padding-left: 15px'>No media yet</div>" ); }
        out.println(str+ FILEEND);
        out.close();	
	}
}
	