package oculusPrime;

import java.util.Collection;
import java.util.Set;

import org.red5.client.net.rtmp.ClientExceptionHandler;
import org.red5.client.net.rtmp.RTMPClient;
import org.red5.io.utils.ObjectMap;
import org.red5.server.api.IConnection;
import org.red5.server.api.service.IPendingServiceCall;
import org.red5.server.api.service.IPendingServiceCallback;
import org.red5.server.api.service.IServiceCapableConnection;
import oculusPrime.commport.ArduinoPrime;

public class Red5Client extends RTMPClient {
    private State state = State.getReference();
    private Settings settings = Settings.getReference();
    private Application app;
//  private NetworkServlet networkServlet = new NetworkServlet();
    private long lastping;
    private static final long PINGTIMEOUT = 10000;
    private long stayConnectedId;

    public Red5Client(Application a) {
        app = a;

//        this.setConnectionClosedHandler(new Runnable() {
//            @Override
//            public void run() {
//
//                Util.log("relay server connection closed", this);
//                state.delete(State.values.relayserver);
//            }
//        });

        this.setServiceProvider(this);

    }

    public void connectToRelay() {
        connectToRelay(null);
    }

    public void connectToRelay(String str) {

        String[] hostuserpass = null;
        if (str != null) {
            hostuserpass = str.split(" ");
            if (hostuserpass.length != 3) {
                app.driverCallServer(PlayerCommands.messageclients, "invalid relay server login info");
                return;
            }
            settings.writeSettings(GUISettings.relayserver, hostuserpass[0]);
            settings.writeSettings(GUISettings.relayserverauth, Settings.DISABLED);
//            Util.log(hostuserpass[0]+hostuserpass[1]+hostuserpass[2], this);
        }

        if (settings.getBoolean(ManualSettings.useflash)) {
            state.set(State.values.guinotify, "relay server in use, setting &quot;useflash&quot; set to false");
            Util.log("setting useflash false", this);
            settings.writeSettings(ManualSettings.useflash, Settings.FALSE);
        }

        if (state.exists(State.values.relayserver)) {
            app.driverCallServer(PlayerCommands.messageclients, "relay server already connected to: " +
                state.get(State.values.relayserver));
            return;
        }

        if (hostuserpass == null &&
                (settings.readSetting(GUISettings.relayserver).equals(Settings.DISABLED) ||
                settings.readSetting(GUISettings.relayserverauth).equals(Settings.DISABLED)) ) {
            app.driverCallServer(PlayerCommands.messageclients, "relay server not set");
            return;
        }

        setExceptionHandler(new ClientExceptionHandler() {
            @Override
            public void handleException(Throwable throwable) {
                Util.log("connectToRelay(): " + throwable.getLocalizedMessage(), this);
//                throwable.printStackTrace();
            }
        });

        String server = settings.readSetting(GUISettings.relayserver);
        String app = "oculusPrime";
        int port = Integer.valueOf(settings.readRed5Setting("rtmp.port"));

        String args[] = new String[1];
        if (hostuserpass !=null) {
            args[0] = hostuserpass[1]+" "+hostuserpass[2]+" remember";
        }
        else {
            args[0] = settings.readSetting(GUISettings.relayserverauth);
        }

        connect(server, port, makeDefaultConnectionParams(server, port, app), connectCallback, args);
        stayConnected();

    }

    private IPendingServiceCallback connectCallback = new IPendingServiceCallback() {
        @Override
        public void resultReceived(IPendingServiceCall call) {

            ObjectMap<?, ?> map = (ObjectMap<?, ?>) call.getResult();
            String code = (String) map.get("code");
            Util.log("Red5Client connectCallback Response code: " + code, this);
            if ("NetConnection.Connect.Success".equals(code)) { // success
                Util.log("Remote relay server connect success", this);

                if (state.exists(State.values.relayserver)) { // just in case
                    Util.log("error, state relay server exists", this);
                    return;
                }

                if (state.exists(State.values.driver)) {
                    app.driverCallServer(PlayerCommands.messageclients, "connected to relay server, logging out this connection");
                    app.driverCallServer(PlayerCommands.driverexit, null);
                }

                state.set(State.values.relayserver, settings.readSetting(GUISettings.relayserver));

                app.driverCallServer(PlayerCommands.messageclients, "connected to relay server: " +
                        state.get(State.values.relayserver));

                // notify remote server that this is an authenticated relay client
                invoke("setRelayClient", new IPendingServiceCallback() {
                    @Override
                    public void resultReceived(IPendingServiceCall iPendingServiceCall) {

                    }
                });


            }

            else if ("NetConnection.Connect.Rejected".equals(code)) {
                disconnect();
                Util.log("Red5Client connect rejected: " + map.get("description"), this);
                state.delete(State.values.relayserver);
            }

            else {
                Util.log("Remote relay server connect failed", this);
                disconnect();
                state.delete(State.values.relayserver);
            }
        }
    };

