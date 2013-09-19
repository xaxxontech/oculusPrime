package developer;

import java.io.*;
import java.net.*;
import java.util.*;

import oculus.Util;

/**
 * Basic FTP functionality 
 */
public class FTP {

    private Socket socket = null;
    private BufferedReader reader = null;
    private BufferedWriter writer = null;

    public FTP() {}

	/**
	 * Connect to the FTP Server 
	 * 
	 * @param host is the URL to the FTP server 
	 * @param port is the port the FT server is listening to (21 is default)
	 * @param user is the name of the FTP Account 
	 * @param pass is the matching pass word for this user 
	 * @throws Exception if the connection fails. FTP error code included 
	 */
    public synchronized void connect(String host, String port, String user, String pass) throws IOException {

    	// System.out.println("host : " + host );
    	// System.out.println("port : " + port );
    	// System.out.println("user : " + user );
    	// System.out.println("pass : " + pass );
    	
        socket = new Socket(host, Integer.parseInt(port));
        reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));

        String response = readLine();

        if (!response.startsWith("220 ")) 
            throw new IOException("error connecting to the FTP server: " + response);

        sendLine("USER " + user);
        response = readLine();

        if (!response.startsWith("331 ")) 
            throw new IOException("bad user name: " + response);
             
        sendLine("PASS " + pass);
        response = readLine();

        if (!response.startsWith("230 ")) 
            throw new IOException("bad password: " + response);
    }

    /** Disconnects from the FTP server. */
    public synchronized void disconnect() throws IOException {
        try {
            sendLine("QUIT");
            if(socket.isConnected())socket.close();
        }

        finally {
        	socket = null; 
        }
    }
    
    /**
     * Changes the working directory 
     * @return true if successful.
     */   
    public synchronized boolean cwd(String dir) throws IOException {

        sendLine("CWD " + dir);
        return (readLine().startsWith("250 "));
    }

    /**
     *  Sends a file to be stored on the FTP server.
     */
    public synchronized boolean storString(final String filename, final String input) throws IOException {
    	
    	if((filename== null) || (input==null)) return false;
    	
    	ascii();
        sendLine("PASV");
        String response = readLine();
        if (!response.startsWith("227 ")) throw new IOException("could not switch to passive mode: " + response);
        
        String ip = null;
        int port = -1;

        int opening = response.indexOf('(');
        int closing = response.indexOf(')', opening + 1);

        if (closing > 0) {

            String dataLink = response.substring(opening + 1, closing);
            StringTokenizer tokenizer = new StringTokenizer(dataLink, ",");

            try {
                ip = tokenizer.nextToken() + "." + tokenizer.nextToken() + "." + tokenizer.nextToken() + "." + tokenizer.nextToken();
                port = Integer.parseInt(tokenizer.nextToken()) * 256 + Integer.parseInt(tokenizer.nextToken());
            } catch (Exception e) {
                throw new IOException("error: " + response);
            }
        }

        //
        // write to file on host 
        // 
        sendLine("STOR " + filename);
        Socket dataSocket = new Socket(ip, port);
        
        Util.debug(dataSocket.toString() + " " + filename, this);
        
        response = readLine();
        if (!response.startsWith("150 ")) {
            throw new IOException("bad perms to send the file");
        }

        BufferedOutputStream output = new BufferedOutputStream(dataSocket.getOutputStream());
        output.write( input.getBytes() );  
        
        output.flush();
        output.close();

        response = readLine();
        return response.startsWith("226 ");
    }

    /**
     * Set binary mode for sending binary files.
     */
    public synchronized boolean bin() throws IOException {

        sendLine("TYPE I");
        return (readLine().startsWith("200 "));
    }
    
    /**
     * Use ASCII mode 
     */
    public synchronized boolean ascii() throws IOException {

        sendLine("TYPE A");
        return (readLine().startsWith("200 "));
    }

    /**
     * Use ASCII mode 
     */
    public synchronized boolean pasv() throws IOException {
    	  
    	sendLine("PASV");
        String response = readLine();
         
        return (!response.startsWith("227 "));
    }

    /**
     * Sends a line to the socket 
     */
    private synchronized void sendLine(String line) throws IOException {

        if (socket == null) throw new IOException("FTP is not connected.");
        
        try {

            writer.write(line + "\r\n");
            writer.flush();

        } catch (IOException e) {

            socket = null;
            throw e;

        }
    }

    /**
     * Read a line from the server 
     *
     * @return the data from the FTP server 
     * @throws IOException on error 
     */
    private synchronized String readLine() throws IOException {
        return reader.readLine();
    }
}