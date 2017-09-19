package developer;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Vector;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import oculusPrime.Util;

/** manage XML file for navigation */
public class NavigationUtilities {

	public static File navroutesfile = new File(System.getenv("RED5_HOME")+"/conf/navigationroutes.xml");
	
	public static final String ESTIMATED_DISTANCE_TAG = "estimateddistance";
	public static final String ESTIMATED_TIME_TAG = "estimatedtime";
	public static final String ROUTE_COUNT_TAG = "routecount";	
	public static final String ROUTE_FAIL_TAG = "routefail";
	public static final String WAYPOINT_NAME = "wpname";
	public static final String ROUTE_NAME = "rname";
	public static final String ACTIVE = "active";

	public static synchronized void saveRoute(final String str){
		
		final String current = routesLoad();
		
		if(str.equalsIgnoreCase(current)){
// 			Util.debug("saveRoute(): skipped, same XML string");
			return;
		}
		
		// TODO: COMPARE TWO STRING... RETURN NAME OF ROUTE THAT WAS EDITED... ? or reset states, esitmates? 
		
		try {
			FileWriter fw = new FileWriter(navroutesfile);
			fw.append(str);
			fw.close();
		} catch (Exception e){ Util.printError(e); }
	}
	
	/** */
	public static String XMLtoString(Document doc) {
		String output = null;
		try {
			TransformerFactory tf = TransformerFactory.newInstance();
			Transformer transformer = tf.newTransformer();
			transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
			StringWriter writer = new StringWriter();
			transformer.transform(new DOMSource(doc), new StreamResult(writer));
			output = writer.getBuffer().toString().replaceAll("\n|\r", "");
		}
		catch (Exception e){ Util.printError(e); }
		return output;
	}
	
	/** */
	public static String routesLoad(){
		String result = "";
		BufferedReader reader;
		try {
			reader = new BufferedReader(new FileReader(navroutesfile));
			String line = "";
			while ((line = reader.readLine()) != null) 	result += line;
			reader.close();
		} catch (Exception e) {
			return "<routeslist></routeslist>";
		}
		return result;
	}
	
	/** */
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
	
	/** */
	public static Vector<String> getRoutes(final String xml){
		Vector<String> names = new Vector<String>();
		NodeList routes = loadXMLFromString(xml).getDocumentElement().getChildNodes();
		for(int i = 0; i < routes.getLength(); i++){
			String rname = ((Element) routes.item(i)).getElementsByTagName(ROUTE_NAME).item(0).getTextContent();
			names.add(rname);
		}
		return names;
	}
	
	/** */
	public static Vector<String> getRoutes(){
		return getRoutes(routesLoad());
	}
	
	/** */
	public static Vector<String> getWaypointsForRoute(final String routename, final String xml){
		if(routename == null) return null;
		Document document = loadXMLFromString(xml);
		NodeList routes = document.getDocumentElement().getChildNodes();
		Vector<String> ans = new Vector<String>();
		for(int i = 0; i < routes.getLength(); i++){
			String rname = ((Element) routes.item(i)).getElementsByTagName(ROUTE_NAME).item(0).getTextContent();
			if(routename.equals(rname)){
	    		NodeList wp = ((Element) routes.item(i)).getElementsByTagName(WAYPOINT_NAME);
	    		for(int j = 0 ; j < wp.getLength() ; j++){
	        		ans.add(wp.item(j).getTextContent());
	        		
	        		// TODO: 
	        		if( wp.item(j).getTextContent().contains("&nbsp;"))
	        			Util.log("getWaypointsForRoute(): ... WARNING .... HTML CHARATERS IN  NAV XML ...", "NavigationUtilities.getWaypointsForRoute()");

	    		}
			}
		}
		return ans;
	}
	
	/** */
	public static Vector<String> getWaypointsForRoute(String rname) {
		return getWaypointsForRoute(rname, routesLoad());
	}
	
	//---------------- route fails
	
	public static int getRouteFails(final String name){
		return Integer.parseInt(getRouteFailsString(name));
	}
	
	public static int getRouteFails(final String name, final String xml){
		return Integer.parseInt(getRouteFailsString(name, xml));
	}
	
