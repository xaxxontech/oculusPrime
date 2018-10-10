package oculusPrime;


import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import oculusPrime.State.values;


public class Network {

    Application app = null;
    static State state = State.getReference();
    Settings settings = Settings.getReference();
    private static String preferredrouter = null;;

    public Network (Application a) {
        app = a;
        pollInfo();
    }

    // from Access Point Manager xml message
    public static boolean connectedToPreferred() {
        return state.equals(values.ssid, preferredrouter);
    }

    private void pollInfo() {
        new Thread(new Runnable() {
            public void run() {
                try {

                    int networkInfoToStateFailCount = 0;
                    long wait = 0;

                    while (true) {

                        if (!state.exists(State.values.localaddress)) Util.updateLocalIPAddress();
                        else if (state.equals(State.values.localaddress, "127.0.0.1")) Util.updateLocalIPAddress();

                        if (!state.exists(State.values.externaladdress)) updateExternalIPAddress();

                        if (System.currentTimeMillis() > wait && networkInfoToStateFailCount < 10) {
                            if (networkInfoToState()) {
                                wait = 0;
                                networkInfoToStateFailCount = 0;
                            }
                            else {
                                wait = System.currentTimeMillis() + Util.ONE_MINUTE;
                                networkInfoToStateFailCount ++;
                            }
                        }

                        Thread.sleep(10000);
                    }
                } catch (Exception e) {
                    Util.printError(e);
                }
            }
        }).start();
    }

    private boolean networkInfoToState() {

        String data = Util.readUrlToString("http://127.0.0.1/?action=xmlinfo");
        if(data == null) data = "";
        if(data.equals("")) return false;

        Document document = Util.loadXMLFromString(data);
        if (document == null) {
            nukeStateValues();
            return false;
        }

        // ssid
        if (document.getElementsByTagName("networkcurrent").getLength() > 0) {
            Element networkcurrent = (Element) document.getElementsByTagName("networkcurrent").item(0);
            String ssid = networkcurrent.getElementsByTagName("name").item(0).getTextContent();
            if (!ssid.equals(state.get(State.values.ssid))) { // changed
                state.delete(State.values.externaladdress);
                state.delete(State.values.localaddress);
                state.set(State.values.ssid, ssid);
            }
        }
        else {
            if (state.exists(State.values.ssid)) { // changed
                state.delete(State.values.externaladdress);
                state.delete(State.values.localaddress);
                state.delete(State.values.ssid);
            }
        }

        // preferred router
        if (document.getElementsByTagName("preferredrouter").getLength() > 0) {
            Element preferred = (Element) document.getElementsByTagName("preferredrouter").item(0);
            preferredrouter = preferred.getTextContent();
        }

        // gateway
        if (document.getElementsByTagName("gatewayaddress").getLength() > 0) {
            Element gateway = (Element) document.getElementsByTagName("gatewayaddress").item(0);
            String gatewayaddress = gateway.getTextContent();
            if (!gatewayaddress.equals(state.get(State.values.gatewayaddress)))
                state.set(State.values.gatewayaddress, gatewayaddress);
        }
        else if (state.exists(State.values.gatewayaddress))
            state.delete(State.values.gatewayaddress);

        // localaddress, or fallback to Util.updateLocalIPAddress();
        if (document.getElementsByTagName("localaddress").getLength() > 0) {
            Element local = (Element) document.getElementsByTagName("localaddress").item(0);
            String localaddress = local.getTextContent();
            if (!localaddress.equals(state.get(State.values.localaddress)))
                state.set(State.values.localaddress, localaddress);
        }

        // message
        if (document.getElementsByTagName("message").getLength() > 0) {
            Element message = (Element) document.getElementsByTagName("message").item(0);
            app.driverCallServer(PlayerCommands.messageclients, message.getTextContent());
        }

        // networksinrange
        if (document.getElementsByTagName("networksinrange").getLength() > 0) {
            String str = "";
            NodeList networksinrange = document.getElementsByTagName("network");
            for (int i = 0; i < networksinrange.getLength(); i++) {
                if (i>0) str += ",";
                str += ((Element) networksinrange.item(i)).getElementsByTagName("name").item(0).getTextContent();
                str += " "+((Element) networksinrange.item(i)).getElementsByTagName("strength").item(0).getTextContent();
            }
            if (!str.equals(state.get(State.values.networksinrange)))
                state.set(State.values.networksinrange, str);
        }
        else if (state.exists(State.values.networksinrange))
            state.delete(State.values.networksinrange);

        // networksknown
        if (document.getElementsByTagName("networksknown").getLength() > 0) {
            String str = "";
            Element networksknown = (Element) document.getElementsByTagName("networksknown").item(0);
            NodeList names = networksknown.getElementsByTagName("name");
            for (int i = 0; i < names.getLength(); i++) {
                if (i>0) str += ",";
                str += ((Element) names.item(i)).getTextContent();
            }
            if (!str.equals(state.get(State.values.networksknown)))
                state.set(State.values.networksknown, str);
        }
        else if (state.exists(State.values.networksknown))
            state.delete(State.values.networksknown);

        return true;
    }

