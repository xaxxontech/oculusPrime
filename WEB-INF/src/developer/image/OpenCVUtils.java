package developer.image;


import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;


import oculusPrime.Application;
import oculusPrime.AutoDock;
import oculusPrime.State;
import oculusPrime.Util;

import org.opencv.core.*;
import org.opencv.highgui.Highgui;
import org.opencv.highgui.VideoCapture;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;


public class OpenCVUtils {
	State state;
	Application app;

	public OpenCVUtils(Application a) {    // constructor
//		System.loadLibrary( Core.NATIVE_LIBRARY_NAME ); // moved to Application so only loaded once
		state = State.getReference();
		app = a;
	}

	public static BufferedImage matToBufferedImage(Mat matrix) { // type_intRGB
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

	public static Mat bufferedImageToMat(BufferedImage img) {
//		img = ImageUtils.toBufferedImageOfType(img, BufferedImage.TYPE_3BYTE_BGR);

		byte[] pixels = ((DataBufferByte) img.getRaster().getDataBuffer()).getData();
		Mat m = new Mat(img.getHeight(), img.getWidth(), CvType.CV_8UC3);
		m.put(0, 0, pixels);
		return m;
	}

	public Mat getWebCamImg(VideoCapture capture) {
//    	VideoCapture capture =new VideoCapture(camnum); 
		capture.set(Highgui.CV_CAP_PROP_FRAME_WIDTH, 320);
		capture.set(Highgui.CV_CAP_PROP_FRAME_WIDTH, 240);
		Mat webcam_image = null;
		if (capture.isOpened()) {
			webcam_image = new Mat();
			capture.grab(); // discard 1st
			Util.delay(1000);
			capture.read(webcam_image);
		}
//    	capture.release();
		return webcam_image;
	}

	public void jpgStream(final String res) {
		if (state.exists(State.values.jpgstream)) return; // already running;

		new Thread(new Runnable() {
			public void run() {

				if (! state.get(State.values.stream).equals(Application.streamstate.stop.toString())) {
					app.publish(Application.streamstate.stop);
					Util.delay(Application.STREAM_CONNECT_DELAY*2);
				}

				VideoCapture capture = new VideoCapture(0);
				Util.delay(1000); // alow time to init

				if (!capture.isOpened()) {
					Util.log("unable to open camera", this);
					return;
				}

				state.set(State.values.jpgstream, true);

				if (res.equals(AutoDock.HIGHRES)) {
					capture.set(Highgui.CV_CAP_PROP_FRAME_WIDTH, 640);
					capture.set(Highgui.CV_CAP_PROP_FRAME_WIDTH, 480);
				} else {
					capture.set(Highgui.CV_CAP_PROP_FRAME_WIDTH, 320);
					capture.set(Highgui.CV_CAP_PROP_FRAME_WIDTH, 240);
				}

				Mat webcam_image = new Mat();
				capture.grab(); // discard 1st

				while (state.getBoolean(State.values.jpgstream)) {
					capture.read(webcam_image);
					if (webcam_image.width() <=0) {
						Util.log("img 0 size", this);
						break;
					}
					Application.videoOverlayImage = matToBufferedImage(webcam_image);
					Util.delay(25); // cpu saver
				}

				capture.release();
				Util.delay(1000); // alow time to init
				capture = new VideoCapture(0); // needed to release camera...?
				capture.release();

				webcam_image.release();
				System.gc(); // suggest garbage collection, unable to init camera without it!
				Util.log("jpgstream() thread exit", this);

				state.delete(State.values.jpgstream);

			}
		}).start();
	}

}