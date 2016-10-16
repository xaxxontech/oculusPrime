package developer;

import developer.depth.Stereo;
import oculusPrime.*;
import oculusPrime.State.values;
import oculusPrime.commport.ArduinoPower;
import oculusPrime.commport.ArduinoPrime;

/**
 * Created by colin on 8/3/2016.
 */
public class Calibrate implements Observer{
    private Settings settings = Settings.getReference();;
    private Application app = null;
    static State state = null;
    private static final int camwidth = Video.lowreswidth;
    private double cumulativeangle = 0;

    /** Constructor */
    public Calibrate(Application a) {
        app = a;
        state = State.getReference();
        state.addObserver(this);
    }

    /**
     * rotate until find dock
     * once dockfound, start logging cumulative angle from gyro
     * keep rotating until dock found again
     * use nominal camera FOV angle and dockmetrics to calculate gyro comp
     */
    public void calibrateRotation() {
        final int REVOLUTIONS = 0; // >0 allows extra time for trackturnrate() to dial in for floor time
                                    // TODO: full rev should be done before odometry turned on, in case turn rate too fast
        new Thread(new Runnable() { public void run() {
            if (state.getBoolean(values.calibratingrotation)) return;
            state.set(values.calibratingrotation, true);

            if (state.get(values.stream).equals(Application.streamstate.stop.toString()) ||
                    state.get(values.stream).equals(Application.streamstate.mic.toString())) {
                app.driverCallServer(PlayerCommands.publish, Application.streamstate.camera.toString());
            }
            app.driverCallServer(PlayerCommands.spotlight, "0");
            app.driverCallServer(PlayerCommands.cameracommand, ArduinoPrime.cameramove.reverse.toString());
            app.driverCallServer(PlayerCommands.floodlight, Integer.toString(AutoDock.FLHIGH));


            Util.delay(5000);
            if (!state.getBoolean(values.calibratingrotation)) return;

            // initial dock target seek
            int rot = 0;
            while (state.getBoolean(values.calibratingrotation)) {
                SystemWatchdog.waitForCpu();

                app.driverCallServer(PlayerCommands.dockgrab, null);
                long start = System.currentTimeMillis();
                while (!state.exists(values.dockfound.toString()) && System.currentTimeMillis() - start < Util.ONE_MINUTE)
                    Util.delay(10);  // wait

                if (state.getBoolean(values.dockfound)) break; // great, onwards
                else { // rotate a bit (non odometry)
                    app.driverCallServer(PlayerCommands.left, "25");
                    Util.delay(100);
                    start = System.currentTimeMillis();
                    while(!state.get(values.direction).equals(ArduinoPrime.direction.stop.toString())
                            && System.currentTimeMillis() - start < 5000) { Util.delay(10); } // wait
                    Util.delay(ArduinoPrime.TURNING_STOP_DELAY);
                }
                rot ++;

                if (rot == 25) { // failure give up
                    app.driverCallServer(PlayerCommands.messageclients, "Calibrate.calibrateRotation() failed to find dock");
                    state.set(values.calibratingrotation, false);
                    return;
                }
            }

            if (!state.getBoolean(values.calibratingrotation)) return;

            //assumed target found, calculate target ctr angle from bot center, turn on gyro
            // 92 104 52 31 0.020408163 -- 1st value is x pixels from left
            // assumed 320x24
            int xdock = Integer.parseInt(state.get(values.dockmetrics).split(" ")[0]);
            double firstangledegrees = (double) (camwidth/2 - xdock)/camwidth * Stereo.camFOVx43;
//            app.driverCallServer(PlayerCommands.messageclients, "found target off-center: "+firstangledegrees); // TODO: debug

            // start gyro recording
            state.set(values.odometrybroadcast, 250);
            app.driverCallServer(PlayerCommands.odometrystart, null);
            cumulativeangle = 0; // negative because cam reversed

            // 2nd dock target seek
            Util.delay(1000); // getting incomplete turns?
            SystemWatchdog.waitForCpu();

            app.driverCallServer(PlayerCommands.left, Integer.toString(360*REVOLUTIONS+180)); // assume default settings are pretty good, to speed things up..?
            Util.delay((long) ((360*REVOLUTIONS+180) / state.getDouble(values.odomturndpms.toString())));
            long start = System.currentTimeMillis();
            while(!state.get(values.direction).equals(ArduinoPrime.direction.stop.toString())
                    && System.currentTimeMillis() - start < 15000) { Util.delay(10); } // wait
            Util.delay(ArduinoPrime.TURNING_STOP_DELAY);
            rot = 0;
            while (state.getBoolean(values.calibratingrotation)) {
                SystemWatchdog.waitForCpu();
                app.driverCallServer(PlayerCommands.dockgrab, null);
                start = System.currentTimeMillis();
                while (!state.exists(values.dockfound.toString()) && System.currentTimeMillis() - start < Util.ONE_MINUTE)
                    Util.delay(10);  // wait

                if (state.getBoolean(values.dockfound)) break; // great, onwards
                else { // rotate a bit
                    app.driverCallServer(PlayerCommands.left, "25");
                    Util.delay((long) (180 / state.getDouble(values.odomturndpms.toString()))); // TODO: why 180?
                    start = System.currentTimeMillis();
                    while(!state.get(values.direction).equals(ArduinoPrime.direction.stop.toString())
                            && System.currentTimeMillis() - start < 5000) { Util.delay(10); } // wait
                    Util.delay(ArduinoPrime.TURNING_STOP_DELAY);
                }
                rot ++;

                if (rot == 25) { // failure give up
                    app.driverCallServer(PlayerCommands.messageclients, "Calibrate.calibrateRotation() failed to find dock");
                    state.set(values.calibratingrotation, false);
                    break;
                }
            }

            if (!state.getBoolean(values.calibratingrotation)) {
                app.driverCallServer(PlayerCommands.odometrystop, null);
                state.delete(values.odometrybroadcast);
                return;
            }

            // done
            xdock = Integer.parseInt(state.get(values.dockmetrics).split(" ")[0]);
            double finalangledegrees = (double) (camwidth/2 - xdock)/camwidth * Stereo.camFOVx43; // negative because cam reversed
            double cameraoffset = firstangledegrees - finalangledegrees;
            String msg = "1st cam angle: "+String.format("%.3f",firstangledegrees);
            msg += "<br>2nd cam angle: "+String.format("%.3f", finalangledegrees);
            msg += "<br>cumulative angle reported by gyro: "+String.format("%.3f",cumulativeangle);
            msg += "<br>actual angle moved: "+String.format("%.3f", (360*REVOLUTIONS+360+cameraoffset));
            msg += "<br>original gyrocomp setting: "+Double.toString(settings.getDouble(ManualSettings.gyrocomp));
            double newgyrocomp = (360*REVOLUTIONS+360+cameraoffset)/(cumulativeangle/settings.getDouble(ManualSettings.gyrocomp));
            settings.writeSettings(ManualSettings.gyrocomp, String.format("%.4f", newgyrocomp));
            msg += "<br>new gyrocomp setting: "+settings.getDouble(ManualSettings.gyrocomp);
            app.driverCallServer(PlayerCommands.messageclients, msg); // TODO: debug

            app.driverCallServer(PlayerCommands.odometrystop, null);
            state.delete(values.odometrybroadcast);
            state.set(values.calibratingrotation, false);

        } }).start();

    }

    @Override
    public void updated(String key) {
        if (key.equals(State.values.distanceangle.name()) && state.exists(key) )  { // used by calibrateRotation()
            cumulativeangle -= Double.parseDouble(state.get(values.distanceangle).split(" ")[1]); // negative because cam reversed
        }
    }
}
