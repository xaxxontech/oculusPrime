package developer;

import oculus.Application;
import oculus.ManualSettings;
import oculus.Observer;
import oculus.Settings;
import oculus.State;
import oculus.Util;

/**  low of battery to warm user with email */
public class EmailAlerts implements Observer {

	public static final int WARN_LEVEL = 40;
	
	private Settings settings = Settings.getReference();;
	private State state = State.getReference();
	private Application app = null;
	private boolean sent = false;
	
	/** Constructor */
	public EmailAlerts(Application parent) {
		
		app = parent;
		
		// is configured
		if(settings.readSetting(ManualSettings.email_smtp_server) != null){
			
			// not disabled
			if( ! settings.readSetting(ManualSettings.email_smtp_server).equals(Settings.DISABLED)){
		
				state.addObserver(this);
				oculus.Util.debug("starting email alerts for battery life", this);
				
			}
		}
	}

	@Override
	public void updated(String key) {
		
		if(sent) return;
		
		if( ! key.equals(State.values.batterylife.name())) return;
		
		Util.debug(".. checking battery", this);
		
		if (state.getInteger(State.values.batterylife.name()) < WARN_LEVEL) {
			
			app.message("battery low, sending email", null, null);
			
			String msg = "The battery " + Integer.toString(state.getInteger(State.values.batterylife.name())) + "% and is draining!"; 
							
			// add the link back to the user screen 
			msg += "\n\nPlease find the dock, log in here: http://" 
				+ State.getReference().get(State.values.externaladdress.name()) 
				+ ":" + settings.readRed5Setting("http.port") 
				+ "/oculus/";
			
			// send email 
			new SendMail("Oculus Message", msg, app); 
			
			// only sent once 
			sent = true;
			
		}
	}
}
