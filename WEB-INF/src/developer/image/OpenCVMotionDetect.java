package developer.image;


import oculusPrime.*;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;
import org.opencv.video.BackgroundSubtractorMOG2;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URL;

public class OpenCVMotionDetect  {
    OpenCVUtils cv = new OpenCVUtils();;
    private Mat fore = new Mat();
    public Mat detect = new Mat(); // test panel only
    private State state = State.getReference();
    private Settings settings;
    private Application app = null;

    public volatile boolean imageupdated = false; // test panel only

    // constructor
    public OpenCVMotionDetect() {
    }

    public OpenCVMotionDetect(Application a) {
        this();
        app = a;
        settings = Settings.getReference();
    }

    public void motionDetectGo() {
        if (state.getBoolean(State.values.motiondetectwatching)) {
            Util.log("error, motion detect already running", this);
            return;
        }

        state.delete(State.values.streamactivity);
        state.set(State.values.motiondetectwatching, true);

        new Thread(new Runnable() {
            public void run() {
                BackgroundSubtractorMOG2 mog = new BackgroundSubtractorMOG2(0, 1024, false);
//                frame = cv.bufferedImageToMat(ImageUtils.getImageFromStream());

                int f = 0;
                long timeout = System.currentTimeMillis() + Util.FIVE_MINUTES;
                while (state.getBoolean(State.values.motiondetectwatching) && System.currentTimeMillis() < timeout) {

                    if (!state.getBoolean(State.values.motiondetectwatching)) {
                        Util.log("error, motion detect timeout", this);
                        return;
                    }

                    BufferedImage img = null;

                    // comment below for test panel
                    try {
                        img = ImageIO.read(new URL("http://127.0.0.1:" +
                                settings.readRed5Setting("http.port") + "/oculusPrime/frameGrabHTTP"));
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    double threshold = settings.getDouble(ManualSettings.motionthreshold.toString());

                    // test panel only
//                    double threshold= 0.002;
//                    img = ImageUtils.getImageFromStream();

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
                        if (!state.getBoolean(State.values.motiondetectwatching)) break;
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

                state.set(State.values.motiondetectwatching, false);
            }
        }).start();

    }



}