	public static String getRouteFailsString(final String name){
		return getRouteFailsString(name, routesLoad());
	}
	
	public static String getRouteFailsString(final String name, final String xml){
		if(name == null || xml == null) return "0";
		NodeList routes = loadXMLFromString(xml).getDocumentElement().getChildNodes();
		for (int i = 0; i < routes.getLength(); i++){
			String rname = ((Element) routes.item(i)).getElementsByTagName(ROUTE_NAME).item(0).getTextContent();
			if (rname.equals(name)){
				try {
					return ((Element) routes.item(i)).getElementsByTagName(ROUTE_FAIL_TAG).item(0).getTextContent(); 
				} catch (Exception e){}
				break;
			}
		}
		return "0";
	}
	
	public static void setRouteFails(final String name, final int routefails){
		if(name == null) return;
		Document document = NavigationUtilities.loadXMLFromString(routesLoad());
		NodeList routes = document.getDocumentElement().getChildNodes();
		Element route = null;
		for (int i = 0; i < routes.getLength(); i++){
			String rname = ((Element) routes.item(i)).getElementsByTagName(ROUTE_NAME).item(0).getTextContent();
			if (rname.equals(name)){
				route = (Element) routes.item(i);				
				try {
					route.getElementsByTagName(ROUTE_FAIL_TAG).item(0).setTextContent(Integer.toString(routefails));
				} catch (Exception e) { // create if not there 
					Node fail = document.createElement(ROUTE_FAIL_TAG);
					fail.setTextContent(Integer.toString(routefails));
					route.appendChild(fail);
				}
				saveRoute(XMLtoString(document));
				break;
			}
		}
	}

	//--------------- route count
	
	public static void setRouteCount(final String name, final int routecount){
		if(name == null) return;
		Document document = NavigationUtilities.loadXMLFromString(routesLoad());
		NodeList routes = document.getDocumentElement().getChildNodes();
		Element route = null;
		for (int i = 0; i < routes.getLength(); i++){
			String rname = ((Element) routes.item(i)).getElementsByTagName(ROUTE_NAME).item(0).getTextContent();
			if (rname.equals(name)){
				route = (Element) routes.item(i);				
				try {
					route.getElementsByTagName(ROUTE_COUNT_TAG).item(0).setTextContent(Integer.toString(routecount));
				} catch (Exception e) { // create if not there 
					Node count = document.createElement(ROUTE_COUNT_TAG);
					count.setTextContent(Integer.toString(routecount));
					route.appendChild(count);
				}
				saveRoute(XMLtoString(document));
				break;
			}
		}
	}
	
	public static String getRouteCountString(final String name, final String xml){
		if(name == null || xml == null) return "0";
		NodeList routes = loadXMLFromString(xml).getDocumentElement().getChildNodes();
		for (int i = 0; i < routes.getLength(); i++){
			String rname = ((Element) routes.item(i)).getElementsByTagName(ROUTE_NAME).item(0).getTextContent();
			if (rname.equals(name)){
				try {	
					return ((Element) routes.item(i)).getElementsByTagName(ROUTE_COUNT_TAG).item(0).getTextContent(); 
				} catch (Exception e){break;}
			}
		}
		return "0";
	}
	
	public static String getRouteCountString(final String name){
		return getRouteCountString(name, routesLoad());
	}
	
	public static int getRouteCount(final String name, final String xml){
		return Integer.parseInt(getRouteCountString(name, xml));
	}
	
	public static int getRouteCount(final String name){
		return Integer.parseInt(getRouteCountString(name));
	}
	
	//------------ distance -- units meters ---------//
	
