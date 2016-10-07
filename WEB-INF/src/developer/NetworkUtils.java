package developer;


import oculusPrime.*;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.BufferedInputStream;
import java.net.URL;
import java.net.URLConnection;

public class NetworkUtils implements Observer {

    Application app;
    State state;

    public NetworkUtils(Application a) {
        app = a;
        state = State.getReference();
        state.addObserver(this);
    }

    public boolean networkInfoToState() {

        String data = "";
        try {
            String url = "http://127.0.0.1/?action=xmlinfo";
            URLConnection connection = new URL(url).openConnection();
            BufferedInputStream in = new BufferedInputStream(connection.getInputStream());
            int i;
            while ((i = in.read()) != -1) data += (char)i;
            in.close();
        } catch (Exception e) {
            Util.printError(e);
            return false;
        }
        if (data.equals("")) return false;

        Document document = Util.loadXMLFromString(data);

        // ssid
        if (document.getElementsByTagName("networkcurrent").getLength() > 0) {
            Element networkcurrent = (Element) document.getElementsByTagName("networkcurrent").item(0);
            state.set(State.values.ssid, networkcurrent.getElementsByTagName("name").item(0).getTextContent());
        }
        else state.delete(State.values.ssid);

        // gateway
        if (document.getElementsByTagName("gatewayaddress").getLength() > 0) {
            Element gateway = (Element) document.getElementsByTagName("gatewayaddress").item(0);
            state.set(State.values.gatewayaddress, gateway.getTextContent());
        }
        else state.delete(State.values.gatewayaddress);

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
            state.set(State.values.networksinrange, str);
        }
        else state.delete(State.values.networksinrange);

        // networksknown
        if (document.getElementsByTagName("networksknown").getLength() > 0) {
            String str = "";
            Element networksknown = (Element) document.getElementsByTagName("networksknown").item(0);
            NodeList names = networksknown.getElementsByTagName("name");
            for (int i = 0; i < names.getLength(); i++) {
                if (i>0) str += ",";
                str += ((Element) names.item(i)).getTextContent();
            }
            state.set(State.values.networksknown, str);
        }
        else state.delete(State.values.networksknown);

        return true;
    }

    @Override
    public void updated(String key) {
        if(key.equals(State.values.ssid.name())){
            Util.updateExternalIPAddress();
            Util.updateLocalIPAddress();
        }
    }


}
