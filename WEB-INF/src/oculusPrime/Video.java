package oculusPrime;


import developer.Ros;

import oculusPrime.State.values;

import javax.imageio.ImageIO;
import javax.sql.rowset.WebRowSet;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


public class Video {

    private static State state = State.getReference();
    private Application app = null;
    private String port;
    private int devicenum = 0;  // should match lifecam cam
    private int adevicenum = 1; // should match lifecam mic
    private static final int defaultquality = 5;
    private static final int quality720p = 7;
    public static final int defaultwidth=640;
    private static final int defaultheight=480;
    public static final int lowreswidth=320;
    private static final int lowresheight=240;
    private static final String PATH="/dev/shm/rosimgframes/";
    private static final String EXT=".bmp";
    private volatile long lastframegrab = 0;
    public int lastwidth=0;
    public int lastheight=0;
    private int lastfps=0;
    public long lastbitrate = 0;
    public static long STREAM_CONNECT_DELAY = 2000;
    private Settings settings = Settings.getReference();

    private String SIGNALLINGSERVERCMD = "python3 ./simple-server.py";
    private String signallingserverpstring;
    private volatile long lastvideocommand = 0;
    public String camerapstring = null;
    private String webrtcpstring = null;
    private ArrayList<String> webrtccmdarray = new ArrayList<>();;
    public final static String MICWEBRTCPSTRING = "micwebrtc";
    public static final String MICWEBRTC = Settings.tomcathome +"/"+Settings.appsubdir+"/"+MICWEBRTCPSTRING; // gstreamer webrtc microphone c binary
    public static final String SOUNDDETECT = Settings.tomcathome +"/"+Settings.appsubdir+"/sounddetect";

    // lifecam cinema measured angles (may not reflect average)
    public final static double camFOVx169 = 68.46;
    public static final double camFOVy169 = 41.71;
    public static final double camFOVx43 = 58.90;
    public static final double camFOVy43 = 45.90;

    public Video(Application a) {
        app = a;
        setAudioDevice();
        setVideoDevice();

        launchTURNserver();
        launchSignallingServer();

        String vals[] = settings.readSetting(settings.readSetting(GUISettings.vset)).split("_");
        lastwidth = Integer.parseInt(vals[0]);
        lastheight = Integer.parseInt(vals[1]);
        lastfps = Integer.parseInt(vals[2]);
        lastbitrate = Long.parseLong(vals[3]);

        state.set(State.values.stream, Application.streamstate.stop.toString());
    }

    private void setAudioDevice() {
        try {
            String cmd[] = new String[]{"arecord", "--list-devices"};
            Process proc = Runtime.getRuntime().exec(cmd);
            proc.waitFor();

            String line = null;
            BufferedReader procReader = new BufferedReader(new InputStreamReader(proc.getInputStream()));
            while ((line = procReader.readLine()) != null) {
                if(line.startsWith("card") && line.contains("LifeCam")) {
                    adevicenum = Integer.parseInt(line.substring(5,6));      // "card 0"
                }
            }

        } catch (Exception e) { Util.printError(e); }
    }

    private void setVideoDevice() {
        try {
            String cmd[] = new String[]{"v4l2-ctl", "--list-devices"};
            Process proc = Runtime.getRuntime().exec(cmd);
            proc.waitFor();

            String line = null;
            BufferedReader procReader = new BufferedReader(new InputStreamReader(proc.getInputStream()));
            while ((line = procReader.readLine()) != null) {
                if(line.contains("LifeCam")) {
                    line = procReader.readLine().trim();
                    devicenum = Integer.parseInt(line.substring(line.length() - 1));
                    Util.debug(line+ " devicenum="+devicenum, this);

//                    v4l2-ctl --set-ctrl=focus_auto=0
//                    v4l2-ctl --set-ctrl=focus_absolute=0
                    cmd = new String[]{"v4l2-ctl", "--device="+devicenum, "--set-ctrl=focus_auto=0"};
                    proc = Runtime.getRuntime().exec(cmd);
                    proc.waitFor();

                    cmd = new String[]{"v4l2-ctl", "--device="+devicenum, "--set-ctrl=focus_absolute=0"};
                    proc = Runtime.getRuntime().exec(cmd);
                    proc.waitFor();

                }
            }

        } catch (Exception e) { Util.printError(e);}
    }

    private void launchTURNserver() {
        killTURNserver();

        // turnserver --user=auto:robot  --realm=xaxxon.com --no-stun --listening-port=3478
        String cmd = "turnserver --user="+settings.readSetting(ManualSettings.turnserverlogin) +
                " --realm=xaxxon.com --no-stun --listening-port=" +
                settings.readSetting(ManualSettings.turnserverport);

        Util.systemCall(cmd);
    }

