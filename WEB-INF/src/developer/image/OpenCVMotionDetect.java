package developer.image;


import oculusPrime.*;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;
import org.opencv.video.BackgroundSubtractorMOG2;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.List;

public class OpenCVMotionDetect  {
    OpenCVUtils cv = new OpenCVUtils();;
    private Mat fore = new Mat();
    public Mat detect = new Mat(); // test panel only
    private State state = null;
    private Settings settings = null;
    private Application app = null;

    public volatile boolean imageupdated = false; // test panel only

    // constructor
    public OpenCVMotionDetect() {
    }

    public OpenCVMotionDetect(Application a) {
        this();
        app = a;
        settings = Settings.getReference();
        state = State.getReference();
    }

    public void motionDetectGo() {
        if (state.getBoolean(State.values.motiondetect)) {
            Util.log("error, motion detect already running", this);
            return;
        }

        state.delete(State.values.streamactivity);
        state.set(State.values.motiondetect, true);

        new Thread(new Runnable() {
            public void run() {
                BackgroundSubtractorMOG2 mog = new BackgroundSubtractorMOG2(0, 1024, false);
//                frame = cv.bufferedImageToMat(ImageUtils.getImageFromStream());

                int f = 0;
                long timeout = System.currentTimeMillis() + Util.FIVE_MINUTES;
                while (state.getBoolean(State.values.motiondetect) && System.currentTimeMillis() < timeout) {

                    if (!state.getBoolean(State.values.motiondetect)) {
                        Util.log("error, motion detect timeout", this);
                        return;
                    }

                    BufferedImage img = null;

                    try {
                        img = ImageIO.read(new URL("http://127.0.0.1:" +
                                settings.readRed5Setting("http.port") + "/oculusPrime/frameGrabHTTP"));
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    double threshold = settings.getDouble(ManualSettings.motionthreshold.toString());

                    if (img == null) {
                        Util.log("stream unavailable", this);
                        break;
                    }

                    Mat frame = cv.bufferedImageToMat(img);

                    mog.apply(frame, fore, 0);
//                    mog.apply(frame, fore);
                    Imgproc.erode(fore, fore, new Mat());
                    Imgproc.dilate(fore, fore, new Mat());

                    double movingpixels = Core.countNonZero(fore);
                    double activity = movingpixels / (fore.width()*fore.height());
                    if ( activity > threshold && f>1) { // motion detected
//                        System.out.println(movingpixels);
                        if (!state.getBoolean(State.values.motiondetect)) break;
                        Mat m = new Mat();
                        fore.copyTo(m);
                        Imgproc.cvtColor(m, m, Imgproc.COLOR_GRAY2BGR);
                        Core.addWeighted(m, 0.5, frame, 1.0, 1.0, detect);
                        Application.processedImage = cv.matToBufferedImage(detect);
                        imageupdated = true;
                        state.set(State.values.streamactivity, "video " + activity);
                        if (app != null)
                            app.driverCallServer(PlayerCommands.messageclients, "motion detected "+activity);
                        break;
                    }

                    f ++;
                    Util.delay(100); // cpu saver
                }

                state.set(State.values.motiondetect, false);
            }
        }).start();

    }

    public void motionDetectGoTest() {

        new Thread(new Runnable() { public void run() {

            double threshold= 0.00001;
            int f = 0;
            BackgroundSubtractorMOG2 mog = new BackgroundSubtractorMOG2(0, 16, false);

            Mat frame;
            Mat gr = null;
            Mat bl = null;
            Mat m =  new Mat();

            String port = "5080";
            if (settings != null) {
                port = settings.readRed5Setting("http.port");
            }

            while (true) {

                if (state != null) {
                    if (!state.getBoolean(State.values.motiondetect)) break;
                }

                BufferedImage img = null;

                try {
                    img = ImageIO.read(new URL("http://127.0.0.1:" + port + "/oculusPrime/frameGrabHTTP"));
                } catch (IOException e) {
                    e.printStackTrace();
                }

                if (img == null) {
                    Util.log("stream unavailable", this);
                    state.delete(State.values.motiondetect);
                    break;
                }

                frame = cv.bufferedImageToMat(img);

//                frame = cv.bufferedImageToMat(ImageUtils.getImageFromStream());

                mog.apply(frame, fore, 0.01);
                //                    mog.apply(frame, fore);
                Imgproc.erode(fore, fore, new Mat());
                Imgproc.dilate(fore, fore, new Mat());

                double movingpixels = Core.countNonZero(fore);
                double activity = movingpixels / (fore.width() * fore.height());
                if (activity > threshold && f > 1) { // motion detected
//                    System.out.println(movingpixels);

                    fore.copyTo(m);

                    if (gr == null) gr = new Mat().zeros(frame.height(), frame.width(), CvType.CV_8U);
                    if (bl == null) bl = new Mat().zeros(frame.height(), frame.width(), CvType.CV_8U);
                    List<Mat> listMat = Arrays.asList(bl, gr, m);
                    Core.merge(listMat, m);
                    Core.addWeighted(m, 1.0, frame, 1.0, 1.0, detect);


//                    Imgproc.cvtColor(m, m, Imgproc.COLOR_GRAY2BGR);
//                    Core.addWeighted(m, 0.5, frame, 1.0, 1.0, detect);
                }
                else {
                    frame.copyTo(detect);
                }

                imageupdated = true;

                f++;
                Util.delay(50);

            }

        }     }).start();

    }

    public void motionDetectStream() {
        if (!state.get(State.values.stream).equals(Application.streamstate.camera.toString()) &&
                !state.get(State.values.stream).equals(Application.streamstate.camandmic.toString()) ) {
            app.message("motion detect unavailable, camera not running", null, null);
            return;
        }

        state.set(State.values.motiondetect, true);
        motionDetectGoTest();

        new Thread(new Runnable() {
            public void run() {
                try {

                    while(state.getBoolean(State.values.motiondetect)) {
                        if (imageupdated) {
                            app.videoOverlayImage = cv.matToBufferedImage(detect);
                            imageupdated = false;
                        }
                        Util.delay(10);
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                    state.delete(State.values.motiondetect);
                }
            }
        }).start();
    }


}
