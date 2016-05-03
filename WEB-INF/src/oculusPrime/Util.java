package oculusPrime;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Comparator;
import java.util.Date;
import java.util.Vector;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import developer.NavigationLog;
import oculusPrime.State.values;
import oculusPrime.commport.PowerLogger;

public class Util {
	
	public final static String sep = System.getProperty("file.separator");

	public static final long ONE_DAY = 86400000;
	public static final long ONE_MINUTE = 60000;
	public static final long TWO_MINUTES = 120000;
	public static final long FIVE_MINUTES = 300000;
	public static final long TEN_MINUTES = 600000;
	public static final long ONE_HOUR = 3600000; 
	public static final int MIN_FILE_COUNT = 20;  
	public static final int MAX_HISTORY = 60;
	public static final int PRECISION = 2;	
	
	static Vector<String> history = new Vector<String>(MAX_HISTORY);
	static private String rosinfor = null;
	static private int rosattempts = 0;
	
	public static void delay(long delay) {
		try { Thread.sleep(delay); } 
		catch (Exception e){ printError(e); }
	}

	public static void delay(int delay) {
		try { Thread.sleep(delay); } 
		catch (Exception e){ printError(e); }
	}

	public static String getTime() {
        Date date = new Date();
		return date.toString();
	}

	public static String getDateStamp() {
		DateFormat dateFormat = new SimpleDateFormat("yyyy-M-dd_HH-mm-ss");
		Calendar cal = Calendar.getInstance();
		return dateFormat.format(cal.getTime());
	}

	/**
	 * Returns the specified double, formatted as a string, to n decimal places,
	 * as specified by precision.
	 * <p/>
	 * ie: formatFloat(1.1666, 1) -> 1.2 ie: formatFloat(3.1666, 2) -> 3.17 ie:
	 * formatFloat(3.1666, 3) -> 3.167
	 */
	public static String formatFloat(double number, int precision) {

		String text = Double.toString(number);
		if (precision >= text.length()) {
			return text;
		}

		int start = text.indexOf(".") + 1;
		if (start == 0)
			return text;

		if (precision == 0) {
			return text.substring(0, start - 1);
		}

		if (start <= 0) {
			return text;
		} else if ((start + precision) <= text.length()) {
			return text.substring(0, (start + precision));
		} else {
			return text;
		}
	}

	public static String formatFloat(String text, int precision) {
		int start = text.indexOf(".") + 1;
		if (start == 0) return text;

		if (precision == 0) return text.substring(0, start - 1);
	
		if (start <= 0) {
			return text;
		} else if ((start + precision) <= text.length()) {
			return text.substring(0, (start + precision));
		} else {
			return text;
		}
	}
	
	/**
	 * Returns the specified double, formatted as a string, to n decimal places,
	 * as specified by precision.
	 * <p/>
	 * ie: formatFloat(1.1666, 1) -> 1.2 ie: formatFloat(3.1666, 2) -> 3.17 ie:
	 * formatFloat(3.1666, 3) -> 3.167
	 */
	public static String formatFloat(double number) {

		String text = Double.toString(number);
		if (PRECISION >= text.length()) {
			return text;
		}

		int start = text.indexOf(".") + 1;
		if (start == 0)
			return text;

		if (start <= 0) {
			return text;
		} else if ((start + PRECISION) <= text.length()) {
			return text.substring(0, (start + PRECISION));
		} else {
			return text;
		}
	}

	/*
	public static boolean copyfile(String srFile, String dtFile) {
		try {
			
			File f1 = new File(srFile);
			File f2 = new File(dtFile);
			InputStream in = new FileInputStream(f1);

			// Append
			OutputStream out = new FileOutputStream(f2, true);

			byte[] buf = new byte[1024];
			int len;
			while ((len = in.read(buf)) > 0) {
				out.write(buf, 0, len);
			}
			in.close();
			out.close();

		} catch (Exception e) {
			System.out.println(e.getMessage());
			return false;
		}

		// file copied
		return true;
	}
	*/
	
	/**
	 * Run the given text string as a command on the host computer. 
	 * 
	 * @param args is the command to run, like: "restart
	 * 
	 */
	public static void systemCallBlocking(final String args) {
		try {	

			Process proc = Runtime.getRuntime().exec(args);

//			long start = System.currentTimeMillis();
//			BufferedReader procReader = new BufferedReader(new InputStreamReader(proc.getInputStream()));
//			String line = null;
//			System.out.println(proc.hashCode() + "OCULUS: exec():  " + args);
//			while ((line = procReader.readLine()) != null)
//				System.out.println(proc.hashCode() + " systemCallBlocking() : " + line);
			
			proc.waitFor(); // required for linux else throws process hasn't terminated error

//			System.out.println("OCULUS: process exit value = " + proc.exitValue());
//			System.out.println("OCULUS: blocking run time = " + (System.currentTimeMillis()-start) + " ms");

		} catch (Exception e) {
			e.printStackTrace();
		}
	}	

