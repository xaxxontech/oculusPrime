package oculusPrime.commport;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.util.Vector;

import oculusPrime.AutoDock;
import oculusPrime.Util;

public class PowerHistory {
	//public final static String redhome = System.getenv("RED5_HOME");
	//public final static String powerlogTemp = redhome + "/log/power.copy";

	static final String SERIAL_IN = "serial in:";
	
	// grep -r 'serial in:' ~/oculusPrime/log/power.log | tail -n 90
	
	// Sun Mar 26 15:08:15 PDT 2017, oculusprime.commport.arduinopower, serial in: docked 99%_charging cells:4.11_4.12_4.15_idle:4.09_4.12_4.13_sh:000_sCV:13.95_sIV:14.26_pwm:167_amps:0.34_mAh:5762_lV:12.34_cC:-0.04_bP:0_eR:0
	// serial in: docked   99%_charging cells:4.11_4.12_4.15_idle:4.09_4.12_4.13_sh:000_sCV:13.86_sIV:14.12_pwm:167_amps:0.26_mAh:5773_lV:12.34_cC:-0.02_bP:0_eR:0
	// serial in: undocked 99%          cells:3.99_4.10_4.02_sV:11.19_pwm:0_amps:-1.84_mAh:5773_lV:12.09_cC:-0.02_bP:1_eR:0
	
	boolean isDocked = false;
	boolean isCharging = false;
	double volts, cell1, cell2, cell3, amps = 0;
	int batteryPercentage = 0;
	
	public static synchronized Vector<String> getFile(final int results){
		
		final Long start = System.currentTimeMillis();
		final String name = "log/power_copy_" + System.currentTimeMillis() + ".txt";
		Vector<String> data = new Vector<String>(results);
		
		// let the linux system do it 
		Util.systemCall("cp " + PowerLogger.powerlog + " " + name);

		/*
		File temp = new File(name);
		try {
			Files.copy(new File(PowerLogger.powerlog), temp);
		} catch (IOException e) {
			Util.printError(e);
		}
		*/
		
		// wait for copy to finish 
		File temp = new File(name);
		int c = 0;
		while( ! temp.exists()) {
			try { Thread.sleep(2); } catch (InterruptedException e) { e.printStackTrace();	}
			// Util.debug(".. waiting on file creation: " + name);
			temp = new File(name);
			if(c++ > 10){
				Util.log("fail to create copy of power log", "PowerHistory");
				return null;
			}
		}
		
		FileLock lock = null;
		FileChannel fileChannel = null;
		RandomAccessFile logfile = null;
		
		try {
			logfile = new RandomAccessFile(temp, "rw");
		} catch (FileNotFoundException e) {
			Util.printError(e);
			new File(name).delete();
			return null;
		}
		
		fileChannel = logfile.getChannel();
		if(fileChannel == null) {
			// Util.debug("failed to lock.. : " + temp.getAbsolutePath());
			try { logfile.close(); } catch (IOException e) { Util.printError(e); }	
			new File(name).delete();
			return null;
		}
	
		// blocking call
		Util.log(".. waiting on lockfile", "PowerHistory");
		try {
			lock = fileChannel.lock();
		} catch (IOException e) {
			Util.printError(e);
			try { logfile.close();	} catch (IOException e2) { Util.printError(e2); }		
//			try { lock.release();	} catch (IOException e1) { Util.printError(e1); }		
			new File(name).delete();
			return null;	
		}
		
		String line;
		int count = 0; 
		try {

			BufferedReader br = new BufferedReader(new FileReader(temp));			
			while((line = br.readLine()) != null) {
				if(line.contains(SERIAL_IN)){
					if(data.size()-1 == results) data.remove(0);
					data.addElement(line.trim());
				}
				count++;
			}
			br.close();		
				
		} catch (IOException e) {
			
			Util.printError(e);
			
			try {
				logfile.close();
			} catch (IOException e1) { Util.printError(e); }		
			try{
				lock.release();
			} catch (IOException e1) { Util.printError(e); }		
			new File(name).delete();
			return data;	
		}
		
		// clean up
		try {
			lock.release();
		} catch (IOException e) {
			Util.printError(e);
			Util.log("can't release file", "PowerHistory");
			new File(name).delete();
		}
		try {
			logfile.close();
		} catch (IOException e) {
			Util.printError(e);
			Util.log("can't close file", "PowerHistory");
			new File(name).delete();
		}
		
		new File(name).delete();
		Util.log("PowerHistory copy to temp file: " + (System.currentTimeMillis() - start) + " ms, lines read: " + count,
				"PowerHistory");
		return data;
	
	}
	
