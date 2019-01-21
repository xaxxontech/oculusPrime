package oculusPrime;

import java.io.*;
import java.net.*;

public class Downloader {

	public boolean FileDownload(final String fileAddress,
								final String localFileName, final String destinationDir) {

		return FileDownload(fileAddress, localFileName, destinationDir, Util.ONE_MINUTE*5); // default 5 min timeout
	}
	
	/**
	 * 
	 * Down load a given URL to the local disk. Will delete existing file first, and create directory if required. 
	 *
	 * @param fileAddress is the full http://url of the remote file
	 * @param localFileName the file name to use on the host
	 * @param destinationDir the folder name to put this down load into 
	 * @return true if the file is down loaded, false on any error. 
	 * 
	 */
	public boolean FileDownload(final String fileAddress,
		final String localFileName, final String destinationDir, long timeout) {

		// long start = System.currentTimeMillis();
		String sep = System.getProperty("file.separator");
		
		InputStream is = null;
		OutputStream os = null;
		URLConnection URLConn = null;

		// create path to local file
		final String path = System.getenv("RED5_HOME")+ sep + destinationDir + sep + localFileName;

		// create target directory
		new File(System.getenv("RED5_HOME")+ sep + destinationDir).mkdirs();

		// delete target first
		new File(path).delete();

		// test is really gone
		if (new File(path).exists()) {
			Util.log("can't delete existing file: " + path, this);
			return false;
		}

		try {

			int ByteRead, ByteWritten = 0;
			os = new BufferedOutputStream(new FileOutputStream(path));

			URLConn = new URL(fileAddress).openConnection();
			is = URLConn.getInputStream();
			byte[] buf = new byte[1024];

			// pull in the bytes
			long t = System.currentTimeMillis();
			while ((ByteRead = is.read(buf)) != -1) {
				if (System.currentTimeMillis() - t > timeout) {
					Util.log("Download timed out, aborted", this);
					is.close();
					os.close();
					return false;
				}
				os.write(buf, 0, ByteRead);
				ByteWritten += ByteRead;
			}

			Util.log("saved to local file: " + path + " bytes: " + ByteWritten, this);
			// Util.debug("download took: "+ (System.currentTimeMillis()-start) + " ms", this);
			// Util.debug("downloaded " + ByteWritten + " bytes to: " + path, this);

		} catch (Exception e) {
			Util.log("ERROR downloading file", this);
//			e.printStackTrace();
			return false;
		}
		// ** commented all below out because it hangs
		finally {
			try {
				if (is != null) is.close();
				if (os != null) os.close();
			} catch (IOException e) {
//				Util.log(e.getMessage(), this);
				return false;
			}
		}

		// all good
		return true;
	}


	/**
	 * @param zipFile the zip file that needs to be unzipped
	 * @param destFolder the folder into which unzip the zip file and create the folder structure
	 */
	public boolean unzipFolder( String zipFile, String destFolder ) {
		
		String sep = System.getProperty("file.separator");
		final String zip = (System.getenv("RED5_HOME") + sep + zipFile).trim();
		final String des = (System.getenv("RED5_HOME") + sep + destFolder).trim(); 

		if( ! new File(zip).exists()){	
			Util.log("no zip file found: " + zip, this);
			return false;
		}
				
		Util.systemCallBlocking("unzip "+zip+" -d "+des);
			
		// test if folders 
		if(new File(des).exists())
			if(new File(des).isDirectory())
				if(new File(des+sep+"update").exists())
					if(new File(des+sep+"update").isDirectory()) 
						return true;
		
		// error state
		Util.log("unzip and delete dirs - error", this);
		return false;
	}
	
	
	/**
	 * @param filename
	 */
	public void deleteFile(String filename) {
		String sep = System.getProperty("file.separator");

		File f = new File(System.getenv("RED5_HOME")+sep+filename);
		try {
			f.delete();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public boolean deleteDir(File dir) {

	    if (dir.isDirectory()) {
	        String[] children = dir.list();
	        for (int i=0; i<children.length; i++) {
	            boolean success = deleteDir(new File(dir, children[i]));
	            if (!success) return false;
	        }
	    }

	    // The directory is now empty so delete it
	    return dir.delete();
	}

}
