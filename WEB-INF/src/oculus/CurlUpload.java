package oculus;

import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

public class CurlUpload {

	static public final String BASE = "webapps/oculus/WEB-INF/classes/";
	static private Runtime runtime = Runtime.getRuntime();
	static String user;
	static String pass;
	static String host;

	public static void main(String[] args) {

		if(args.length!=3) return; 
		host = args[0];
		user = args[1];
		pass = args[2];
		
		final Collection<File> all = new ArrayList<File>();
		addFilesRecursively(new File("."), all);
		Iterator<File> dirs = all.iterator();
		while (dirs.hasNext()) {
			File loop = dirs.next();
			if (new File(loop.getAbsolutePath()).isDirectory()) {
				if (loop.getAbsolutePath().contains("classes")) {
					if (!loop.getAbsolutePath().contains(".svn")) {
						String path = loop.toString().substring(2);						
						doFolder(path, new ClassFilter());
					}
				}
			}
		}
		
		/* restart the target robot too?
		
		String port = new Settings().readSetting(OptionalSettings.commandport);
		if(port==null) port = "4444";
		*/
		
		Util.delay(5000);
		macProc("java -classpath \".:"+BASE+"\" developer.terminal.Terminal " 
				+ host + " 4444 " + user + " " + pass + " beep restart &");
	
		System.out.println(" -- done uploading --");
	}

	private static void addFilesRecursively(File file, Collection<File> all) {
		final File[] children = file.listFiles();
		if (children != null) {
			for (File child : children) {
				all.add(child);
				addFilesRecursively(child, all);
			}
		}
	}

	static void doFolder(final String folder, FilenameFilter filter) {
		File dir = new File(folder);
		if (dir.isDirectory()) {
			String[] files = dir.list(new ClassFilter());
			for (int i = 0; i < files.length; i++) 
				macProc("curl -T " + folder + "/" + files[i] + " ftp://" + host + "/"
						+ folder + "/"+ " --user " + user + ":" + pass
						+ " --ftp-create-dirs & ");
		}
	}

	static class ClassFilter implements FilenameFilter {
		public boolean accept(File dir, String name) {
			return (name.endsWith(".class"));
		}
	}

	public static void macProc(final String arg) {

		System.err.println("exec: " + arg);

		final String[] args = new String[] { "/bin/sh", "-c", arg };
		try {
	
			runtime.exec(args);

		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
