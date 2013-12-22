package developer.depth;

/*
 * this class to be run standalone as jar under oculusPrime/xtionread/
 * also copy contents of 'OpenNI-Linux-x86-2.2/Redist' folder to oculusPrime/xtionread/
 * called by OpenNIRead.startDepthCam()
 */

import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.util.List;

import org.openni.*;

public class XtionRead {

    static VideoStream mVideoStream;
    static VideoFrameRef mLastFrame;
    
    public XtionRead() {

    }

    public static void main(String s[]) {
        // initialize OpenNI
        OpenNI.initialize();
        
        List<DeviceInfo> devicesInfo = OpenNI.enumerateDevices();
        if (devicesInfo.isEmpty()) {
            return;
        }
        
        Device device = Device.open(devicesInfo.get(0).getUri());

		SensorType type =  SensorType.DEPTH;
		mVideoStream = VideoStream.create(device, type);
		
		VideoMode mode = new VideoMode(320, 240, 30, PixelFormat.DEPTH_1_MM.toNative());
		mVideoStream.setVideoMode(mode);
		
		mVideoStream.start();
		XtionRead blob = new XtionRead();

		try {

//        	long lasttime = System.currentTimeMillis();
//        	int fps = 0;
	    	
	    	while (true) {
		    	mLastFrame = mVideoStream.readFrame();
		    	ByteBuffer frameData = mLastFrame.getData().order(ByteOrder.LITTLE_ENDIAN);
		    	FileChannel out = new FileOutputStream("/run/shm/xtion.raw").getChannel();
		    	out.write(frameData);
		    	out.close();
 
//		    	fps ++;
//		    	long now = System.currentTimeMillis();
//		    	if (now - lasttime  >= 1000) {
//		    		System.out.println(fps);
//		    		lasttime = now;
//		    		fps = 0;
//		    	}
		    	
	    	}

    	} catch(Exception e) { e.printStackTrace(); }
		
    }
	
}