    protected static void killTURNserver() {
        // kill running instances
        String cmd = "pkill turnserver";
        Util.systemCallBlocking(cmd);
    }

    protected void launchSignallingServer() {
        if (signallingserverpstring != null)
            killSignallingServer();

        String cmd = Settings.tomcathome+Util.sep+"signalling"+Util.sep+"run";
        String portarg = " --port "+settings.readSetting(ManualSettings.webrtcport);
        signallingserverpstring = SIGNALLINGSERVERCMD + portarg;
        Util.systemCall(cmd+portarg);
    }

    protected void killSignallingServer() {

        ProcessBuilder processBuilder = new ProcessBuilder("pkill", "-f", signallingserverpstring);

        try {
            Process proc = processBuilder.start();
        } catch (Exception e) { e.printStackTrace(); }

        signallingserverpstring = null;
    }

    // restart webrtc connection, called by javascript for periodic webkit connect failure
    public void webrtcRestart() {
        new Thread(new Runnable() { public void run() {
            String w = webrtcpstring;
            killwebrtc();
            webrtcpstring = w;
            Util.delay(1000);
            Ros.launch(webrtccmdarray);
        } }).start();
    }

    public void publish (final Application.streamstate mode, final int w, final int h, final int fps, final long bitrate) {

        if (System.currentTimeMillis() < lastvideocommand + STREAM_CONNECT_DELAY) {
            app.driverCallServer(PlayerCommands.messageclients, "video command received too soon after last, dropped");
            return;
        }

        lastvideocommand = System.currentTimeMillis();

        if ( (mode.equals(Application.streamstate.camera) || mode.equals(Application.streamstate.camandmic)) &&
                (state.get(values.stream).equals(Application.streamstate.camera.toString()) ||
                        state.get(values.stream).equals(Application.streamstate.camandmic.toString()))
        ) {
            app.driverCallServer(PlayerCommands.messageclients, "camera already running, stream: "+mode.toString()+" command dropped");
            return;
        }

        lastwidth = w;
        lastheight = h;
        lastfps = fps;
        lastbitrate = bitrate;
        final long id = System.currentTimeMillis();

        app.driverCallServer(PlayerCommands.streammode, mode.toString());

        new Thread(new Runnable() { public void run() {

            switch (mode) {
                case camera:

                    if (camerapstring == null) {
                        camerapstring = Ros.launch(new ArrayList<String>(Arrays.asList(Ros.CAMERA,
                                "width:="+lastwidth, "height:="+lastheight, "device:=/dev/video"+devicenum)));
                    }

                    if (state.exists(values.driverclientid) && webrtcpstring == null) {

                        webrtccmdarray = new ArrayList<String>(Arrays.asList(Ros.WEBRTC,
                                "peerid:=--peer-id=" + state.get(values.driverclientid),
                                "webrtcserver:=--server=wss://"+settings.readSetting(ManualSettings.webrtcserver)+":"
                                        +settings.readSetting(ManualSettings.webrtcport),
                                "videowidth:=--video-width=" + lastwidth, "videoheight:=--video-height=" + lastheight,
                                "videobitrate:=--video-bitrate=" + lastbitrate,
                                "turnserverport:=--turnserver-port="+settings.readSetting(ManualSettings.turnserverport),
                                "turnserverlogin:=--turnserver-login="+settings.readSetting(ManualSettings.turnserverlogin)
                        ));

                        webrtcStatusListener(webrtccmdarray, mode.toString());
                        webrtcpstring = Ros.launch(webrtccmdarray);
                    }

                    break;
                case camandmic:

                    if (camerapstring == null) {
                        camerapstring = Ros.launch(new ArrayList<String>(Arrays.asList(Ros.CAMERA,
                                "width:="+lastwidth, "height:="+lastheight, "device:=/dev/video"+devicenum)));
                    }

                    if (state.exists(values.driverclientid) && webrtcpstring == null) {

                        webrtccmdarray = new ArrayList<String>(Arrays.asList(Ros.WEBRTC,
                                "peerid:=--peer-id=" + state.get(values.driverclientid),
                                "webrtcserver:=--server=wss://"+settings.readSetting(ManualSettings.webrtcserver)+":"
                                        +settings.readSetting(ManualSettings.webrtcport),
                                "audiodevice:=--audio-device=" + adevicenum,
                                "videowidth:=--video-width=" + lastwidth, "videoheight:=--video-height=" + lastheight,
                                "videobitrate:=--video-bitrate=" + lastbitrate,
                                "turnserverport:=--turnserver-port="+settings.readSetting(ManualSettings.turnserverport),
                                "turnserverlogin:=--turnserver-login="+settings.readSetting(ManualSettings.turnserverlogin)
                        ));

                        webrtcStatusListener(webrtccmdarray, mode.toString());
                        webrtcpstring = Ros.launch(webrtccmdarray);
                    }

                    break;

                case mic:
                    if (state.exists(values.driverclientid)) {

                        ProcessBuilder processBuilder = new ProcessBuilder();

                        webrtcpstring = MICWEBRTC+
                                " --peer-id=" + state.get(values.driverclientid)+
                                " --audio-device=" + adevicenum+
                                " --server=wss://"+settings.readSetting(ManualSettings.webrtcserver)+":"
                                +settings.readSetting(ManualSettings.webrtcport)+
                                " --turnserver-port="+settings.readSetting(ManualSettings.turnserverport)+
                                " --turnserver-login="+settings.readSetting(ManualSettings.turnserverlogin)
                        ;

                        webrtccmdarray = new ArrayList<>();
                        String[] array = webrtcpstring.split(" ");
                        for (String t : array) webrtccmdarray.add(t);
                        processBuilder.command(webrtccmdarray);
                        Process proc = null;

                        try{
                            int attempts = 0;
                            while (attempts<10) {
                                Util.debug("mic webrtc signalling server attempt #"+attempts, this);
                                proc = processBuilder.start();
                                int exitcode = (proc.waitFor());
                                if (exitcode == 0) break;
                                else {
                                    Util.debug("micwebrtc exit code: "+exitcode, this);
                                    if (!state.get(values.stream).equals(Application.streamstate.mic.toString()))
                                        break;
                                }
                                attempts ++;
                            }
                            if (attempts == 10)
                                app.driverCallServer(PlayerCommands.messageclients, "mic webrtc failed to start");

                        } catch (Exception e) { e.printStackTrace(); }

                    }
                    break;

                case stop:
                    forceShutdownFrameGrabs();
                    killwebrtc();
                    killcamera();
                    Util.systemCall("pkill "+MICWEBRTCPSTRING);
                    break;

            }

        } }).start();

    }