	public static void setRouteDistanceEstimate(final String name, final int meters){
		if(name == null) return;
		Document document = NavigationUtilities.loadXMLFromString(routesLoad());
		NodeList routes = document.getDocumentElement().getChildNodes();
		Element route = null;
		for (int i = 0; i < routes.getLength(); i++){
			String rname = ((Element) routes.item(i)).getElementsByTagName(ROUTE_NAME).item(0).getTextContent();
			if (rname.equals(name)){
				route = (Element) routes.item(i);				
				try {	
					route.getElementsByTagName(ESTIMATED_DISTANCE_TAG).item(0).setTextContent(Util.formatFloat(meters, 0));
				} catch (Exception e) { // create if not there 
					Node dist = document.createElement(ESTIMATED_DISTANCE_TAG);
					dist.setTextContent(Util.formatFloat(meters, 0));
					route.appendChild(dist);
				}
				saveRoute(XMLtoString(document));
				break;
			}
		}
	}
	
	public static String getRouteDistanceEstimateString(final String name, final String xml){
		if(name == null || xml == null) return "0";
		NodeList routes = loadXMLFromString(xml).getDocumentElement().getChildNodes();
		for (int i = 0; i < routes.getLength(); i++){
			String rname = ((Element) routes.item(i)).getElementsByTagName(ROUTE_NAME).item(0).getTextContent();
			if (rname.equals(name)){
				try {
					return ((Element) routes.item(i)).getElementsByTagName(ESTIMATED_DISTANCE_TAG).item(0).getTextContent(); 
				} catch (Exception e){return "0";}
			}
		}
		return "0";
	}
		
	public static String getRouteDistanceEstimateString(final String name){
		return getRouteDistanceEstimateString(name, routesLoad());
	}
	
	public static int getRouteDistanceEstimate(final String name, final String xml){
		return Integer.parseInt(getRouteDistanceEstimateString(name, xml));
	}
	
	public static int getRouteDistanceEstimate(final String name){
		return (int) Double.parseDouble(getRouteDistanceEstimateString(name));
	}
	
	//-------------------- time  -- units seconds --------------------//

	public static void setRouteTimeEstimate(final String name, final int seconds){
		if(name == null) return;
		Document document = NavigationUtilities.loadXMLFromString(routesLoad());
		NodeList routes = document.getDocumentElement().getChildNodes();
		Element route = null;
		for (int i = 0; i < routes.getLength(); i++){
			String rname = ((Element) routes.item(i)).getElementsByTagName(ROUTE_NAME).item(0).getTextContent();
			if (rname.equals(name)){
				route = (Element) routes.item(i);				
				try {
					route.getElementsByTagName(ESTIMATED_TIME_TAG).item(0).setTextContent(Integer.toString(seconds));
				} catch (Exception e) { // create if not there 
					Node time = document.createElement(ESTIMATED_TIME_TAG);
					time.setTextContent(Integer.toString(seconds));
					route.appendChild(time);
				}
				saveRoute(XMLtoString(document));
				break;
			}
		}
	}

	public static String getRouteTimeEstimateString(final String name, final String xml){
		if(name == null) return "0";
		NodeList routes = loadXMLFromString(xml).getDocumentElement().getChildNodes();
		for (int i = 0; i < routes.getLength(); i++){
			String rname = ((Element) routes.item(i)).getElementsByTagName(ROUTE_NAME).item(0).getTextContent();
			if (rname.equals(name)){
				try {
					return ((Element) routes.item(i)).getElementsByTagName(ESTIMATED_TIME_TAG).item(0).getTextContent(); 
				} catch (Exception e){return "0";}
			}
		}
		return "0";
	}
		
	public static String getRouteTimeEstimateString(final String name){
		return getRouteTimeEstimateString(name, routesLoad());
	}
		
	public static int getRouteTimeEstimate(final String name, final String xml){
		return (int) Double.parseDouble(getRouteTimeEstimateString(name, xml));
	}
	
	public static int getRouteTimeEstimate(final String name){
		return Integer.parseInt(getRouteTimeEstimateString(name));
	}
	
	//-------------- active route

	public static String getActiveRoute(){
		try {
			Document document = NavigationUtilities.loadXMLFromString(NavigationUtilities.routesLoad());
			NodeList routes = document.getDocumentElement().getChildNodes();
			for (int i = 0; i < routes.getLength(); i++) {
				String rname = ((Element) routes.item(i)).getElementsByTagName(ROUTE_NAME).item(0).getTextContent();
				String isactive = ((Element) routes.item(i)).getElementsByTagName(ACTIVE).item(0).getTextContent();
				if(isactive.equals("true")) return rname;//Boolean.TRUE.toString())) return rname;  //-------------------------------------------------------------------- bpool?
			}
		} catch (DOMException e) {
			Util.printError(e);
			return null;
		}
		return null;
	}
	
