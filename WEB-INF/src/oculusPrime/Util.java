package oculusPrime;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.MalformedURLException;
import java.net.URL;
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


public class Util {
	
	private static final int PRECISION = 2;
	public static final long ONE_DAY = 86400000;
	public static final long ONE_MINUTE = 60000;
	public static final long TWO_MINUTES = 120000;
	public static final long FIVE_MINUTES = 300000;
	public static final long TEN_MINUTES = 600000;
	public static final long ONE_HOUR = 3600000;
	
	static final int MAX_HISTORY = 50;
	static Vector<String> history = new Vector<String>();

	/**
	 * Delays program execution for the specified delay.
	 * 
	 * @param delay
	 *            is the specified time to delay program execution
	 *            (milliseconds).
	 */
	public static void delay(long delay) {
		try {
			Thread.sleep(delay);
		} catch (Exception e) {
			System.out.println(e.getMessage());
		}
	}

	/**
	 * Delays program execution for the specified delay.
	 * 
	 * @param delay
	 *            is the specified time to delay program execution
	 *            (milliseconds).
	 */
	public static void delay(int delay) {
		try {
			Thread.sleep(delay);
		} catch (Exception e) {
			System.out.println(e.getMessage());
		}
	}

	public static String getTime() {
        Date date = new Date();
		return date.toString();
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

	/**
	 * Returns the specified double, formatted as a string, to n decimal places,
	 * as specified by precision.
	 * <p/>
	 * ie: formatFloat(1.1666, 1) -> 1.2 ie: formatFloat(3.1666, 2) -> 3.17 ie:
	 * formatFloat(3.1666, 3) -> 3.167
	
	public static String formatString(String number, int precision) {

		String text = number;
		if (precision >= text.length()) {
			return text;
		}

		int start = text.indexOf(".") + 1;

		if (start == 0) return text;

		if (precision == 0) {
			return text.substring(0, start - 1);
		}

		if (start <= 0) {
			return text;
		} else if ((start + precision) <= text.length()) {
			return text.substring(0, (start + precision));
		}

		return text;
	} */

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
	
	/**
	 * Run the given text string as a command on the host computer. 
	 * 
	 * @param args is the command to run, like: "restart
	 * 
	 */
	public static void systemCallBlocking(final String args) {
		try {	
			
			long start = System.currentTimeMillis();
			Process proc = Runtime.getRuntime().exec(args);
			BufferedReader procReader = new BufferedReader(
					new InputStreamReader(proc.getInputStream()));

			String line = null;
			System.out.println(proc.hashCode() + "OCULUS: exec():  " + args);
			while ((line = procReader.readLine()) != null)
				System.out.println(proc.hashCode() + " systemCallBlocking() : " + line);
			
			proc.waitFor(); // required for linux else throws process hasn't terminated error
			System.out.println("OCULUS: process exit value = " + proc.exitValue());
			System.out.println("OCULUS: blocking run time = " + (System.currentTimeMillis()-start) + " ms");

		} catch (Exception e) {
			e.printStackTrace();
		}
	}	

	/**
	 * Run the given text string as a command on the windows host computer. 
	 * 
	 * @param str is the command to run, like: "restart
	 *  
	 */
	public static void systemCall(final String str){
		try {
			Runtime.getRuntime().exec(str);

		} catch (Exception e) {
			printError(e);
		}
	}


	/**
	 * @return this device's external IP address is via http lookup, or null if fails 
	
	public static String getExternalIPAddress(){

		String address = null;
		URL url = null;

		try {
			
			url = new URL("http://checkip.dyndns.org/");

			// read in file from the encoded url
			URLConnection connection = (URLConnection) url.openConnection();
			BufferedInputStream in = new BufferedInputStream(connection.getInputStream());

			int i;
			while ((i = in.read()) != -1) {
				address = address + (char) i;
			}
			in.close();

			// parse html file
			address = address.substring(address.indexOf(": ") + 2);
			address = address.substring(0, address.indexOf("</body>"));
			
		} catch (Exception e) {
			return null;
		}
		
		// all good 
		return address;
	} */ 

    /**
     * @return the local host's IP, null on error
     
    public static String getLocalAddress(){
            try {
                    return (InetAddress.getLocalHost()).getHostAddress();
            } catch (UnknownHostException e) {
                    return null;
            }
    }*/
	
	
//	/** @return a list of ip's for this local network */ 
//	public static String getLocalAddress() {
//		String address = "";
//		try {
//			Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
//			if (interfaces != null)
//				while (interfaces.hasMoreElements()) {
//					NetworkInterface ni = (NetworkInterface) interfaces.nextElement();
//					if (!ni.isVirtual())
//						if (!ni.isLoopback())
//							if (ni.isUp()) {
//								Enumeration<InetAddress> addrs = ni.getInetAddresses();
//								while (addrs.hasMoreElements()) {
//									InetAddress a = (InetAddress) addrs.nextElement();
//									address += a.getHostAddress() + " ";
//								}
//							}
//				}
//		} catch (Exception e) {
//			e.printStackTrace();
//		}
//		
//		String[] addrs = address.split(" ");
//		for(int i = 0 ; i < addrs.length ; i++){
//			if(!addrs[i].contains(":"))
//				return addrs[i];
//		}
//		
//		return null;
//	}

	/**
	 * write new value to user's screen and set it 
	 * change the host computer's volume 
	 * 
	 * @param percent
	 */
	public static void setSystemVolume(int percent, Application app){
		String str = "amixer set Master "+percent+"%";
		
		//	float vol = (float) percent / 100 * 65535;
		//	str = "nircmdc.exe setsysvolume "+ (int) vol; //w in
		// }
		
		Util.systemCall(str);
		Settings settings = Settings.getReference();
		
		//private static final boolean debug = settings.getBoolean(ManualSettings.debugenabled);
		settings.writeSettings(GUISettings.volume.name(), percent);
	}

	/*
	public static String tail(int lines) {
		Vector<String> alllines = new Vector<String>();
		File file =new File(Settings.stdout);
	    FileInputStream filein;
	    try {
	            filein = new FileInputStream(file.getAbsolutePath());
	            BufferedReader reader = new BufferedReader(
	                            new InputStreamReader(filein));
	            String line = "";
	            while ((line = reader.readLine()) != null) {
                    alllines.add(line);
	            }
	            filein.close();
           
	            
	    } catch (Exception e) {
	            e.printStackTrace();
	    }
	    String result="";
	    for(int i=alllines.size()-lines ; i < alllines.size() ; i++) {
	    	result += alllines.elementAt(i).trim()+"<br>";
	    }
	    
		return result;
	}
		*/
	
	
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
		for(; i < history.size() ; i++) str.append(history.get(i) + "\n<br />"); 
		return str.toString();
	}
		
