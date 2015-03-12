package developer;

import java.io.StringReader;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import oculusPrime.State;
import oculusPrime.Util;


public class Scratch {
	

	
    public static void main(String[] args) {
    	
    	String xmlstr = "<?xml version='1.0' encoding='UTF-8'?><routeslist><route><rname>blob</rname>";
    	xmlstr += "<minbetween>60</minbetween><waypoint><wpname>bob and joe</wpname><duration>20</duration>";
    	xmlstr += "<action>rotate</action></waypoint><waypoint><wpname>front door</wpname>";
    	xmlstr += "<duration>0</duration></waypoint></route><route><rname>zarch</rname>";
    	xmlstr += "<minbetween>60</minbetween></route></routeslist>";
    	
    	Document document = Util.loadXMLFromString(xmlstr);	
 	
    	NodeList routes = document.getDocumentElement().getChildNodes();
//    	System.out.println(routes.getLength());
    	for (int i = 0; i< routes.getLength(); i++) {
//    		String name = routes.item(i).getChildNodes().item(0).getNodeValue();  // null
//    		String name = routes.item(i).getAttributes().getNamedItem("rname").getTextContent(); // null pointer err
//    		String name = routes.item(i).getChildNodes().item(0).getTextContent();  // ok
    		String name = ((Element) routes.item(i)).getElementsByTagName("rname").item(0).getTextContent();  
    		
    		System.out.println(name);
    	}
    	
    	Element route = (Element) routes.item(0);
    	NodeList waypoints = route.getElementsByTagName("waypoint");
    	String wpname = ((Element) waypoints.item(0)).getElementsByTagName("wpname").item(0).getTextContent();
    	long timebetween = Long.parseLong(
    			route.getElementsByTagName("minbetween").item(0).getTextContent()) * 1000 * 60;
    	System.out.println(timebetween);
	
    		

    }
}