	public static void deactivateAllRoutes() {
		Document document = loadXMLFromString(routesLoad());
		NodeList routes = document.getDocumentElement().getChildNodes();
		for (int i = 0 ; i < routes.getLength(); i++) 
			((Element) routes.item(i)).getElementsByTagName(ACTIVE).item(0).setTextContent("false");
		
		String xmlString = XMLtoString(document);
		saveRoute(xmlString);
	}
	
	public static boolean routeExists(final String name){
		Vector<String> routes = getRoutes();
		if(routes.contains(name)) return true;
		return false;
	}

	public static void setActiveRoute(final String name){
		if(name == null) return;
		if(name.equals("")) return;
		try {
			Document document = loadXMLFromString(routesLoad());
			NodeList routes = document.getDocumentElement().getChildNodes();
			Element route = null;
			for (int i = 0; i < routes.getLength(); i++){
				String rname = ((Element) routes.item(i)).getElementsByTagName(ROUTE_NAME).item(0).getTextContent();
				((Element) routes.item(i)).getElementsByTagName(ACTIVE).item(0).setTextContent("false");
				if(rname.equals(name)) route = (Element) routes.item(i); 
			}				
			route.getElementsByTagName(ACTIVE).item(0).setTextContent("true");
			saveRoute(XMLtoString(document));
		} catch (DOMException e) {
			Util.printError(e);
		}
	}

	public static Element getRouteElement(final String name){
		return getRouteElement(name, routesLoad());
	}

	public static Element getRouteElement(final String name, final String xml ){
		try {
			Document document = loadXMLFromString(xml);
			NodeList routes = document.getDocumentElement().getChildNodes();
			Element route = null;
			for (int i = 0; i < routes.getLength(); i++){
				String rname = ((Element) routes.item(i)).getElementsByTagName(ROUTE_NAME).item(0).getTextContent();
				if(rname.equals(name)) route = (Element) routes.item(i); 
			}
			return route;
		} catch (DOMException e) {
			Util.printError(e);
			return null;
		}
	}

	public static void routeCompleted(final String name, final int seconds, final int meters){
		
		setRouteCount(name, getRouteCount(name)+1);
		
	//	Util.log("NavigationUtilies.routeCompleted("+name+", " + seconds + ", " + meters + "): called.. ");

		if(seconds <= 0  || meters <= 0) {
	//		Util.log("NavigationUtilies.routeCompleted("+name+", " + seconds + ", " + meters + "): faillllll.. ");
			return; // sanity test 
		}

		final int estsec = getRouteTimeEstimate(name); 
		if(estsec == 0) setRouteTimeEstimate(name, seconds); 
		else if(estsec != seconds) {
			setRouteTimeEstimate(name, (seconds+estsec)/2); // average them	
			Util.log("routeCompleted("+name+", " + seconds + ", " + meters + "): average seconds = " + seconds + " updated = "+ (seconds+estsec)/2,
					"NavigationUtilities, routeCompleted()");
		}

		final int distance = getRouteDistanceEstimate(name);
		if(distance == 0) setRouteDistanceEstimate(name, meters);
		else if(distance != meters){
			setRouteDistanceEstimate(name, (distance+meters)/2); // average them	
			Util.log("routeCompleted("+name+", " + seconds + ", " + meters + "): average meters = " + meters + " updated = "+ (distance+meters)/2,
					"NavigationUtilities, routeCompleted()");
		}
	}

	public static void routeCompleted(String name){
		setRouteCount(name, getRouteCount(name)+1);
	}
	
	public static void routeFailed(String name){
		setRouteFails(name, getRouteFails(name)+1);		
	}
	