    public static void nukeStateValues() {
        State state = State.getReference();
        state.delete(State.values.ssid);
        state.delete(State.values.networksknown);
        state.delete(State.values.gatewayaddress);
        state.delete(State.values.networksinrange);
    }

    public void updateExternalIPAddress(){
        String address = Util.readUrlToString("http://www.xaxxon.com/xaxxon/checkhost");
        if(Util.validIP(address)) {
            if (!address.equals(state.get(State.values.externaladdress)))
                state.set(State.values.externaladdress, address);
        }
        else if (state.exists(State.values.externaladdress))
            state.delete(State.values.externaladdress);
    }

    public void getNetworkSettings() {
        if (!app.loginRecords.isAdmin()) return;

//            String str = comport.speedslow + " " + comport.speedmed + " "
//                    + comport.nudgedelay + " " + comport.maxclicknudgedelay
//                    + " " + comport.maxclickcam
//                    + " " + comport.fullrotationdelay + " " + comport.onemeterdelay + " "
//                    + settings.readSetting(GUISettings.steeringcomp.name()) + " "
//                    + ArduinoPrime.CAM_HORIZ + " " + ArduinoPrime.CAM_REVERSE;
//            sendplayerfunction("drivingsettingsdisplay", str);

        String[] networksinrange = new String[0];
        if (state.exists(State.values.networksinrange))
            networksinrange = state.get(State.values.networksinrange).split(",");

        String[] networksknown = new String[0];
        if (state.exists(State.values.networksknown))
            networksknown = state.get(State.values.networksknown).split(",");

        // current ssid + strength
        String currentnetwork = state.get(State.values.ssid);
        for (int n=0; n<networksinrange.length; n++) {
            if (networksinrange[n].split(" ")[0].equals(state.get(State.values.ssid))) {
                currentnetwork = state.get(State.values.ssid)+ " "+networksinrange[n].split(" ")[1];
                break;
            }
        }

        // header
        String str = "";
        str += "connected to network: "+currentnetwork;
        str +="<br>network IP address: "+state.get(State.values.localaddress);
        str +="<br>gateway: "+state.get(State.values.gatewayaddress);
        str += "<br>";

        // known connnections in range
        str += "<br>known wifi connections in range:";
        for (int nk=0; nk<networksknown.length; nk++) {

            for (int n=0; n<networksinrange.length; n++) {
                if (networksinrange[n].split(" ")[0].equals(networksknown[nk])) {

                    if (networksinrange[n].split(" ")[0].equals(state.get(State.values.ssid))) continue;

                    str+="<br> &nbsp; "+networksknown[nk]+" "+networksinrange[n].split(" ")[1]+" &nbsp; &nbsp; ";
                    str+="<a href='javascript: callServer(&quot;networkconnect&quot;,&quot;"+networksknown[nk]+"&quot)'";
                    str += ">connect</a>";
                    break;
                }
            }
        }

        //
        str += "<br>";
        str += "<br>other wifi connections in range:";
        for (int n=0; n<networksinrange.length; n++) {

            if (networksinrange[n].split(" ")[0].equals(state.get(State.values.ssid))) continue;

            boolean skip = false;
            for (int nk=0; nk<networksknown.length; nk++) {
                if (networksinrange[n].split(" ")[0].equals(networksknown[nk])) {
                    skip=true;
                    break;
                }
            }
            if (skip) continue;

            str += "<br> &nbsp; "+networksinrange[n];
            break;
        }

        str += "<br>";
        str += "<br>all known wifi connections:";
        for (int nk=0; nk<networksknown.length; nk++) {
            str += "<br> &nbsp; "+networksknown[nk];
        }

        str += "<br>";

        if (!state.exists(State.values.relayserver))
            str += "<br><a class=\"blackbg\" href=\"network\" target=\"_blank\">more network controls</a><br>\n";

        String server = settings.readSetting(GUISettings.relayserver);
        str += "<br>relay server: "+server;
        if (state.exists(State.values.relayserver)) {
            str += "<br> &nbsp; status: connected to server";
            str += "<br> &nbsp; <a class=\"blackbg\" href=\"javascript: relayserver('disable')\">\n" +
                    "disable</a>";
        }
        else if (state.exists(State.values.relayclient)) {
            str += "<br> &nbsp; status: client connected from: " + state.get(State.values.relayclient);
            str += "<br> &nbsp; <a class=\"blackbg\" href=\"javascript: relayserver('disable')\">\n" +
                    "disable</a>";
        }
        else {
            str += "<br> &nbsp; status: not connected";
            str += "<br> &nbsp; <a class=\"blackbg\" href=\"javascript: relayserver();\">connect</a> &gt;<br>";
        }

        app.sendplayerfunction("networksettings", str);
    }

    public void connectNetwork(String str) {
        app.driverCallServer(PlayerCommands.messageclients, "attempting connect to network: "+str);
        Util.readUrlToString("http://127.0.0.1/?action=up&router="+str);
    }
}