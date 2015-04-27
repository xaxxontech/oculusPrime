package developer.image;


import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;


import oculusPrime.Util;

import org.opencv.core.*;
import org.opencv.highgui.Highgui;
import org.opencv.highgui.VideoCapture;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;


public class OpenCVUtils {

	public OpenCVUtils() {  	// constructor
//		System.loadLibrary( Core.NATIVE_LIBRARY_NAME ); // moved to Application so only loaded once

	}
    
    public BufferedImage matToBufferedImage(Mat matrix) { // type_intRGB
		int cols = matrix.cols();
		int rows = matrix.rows();
		int elemSize = (int) matrix.elemSize();
		byte[] data = new byte[cols * rows * elemSize];
		int type;
		matrix.get(0, 0, data);
		switch (matrix.channels()) {
		case 1:
			type = BufferedImage.TYPE_BYTE_GRAY;
			break;
		case 3:
			type = BufferedImage.TYPE_3BYTE_BGR;
			// bgr to rgb
			byte b;
			for (int i = 0; i < data.length; i = i + 3) {
				b = data[i];
				data[i] = data[i + 2];
				data[i + 2] = b;
			}
			break;
		default:
			return null;
		}
		BufferedImage image = new BufferedImage(cols, rows, type);
		image.getRaster().setDataElements(0, 0, cols, rows, data);
		return image;
	}

	public Mat bufferedImageToMat(BufferedImage img) {
//		img = ImageUtils.toBufferedImageOfType(img, BufferedImage.TYPE_3BYTE_BGR);

		byte[] pixels = ((DataBufferByte) img.getRaster().getDataBuffer()).getData();
		Mat m = new Mat(img.getHeight(), img.getWidth(), CvType.CV_8UC3);
		m.put(0,0,pixels);
		return m;
	}

    public Mat getWebCamImg(VideoCapture capture) {
//    	VideoCapture capture =new VideoCapture(camnum); 
    	capture.set(Highgui.CV_CAP_PROP_FRAME_WIDTH, 320);
    	capture.set(Highgui.CV_CAP_PROP_FRAME_WIDTH, 240);
    	Mat webcam_image=null;
    	if( capture.isOpened()) {
    		webcam_image=new Mat();
    		capture.grab(); // discard 1st
    		Util.delay(1000);
    		capture.read(webcam_image);
    	}
//    	capture.release();
    	return webcam_image;
    }
    
    
}
