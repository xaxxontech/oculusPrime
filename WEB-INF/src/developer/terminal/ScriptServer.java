package developer.terminal;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.UnknownHostException;


import oculus.TelnetServer;
import oculus.Util;

public class ScriptServer extends AbstractTerminal {
	public static final String SEPERATOR = " : ";

	public String scriptFile = null;

	public ScriptServer(String ip, String port, final String user, final String pass, String filename) 
		throws NumberFormatException, UnknownHostException, IOException {
		super(ip, port, user, pass);
		
		scriptFile = filename;
		execute();
	}
	
	public void parseInput(final String str){
		System.out.println(this.getClass().getName() + " parse: " + str);
		String[] cmd = str.split(SEPERATOR);
		if(cmd.length==2) state.set(cmd[0], cmd[1]);	
	}

	/** loop through given file */
	public void execute(){
		
		System.out.println("running file:" + scriptFile);
		
		out.println("state");
		Util.delay(1900);
			
		//while (state { out.println("state"); delay(100); }
		
		try{
		
			FileInputStream filein = new FileInputStream(scriptFile);
			BufferedReader reader = new BufferedReader(new InputStreamReader(filein));
			String cmd = null;
			while(true){
				
				cmd = reader.readLine();
				if(cmd==null) break;
				
				if( ! cmd.startsWith("#") && cmd.length()>1 ) 
					doCommand(cmd.split(" "));
			
			}
	
			filein.close();
			
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		// log out 
		out.println("bye");
	}
	
	/** */
	public void doCommand(final String[] str){
		
		if(str[0].equals("delay")){
			
			Util.delay(Integer.parseInt(str[1]));
			
		} /*else if(str[0].equals("wait")){ 
			
			System.out.println("wait state " + str[1] + " " + str[2] );
			
			out.println("state " + str[1] + " " + str[2] );
			
		} */ else if(str[0].equals("if")){ 
			
			// state.dump();
			System.out.println("----- if size: " + str.length);
			for(int i = 0 ; i < str.length ; i++) System.out.println(i+ " _ " + str[i]);
			
			if(state.get(str[1]) == null){

				System.out.println("if null ... " + str[3]);
				for(int i = 3 ; i < str.length ; i++) out.print(str[i] + " ");
				out.println();
				
			} else {
				
				if(state.get(str[1]).equals(str[2])) {
					System.out.println("if sending... " + str[3]);
					out.println(str[3]);
				}
							
			}
			
		} else if(str[0].equals("not")){ 
			
			// state.dump();
			System.out.println("----- not size: " + str.length);
			for(int i = 0 ; i < str.length ; i++) System.out.print("  " + str[i]);
			System.out.println();
			
			if(state.get(str[1]) == null){
				
				System.out.println("null in state... " + str[1]);
				out.println(str[3]);
				for(int i = 3 ; i < str.length ; i++) out.print(str[i] + " ");
				out.println();
				
			} else {
				
				if( ! state.get(str[1]).equals(str[2])) {
					for(int i = 3 ; i < str.length ; i++) out.print(str[i] + " ");
					out.println();
				}
				
				else System.out.println("is equal... needs to be not " + str[1] + " = " + state.get(str[1]));
			
			}
		
		} else if(str[0].equals("greater")){ 
			
			// state.dump();
			System.out.println("----- greater size: " + str.length);
			for(int i = 0 ; i < str.length ; i++) System.out.print(" " + str[i]);
			System.out.println();
			
			if(state.get(str[1]) == null){
				
				System.out.println("null in greater looking up ... " + str[1]);
				
			} else {
				
				if( state.getInteger(str[1]) > Integer.parseInt(str[2])) {
					for(int i = 3 ; i < str.length ; i++) out.print(str[i] + " ");
					out.println();
				}
				
				else System.out.println("not greater _" + str[1] + "_ = " + state.get(str[1]) + " value passed: " + str[2]);
							
			}
				
		} else if(str[0].equals("less")){ 
			
			// state.dump();
			System.out.println("----- less size: " + str.length);
			for(int i = 0 ; i < str.length ; i++) System.out.print(" " + str[i]);
			System.out.println();
			
			if(state.get(str[1]) == null){
				
				System.out.println("null in less looking up ... " + str[1]);
				
			} else {
				
				if( state.getInteger(str[1]) < Integer.parseInt(str[2])) {
					for(int i = 3 ; i < str.length ; i++) out.print(str[i] + " ");
					out.println();
				}
				
				else System.out.println("not less _" + str[1] + "_ = " + state.get(str[1]) + " value passed: " + str[2]);
							
			}
			
		} else {
			
			// pass through... send to robot 
			System.out.print("sending to bot: ");
			for(int i = 0 ; i < str.length ; i++) System.out.print(str[i] + " ");
			System.out.println();
			
			for(int i = 0 ; i < str.length ; i++) out.print(str[i] + " ");
			out.println();
				
			
		}	
	} 

	/** parameters: ip, port, user name, password, script file */ 
	public static void main(String args[]) throws IOException {
		new ScriptServer(args[0], args[1], args[2], args[3], args[4]);
	}
}