    // checks if webrtcstatus == 'connected' after short time (successful connect to singnalling server)
    // if not, kill roslaunch, relaunch
    // ros2 only
    private void webrtcStatusListener(final ArrayList<String> strarray, final String mode) {

        if (!settings.getBoolean(ManualSettings.ros2)) return;

        state.delete(values.webrtcstatus); // required because ros2 launch files don't send SIGINT to processes on shutdown

        new Thread(new Runnable() { public void run() {

            int attempts = 0;
            while (mode.equals(state.get(values.stream)) && attempts < 5) {
                if (state.block(values.webrtcstatus, "connected", 5000)) return;

                if (!mode.equals(state.get(values.stream))) return;

                Util.log("!connected, relaunching webrtcpstring", this);
                killwebrtc();
                Util.delay(5500);
                state.delete(values.webrtcstatus);
                Ros.launch(strarray); // this only works once! because launch modifies it in mem

                attempts ++;
            }

            if (attempts >= 5) Util.log("webrtc signalling server connection attempt max reached, giving up", this);

        } }).start();

    }

    private void forceShutdownFrameGrabs() {
        if (state.exists(State.values.writingframegrabs))
            state.delete(State.values.writingframegrabs);
    }

    private void killwebrtc() {
        if (webrtcpstring != null) {
            Ros.killlaunch(webrtcpstring);
            webrtcpstring = null;
        }
    }

    public void killcamera() {
        if (camerapstring != null) {
            Ros.killlaunch(camerapstring);
            camerapstring = null;
        }
    }