	public static void log(String method, Exception e, Object c) {
		log(method + ": " + e.getLocalizedMessage(), c);
	}
	
	public static void log(String str) {
		if(history.size() > MAX_HISTORY) history.remove(0);
		history.add(getTime() + ", " +str);
		System.out.println("OCULUS: " + getTime() + ", " + str);
	}
	
	public static void log(String str, Object c) {
		final String filter = c.getClass().getName().toLowerCase();
		if(history.size() > MAX_HISTORY) history.remove(0);
		history.add(getTime() + ", " + filter + ", " +str);
		System.out.println("OCULUS: " + getTime() + ", " + filter + ", " + str);
	}
	
    public static void debug(String str, Object c) {
		if(Settings.getReference().getBoolean(ManualSettings.debugenabled)) 
			System.out.println("DEBUG: " + getTime() + ", " + c.getClass().getName() +  ", " +str);
	}
    
    public static void debug(String str) {
    	if(Settings.getReference().getBoolean(ManualSettings.debugenabled))
    		System.out.println("DEBUG: " + getTime() + ", " +str);
    }
    
	public static String memory() {
    	String str = "";
		str += "memory : " +
				((double)Runtime.getRuntime().freeMemory()
						/ (double)Runtime.getRuntime().totalMemory())*100 + "% free<br>";
		
		str += "memory total : "+Runtime.getRuntime().totalMemory()+"<br>";    
	    str += "memory free : "+Runtime.getRuntime().freeMemory()+"<br>";
		return str;
    }
	
	public static void reboot() {
		String str  = Settings.redhome + Settings.sep + "systemreboot.sh";
		Util.systemCall(str);
	}
	
	public static void shutdown() {
		String str  = Settings.redhome + Settings.sep + "systemshutdown.sh"; 
		Util.systemCall(str);
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
		System.err.println("Error "+getTime()+ ":");
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
}