	/**
	 * Run the given text string as a command on the windows host computer. 
	 * 
	 * @param str is the command to run, like: "restart"
	 */
	public static void systemCall(final String str){
		try { Runtime.getRuntime().exec(str); 
		} catch (Exception e) { printError(e); }
	}

	public static void setSystemVolume(int percent){
		if (State.getReference().get(values.osarch).equals(Application.ARM))
			try {
//				Util.systemCall("amixer set Master " + percent + "%"); // doesn't work in xubuntu 14.04 fresh install
				Util.systemCall("amixer set PCM "+percent+"%"); // works raspian
			} catch (Exception e) { log("Util.setSystemVolume amixer command error", null); }
		else {
//		Util.systemCall("pactl -- set-sink-volume 0 "+percent+"%"); 		// pactl -- set-sink-volume 0 80%
			try {
				Runtime.getRuntime().exec("pactl -- set-sink-volume 0 " + percent + "%");
			} catch (Exception e) {
				log("Util.setSystemVolume; error setting volume with pulse audio pactl command", null);
			}
		}
		Settings.getReference().writeSettings(GUISettings.volume.name(), percent);
	}

	public static void saveUrl(String filename, String urlString) throws MalformedURLException, IOException {
        BufferedInputStream in = null;
        FileOutputStream fout = null;
        try{
                in = new BufferedInputStream(new URL(urlString).openStream());
                fout = new FileOutputStream(filename);
                byte data[] = new byte[1024];
                int count;
                while ((count = in.read(data, 0, 1024)) != -1)
                	fout.write(data, 0, count);	
                
        } finally {    
        	if (in != null) in.close();
            if (fout != null) fout.close();
        }
    }
	
	public static String tail(int lines){
		int i = 0;
		StringBuffer str = new StringBuffer();
	 	if(history.size() > lines) i = history.size() - lines;
		for(; i < history.size() ; i++) str.append(history.get(i) + "\n<br>"); 
		return str.toString();
	}
	
	public static String tailFormated(int lines){
		int i = 0;
		final long now = System.currentTimeMillis();
		StringBuffer str = new StringBuffer();
	 	if(history.size() > lines) i = history.size() - lines;
		for(; i < history.size() ; i++) {
			String line = history.get(i).substring(history.get(i).indexOf(",")+1).trim();
			String stamp = history.get(i).substring(0, history.get(i).indexOf(","));
			line = line.replaceFirst("\\$[0-9]", "");
			line = line.replaceFirst("^oculusprime.", "");
			line = line.replaceFirst("^oculusPrime.", "");
			line = line.replaceFirst("^Application.", "");
			line = line.replaceFirst("^static, ", "");		
			double delta = (double)(now - Long.parseLong(stamp)) / (double) 1000;
			String unit = " sec ";
			String d = formatFloat(delta, 0);
			if(delta > 60) { delta = delta / 60; unit = " min "; d =  formatFloat(delta, 1); }
			str.append("\n<tr><td colspan=\"11\">" + d + "<td>" + unit + "<td>&nbsp;&nbsp;" + line + "</tr> \n"); 
		}
		return str.toString();
	}
	
	public static void log(String method, Exception e, Object c) {
		log(method + ": " + e.getLocalizedMessage(), c);
	}
	
	public static void log(String str, Object c) {
    	if(str == null) return;
		String filter = "static";
		if(c!=null) filter = c.getClass().getName();
		if(history.size() > MAX_HISTORY) history.remove(0);
		history.add(System.currentTimeMillis() + ", " + filter + ", " +str);
		System.out.println("OCULUS: " + getTime() + ", " + filter + ", " + str);
	}
	
    public static void debug(String str, Object c) {
    	if(str == null) return;
    	String filter = "static";
    	if(c!=null) filter = c.getClass().getName();
		if(Settings.getReference().getBoolean(ManualSettings.debugenabled)) {
			System.out.println("DEBUG: " + getTime() + ", " + filter +  ", " +str);
			history.add(System.currentTimeMillis() + ", " +str);
		}
	}
    
    public static void debug(String str) {
    	if(str == null) return;
    	if(Settings.getReference().getBoolean(ManualSettings.debugenabled)){
    		System.out.println("DEBUG: " + getTime() + ", " +str);
    		history.add(System.currentTimeMillis() + ", " +str);
    	}
    }
    