	/* */
	public static void resetAllRouteStats(){
		Document document = NavigationUtilities.loadXMLFromString(routesLoad());
		NodeList routes = document.getDocumentElement().getChildNodes();
		for (int i = 0; i < routes.getLength(); i++){
			
			Element route = (Element) routes.item(i);	
			
			try {
				route.getElementsByTagName(ESTIMATED_TIME_TAG).item(0).setTextContent("0");
			} catch (Exception e) { // create if not there 
				Node time = document.createElement(ESTIMATED_TIME_TAG);
				time.setTextContent("0");
				route.appendChild(time);
			}
			
			try {	
				route.getElementsByTagName(ESTIMATED_DISTANCE_TAG).item(0).setTextContent("0");
			} catch (Exception e) { // create if not there 
				Node dist = document.createElement(ESTIMATED_DISTANCE_TAG);
				dist.setTextContent("0");
				route.appendChild(dist);
			}
			
			try {
				route.getElementsByTagName(ROUTE_COUNT_TAG).item(0).setTextContent("0");
			} catch (Exception e) { // create if not there 
				Node count = document.createElement(ROUTE_COUNT_TAG);
				count.setTextContent("0");
				route.appendChild(count);
			}
			
			try {
				route.getElementsByTagName(ROUTE_FAIL_TAG).item(0).setTextContent("0");
			} catch (Exception e) { // create if not there 
				Node fail = document.createElement(ROUTE_FAIL_TAG);
				fail.setTextContent("0");
				route.appendChild(fail);
			}	
		}
	
	//  screws up junit tests
	//	NavigationLog.newItem(NavigationLog.ALERTSTATUS, "Utilities.resetAllRouteStats");
	//	Util.log("NavigationUtilies.developer.NavigationUtilities.resetAllRouteStats(): " + XMLtoString(document));

		saveRoute(XMLtoString(document));
	}
	
	/** do in single method, don't call helper methods in loop, too many file operations */
	public static String getRouteStatsHTML(){
		
		String info = "<table >\n<tbody><tr><th>Route Name <th>Seconds<th>Meters</font><th>Success<th>Failures</tr>";
		Document document = NavigationUtilities.loadXMLFromString(routesLoad());
		NodeList routes = document.getDocumentElement().getChildNodes();
		for (int i = 0; i < routes.getLength(); i++){
			try {
				
				Element route = (Element) routes.item(i);	
				String rname = ((Element) routes.item(i)).getElementsByTagName(ROUTE_NAME).item(0).getTextContent();
				
				// does nada in master branch 
				// info += "<tr><td><a href=\"dashboard?action=resetstats&route=" + rname + "\">" + rname + "</a>";
				info += "<tr><td>" + rname;
				info += "<td>" + route.getElementsByTagName(ESTIMATED_TIME_TAG).item(0).getTextContent();
				info += "<td>" + route.getElementsByTagName(ESTIMATED_DISTANCE_TAG).item(0).getTextContent();
				info += "<td>" + route.getElementsByTagName(ROUTE_COUNT_TAG).item(0).getTextContent();
				info += "<td>" + route.getElementsByTagName(ROUTE_FAIL_TAG).item(0).getTextContent() + "</tr>\n";
				
			} catch (Exception e) {}
		}
		
		info += "</tbody></table>";
		return info;
	}
	
	public static String getRouteStats(){
		String info = "";	
		Document document = NavigationUtilities.loadXMLFromString(routesLoad());
		NodeList routes = document.getDocumentElement().getChildNodes();
		for (int i = 0; i < routes.getLength(); i++){
			try {		
				Element route = (Element) routes.item(i);	
				String rname = ((Element) routes.item(i)).getElementsByTagName(ROUTE_NAME).item(0).getTextContent();
				info += "route name: " + rname + " time: " + route.getElementsByTagName(ESTIMATED_TIME_TAG).item(0).getTextContent()
				              + " distance: " + route.getElementsByTagName(ESTIMATED_DISTANCE_TAG).item(0).getTextContent()
			                  + " count: " + route.getElementsByTagName(ROUTE_COUNT_TAG).item(0).getTextContent()
				              + " fail: " + route.getElementsByTagName(ROUTE_FAIL_TAG).item(0).getTextContent() + "\n";
				
			} catch (Exception e) {}
		}
		return info;
	}
	
}
