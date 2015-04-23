package developer.image;


import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;
import org.opencv.video.BackgroundSubtractorMOG2;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URL;

/**
 * Created by colin on 21/04/15.
 */
public class OpenCVMotionDetect  {
    OpenCVUtils cv;

    public OpenCVMotionDetect() {
        cv = new OpenCVUtils(); // TODO: testing

    }

    public BufferedImage getImage() {
        BufferedImage img = null;
        try {
//            img = ImageIO.read(new URL("http://127.0.0.1:5080/oculusPrime/frameGrabHTTP"));
            img = ImageIO.read(new URL("http://192.168.0.107:5080/oculusPrime/frameGrabHTTP"));
        } catch (IOException e) { e.printStackTrace(); }
//        Mat m = cv.bufferedImageToMat(img);
//        Imgproc.cvtColor(m, m, Imgproc.COLOR_RGB2GRAY);
//        img = cv.matToBufferedImage(m);
        return img;
    }

    public void motionDetectGo() {
        BackgroundSubtractorMOG2 mog = new BackgroundSubtractorMOG2(30, 16, true);

        int frame = 0;
        while (frame < 20) {
            Mat m = cv.bufferedImageToMat(getImage());
        }
    }



}
