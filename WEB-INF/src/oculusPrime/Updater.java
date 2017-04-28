package oculusPrime;

import java.io.*;
import java.net.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Updater {
		
	public static final String path = "https://www.xaxxon.com/downloads/";
	
	/** @return number of current version, or -1 if unknown */
	public double getCurrentVersion() {
		// log.info("reading current version");
		double currentVersion = -1;

		// get current version info from txt file in root folder
		String filename =  Settings.redhome+Util.sep+"version.nfo";
		
		FileInputStream filein;
		try {
			filein = new FileInputStream(filename);
			BufferedReader reader = new BufferedReader(new InputStreamReader(filein));
			String line = "";
		    while ((line = reader.readLine()) != null) {
		    	if ((line.trim()).equals("version")) {
//		    		currentVersion = Integer.parseInt((reader.readLine()).trim());
		    		currentVersion = Double.parseDouble((reader.readLine()).trim());
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
		// format =>  updatepackage_oculusprime_server_v###.zip  => where ### = digits or .
		if (!downloadListPage.equals(null)) {
			BufferedReader reader = new BufferedReader(new StringReader(downloadListPage));
		    String str;
		    Pattern pat = Pattern.compile("updatepackage_oculusprime_server_v.{0,12}\\.zip");
		    Matcher mat = null;
			try {
				while ((str = reader.readLine()) != null) {
					mat = pat.matcher(str);
					while (mat.find()) {
						filename = path+mat.group();
						break;
					}
				}
			} catch(IOException e) {
			  e.printStackTrace();
			}
		}
		Util.log("update filename: "+filename,this);
		return filename; 
	}
	
	/**
	 * 
	 * @param url string 
	 * @return version integer
	 */
	public double versionNum(String url) {
		double version = -1;
		Pattern pat = Pattern.compile("\\d+\\.+\\d*$");
		url = url.replaceFirst("\\.zip$", "");
		Matcher mat = pat.matcher(url);
		while (mat.find()) {
			version = Double.parseDouble(mat.group());
			break;
		}
		Util.log("update version: "+version,this);
		return version;
	}

	/**
	 * download hex firmware file and install via system call to avrdude
	 * note: blocking
	 *
	 * @param id firmware id
	 * @param version firmware version no.
	 * @param port  current USB port
	 */
	public static void updateFirmware(final String id, final Double version, final String port) {

		// download file
		String filename = id+"_"+version+".hex";
		String fileurl = path+filename;
		String folder = "avrdude";
		Util.log("Updater.updateFirmware() downloading url: " + fileurl, null);
		Downloader dl = new Downloader();
		if (! dl.FileDownload(fileurl, filename, folder, Util.ONE_MINUTE)) {
			Util.log("Updater.updateFirmware() error, file download failed", null);
			return;
		}

		// run avrdude
		String cmd= "./avrdude";
		String args = "-v -p atmega328p -c arduino -P "+port+
				" -b 57600 -C avrdude.conf -D -U flash:w:"+filename+":i";
		Util.log("Updater.updateFirmware(): "+cmd+" "+args, null);

		try {

			Process proc = Runtime.getRuntime().exec( Settings.redhome+Util.sep+folder+Util.sep+"run "+cmd+" "+args);
			proc.waitFor();

			// cleanup
			File file = new File( Settings.redhome+Util.sep+folder+Util.sep+filename);
			Util.log("Updater.updateFirmware(): deleting file "+file.getAbsolutePath(), null);
			file.delete();

		} catch (Exception e) {
			e.printStackTrace();
		}

	}
}
