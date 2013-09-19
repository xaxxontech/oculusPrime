package oculus;

import java.io.*;
import java.net.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Updater {
		
	public static final String path = "http://code.google.com/p/oculus/downloads/list";
	// "http://dev.xaxxon.com/files/");
	
	/** @return number of current version, or -1 if unknown */
	public int getCurrentVersion() {
		// log.info("reading current version");
		int currentVersion = -1;

		// get current version info from txt file in root folder
		String sep = System.getProperty("file.separator");
		String filename = System.getenv("RED5_HOME")+sep+"version.nfo";
		
		FileInputStream filein;
		try {
			filein = new FileInputStream(filename);
			BufferedReader reader = new BufferedReader(new InputStreamReader(filein));
			String line = "";
		    while ((line = reader.readLine()) != null) {
		    	if ((line.trim()).equals("version")) {
		    		currentVersion = Integer.parseInt((reader.readLine()).trim());
		    		break;
		    	}
		    }
			filein.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
		// log.info("read current version: "+currentVersion);
		return currentVersion;	
	}
	
	/**
	* @return filname url string
	* */
	public String checkForUpdateFile() {  
		String filename = "";
		
		//pull download list into string
		String downloadListPage = "";
		try {
			//URL url = new URL("http://code.google.com/p/oculus/downloads/list");
			//URL url = new URL("http://dev.xaxxon.com/files/");
			URLConnection con = new URL(path).openConnection();
			String charset = "ISO-8859-1";
			Reader r = new InputStreamReader(con.getInputStream(), charset);
			StringBuilder buf = new StringBuilder();
			while (true) {
			  int ch = r.read();
			  if (ch < 0)
			    break;
			  buf.append((char) ch);
			}
			downloadListPage = buf.toString();
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		
		// scrape html, check for matching update files
		// format =>  http://.../update*###.zip  where ### = version num (any number of digits)
		if (!downloadListPage.equals(null)) {
			BufferedReader reader = new BufferedReader(new StringReader(downloadListPage));
		    String str;
		    Pattern pat = Pattern.compile("oculus\\.googlecode\\.com/files/update\\w*\\.zip");
		    //Pattern pat = Pattern.compile("http://oculus\\.googlecode\\.com/files/update\\w*\\.zip");
		    // http://oculus.googlecode.com/files/
		    //Pattern pat = Pattern.compile("update\\w*\\.zip");
		    Matcher mat = null;
			try {
				while ((str = reader.readLine()) != null) {
					mat = pat.matcher(str);
					while (mat.find()) {
						filename = "http://"+mat.group();
						break;
					}
				}
			} catch(IOException e) {
			  e.printStackTrace();
			}
		}
		//if (!filename.equals("")) { filename = "http://dev.xaxxon.com/files/" + filename; }
		//System.out.println("filename="+filename);
		return filename; 
	}
	
	/**
	 * 
	 * @param url string 
	 * @return version integer
	 */
	public int versionNum(String url) {
		int version = -1;
		Pattern pat = Pattern.compile("\\d+$");
		url = url.replaceFirst("\\.zip$", "");
		Matcher mat = pat.matcher(url);
		while (mat.find()) {
			version = Integer.parseInt(mat.group());
			break;
		}
		return version;
	}
}
