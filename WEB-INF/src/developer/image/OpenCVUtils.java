package developer.image;


import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;


import oculusPrime.*;

import org.opencv.core.*;


public class OpenCVUtils {
	State state;
	Application app;


	public OpenCVUtils(Application a) {    // constructor
		state = State.getReference();
		app = a;
	}

    public void loadOpenCVnativeLib() {
        if ( State.getReference().get(State.values.osarch).equals(Application.ARM)) {
            Util.log("ARM system detected, openCV skipped", this);
            return;
        }

        try {
            System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
            Util.log("opencv native lib "+Core.NATIVE_LIBRARY_NAME+" loaded OK", this);
        } catch (UnsatisfiedLinkError e) {
            Util.log("opencv native lib "+Core.NATIVE_LIBRARY_NAME+" not available", this);
        }

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

}