    public void framegrab() {

        state.set(State.values.framegrabbusy, true);

        lastframegrab = System.currentTimeMillis();

        new Thread(new Runnable() { public void run() {

            if (!state.exists(values.writingframegrabs))
                dumpframegrabs();

            File dir = new File(PATH);
            long start = System.currentTimeMillis();
            while (System.currentTimeMillis() - start < 5000) {
                if (dir.isDirectory()) break;
                Util.delay(100);
            }

            if (!dir.isDirectory()) {
                Util.log(PATH+" unavailable", this);
                state.set(values.framegrabbusy, false);
                return;
            }

            // determine latest image file -- use second latest to resolve incomplete file issues
            int attempts = 0;
            while (attempts < 15) {

                File imgfile = null;
                start = System.currentTimeMillis();
                while (imgfile == null && System.currentTimeMillis() - start < 10000) {
                    int highest = 0;
                    int secondhighest = 0;
                    for (File file : dir.listFiles()) {
                        int i = Integer.parseInt(file.getName());
                        if (i > highest) {
                            highest = i;
                        }
                        if (i > secondhighest && i < highest) {
                            imgfile = file;
                            secondhighest = i;
                        }
                    }
                    Util.delay(1);
                }
                if (imgfile == null) {
                    Util.log("framegrab frame unavailable", this);
                    break;
                } else {
                    try {

                        FileInputStream fis = null;
                        byte[] bArray = new byte[(int) imgfile.length()];
                        fis = new FileInputStream(imgfile);
                        fis.read(bArray);
                        fis.close();

                        app.processedImage = new BufferedImage(lastwidth, lastheight, BufferedImage.TYPE_INT_RGB);

                        int i=0;
                        for (int y=0; y< lastheight; y++) {
                            for (int x=0; x<lastwidth; x++) {
                                int argb;
                                argb = (bArray[i]<<16) + (bArray[i+1]<<8) + bArray[i+2];

                                app.processedImage.setRGB(x, y, argb);
                                i += 3;
                            }
                        }

                        break;

                    } catch (Exception e) {
                        e.printStackTrace();
                        attempts++;
                    }
                }
            }

            state.set(values.framegrabbusy, false);

        } }).start();
    }

    public void dumpframegrabs() {

        Util.debug("dumpframegrabs()", this);

        new Thread(new Runnable() { public void run() {

            if (state.exists(values.writingframegrabs)) {
                forceShutdownFrameGrabs();
                Util.delay(STREAM_CONNECT_DELAY); // allow ros node time to exit
            }

            if (state.exists(values.writingframegrabs)) return; // just in case

            state.set(State.values.writingframegrabs, true);

            // run ros node
            String topic = "/usb_cam/image_raw";
            Ros.roscommand("rosrun "+Ros.ROSPACKAGE+" image_to_shm.py _camera_topic:="+topic);

            while(state.exists(State.values.writingframegrabs)
                    && System.currentTimeMillis() - lastframegrab < Util.ONE_MINUTE) {
                Util.delay(10);
            }

            state.delete(State.values.writingframegrabs);
            // kill ros node
            Ros.roscommand("rosnode kill /image_to_shm");

        } }).start();

    }


    public void sounddetectgst(String mode) {

        if (!state.exists(State.values.sounddetect)) state.set(State.values.sounddetect, false);


        // mode = false
        if (mode.toLowerCase().equals(Settings.FALSE)) {
            if (!state.getBoolean(State.values.sounddetect)) {
                app.driverCallServer(PlayerCommands.messageclients, "sound detection not running, command dropped");
            } else {
                state.set(State.values.sounddetect, false);
                app.driverCallServer(PlayerCommands.messageclients, "sound detection cancelled");
            }
            return;
        }


        // mode = true

        if (state.get(values.stream).equals(Application.streamstate.camandmic.toString())  ||
                state.get(values.stream).equals(Application.streamstate.mic.toString()) ) {
            app.driverCallServer(PlayerCommands.messageclients, "mic already running, command dropped");
            return;
        }

        app.driverCallServer(PlayerCommands.messageclients, "sound detection enabled");

        state.set(State.values.sounddetect, true);
        state.delete(State.values.streamactivity);

        new Thread(new Runnable() { public void run() {

            long timeout = System.currentTimeMillis() + Util.ONE_HOUR;

            ProcessBuilder processBuilder = new ProcessBuilder();

            String cmd = SOUNDDETECT+" "+ adevicenum;

            List <String> args = new ArrayList<>();;
            String[] array = cmd.split(" ");
            for (String t : array) args.add(t);
            processBuilder.command(args);

            try {
                Process proc = processBuilder.start();

                BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()));

                String line = reader.readLine(); // skip 1st line
                while ((line = reader.readLine()) != null && state.getBoolean(State.values.sounddetect)) {
                    double voldB = Double.parseDouble(line);
                    Util.debug("soundlevel: "+voldB, this);
                    if (Double.parseDouble(line) > settings.getDouble(ManualSettings.soundthreshold)) {
                        state.set(State.values.streamactivity, "audio " + voldB+"dB");
                        app.driverCallServer(PlayerCommands.messageclients, "sound detected: "+ voldB+" dB");
                        break;
                    }
                }

                state.set(State.values.sounddetect, false);
                proc.destroy();
                app.driverCallServer(PlayerCommands.messageclients, "sound detection disabled");

            } catch (Exception e) { e.printStackTrace(); }

        } }).start();

    }


    public String record(String mode) { return record(mode, null); }

    public String record(String mode, String optionalfilename) {
        // TODO: everything
        return null;
    }

}