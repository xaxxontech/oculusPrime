package oculusPrime;


import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

public class Video {

    private static State state = State.getReference();
    private Application app = null;
    private String host = "127.0.0.1";
    private String port = "1935";
    private int devicenum = 0;  // should match lifecam cam
    private int adevicenum = 1; // should match lifecam mic
    private int quality = 5;
    private static final int defaultwidth=640;
    private static final int defaultheight=480;
    private static final String PATH="/dev/shm/avconvframes/";
    private static final String EXT=".bmp";
    private volatile long lastframegrab = 0;
    private int lastwidth=0;
    private int lastheight=0;
    private int lastfps=0;
    private Application.streamstate lastmode = Application.streamstate.stop;
    private long publishid = 0;
    static final long STREAM_RESTART = Util.ONE_MINUTE*7;

    public Video(Application a) {
        app = a;
        state.set(State.values.stream, Application.streamstate.stop.toString());
    }

    public void publish (final Application.streamstate mode, final int w, final int h, final int fps) {
        // todo: determine video device (in constructor)
        // todo: disallow unsafe custom values (device can be corrupted?)

        if (w==lastwidth && h==lastheight && fps==lastfps && mode.equals(lastmode)) {
            Util.log("identical stream already running, dropped", this);
            return;
        }

        lastwidth = w;
        lastheight = h;
        lastfps = fps;
        lastmode = mode;
        final long id = System.currentTimeMillis();
        publishid = id;

        new Thread(new Runnable() { public void run() {

            // nuke currently running avconv if any
            if (!state.get(State.values.stream).equals(Application.streamstate.stop.toString()) &&
                    !mode.equals(Application.streamstate.stop.toString())) {
                state.set(State.values.writingframegrabs, false);
                Util.systemCallBlocking("pkill avconv");
                Util.delay(Application.STREAM_CONNECT_DELAY);
            }

            switch (mode) {
                case camera:
                    Util.systemCall("avconv -f video4linux2 -s " + w + "x" + h + " -r " + fps +
                            " -i /dev/video" + devicenum + " -f flv -q " + quality + " rtmp://" + host + ":" +
                            port + "/oculusPrime/stream1");
                    // avconv -f video4linux2 -s 640x480 -r 8 -i /dev/video0 -f flv -q 5 rtmp://127.0.0.1:1935/oculusPrime/stream1
                    app.driverCallServer(PlayerCommands.streammode, mode.toString());
                    break;
                case mic:
                    Util.systemCall("avconv -re -f alsa -ac 1 -ar 22050 " +
                            "-i hw:" + adevicenum + " -f flv rtmp://" + host + ":" +
                            port + "/oculusPrime/stream1");
                    app.driverCallServer(PlayerCommands.streammode, mode.toString());
                    break;
                case camandmic:
                    Util.systemCall("avconv -re -f alsa -ac 1 -ar 22050 " +
                            "-i hw:" + adevicenum + " -f flv rtmp://" + host + ":" +
                            port + "/oculusPrime/stream2");
                    app.driverCallServer(PlayerCommands.streammode, mode.toString());

                    Util.systemCall("avconv -f video4linux2 -s " + w + "x" + h + " -r " + fps +
                            " -i /dev/video" + devicenum + " -f flv -q " + quality + " rtmp://" + host + ":" +
                            port + "/oculusPrime/stream1");

//                    if (state.get(State.values.osarch).equals(Application.ARM))
//                        Util.delay(Application.STREAM_CONNECT_DELAY);

                    break;
                case stop:
                    state.set(State.values.writingframegrabs, false);
                    Util.systemCall("pkill avconv");
                    app.driverCallServer(PlayerCommands.streammode, mode.toString());
                    break;
            }

        } }).start();

        if (mode.equals(Application.streamstate.stop) ) return;

        // stream restart timer
        new Thread(new Runnable() { public void run() {
            long start = System.currentTimeMillis();
            while ( (id == publishid && System.currentTimeMillis() < start + STREAM_RESTART) ||
                    state.getBoolean(State.values.writingframegrabs))
                Util.delay(50);

            if (id == publishid) { // restart stream
                Util.systemCall("pkill avconv");
                lastmode = Application.streamstate.stop;
                Util.delay(Application.STREAM_CONNECT_DELAY);
                publish(mode, w,h,fps);
//                app.driverCallServer(PlayerCommands.messageclients, "stream restart after "+(System.currentTimeMillis()-start+"ms"));
            }

        } }).start();

    }

    public void framegrab(final String res) {

        state.set(State.values.framegrabbusy.name(), true);

//        Util.log("framegrab start", this); // TODO: nuke
        lastframegrab = System.currentTimeMillis();

        new Thread(new Runnable() { public void run() {
            if (!state.getBoolean(State.values.writingframegrabs)) {
                dumpframegrabs(res);
                Util.delay(Application.STREAM_CONNECT_DELAY);
            }

            // determine latest image file
            File dir=new File(PATH);
            File imgfile = null;
            long start = System.currentTimeMillis();
            while (imgfile == null && System.currentTimeMillis()-start < 10000) {
                int highest = 0;
                for (File file : dir.listFiles()) {
                    int i = Integer.parseInt(file.getName().split("\\.")[0]);
                    if (i > highest) {
                        imgfile = file;
                        highest = i;
                    }
                }
                Util.delay(1);
            }
            if (imgfile == null) { Util.log("avconv frame unavailable", this); }
            else {
//            app.processedImage  = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
//                Util.log(imgfile.getName(), this); // TODO: nuke
                try {
                    app.processedImage = ImageIO.read(imgfile);
                } catch (IOException e) {
                    Util.printError(e);
                }
            }

            state.set(State.values.framegrabbusy, false);

        } }).start();
    }

    private void dumpframegrabs(final String res) {

        Util.log("dumpframegrabs start", this); // TODO: nuke

        state.set(State.values.writingframegrabs, true);

        // setup ram drive folder, this part blocking so complete before framegrab thread gets underway
        File dir=new File(PATH);
        dir.mkdirs();
        for(File file: dir.listFiles()) file.delete(); // nuke any existing files

        int width = defaultwidth;
        int height = defaultheight;

        // set resolution
        if (res.equals(AutoDock.LOWRES)) {
            width=320;
            height=240;
        }


        try {
            Runtime.getRuntime().exec(new String[]{"avconv", "-analyzeduration", "0", "-i",
                    "rtmp://" + host + ":" + port + "/oculusPrime/stream1 live=1", "-s", width+"x"+height,
                    "-r", Integer.toString(15), "-q", Integer.toString(quality), PATH+"%d"+EXT  });
        }catch (Exception e) { Util.printError(e); }

        new Thread(new Runnable() { public void run() {

            Util.delay(500); // required?

            // continually clean all but the latest few files, prevent mem overload
            int i=1;
            while(state.getBoolean(State.values.writingframegrabs)
                    && System.currentTimeMillis() - lastframegrab < Util.ONE_MINUTE) {
                File file = new File(PATH+i+EXT);
                if (file.exists() && new File(PATH+(i+7)+EXT).exists()) {
                    file.delete();
                    i++;
                }
                Util.delay(50);
            }

            state.set(State.values.writingframegrabs, false);
            Util.systemCall("pkill -n avconv"); // kills newest only
            Util.log("dumpframegrabs thread exit", this);  // TODO: nuke

        } }).start();

    }

}
