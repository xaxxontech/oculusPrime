package oculusPrime;


import javax.imageio.ImageIO;
import java.io.File;
import java.io.IOException;
import java.io.BufferedReader;
import java.io.InputStreamReader;


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
    static final long STREAM_RESTART = Util.ONE_MINUTE*6;
    static final String FFMPEG = "ffmpeg";
    static final String AVCONV = "avconv";
    private String avprog = AVCONV;
    private static long STREAM_CONNECT_DELAY = Application.STREAM_CONNECT_DELAY;
    private static int dumpfps = 15;

    public Video(Application a) {
        app = a;
        state.set(State.values.stream, Application.streamstate.stop.toString());
        setAudioDevice();
        if (state.get(State.values.osarch).equals(Application.ARM)) {
//            avprog = FFMPEG;
//            dumpfps = 8;
//            STREAM_CONNECT_DELAY=3000;
        }
    }

    private void setAudioDevice() {
        try {
            String cmd[] = new String[]{"arecord", "--list-devices"};
            Process proc = Runtime.getRuntime().exec(cmd);
            proc.waitFor();
            proc.waitFor();

            String line = null;
            BufferedReader procReader = new BufferedReader(new InputStreamReader(proc.getInputStream()));
            while ((line = procReader.readLine()) != null) {
                if(line.startsWith("card") && line.contains("LifeCam")) {
                    adevicenum = Integer.parseInt(line.substring(5,6));      // "card 0"
                    Util.debug(line, this);
                }
            }

        } catch (Exception e) { Util.printError(e);}
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
                forceShutdownFrameGrabs();
                Util.systemCallBlocking("pkill "+avprog);
                Util.delay(STREAM_CONNECT_DELAY);
            }

            switch (mode) {
                case camera:
                    Util.systemCall(avprog+" -f video4linux2 -s " + w + "x" + h + " -r " + fps +
                            " -i /dev/video" + devicenum + " -f flv -q " + quality + " rtmp://" + host + ":" +
                            port + "/oculusPrime/stream1");
                    // avconv -f video4linux2 -s 640x480 -r 8 -i /dev/video0 -f flv -q 5 rtmp://127.0.0.1:1935/oculusPrime/stream1
                    app.driverCallServer(PlayerCommands.streammode, mode.toString());
                    break;
                case mic:
                    Util.systemCall(avprog+" -re -f alsa -ac 1 -ar 22050 " +
                            "-i hw:" + adevicenum + " -f flv rtmp://" + host + ":" +
                            port + "/oculusPrime/stream1");
                    // avconv -re -f alsa -ac 1 -ar 22050 -i hw:1 -f flv rtmp://127.0.0.1:1935/oculusPrime/stream1
                    app.driverCallServer(PlayerCommands.streammode, mode.toString());
                    break;
                case camandmic:
                    Util.systemCall(avprog+" -re -f alsa -ac 1 -ar 22050 " +
                            "-i hw:" + adevicenum + " -f flv rtmp://" + host + ":" +
                            port + "/oculusPrime/stream2");
                    // avconv -re -f alsa -ac 1 -ar 22050 -i hw:1 -f flv rtmp://127.0.0.1:1935/oculusPrime/stream2

                    Util.systemCall(avprog+" -f video4linux2 -s " + w + "x" + h + " -r " + fps +
                            " -i /dev/video" + devicenum + " -f flv -q " + quality + " rtmp://" + host + ":" +
                            port + "/oculusPrime/stream1");



//                    String cmd = "bash -c '"+ avprog+" -re -f alsa -ac 1 -ar 22050 " +
//                            "-i hw:" + adevicenum + " -f flv rtmp://" + host + ":" +
//                            port + "/oculusPrime/stream2";
//                    // avconv -re -f alsa -ac 1 -ar 22050 -i hw:1 -f flv rtmp://127.0.0.1:1935/oculusPrime/stream2
//
//                    cmd += "</dev/null >/dev/null 2>/dev/null & ";
//
//                    if (avprog.equals(FFMPEG))

//                        cmd += "sleep 1; v4l2-ctl --set-input "+devicenum+" ; ";
//
//                    cmd += avprog+" -f video4linux2 -s " + w + "x" + h + " -r " + fps +
//                            " -i /dev/video" + devicenum + " -f flv -q " + quality + " rtmp://" + host + ":" +
//                            port + "/oculusPrime/stream1";
//
//                    cmd += " '";
//
//                    Util.log(cmd, this); // TODO: nuke
//                    Util.systemCall(cmd);

                    app.driverCallServer(PlayerCommands.streammode, mode.toString());
//                    if (state.get(State.values.osarch).equals(Application.ARM))
//                        Util.delay(Application.STREAM_CONNECT_DELAY);

                    break;
                case stop:
                    forceShutdownFrameGrabs();
                    Util.systemCall("pkill "+avprog);
                    app.driverCallServer(PlayerCommands.streammode, mode.toString());
                    break;

            }

        } }).start();

        if (mode.equals(Application.streamstate.stop) ) return;

        // stream restart timer
        new Thread(new Runnable() { public void run() {
            long start = System.currentTimeMillis();
            while ( id == publishid && (System.currentTimeMillis() < start + STREAM_RESTART) ||
                    state.getBoolean(State.values.writingframegrabs) ||
                    state.getBoolean(State.values.autodocking) )
                Util.delay(50);

            if (id == publishid) { // restart stream
                forceShutdownFrameGrabs();
                Util.systemCall("pkill -9 "+avprog);
                lastmode = Application.streamstate.stop;
                Util.delay(STREAM_CONNECT_DELAY);
                publish(mode, w,h,fps);
//                app.driverCallServer(PlayerCommands.messageclients, "stream restart after "+(System.currentTimeMillis()-start+"ms"));
            }

        } }).start();

    }

    private void forceShutdownFrameGrabs() {
        if (state.getBoolean(State.values.writingframegrabs)) {
            state.set(State.values.writingframegrabs, false);
//            Util.delay(STREAM_CONNECT_DELAY);
        }
    }

    public void framegrab(final String res) {

        state.set(State.values.framegrabbusy.name(), true);

//        Util.log("framegrab start", this); // TODO: nuke
        lastframegrab = System.currentTimeMillis();

        new Thread(new Runnable() { public void run() {
            if (!state.getBoolean(State.values.writingframegrabs)) {
                dumpframegrabs(res);
                Util.delay(STREAM_CONNECT_DELAY);
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
            if (imgfile == null) { Util.log(avprog+" frame unavailable", this); }
            else {
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
            Runtime.getRuntime().exec(new String[]{avprog, "-analyzeduration", "0", "-i",
                    "rtmp://" + host + ":" + port + "/oculusPrime/stream1 live=1", "-s", width+"x"+height,
                    "-r", Integer.toString(dumpfps), "-q", Integer.toString(quality), PATH+"%d"+EXT  });
            // avconv -analyzeduration 0 -i rtmp://127.0.0.1:1935/oculusPrime/stream1 live=1 -s 640x480 -r 15 -q 5 /dev/shm/avconvframes/%d.bmp
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
            Util.systemCall("pkill -n "+avprog); // kills newest only
            Util.log("dumpframegrabs thread exit", this);  // TODO: nuke

        } }).start();

    }

}
