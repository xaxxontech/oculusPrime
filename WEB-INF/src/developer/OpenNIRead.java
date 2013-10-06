package developer;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.util.Map;

import oculusPrime.Util;


public class OpenNIRead  {
	
	private boolean depthCamInit = false;
	public  boolean depthCamGenerating = false;
	private ByteBuffer frameData;
	private int[] lastresult;
	Process camproc;
	
	public OpenNIRead()  {
	}
	
	public void startDepthCam() {
		if (depthCamInit) return;
		oculusPrime.Util.log("start depth cam", this);

		new Thread(new Runnable() { 
			public void run() {
				try {
					
					String sep = System.getProperty("file.separator");
					String dir = System.getenv("RED5_HOME")+sep+"xtionread";
					String javadir = System.getProperty("java.home");
					String cmd = javadir+sep+"bin"+sep+"java"; 
					String arg = dir+sep+"xtion.jar";
					ProcessBuilder pb = new ProcessBuilder(cmd, "-jar", arg);
					Map<String, String> env = pb.environment();
					env.put("LD_LIBRARY_PATH", dir);
					camproc = pb.start();
					
				} catch (Exception e) {
					e.printStackTrace();
				}		
			} 	
		}).start();

		depthCamGenerating = true;
		depthCamInit = true;
		
	}
	
	public void stopDepthCam()  {
		if (!depthCamInit) return;
		oculusPrime.Util.log("stop depth cam", this);

		camproc.destroy();
		
		depthCamInit = false;
		depthCamGenerating = false;
	}
	
	public int[] readHorizDepth(int y) {

		int width = 320;
		int[]result = new int[width];
		int size = 320*240*2;
		
    	try {
    		FileInputStream file = new FileInputStream("/run/shm/xtion.raw");
			FileChannel ch = file.getChannel();
			if (ch.size() == size) {
				frameData = ByteBuffer.allocate((int) size);
				ch.read(frameData.order(ByteOrder.LITTLE_ENDIAN));
				ch.close();
				file.close();
			}
			ch.close();
			file.close();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

    	boolean blank=true;
		for (int x=0; x<width; x++) {
	        
	        int p = ((width * y)+x)*2;
	        int depth = (int) frameData.getShort(p);
	        result[x] = depth;
	        
	        if (depth != 0) { blank = false; }
		}
		
	//			Util.log(Integer.toString((int) frameData.getShort(4500)), this);

			

		if (blank) { return lastresult; }
		else { 
			lastresult = result;
			return result; 
		}
		
		
	}

	
}
