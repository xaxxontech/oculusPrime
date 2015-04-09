package developer;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

import oculusPrime.LoginRecords;
import oculusPrime.State;
import oculusPrime.Util;

public class UpdateFTP {

	public final static String sep = System.getProperty("file.separator");
	public final static String redhome = System.getenv("RED5_HOME");
	public static String ftpconfig = redhome+sep+"conf"+sep+"ftp.properties";
	
	public static final int DEFAULT_TIME = 30 * 60000; 
	public static final String ftpTimer = "ftpTimer";
	
	private static int delay = DEFAULT_TIME;
	private static State state = State.getReference();
	private static FTP ftp = new FTP();
	
	private String host, port, user, pass, folder;
	
	public static boolean configured(){
		File propfile = new File(ftpconfig);
		return propfile.exists();
	}

	public UpdateFTP(){ 
		
		Properties props = new Properties();
		
		try {

			FileInputStream propFile = new FileInputStream(ftpconfig);
			props.load(propFile);
			propFile.close();
			
		} catch (Exception e) {
			return;
		}	
		
		user = (String) props.getProperty("user", System.getProperty("user.name"));
		host = (String) props.getProperty("host", "localhost");
		folder = (String) props.getProperty("folder", "telemetry");
		port = (String) props.getProperty("port", "21");
		pass = props.getProperty("password");
		
		if(props.getProperty("update")!=null)
			delay = Integer.parseInt(props.getProperty("update").trim()) + 60000;
		
		new Thread(new Runnable() {
			@Override
			public void run() {
				Util.delay(delay);
				while(true){
					// state.set(ftpTimer, true);
					updateServer();
					Util.delay(delay);
				}
			}
		}).start();
	}
	
	/*
	@Override
	public void updated(final String key) {
		
		if( ! key.equals(ftpTimer)) return;
		
	}
	*/
		
	public void updateServer() {
		new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					
					ftp.connect(host, port, user, pass);
					ftp.cwd(folder);
					
					ftp.storString("ip.php", state.get(State.values.externaladdress.name()));
					ftp.storString("last.php", new java.util.Date().toString());
					ftp.storString("user.php", System.getProperty("user.name"));		
					ftp.storString("state.php", state.toString());
					ftp.storString("users.php",  new LoginRecords().toString());
					
					ftp.disconnect();
					
				} catch (IOException e) {
					Util.debug(e.getMessage(), this);
				}
			}
		}).start();
	}
}
