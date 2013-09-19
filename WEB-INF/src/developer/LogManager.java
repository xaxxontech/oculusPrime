package developer;

import java.io.RandomAccessFile;

/**
 * Manage a log file on local storage
 */
public class LogManager {
	
    public static final String CRLF = "\r\n";
    
    private RandomAccessFile logfile = null;

    public LogManager() {}

    /**
     * Opens the specified logfile with read/write access.
     * 
     * @param filename
     *            is the name of the log file.
     */
    public void open(String filename) {

        // sanity check
        if (isOpen()){
        	System.err.println("log file is allready open: " + filename);
            return ;
        }
              
        try {

            logfile = new RandomAccessFile(filename, "rw");

        } catch (Exception e) {
        	System.err.println("can't open: " + filename);
        	logfile = null;
        }
    }

    /**
     * Closes the logfile.
     */
    public void close() {
    	
    	if(logfile == null) return;
    	
        try {
        	if(isOpen())
        		logfile.close();
        } catch (Exception e) {
        	logfile = null;
        }
    }

    /**
     * Appends data to the log file.
     * <p/>
     * If the logfile has not been previously opened, or if there is a file reading error,
     * this method will do nothing.
     * 
     * @param data
     *            is the text to append to the logfile.
     */
    public synchronized void append(String data) {
    	
    	// sanity check
        if (!isOpen()) {
            return;
        }
        
        try {

            // position file pointer at the end of the logfile
            logfile.seek(logfile.length());
            
            // add date 
            data = System.currentTimeMillis() + " " + data;

            // log zephyr.framework.state
            logfile.writeBytes(data + CRLF);

        } catch (Exception e) {
        	
        	logfile = null;
        	// System.err.println("error on append, closed file", this);
        }
    }

    // Returns true if the logfile is open, otherwise false.
    public boolean isOpen() {
        return logfile != null;
    }
}
