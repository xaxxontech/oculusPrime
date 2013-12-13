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
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.UnknownHostException;
import java.util.Date;
import java.util.Vector;

public class Util {
	
	private static final int PRECISION = 2;
	public static final long ONE_DAY = 86400000;
	public static final long ONE_MINUTE = 60000;
	public static final long TWO_MINUTES = 120000;
	public static final long FIVE_MINUTES = 300000;
	public static final long TEN_MINUTES = 600000;
	
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


	/*
	 * 
	 */
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
	 */
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
	}

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
	 * @param str is the command to run, like: "restart
	 * 
	 */
	public static void systemCallBlocking(final String args) {
		try {	
			
			long start = System.currentTimeMillis();
			Process proc = Runtime.getRuntime().exec(args);
			BufferedReader procReader = new BufferedReader(
					new InputStreamReader(proc.getInputStream()));

			// String line = null;
			System.out.println(proc.hashCode() + "OCULUS: exec():  " + args);
			while (/*(line = */procReader.readLine() != null)
				// System.out.println(proc.hashCode() + " systemCallBlocking() : " + line);
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
		new Thread(new Runnable() { 
			public void run() {
				try {
					Runtime.getRuntime().exec(str);
//					Process proc = Runtime.getRuntime().exec(str);
//					BufferedReader procReader = new BufferedReader(
//							new InputStreamReader(proc.getInputStream()));
//
//					String line = null;
//					System.out.println("OCULUS: process exit value = " + str);
//					while ((line = procReader.readLine()) != null)
//						System.out.println("OCULUS: systemCall(), " + line);
//					
//					System.out.println("OCULUS: process exit value = " + proc.exitValue());
				
				} catch (Exception e) {
					e.printStackTrace();
				}		
			} 	
		}).start();
	}


	/**
	 * @return this device's external IP address is via http lookup, or null if fails 
	 */ 
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
	}

    /**
     * @return the local host's IP, null on error
     */
    public static String getLocalAddress(){
            try {
                    return (InetAddress.getLocalHost()).getHostAddress();
            } catch (UnknownHostException e) {
                    return null;
            }
    }
	
	
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
		String str;
		if (Settings.os.equals("linux")) {
			str = "amixer set Master "+percent+"%";
		}
		else  {
			float vol = (float) percent / 100 * 65535;
			str = "nircmdc.exe setsysvolume "+ (int) vol; //w in
		}
		Util.systemCall(str);
		Settings settings = Settings.getReference();
		//private static final boolean debug = settings.getBoolean(ManualSettings.debugenabled);
		settings.writeSettings(GUISettings.volume.name(), percent);
	}

	/**
	 * If enabled in settings with "notify", this method will turn up volume to max,
	 * say the string, and then restore the volume to the original setting. 
	 * 
	 * 
	 * @param str
	 * 				is the phrase to turn from text to speech 
	 */
	public static void beep() {
		if(Settings.os.equals("windows")){
			systemCall("nircmdc.exe beep 500 1000");
		}else log("need linux beep"); 
		// TODO: linux beep
	}
	
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
	
	public static void log(String str, Object c) {
		System.out.println("OCULUS: " + getTime() + ", " + c.getClass().getName() + ", " +str);
	}

	public static void log(String str) {
		System.out.println("OCULUS: " + getTime() + ", " + str);
	}
	
	
    public static void debug(String str, Object c) {
		if(Settings.getReference().getBoolean(ManualSettings.debugenabled)) 
			System.out.println("DEBUG: " + getTime() + ", " + c.getClass().getName() +  ", " +str);
	}

	public static String memory() {
    	String str = "";
		str += "memory : " +
				((double)Runtime.getRuntime().freeMemory()
						/ (double)Runtime.getRuntime().totalMemory()) + " %<br>";
		
		str += "memorytotal : "+Runtime.getRuntime().totalMemory()+"<br>";    
	    str += "memoryfree : "+Runtime.getRuntime().freeMemory()+"<br>";
		return str;
    }
	
	public static void reboot() {
		if (Settings.os.equals("linux")) {
			String str  = Settings.redhome + Settings.sep + "systemreboot.sh"; // windows & linux
			Util.systemCall(str);
		}
		else { Util.systemCall("shutdown -r -f -t 01"); } // windows
	}
	
	public static void shutdown() {
		if (Settings.os.equals("linux")) {
			String str  = Settings.redhome + Settings.sep + "systemshutdown.sh"; // windows & linux
			Util.systemCall(str);
		}
		else { Util.systemCall("shutdown -s -f -t 01"); } // windows		
	}

}
