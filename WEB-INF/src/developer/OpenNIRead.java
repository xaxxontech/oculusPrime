package developer;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

import org.openni.*;

public class OpenNIRead implements VideoStream.NewFrameListener {
	
	private boolean depthCamInit = false;
	public  boolean depthCamGenerating = false;
    private VideoStream mVideoStream;
    private Device device;
    VideoFrameRef mLastFrame;
    
	
	public OpenNIRead()  {
	}
	
	public void startDepthCam() {
		if (depthCamInit) return;
		
		oculusPrime.Util.log("start depth cam", this);

		
		OpenNI.initialize();
		
		List<DeviceInfo> devicesInfo = OpenNI.enumerateDevices();
		if (devicesInfo.isEmpty()) {
			oculusPrime.Util.log("No OpenNI device is connected, Error", this);
			return;
		}
		device = Device.open(devicesInfo.get(0).getUri());

		SensorType type =  SensorType.DEPTH;
		mVideoStream = VideoStream.create(device, type);
		
		VideoMode mode = new VideoMode(320, 240, 30, PixelFormat.DEPTH_1_MM.toNative());
		mVideoStream.setVideoMode(mode);
		mVideoStream.addNewFrameListener(this);
		
//		oculusPrime.Util.log(device.getSensorInfo(type).toString(), this);
		mVideoStream.start();
		
		depthCamInit = true;
		oculusPrime.Util.log("started depth cam", this);
	}
	
	public void stopDepthCam()  {
		if (!depthCamInit) return;
		
		oculusPrime.Util.log("stop depth cam", this);
		mVideoStream.stop();
		OpenNI.shutdown();
        
		depthCamInit = false;
	}
	
	public int[] readHorizDepth(int y) {
		
//		int width = 320;
//		VideoFrameRef frame = mVideoStream.readFrame();
//		
//        ByteBuffer frameData = frame.getData().order(ByteOrder.LITTLE_ENDIAN);
//        
//        int[] result = new int[width];
//        
//        for (int x=0; x<width; x++) {
//        	result[x] = (int) frameData.getShort(y*width+x) & 0xFFFF;
//        }
//
//		return result;

		/*
		try {
			context.waitAnyUpdateAll();
				// sometimes throws: org.OpenNI.StatusException: Xiron OS got an event timeout!
		} catch (StatusException e) {
			e.printStackTrace();
		}
		*/
//		depth.getMetaData(depthMD);
//		int[] result = new int[depthMD.getXRes()];
//		int p=0;
//		for (int x=0; x < depthMD.getXRes(); x++) {
//			result[p]= depthMD.getData().readPixel(x, y); // depthMD.getYRes() / 2);
//			p++;
//		}
        
        
		
		return new int[]{1,2,3};
		
		
	}

	@Override
	public void onFrameReady(VideoStream arg0) {
        if (mLastFrame != null) {
            mLastFrame.release();
            mLastFrame = null;
        }
        
        mLastFrame = mVideoStream.readFrame();

//        mLastFrame.release();
//        mLastFrame = null;        
		
	}


	
}
