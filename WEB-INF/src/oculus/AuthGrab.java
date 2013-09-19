package oculus;

import java.io.IOException;
import java.io.OutputStream;

import javax.servlet.*;
import javax.servlet.http.*;

import org.jasypt.util.password.ConfigurablePasswordEncryptor;

public class AuthGrab extends HttpServlet {
	
	private static final long serialVersionUID = 1L;
	private static Application app = null;
	private static State state = State.getReference();
	private static Settings settings;
//	public static byte[] img  = null;

	public static void setApp(Application a) {
		if(app != null) return;
		app = a;
		settings = Settings.getReference();	
	}
	
	public boolean login(String user, String pass){
		
        if(user==null || pass==null) return false;
        
        // long start = System.currentTimeMillis();
        
		//if(app.logintest(user, pass)==null){
			
			// fail.. so see if was a plain text password
		    ConfigurablePasswordEncryptor passwordEncryptor = new ConfigurablePasswordEncryptor();
			passwordEncryptor.setAlgorithm("SHA-1");
			passwordEncryptor.setPlainDigest(true);
			String encryptedPassword = (passwordEncryptor
					.encryptPassword(user + settings.readSetting("salt") + pass)).trim();
			
			if(app.logintest(user, encryptedPassword)==null){
//				Util.debug("login failure, took " + (System.currentTimeMillis() - start) + " ms", this);
				return false;
			}
		//}
		
//		Util.debug("login done in " + (System.currentTimeMillis() - start) + " ms", this);	
		return true;
	}
	
	public void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
		doPost(req,res);
	}
	
	public void doPost(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
				
        final String user = req.getParameter("user");
        final String pass = req.getParameter("pass");
        final String mode = req.getParameter("mode");

        if(login(user, pass)) {	
        
        	// ready a responce 
    		res.setContentType("image/jpeg");
    		OutputStream out = res.getOutputStream();
        	
        	// only get new if ask ... could be faster to send the last one if was just taken 
        	if(mode!=null) if(mode.equals("update")) getImage();
        	
        	sendImage(out);
        
        } else {
          
        	// debug message for bad auth 
    		res.setContentType("text/html");
    		OutputStream out = res.getOutputStream();
    		out.write("<b>loging failure </b> ".getBytes());
    		out.close();
    		
        }  
	}
	
	public void getImage(){
		
		// long start = System.currentTimeMillis();		
		
		
//		if(state.get(PlayerCommands.publish)==null){
		if(state.get(State.values.stream)==null || state.get(State.values.stream).equals("stop")){
    		
    		Util.debug("cam was off, turned on..... ", this);
    		
    		//TODO:  doesn't work still..... COLIN
    		// app.playerCallServer(PlayerCommands.publish, "camera");
    		// app.publish("camera");
    		
    		// wait for any value in state for 'publish'
    		// if( ! state.block(PlayerCommands.publish, "cam", 30000)){
    		//	Util.log("timeout trying to turn on camera...", this);
    		//	return;
    		//}
    	}
		
		// wait for result
		if (app.frameGrab()) {
			if( ! state.block(oculus.State.values.framegrabbusy, "false", 700)){
				Util.debug("getImage(), timeout ", this);
				return;
			}
			
			// Util.debug("frame grab done in " + (System.currentTimeMillis() - start) + " ms", this);
		} // else { Util.debug("frame grab busy?", this); }
	}
	
	public void sendImage(OutputStream out) throws IOException {
	
		if(Application.framegrabimg==null) return;
		
		// long start = System.currentTimeMillis();
		for (int i=0; i<Application.framegrabimg.length; i++) out.write(Application.framegrabimg[i]);
		out.close();
		   
		// Util.debug("frame grab done sending in " + (System.currentTimeMillis() - start) + " ms", this);			
	}
}
