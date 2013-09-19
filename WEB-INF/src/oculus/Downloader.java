package oculus;

import java.io.*;
import java.net.*;

public class Downloader {
	
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
			final String localFileName, final String destinationDir) {

		long start = System.currentTimeMillis();
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
			while ((ByteRead = is.read(buf)) != -1) {
				os.write(buf, 0, ByteRead);
				ByteWritten += ByteRead;
			}

			Util.log("saved to local file: " + path + " bytes: " + ByteWritten, this);
			Util.log("download took: "+ (System.currentTimeMillis()-start) + " ms", this);
			Util.log("downloaded " + ByteWritten + " bytes to: " + path, this);

		} catch (Exception e) {
			Util.log(e.getMessage(), this);
			return false;
		} finally {
			try {
				is.close();
				os.close();
			} catch (IOException e) {
				Util.log(e.getMessage(), this);
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
				
		// if zip exists, blocking extraction 
		if (System.getProperty("os.name").matches("Linux")) { 
			Util.systemCallBlocking("unzip "+zip+" -d "+des);
		}
		else {
			Util.systemCallBlocking("fbzip -e -p " + zip + " " + des);
		}
			
		// test if folders 
		if(new File(des).exists())
			if(new File(des).isDirectory())
				if(new File(des+sep+"update").exists())
					if(new File(des+sep+"update").isDirectory()) 
						return true;
		
		// error state
		Util.log("unzip and delete dirs - error");
		return false;
	}
	
	/**
	 * @param zipFile the zip file that needs to be unzipped
	 * @param destFolder the folder into which unzip the zip file and create the folder structure
	 
	public boolean unzipFolderJava( String zipFile, String destFolder ) {
		boolean result = false;
		try {
			ZipFile zf = new ZipFile(zipFile);
			Enumeration< ? extends ZipEntry> zipEnum = zf.entries();
			String dir = destFolder;

			while( zipEnum.hasMoreElements() ) {
				ZipEntry item = (ZipEntry) zipEnum.nextElement();

				if (item.isDirectory()) {
					File newdir = new File(dir + File.separator + item.getName());
					newdir.mkdir();
				} else {
					String newfilePath = dir + File.separator + item.getName();
					File newFile = new File(newfilePath);
					if (!newFile.getParentFile().exists()) {
						newFile.getParentFile().mkdirs();
					}

					InputStream is = zf.getInputStream(item);
					FileOutputStream fos = new FileOutputStream(newfilePath);
					int ch;
					while( (ch = is.read()) != -1 ) {
						fos.write(ch);
					}
					is.close();
					fos.close();
				}
			}
			result = true;
			zf.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return result;
	}
	 */
	
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