	public PowerHistory(String line){
		
		// line = line.replace(", oculusprime.commport.arduinopower, ", " ");
		if(line.contains(SERIAL_IN)) line = line.split(SERIAL_IN)[1].trim();

		String tokens[] = line.split("\\s+");		
		if(tokens[0].equals(AutoDock.DOCKED)) isDocked = true;
		if(tokens[0].contains("charging")) isCharging = true;  //////////// fails? 
		
		String batt = tokens[1].replace("%_charging", "").replaceFirst("%", "");
		if(Util.isInteger(batt)) batteryPercentage = Integer.parseInt(batt);
		
		// m = tokens[2]; //cells:3.67_3.81_3.76_sV:11.03_pwm:0_amps:-1.60_mAh:1100_lV:11.25_cC:-0.04_bP:1_eR:0
		
		tokens = tokens[2].replace("cells:", "").split("_"); 
		cell1 = Double.parseDouble(tokens[0]);
		cell2 = Double.parseDouble(tokens[1]);
		cell3 = Double.parseDouble(tokens[2]);

		if(tokens[5].contains("amps:")) amps = Double.parseDouble(tokens[5].split("amps:")[1]);
			
		/*
		for(int i = 0; i < tokens.length ; i++)
		Util.log(i + " " + tokens[i]);

OCULUS: Tue Mar 28 00:46:44 PDT 2017, static, 0 3.68
OCULUS: Tue Mar 28 00:46:44 PDT 2017, static, 1 3.82
OCULUS: Tue Mar 28 00:46:44 PDT 2017, static, 2 3.77
OCULUS: Tue Mar 28 00:46:44 PDT 2017, static, 3 sV:11.05
OCULUS: Tue Mar 28 00:46:44 PDT 2017, static, 4 pwm:0
OCULUS: Tue Mar 28 00:46:44 PDT 2017, static, 5 amps:-1.50
OCULUS: Tue Mar 28 00:46:44 PDT 2017, static, 6 mAh:1089
OCULUS: Tue Mar 28 00:46:44 PDT 2017, static, 7 lV:11.25
OCULUS: Tue Mar 28 00:46:44 PDT 2017, static, 8 cC:-0.04
OCULUS: Tue Mar 28 00:46:44 PDT 2017, static, 9 bP:1
OCULUS: Tue Mar 28 00:46:44 PDT 2017, static, 10 eR:0

*/
	}
	 
	@Override public String toString() { 
		
	//	getFile();
		
		if(isCharging) return "charging life " + batteryPercentage + "% volts " + volts + " cell#1 " + cell1 + " cell#2" + cell2 + " cell#3 " + cell3 + " amps " + amps; 
		else return "life " + batteryPercentage + "% volts " + volts + " cell#1 " + cell1 + " cell#2" + cell2 + " cell#3 " + cell3 + " amps " + amps; 
		
	//	return PowerHistory.getChargingString(10).get(0);
		
	}
	 
	public static Vector<PowerHistory> getTail(int lines){
		Vector<PowerHistory> tail = new Vector<PowerHistory>();
		Vector<String> text = getTailString(lines);
		for(int i = 0 ; i < text.size() ; i++) tail.add(new PowerHistory(text.get(i)));		
		return tail;		
	}
	
	public static Vector<String> getChargingString(int lines){
		Vector<String> tail = new Vector<String>();
		try {	
			String[] cmd = new String[]{"/bin/sh", "-c", "grep -r \'charging\' "+ PowerLogger.powerlog + " | tail -n " + lines};
			Process proc = Runtime.getRuntime().exec(cmd);
			proc.waitFor();
			String line = null;
			BufferedReader procReader = new BufferedReader(new InputStreamReader(proc.getInputStream()));					
			while ((line = procReader.readLine()) != null) {
				if(line.trim().length() > 0)
					if(line.contains(SERIAL_IN)) 
						tail.add(line); // tail.add(line.split(SERIAL_IN)[1].trim());
			}
		} catch (Exception e) { Util.log(e.getMessage(), "PowerHistory"); }
		return tail;
	}
	
	public static Vector<String> getUnDockedString(int lines){
		Vector<String> tail = new Vector<String>();
		try {	
			String[] cmd = new String[]{"/bin/sh", "-c", "grep -r \'undocked\' "+ PowerLogger.powerlog + " | tail -n " + lines};
			Process proc = Runtime.getRuntime().exec(cmd);
			proc.waitFor();
			String line = null;
			BufferedReader procReader = new BufferedReader(new InputStreamReader(proc.getInputStream()));					
			while ((line = procReader.readLine()) != null) {
				if(line.trim().length() > 0)
					if(line.contains(SERIAL_IN)) 
						tail.add(line); // tail.add(line.split(SERIAL_IN)[1].trim());
			}
		} catch (Exception e) { Util.log(e.getMessage(), "PowerHistory"); }
		return tail;
	}
	
	public static Vector<String> getTailString(int lines) {
		Vector<String> tail = new Vector<String>();
		try {	
			String[] cmd = new String[]{"/bin/sh", "-c", "grep -r \'"+SERIAL_IN+"\' "+ PowerLogger.powerlog + " | tail -n " + lines};
			Process proc = Runtime.getRuntime().exec(cmd);
			proc.waitFor();
			String line = null;
			BufferedReader procReader = new BufferedReader(new InputStreamReader(proc.getInputStream()));					
			while ((line = procReader.readLine()) != null) {
				if(line.trim().length() > 0)
					if(line.contains(SERIAL_IN)) 
						tail.add(line); // line.split(SERIAL_IN)[1].trim());
			}
		} catch (Exception e) { Util.log(e.getMessage(), "PowerHistory"); }
		return tail;
	}	
}
