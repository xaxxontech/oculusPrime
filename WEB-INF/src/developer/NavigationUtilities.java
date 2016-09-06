package developer;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.StringReader;
import java.util.Vector;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

public class NavigationUtilities {

	//
	// driver
	//
	public static void main(String args[]) throws Exception {
	
		final String xml = routesLoad("F:\\robot\\archive\\robot backup\\oculusPrime\\conf\\navigationroutes.xml");
	
		Vector<String> r = getRoutes(xml);
		for(int i = 0 ; i < r.size() ; i++){
			
			System.out.println(r.get(i) + " -- " + getWaypointsForRoute(xml, r.get(i)));
				
		}
		
		System.out.println("red route: " + getWaypointsForRoute(xml, "red route") );
		System.out.println("all route: " + getWaypointsAll(xml) );
	}
	
	
	
	public static String routesLoad(String path){
		String result = "";
		BufferedReader reader;
		try {
			reader = new BufferedReader(new FileReader(path));
			String line = "";
			while ((line = reader.readLine()) != null) 	result += line;
			reader.close();
		} catch (Exception e) {
			return "<routeslist></routeslist>";
		}
		return result;
	}
	
	public static String routesLoad(){
		String result = "";
		BufferedReader reader;
		try {
			reader = new BufferedReader(new FileReader(Navigation.navroutesfile));
			String line = "";
			while ((line = reader.readLine()) != null) 	result += line;
			reader.close();
		} catch (Exception e) {
			return "<routeslist></routeslist>";
		}
		return result;
	}
	
	public static Document loadXMLFromString(String xml){
		try {    
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			DocumentBuilder builder;
			builder = factory.newDocumentBuilder();
			InputSource is = new InputSource(new StringReader(xml));
			return builder.parse(is);
		} catch (Exception e){e.printStackTrace(); }
		return null;
	}
	
	public static Vector<String> getRoutes(final String xml){
		Vector<String> names = new Vector<String>();
		NodeList routes = loadXMLFromString(xml).getDocumentElement().getChildNodes();
		for(int i = 0; i < routes.getLength(); i++){
			String rname = ((Element) routes.item(i)).getElementsByTagName("rname").item(0).getTextContent();
			names.add(rname);
		}
		return names;
	}
	
	public static Vector<String> getWaypointsForRouteList(final String xml, final String routename){
		Document document = loadXMLFromString(xml);
		NodeList routes = document.getDocumentElement().getChildNodes();
		Vector<String> ans = new Vector<String>();
		for(int i = 0; i < routes.getLength(); i++){
			String rname = ((Element) routes.item(i)).getElementsByTagName("rname").item(0).getTextContent();
			if(routename.equals(rname)){
				NodeList wp = ((Element) routes.item(i)).getElementsByTagName("wpname");
				for(int j = 0 ; j < wp.getLength() ; j++){
					if(!ans.contains(wp.item(j).getTextContent())) 
						ans.add(wp.item(j).getTextContent());
				}
			}
		}
		return ans;
	}
	
	public static Vector<String> getWaypointsForRoute(final String xml, final String routename){
		Document document = loadXMLFromString(xml);
		NodeList routes = document.getDocumentElement().getChildNodes();
		Vector<String> ans = new Vector<String>();
		for(int i = 0; i < routes.getLength(); i++){
			String rname = ((Element) routes.item(i)).getElementsByTagName("rname").item(0).getTextContent();
			if(routename.equals(rname)){
	    		NodeList wp = ((Element) routes.item(i)).getElementsByTagName("wpname");
	    		for(int j = 0 ; j < wp.getLength() ; j++){
	        		ans.add(wp.item(j).getTextContent());
	    		}
			}
		}
		return ans;
	}
	
	public static Vector<String> getWaypointsAll(final String xml){
		Document document = loadXMLFromString(xml);
		NodeList routes = document.getDocumentElement().getChildNodes();
		Vector<String> ans = new Vector<String>();
		for (int i = 0; i < routes.getLength(); i++){
    		NodeList wp = ((Element) routes.item(i)).getElementsByTagName("wpname");
    		for(int j = 0 ; j < wp.getLength() ; j++){
        		if(!ans.contains(wp.item(j).getTextContent())) ans.add(wp.item(j).getTextContent());
    		}
		}		
		return ans;
	}
}