	public static String memory() {
    	String str = "";
		str += "memory : " + ((double)Runtime.getRuntime().freeMemory()
			/ (double)Runtime.getRuntime().totalMemory())*100 + "% free<br>";
		
		str += "memory total : "+Runtime.getRuntime().totalMemory()+"<br>";    
	    str += "memory free : "+Runtime.getRuntime().freeMemory()+"<br>";
		return str;
    }
	
	public static Document loadXMLFromString(String xml){
		try {
	    
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			DocumentBuilder builder;
		
			builder = factory.newDocumentBuilder();
			InputSource is = new InputSource(new StringReader(xml));
			return builder.parse(is);
		
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	// replaces standard e.printStackTrace();
	public static String XMLtoString(Document doc) {
		String output = null;
		try {
			TransformerFactory tf = TransformerFactory.newInstance();
			Transformer transformer = tf.newTransformer();
			transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
			StringWriter writer = new StringWriter();
			transformer.transform(new DOMSource(doc), new StreamResult(writer));
			output = writer.getBuffer().toString().replaceAll("\n|\r", "");
		}
		catch (Exception e) {
			e.printStackTrace();
		}
		return output;
	}

	public static void printError(Exception e) {
		System.err.println("error "+getTime()+ ":");
		e.printStackTrace();
	}
	
	public static boolean validIP (String ip) {
	    try {
	    	
	        if (ip == null || ip.isEmpty()) return false;
	        String[] parts = ip.split( "\\." );
	        if ( parts.length != 4 ) return false;
	        for ( String s : parts ) {
	            int i = Integer.parseInt( s );
	            if ( (i < 0) || (i > 255) )
	            	return false;
	        }
	        
	        if(ip.endsWith(".")) return false;
	        
	        return true;
	    } catch (NumberFormatException nfe) {
	        return false;
	    }
	}

	public static long[] readProcStat() {
		try {

			BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream("/proc/stat")));
			String line = reader.readLine();
			reader.close();
			String[] values = line.split("\\s+");
			long total = Long.valueOf(values[1])+Long.valueOf(values[2])+Long.valueOf(values[3])+Long.valueOf(values[4]);
			long idle = Long.valueOf(values[4]);
			return new long[] { total, idle};

		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	public static int getCPU(){
		long[] procStat = readProcStat();
		long totproc1st = procStat[0];
		long totidle1st = procStat[1];
		Util.delay(100);
		procStat = readProcStat();
		long totproc2nd = procStat[0];
		long totidle2nd = procStat[1];
		int percent = (int) ((double) ((totproc2nd-totproc1st) - (totidle2nd - totidle1st))/ (double) (totproc2nd-totproc1st) * 100);
		State.getReference().set(values.cpu, percent);
		return percent;
	}

	// top -bn 2 -d 0.1 | grep '^%Cpu' | tail -n 1 | awk '{print $2+$4+$6}'
	// http://askubuntu.com/questions/274349/getting-cpu-usage-realtime
	/*
	public static String getCPUTop(){
		try {

			String[] cmd = { "/bin/sh", "-c", "top -bn 2 -d 5 | grep '^%Cpu' | tail -n 1 | awk \'{print $2+$4+$6}\'" };
			Process proc = Runtime.getRuntime().exec(cmd);
			BufferedReader procReader = new BufferedReader(new InputStreamReader(proc.getInputStream()));
			return procReader.readLine();

		} catch (Exception e) {
			e.printStackTrace();
		}

		return null;
	}	
	
	public static boolean testHTTP(){
		
		final String ext = State.getReference().get(values.externaladdress); 
		final String http = State.getReference().get(State.values.httpport);
		final String url = "http://"+ext+":"+ http +"/oculusPrime";
		
		if(ext == null || http == null) return false;
	
		try {
			
			log("testPortForwarding(): "+url, "testHTTP()");
			URLConnection connection = (URLConnection) new URL(url).openConnection();
			BufferedReader procReader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
			log("testPortForwarding(): "+procReader.readLine(), "testHTTP()");

		} catch (Exception e) {
			 log("testPortForwarding(): failed: " + url, "testHTTP()");
			return false;
		}
		
		return true;
	}
	
	public static boolean testTelnetRouter(){			
		try {

			// "127.0.0.1"; //
			final String port = Settings.getReference().readSetting(GUISettings.telnetport);
			final String ext =State.getReference().get(values.externaladdress);
			log("...telnet test: " +ext +" "+ port, null);
			Process proc = Runtime.getRuntime().exec("telnet " + ext + " " + port);
			BufferedReader procReader = new BufferedReader(new InputStreamReader(proc.getInputStream()));
			
			String line = procReader.readLine();
			if(line.toLowerCase().contains("trying")){
				line = procReader.readLine();
				if(line.toLowerCase().contains("connected")){
					log("telnet test pass...", null);
					return true;
				}
			}
		} catch (Exception e) {
			log("telnet test fail..."+e.getLocalizedMessage(), null);
			return false;
		}
		log("telnet test fail...", null);
		return false;
	}
	

	public static boolean testRTMP(){	
		try {

			final String ext = "127.0.0.1"; //State.getReference().get(values.externaladdress); //	
			final String rtmp = Settings.getReference().readRed5Setting("rtmp.port");

			log("testRTMP(): http = " +ext, null);
			
			Process proc = Runtime.getRuntime().exec("telnet " + ext + " " + rtmp);
			BufferedReader procReader = new BufferedReader(new InputStreamReader(proc.getInputStream()));

			String line = procReader.readLine();
			log("testRTMP(): " + line, null);
			line = procReader.readLine();
			log("testRTMP():" + line, null);
			log("testRTMP(): process exit value = " + proc.exitValue(), null);
			
			if(line == null) return false;
			else if(line.contains("Connected")) return true;
			
		} catch (Exception e) {
			return false;
		}
		
		return true;
	}
	
	public static String getJavaStatus(){
		
		if(redPID==null) return "jetty not running";
		
		String line = null;
		try {
			
			BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream("/proc/"+ redPID +"/stat")));
			line = reader.readLine();
			reader.close();
			log("getJavaStatus:" + line, null);
					
		} catch (Exception e) {
			printError(e);
		}
		
		return line;
	}
	
	public static String getRed5PID(){	
		
		if(redPID!=null) return redPID;
		
		String[] cmd = { "/bin/sh", "-c", "ps -fC java" };
		
		Process proc = null;
		try { 
			proc = Runtime.getRuntime().exec(cmd);
			proc.waitFor();
		} catch (Exception e) {
			Util.log("getRed5PID(): "+ e.getMessage(), null);
			return null;
		}  
		
		String line = null;
		String[] tokens = null;
		BufferedReader procReader = new BufferedReader(new InputStreamReader(proc.getInputStream()));					
		
		try {
			while ((line = procReader.readLine()) != null){
				if(line.contains("red5")) {
					tokens = line.split(" ");
					for(int i = 1 ; i < tokens.length ; i++) {
						if(tokens[i].trim().length() > 0) {
							if(redPID==null) redPID = tokens[i].trim();							
						}
					}
				}	
			}
		} catch (IOException e) {
			Util.log("getRed5PID(): ", e.getMessage());
		}

		return redPID;
	}	
	*/
	
	public static String pingWIFI(final String addr){
		if(addr==null) return null;	
		String[] cmd = new String[]{"ping", "-c1", "-W1", addr};
		long start = System.currentTimeMillis();
		Process proc = null;
		try { 
			proc = Runtime.getRuntime().exec(cmd);
			proc.waitFor();
		} catch (Exception e) {
			Util.log("pingWIFI(): "+ e.getMessage(), null);
			return null;
		}  
		
		String line = null;
		String time = null;
		BufferedReader procReader = new BufferedReader(new InputStreamReader(proc.getInputStream()));					
		
		try {
			while ((line = procReader.readLine()) != null){
				if(line.contains("time=")) {
					time = line.substring(line.indexOf("time=")+5, line.indexOf(" ms"));
					break;
				}	
			}
		} catch (IOException e) {
			Util.log("pingWIFI(): ", e.getMessage());
		}

		if(proc.exitValue() != 0 ) Util.debug("pingWIFI(): exit code: " + proc.exitValue(), null);
		if(time == null) Util.log("pingWIFI(): null result for address: " + addr, null);
		if((System.currentTimeMillis()-start) > 1100)
			Util.debug("pingWIFI(): ping timed out, took over a second: " + (System.currentTimeMillis()-start));
		
		return time;	
	}

	public static void updateLocalIPAddress(){	
		State state = State.getReference();
		String wdev = lookupWIFIDevice();
		
		try {			
			String[] cmd = new String[]{"/bin/sh", "-c", "ifconfig"};
			Process proc = Runtime.getRuntime().exec(cmd);
			proc.waitFor();
			
			String line = null;
			BufferedReader procReader = new BufferedReader(new InputStreamReader(proc.getInputStream()));					
			while ((line = procReader.readLine()) != null) {	
				if(line.contains(wdev)) {
					line = procReader.readLine();
					String addr = line.substring(line.indexOf(":")+1); 
					addr = addr.substring(0, addr.indexOf(" ")).trim();
									
					if(validIP(addr)) State.getReference().set(values.localaddress, addr);
					else Util.debug("Util.updateLocalIPAddress(): bad address ["+ addr + "]", null);
				}
			}
		} catch (Exception e) {
			Util.debug("updateLocalIPAddress(): failed to lookup wifi device", null);
			state.delete(values.localaddress);
			updateEthernetAddress();
		}
	}
	
	public static void updateEthernetAddress(){	
		State state = State.getReference();
		try {			
			String[] cmd = new String[]{"/bin/sh", "-c", "ifconfig"};
			Process proc = Runtime.getRuntime().exec(cmd);
			proc.waitFor();
			String line = null;
			BufferedReader procReader = new BufferedReader(new InputStreamReader(proc.getInputStream()));					
			while ((line = procReader.readLine()) != null) {	
				if(line.contains("eth")) {
					line = procReader.readLine();
					String addr = line.substring(line.indexOf(":")+1); 
					addr = addr.substring(0, addr.indexOf(" ")).trim();
									
					if(validIP(addr)) State.getReference().set(values.localaddress, addr);
					else Util.debug("Util.updateEthernetAddress(): bad address ["+ addr + "]", null);
				}
			}
		} catch (Exception e) {
			state.set(values.localaddress, "127.0.0.1");
		}
		
		if(!state.exists(values.localaddress)) state.set(values.localaddress, "127.0.0.1");
	}
	
	private static String lookupWIFIDevice(){
		String wdev = null;
		try { // this fails if no wifi is enabled 
			Process proc = Runtime.getRuntime().exec(new String[]{"/bin/sh", "-c", "nmcli dev"});
			String line = null;
			BufferedReader procReader = new BufferedReader(new InputStreamReader(proc.getInputStream()));					
			while ((line = procReader.readLine()) != null) {
				if( ! line.startsWith("DEVICE") && line.contains("wireless")){
					String[] list = line.split(" ");
					wdev = list[0];
				}
			}
		} catch (Exception e) {
			Util.log("lookupDevice():  no wifi is enabled  ", null);
		}
		
		return wdev;
	}


	public static void updateExternalIPAddress(){
		new Thread(new Runnable() { public void run() {

			State state = State.getReference();

//  --- changed: updated only called on ssid change from non null
//			if(state.exists(values.externaladdress)) {
//				Util.log("updateExternalIPAddress(): called but already have an ext addr, try ping..", null);
//				if(Util.pingWIFI(state.get(values.externaladdress)) != null) {
//					Util.log("updateExternalIPAddress(): ping sucsessful, reject..", null);
//					return;
//				}
//			}

			try {

				URLConnection connection = (URLConnection) new URL("http://www.xaxxon.com/xaxxon/checkhost").openConnection();
				BufferedInputStream in = new BufferedInputStream(connection.getInputStream());

				int i;
				String address = "";
				while ((i = in.read()) != -1) address += (char)i;
				in.close();

				if(Util.validIP(address)) state.set(values.externaladdress, address);
				else state.delete(values.externaladdress);

			} catch (Exception e) {
				Util.debug("updateExternalIPAddress():"+ e.getMessage(), null);
				state.delete(values.externaladdress);
			}
		} }).start();
	}

	/*
	public static String getJettyPID(){	
		
	//	if(jettyPID!=null) return jettyPID;
		
		String[] cmd = { "/bin/sh", "-c", "ps -fC java" };
		
		Process proc = null;
		try { 
			proc = Runtime.getRuntime().exec(cmd);
			proc.waitFor();
		} catch (Exception e) {
			Util.log("getJettyPID(): "+ e.getMessage(), null);
			return null;
		}  
		
		String line = null;
		String[] tokens = null;
		BufferedReader procReader = new BufferedReader(new InputStreamReader(proc.getInputStream()));					
		
		try {
			while ((line = procReader.readLine()) != null){
				if(line.contains("start.jar")) {
					tokens = line.split(" ");
					if(tokens[0].equals("root"))
					for(int i = 1 ; i < tokens.length ; i++) {
						if(tokens[i].trim().length() > 0) {
							if(jettyPID==null) jettyPID = tokens[i].trim();							
						}
					}
				}	
			}
		} catch (IOException e) {
			Util.log("getJettyPID(): ", e.getMessage());
		}

		return jettyPID;
	}	
	*/
	
	public static void setJettyTelnetPort() {
		new Thread(new Runnable() { public void run() {
			Settings settings = Settings.getReference();
			String url = "http://127.0.0.1/?action=telnet&port=" + settings.readSetting(GUISettings.telnetport);
			try {
				URLConnection connection = (URLConnection) new URL(url).openConnection();
				BufferedInputStream in = new BufferedInputStream(connection.getInputStream());
				while ((in.read()) != -1); // line += (char)i;
				in.close();	
			} catch (Exception e) {}
		} }).start();
	}
	
	public static void updateJetty() {
		new Thread(new Runnable() { public void run() {
			try {
				String url = "http://127.0.0.1/?action=push";
				URLConnection connection = (URLConnection) new URL(url).openConnection();
				BufferedInputStream in = new BufferedInputStream(connection.getInputStream());
				while ((in.read()) != -1);
				in.close();
			} catch (Exception e) {}
		} }).start();
	}

	public static String getJettyStatus() {
		try {
			String url = "http://127.0.0.1/?action=status";
			URLConnection connection = (URLConnection) new URL(url).openConnection();
			BufferedInputStream in = new BufferedInputStream(connection.getInputStream());
			int i; String reply = "";
			while ((i = in.read()) != -1) reply += (char)i;
			in.close();
			return reply;
		} catch (Exception e) {
			return new Date().toString() + " DISABLED";
		}
	}

	public static void deleteLogFiles(){
		
		if(archivePID()){ 
			log("deleteLogFiles(): busy, skipping.. ", null);
			return;
		}
		
		if( ! State.getReference().equals(values.dockstatus, AutoDock.DOCKED)) {
			log("deleteLogFiles(): reboot required and must be docked, skipping.. ", null);
			return;
		}
		
	 	File[] files = new File(Settings.logfolder).listFiles();
	    for (int i = 0; i < files.length; i++){
	       if (files[i].isFile()) files[i].delete();
	       else appendUserMessage("logs folder contains sub folders!");
	    }
	    files = new File(Settings.logfolder).listFiles();
	    if(files.length != 0) log("deleteLogFiles(): must be subfolders: " + files.length, null);	
	    
		truncStaleArchive();
		truncStaleFrames();
		truncState();
	}
	
	public static void truncStaleFrames(){
		File[] files  = new File(Settings.framefolder).listFiles();	
        for (int i = 0; i < files.length; i++){
			if (files[i].isFile()){
				if(!linkedFrame(files[i].getName())){
					debug(files[i].getName() + " was deleted");
					files[i].delete();
				}
	        }
		} 
	}
	
	public static boolean linkedFrame(final String fname){ 
		Process proc = null;
		String line = null;
		try { 
			proc = Runtime.getRuntime().exec( new String[]{ "/bin/sh", "-c", "grep -w \"" + fname + "\" " + NavigationLog.navigationlogpath });
			proc.waitFor();
			BufferedReader procReader = new BufferedReader(new InputStreamReader(proc.getInputStream()));	
			while ((line = procReader.readLine()) != null){
				Util.debug("linkedFrame(): " + line);
				return true;
			}
		} catch (Exception e){return false;};
		
		return false;
	}
	
	public static void truncStaleArchive(){
		File[] files  = new File(Settings.archivefolder).listFiles();
		log("truncStaleArchive(): " + files.length + " files in folder", null);
		if(files.length < MIN_FILE_COUNT) return;
		sortFiles(files);
        for (int i = MIN_FILE_COUNT; i < files.length; i++){
			if (files[i].isFile()){
				log("truncStaleArchive(): " + files[i].getName() + "  was deleted", null);
				files[i].delete();
	        }
		} 
	}
	
	public static void truncState(){
		File[] files  = new File(Settings.logfolder).listFiles(new stateFilter());	
		log("truncState(): " + files.length + " files in folder", null);
		if(files.length < MIN_FILE_COUNT) return;
		sortFiles(files); 
        for (int i = MIN_FILE_COUNT; i < files.length; i++){
			if (files[i].isFile()){
				log("truncState(): " + files[i].getName() + " was deleted", null);
				files[i].delete();
	        }
		} 
	}
	 
	public static class stateFilter implements FilenameFilter{
        @Override
        public boolean accept(File dir, String name) {
            return name.contains("state");
        }
	 }
	 
	private static void sortFiles(File[] files) {
		Arrays.sort(files, new Comparator<File>(){
			public int compare( File f1, File f2){
                long result = f2.lastModified() - f1.lastModified();
                if( result > 0 ){ return 1;
                } else if( result < 0 ){ return -1;
                } else return 0;
            }
        });	
	}

	public static String archiveLogs(){
		final String path = "./archive" + sep + "log_" + System.currentTimeMillis() + ".tar.bz2";
		final String[] cmd = new String[]{"/bin/sh", "-c", "tar -jcf " + path + " log"};
		new File(Settings.redhome + sep + "archive").mkdir(); 
		new Thread(new Runnable() { public void run() {
			try { Runtime.getRuntime().exec(cmd); } catch (Exception e){printError(e);}
		}}).start();
		return path;
	}
	
	public static String archiveImages(){
		final String path = "./archive" + sep + "img_" + System.currentTimeMillis() + ".tar";
		final String[] cmd = new String[]{"/bin/sh", "-c", "tar -jcf " + path + " " + Settings.framefolder};
		new File(Settings.redhome + sep + "archive").mkdir(); 
		new Thread(new Runnable() { public void run() {
			try { Runtime.getRuntime().exec(cmd); } catch (Exception e){printError(e);}
		}}).start();
		return path;
	}
	
	/* not needed? we have this in log folder already..
	public static String archiveROS(){
		final String path = "./archive" + sep + "ros_"+System.currentTimeMillis() + ".tar";
		final String[] cmd = new String[]{"/bin/sh", "-c", "tar -jcf " + path + "  " + Settings.roslogfolder};
		new File(Settings.redhome + sep + "archive").mkdir(); 
		new Thread(new Runnable() { public void run() {
			try { Runtime.getRuntime().exec(cmd); } catch (Exception e){printError(e);}
		}}).start();
		return path;
	}*/
	
	public static String archiveAll(String[] files){
		final String path = "./archive" + sep + "all_"+System.currentTimeMillis() + ".tar";
		String args = "  " + NavigationLog.navigationlogpath + " ";
		for(int i = 0 ; i < files.length ; i++) args += files[i] + " ";
		final String[] cmd = new String[]{"/bin/sh", "-c", "tar -jcf " + path + args};
		new File(Settings.redhome + sep + "archive").mkdir(); 
		new Thread(new Runnable() { public void run() {
			try { Runtime.getRuntime().exec(cmd); } catch(Exception e){printError(e);}
		}}).start();
		return path;
	}
	
	public static void archiveFiles(final String fname, final String[] files){
		String args = "";
		for(int i = 0 ; i < files.length ; i++) args += files[i] + " ";
		final String[] cmd = new String[]{"/bin/sh", "-c", "tar -jcf " + fname + " " + args};
		log("file created: " + fname, null);
		new Thread(new Runnable() { public void run() {
			try { Runtime.getRuntime().exec(cmd); } catch(Exception e){printError(e);}
		}}).start();
	}
	
	public static void compressFiles(final String fname, final String[] files){
		
		if(archivePID()){ 
			log("compressFiles(): busy, skipping.. ", null);
			return;
		}
		
		String args = "";
		for(int i = 0 ; i < files.length ; i++) args += files[i] + " ";
		final String[] cmd = new String[]{"/bin/sh", "-c", "tar -jcf " + fname + " " + args};
		log("file created: " + fname, null);
		new Thread(new Runnable() { public void run() {
			try { Runtime.getRuntime().exec(cmd); } catch(Exception e){printError(e);}
		}}).start();
	}
	
	public static boolean archivePID(){ 
		Process proc = null;
		String line = null;
		try { 
			proc = Runtime.getRuntime().exec( new String[]{ "/bin/sh", "-c", "ps -a" });
			proc.waitFor();
			BufferedReader procReader = new BufferedReader(new InputStreamReader(proc.getInputStream()));	
			while ((line = procReader.readLine()) != null){
				if(line.contains("tar") || line.contains("zip")){
					Util.debug("archivePID(): found: " + line);
					return true;
				}
			}
		} catch (Exception e){return false;};
		
		return false;
	}
	
	public static void manageLogs(){
		
		if(archivePID()){ 
			log("manageLogs(): busy, skipping.. ", null);
			return;
		}
		
		if( ! State.getReference().equals(values.dockstatus, AutoDock.DOCKED)) {
			log("manageLogs(): must be docked, skipping.. ", null);
			return;
		}
	
		new Thread(new Runnable() { public void run() {
			try {
				
				long start = System.currentTimeMillis();
				appendUserMessage("log files being archived");
				
//				String ros = archiveROS();
//				log("manageLogs(): log file: " + ros, null);
//				if(waitForArchive()) log("manageLogs(): **corrupt** log file: " + ros, null);
				
				String images = archiveImages();
				log("manageLogs(): log file: " + images, null);	
				if(waitForArchive()) log("manageLogs(): **corrupt** log file: " + images, null);
				
				String logs = archiveLogs();
				log("manageLogs(): log file: " + logs, null);
				if(waitForArchive()) log("manageLogs(): **corrupt** log file: " + logs, null);
			
				/*
				String all = archiveAll(new String[]{logs, images, ros, Settings.settingsfile});
				log("manageLogs(): log file: " + all, null);
				if(waitForArchive()) log("manageLogs(): **corrupt** log file: " + all, null);
				else {
					
					// done, now clean up.. 
					new File(images).delete();
					new File(logs).delete();
					new File(ros).delete();
				}
				*/
				
				waitForArchive();
				log("manageLogs(): done archiving: " +(System.currentTimeMillis() - start)/1000 + " seconds", null);
				appendUserMessage(", archiving complete");
				
			} catch (Exception e){printError(e);}
		} }).start();
	}
 
	public static boolean waitForArchive(){
		if(archivePID()){ 
			long start = System.currentTimeMillis();
			for(;;){
				if(archivePID()){
					delay(5000);
					debug("waitForArchive(): seconds: " + (System.currentTimeMillis() - start)/1000);
					if((System.currentTimeMillis() - start) > TEN_MINUTES){
						log("waitForArchive(): timout: " + (System.currentTimeMillis() - start)/1000, null);
						break;
					}
				} else break;
			}
			debug("waitForArchive(): exit: " + (System.currentTimeMillis() - start)/1000 + " seconds");
		}
		
		return archivePID();
	}
	
	public static Vector<File> walk(String path, Vector<File> allfiles){
        File root = new File( path );
        File[] list = root.listFiles();
        
        if(list == null) return allfiles;

        for( File f : list ) {
        	if ( f.isDirectory()) walk( f.getAbsolutePath(), allfiles );
            else allfiles.add(f);
        }   
        
        return allfiles;
	 }

	public static int diskFullPercent(){
		try {			
			String line = null;
			String[] cmd = { "/bin/sh", "-c", "df" };
			Process proc = Runtime.getRuntime().exec(cmd);
			BufferedReader procReader = new BufferedReader(new InputStreamReader(proc.getInputStream()));					
			while((line = procReader.readLine()) != null){  	
				if(line.startsWith("/")){
					line = line.substring(0, line.length()-2).trim();
					if(line.contains("%")){
						line = line.substring(line.lastIndexOf(" "), line.length()-1);
						int val = Integer.parseInt(line.trim());
						return val;
					}
				}
			}
		} catch (Exception e){}
		return Settings.ERROR;
	}

	public static long countMbytes(final String path){ 
		Vector<File> f = new Vector<>();
		f = walk(path, f);
		long total = 0;
		for(int i = 0 ; i < f.size() ; i++) total += f.get(i).length();
		return total / (1000*1000);
	 }
	
	public static long countFiles(final String path){ 
		Vector<File> f = new Vector<>();
		f = walk(path, f);
		return f.size();
	}
	
	public static void appendUserMessage(String message){
		State state = State.getReference();
		String msg = state.get(values.guinotify);
		if(msg == null) msg = "";
		if(msg.contains(message)) return;
		else msg += ", ";
		msg = msg.trim();
		if(msg.startsWith("<br>")) msg = msg.substring(4, msg.length());
		if(msg.endsWith("<br>")) msg = msg.substring(0, msg.length()-4);
		if(msg.startsWith(",")) msg = msg.substring(1, msg.length());
		if(msg.endsWith(",")) msg = msg.substring(0, msg.length()-1);
		msg = msg.trim();
		state.set(values.guinotify, msg += message);
	}

	public static void deleteROS() {
		if( ! State.getReference().equals(values.dockstatus, AutoDock.DOCKED)) {
			log("deleteROS(): reboot required and must be docked, skipping.. ", null);
			return;
		}
		
		appendUserMessage("ros purge, reboot required");
		
		new Thread(new Runnable() { public void run() {
			try {
				String[] cmd = {"bash", "-ic", "rm -rf " + Settings.roslogfolder};
				Runtime.getRuntime().exec(cmd);
				new File("rlog.txt").delete();
			} catch (Exception e){printError(e);}
		} }).start();
		
		new Thread(new Runnable() { public void run() {
			try {
				PowerLogger.append("shutting down application", this);
				PowerLogger.close();
				delay(10000);					
				systemCall(Settings.redhome + Util.sep + "systemreboot.sh");
			} catch (Exception e){printError(e);}
		} }).start();
		
	}
	
	public static String getRosCheck(){	
		
		if(rosinfor!=null) return rosinfor;
		
		if(rosattempts++ > 5){
			log("getRosCheck: "+rosattempts++, null);	
			return "err";
		}
	
		try {
			new Thread(new Runnable() { public void run() {
				try {
					String[] cmd = {"bash", "-ic", "rosclean check > rlog.txt"};
					Runtime.getRuntime().exec(cmd);		
				} catch (Exception e){printError(e);}
			}}).start();
		} catch (Exception e){printError(e);}

		try{ 
			String line;
			BufferedReader reader;
			try {
				reader = new BufferedReader(new FileReader("rlog.txt"));
				while ((line = reader.readLine()) != null) rosinfor = line;
				reader.close();		
			} catch (Exception e) { rosinfor = null; }
			
			if(new File("rlog.txt").exists() && rosinfor==null) rosinfor = "0.00";
			
			if(rosinfor.contains("K ROS node logs")) rosinfor = "1";
			if(rosinfor != null) if(rosinfor.contains("M ROS node logs")) 
				rosinfor = rosinfor.substring(0, rosinfor.indexOf("M")).trim();
		} catch (Exception e){ rosinfor = "0.00"; }
		
		return rosinfor;
	}
	
}