    // stay connected as long as relayserver setting set
    private void stayConnected() {

        new Thread(new Runnable() {
            public void run() {
                try {
                    long id = System.currentTimeMillis();
                    stayConnectedId = id;
                    lastping = id;

                    Util.delay(5000); // allow time to connect

                    while(!settings.readSetting(GUISettings.relayserver).equals(Settings.DISABLED) &&
                            stayConnectedId == id) {

                        if (System.currentTimeMillis() - lastping < PINGTIMEOUT &&
                                state.exists(State.values.relayserver)) {
                            // all is well

                            // ping server
                            Util.debug("ping server", this); // TODO: testing
                            invoke("relayPing", new IPendingServiceCallback() {
                                @Override
                                public void resultReceived(IPendingServiceCall iPendingServiceCall) {

                                }
                            });

                            Util.delay(1000);
                            continue;
                        }

                        if (stayConnectedId != id) break;

                        // all is not well

                        if (!state.exists(State.values.relayserver)) {
                            Util.log("state relayserver null", this);
                        }

                        if (System.currentTimeMillis() - lastping > PINGTIMEOUT) {
                            Util.log("ping timeout", this);
                        }

                        app.driverCallServer(PlayerCommands.publish, Application.streamstate.stop.toString());
                        state.delete(State.values.relayserver);

                        Util.delay(10000);
                        if (stayConnectedId != id) break;
                        Util.log("attempting reconnect", this); // TODO: testing
                        connectToRelay();

                    }

                    Util.debug("stayConnected Thread exit", this); // TODO: testing

                } catch (Exception e) {
                    Util.printError(e);
                }
            }
        }).start();
    }

    public void sendToRelay(String functionName, Object[] params) {

        Object[] functionplusparams = new Object[params.length+1];
        functionplusparams[0]=functionName;
        for (int i=1; i<functionplusparams.length; i++) functionplusparams[i]=params[i-1];
        invoke("fromRelayClient", functionplusparams, new IPendingServiceCallback() {
            @Override
            public void resultReceived(IPendingServiceCall iPendingServiceCall) {

            }
        });
    }

    public void relayDisconnect() {
        state.delete(State.values.relayserver);
        disconnect();
    }

    // called by relay server only
    public void relayPong() {
        lastping = System.currentTimeMillis();

    }

    // called by relay server only
    public void relayCallClient(Object[] params) {
        String str = null;
        if (params[1]!=null) str=params[1].toString();
        app.driverCallServer(PlayerCommands.valueOf(params[0].toString()), str);
    }

    // called by relay server only
    public void playerSignIn(Object[] params) {

        // disonnect any drivers/passengers/pending
        boolean delay = false;
        Collection<Set<IConnection>> concollection = app.getConnections();
        for (Set<IConnection> cc : concollection) {
            for (IConnection con : cc) {
                if (con instanceof IServiceCapableConnection && con != app.grabber) {
                    con.close();
                    delay = true;
                }
            }
        }
        if (delay) Util.delay(500); // allow player to logout TODO: blocking!

        state.set(State.values.driver, params[0].toString());
        app.initialstatuscalled = false;
        Util.log("relay playersignin(): " + params[0].toString(), this);
        app.loginRecords.beDriver();
        if (settings.getBoolean(GUISettings.loginnotify))
            app.driverCallServer(PlayerCommands.speech, state.get(State.values.driver));
        app.watchdog.lastpowererrornotify = null;

    }

    // called by relay server only
    public void playerDisconnect() {
        app.loginRecords.signoutDriver();

        //if autodocking, keep autodocking
        if (!state.getBoolean(State.values.autodocking) &&
                !(state.exists(State.values.navigationroute) && !state.exists(State.values.nextroutetime)) ) {

            if (!state.get(State.values.driverstream).equals(Application.driverstreamstate.pending.toString())) {

                if (state.get(State.values.stream) != null) {
                    if (!state.get(State.values.stream).equals(Application.streamstate.stop.toString())) {
                        app.driverCallServer(PlayerCommands.publish, Application.streamstate.stop.toString());
                    }
                }

                app.driverCallServer(PlayerCommands.spotlight, "0");
                app.driverCallServer(PlayerCommands.floodlight, "0");
                app.driverCallServer(PlayerCommands.move, ArduinoPrime.direction.stop.toString());

            }

        }
    }


}
