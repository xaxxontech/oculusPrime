package oculusPrime;

public class PyScripts {
	
//	static Vector<PyScripts> scripts = new Vector<PyScripts>();
	String logFile, id, pyFile = null;
	
	PyScripts (String id, String pyFile, String log ){

		//Util.log("size =" + scripts.size() + " " + scripts.toString());
		//for( int i = 0 ; i < scripts.size() ; i++) Util.log(i + " =" + scripts.get(i).toString());

		this.pyFile = pyFile;
		this.logFile = log;
		this.id = id;
	}
	/*
	static void add(String id, String file, String log){
		// PyScripts py = new PyScripts(id, file, log);
		if( ! exists(id)) scripts.add( new PyScripts(id, file, log));
	}
	
	static boolean exists(String id){
		for( int i = 0 ; i < scripts.size() ; i++) if( scripts.get(i).equals(id)) return true;
		return false;
	}
	
	public String toString(){
		return pyFile;
	}
	*/
	
}