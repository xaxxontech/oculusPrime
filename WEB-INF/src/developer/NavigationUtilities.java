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

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import oculusPrime.State;
import oculusPrime.State.values;
import oculusPrime.Util;

/** manage XML file for nanigation */
public class NavigationUtilities {

	private static final String redhome = System.getenv("RED5_HOME");
	
//	public static final File navroutesfile = new File(redhome+"/conf/navigationroutes.xml");
	 public static final File navroutesfile = new File("F:\\robot\\archive\\robot backup\\oculusPrime\\conf\\navigationroutes.xml");
	
	public static final String ESTIMATED_DISTANCE_TAG = "estimateddistance";
	public static final String ESTIMATED_TIME_TAG = "estimatedtime";
	public static final String ROUTE_COUNT_TAG = "routecount";
	public static final String ROUTE_FAIL_TAG = "routefail";
	
	public static synchronized void saveRoute(final String str){
		
		final String current = routesLoad();
		
		if(str.equalsIgnoreCase(current)){
			Util.debug("saveRoute(): skipped, same XML string");
			return;
		}
		
		// TODO: COMPARE TWO STRING... RETURN NAME OF ROUTE THAT WAS EDITED... ? or reset states, esitmates? 
		
		try {
			FileWriter fw = new FileWriter(navroutesfile);
			fw.append(str);
			fw.close();
		} catch (Exception e){ Util.printError(e); }
	}
	
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
	
	public static synchronized String routesLoad(String path){
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
			reader = new BufferedReader(new FileReader(navroutesfile));
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
	
	public static Vector<String> getRoutes(){
		Vector<String> names = new Vector<String>();
		NodeList routes = loadXMLFromString(routesLoad()).getDocumentElement().getChildNodes();
		for(int i = 0; i < routes.getLength(); i++){
			String rname = ((Element) routes.item(i)).getElementsByTagName("rname").item(0).getTextContent();
			names.add(rname);
		}
		return names;
	}
	/*
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
	*/
	public static Vector<String> getWaypointsForRoute(final String routename, final String xml){
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
	
	public static Vector<String> getWaypointsForRoute(final String routename){
		return getWaypointsForRoute(routesLoad(), routename);
	}
	
	public static Vector<String> getWaypointsForActiveRoute(){
		return getWaypointsForRoute(routesLoad(), getActiveRoute());
	}
	
	// should take from waypoints text file?
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

	public static void updateRouteEstimatess(final String name, final int seconds, final long mm){
		Document document = NavigationUtilities.loadXMLFromString(routesLoad());
		NodeList routes = document.getDocumentElement().getChildNodes();
		Element route = null;
		for (int i = 0; i < routes.getLength(); i++){
			String rname = ((Element) routes.item(i)).getElementsByTagName("rname").item(0).getTextContent();
			if (rname.equals(name)){
				route = (Element) routes.item(i);				
				try {	
					route.getElementsByTagName(ESTIMATED_DISTANCE_TAG).item(0).setTextContent(Util.formatFloat(mm / (double)1000, 0));
				} catch (Exception e) { // create if not there 
					Node dist = document.createElement(ESTIMATED_DISTANCE_TAG);
					dist.setTextContent(Util.formatFloat(mm / (double)1000, 1));
					route.appendChild(dist);
				}
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
		NodeList routes = loadXMLFromString(xml).getDocumentElement().getChildNodes();
		for (int i = 0; i < routes.getLength(); i++){
			String rname = ((Element) routes.item(i)).getElementsByTagName("rname").item(0).getTextContent();
			if (rname.equals(name)){
				try {
					return ((Element) routes.item(i)).getElementsByTagName(ROUTE_FAIL_TAG).item(0).getTextContent(); 
				} catch (Exception e){}
				break;
			}
		}
		return "0";
	}
	
	//--------------- route count
	
	public static String getRouteCountString(final String name, final String xml){
		NodeList routes = loadXMLFromString(xml).getDocumentElement().getChildNodes();
		for (int i = 0; i < routes.getLength(); i++){
			String rname = ((Element) routes.item(i)).getElementsByTagName("rname").item(0).getTextContent();
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
	
	//------------ distance 
	
	public static String getRouteDistanceEstimateString(final String name, final String xml){
		NodeList routes = loadXMLFromString(xml).getDocumentElement().getChildNodes();
		for (int i = 0; i < routes.getLength(); i++){
			String rname = ((Element) routes.item(i)).getElementsByTagName("rname").item(0).getTextContent();
			if (rname.equals(name)){
				try {
					return ((Element) routes.item(i)).getElementsByTagName(ESTIMATED_DISTANCE_TAG).item(0).getTextContent(); 
				} catch (Exception e){break;}
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
		return Integer.parseInt(getRouteDistanceEstimateString(name));
	}
	
	//------------------ 
	
	///public static int getEstimatedMeters(){
	//	return Integer.parseInt(Util.formatFloat(getRouteDistanceEstimate(getActiveRoute()), 0));
	//}
	

	//-------------------- time  

	public static String getRouteTimeEstimateString(final String name, final String xml){
		NodeList routes = loadXMLFromString(xml).getDocumentElement().getChildNodes();
		for (int i = 0; i < routes.getLength(); i++){
			String rname = ((Element) routes.item(i)).getElementsByTagName("rname").item(0).getTextContent();
			if (rname.equals(name)){
				try {
					return ((Element) routes.item(i)).getElementsByTagName(ESTIMATED_TIME_TAG).item(0).getTextContent(); 
				} catch (Exception e){break;}
			}
		}
		return "0";
	}
		
	public static String getRouteTimeEstimateString(final String name){
		return getRouteTimeEstimateString(name, routesLoad());
	}
		
	public static int getRouteTimeEstimate(final String name, final String xml){
		return Integer.parseInt(getRouteTimeEstimateString(name, xml));
	}
	
	public static int getRouteTimeEstimate(final String name){
		return Integer.parseInt(getRouteTimeEstimateString(name));
	}
	
	//--------------
	
	public static void updateRouteStats(final String name, final int routecount, final int routefails){
		Document document = NavigationUtilities.loadXMLFromString(routesLoad());
		NodeList routes = document.getDocumentElement().getChildNodes();
		Element route = null;
		for (int i = 0; i < routes.getLength(); i++){
			String rname = ((Element) routes.item(i)).getElementsByTagName("rname").item(0).getTextContent();
			if (rname.equals(name)){
				route = (Element) routes.item(i);				
				try {
					route.getElementsByTagName(ROUTE_COUNT_TAG).item(0).setTextContent(Integer.toString(routecount));
				} catch (Exception e) { // create if not there 
					Node count = document.createElement(ROUTE_COUNT_TAG);
					count.setTextContent(Integer.toString(routecount));
					route.appendChild(count);
				}
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

	public static String getActiveRoute(){
		Document document = NavigationUtilities.loadXMLFromString(NavigationUtilities.routesLoad());
		NodeList routes = document.getDocumentElement().getChildNodes();
		for (int i = 0; i < routes.getLength(); i++) {
			String rname = ((Element) routes.item(i)).getElementsByTagName("rname").item(0).getTextContent();
			String isactive = ((Element) routes.item(i)).getElementsByTagName("active").item(0).getTextContent();
			if(isactive.equals("true")) return rname;
		}
		return null;
	}
	
	public static void deactivateAllRoutes() {
		Document document = loadXMLFromString(routesLoad());
		NodeList routes = document.getDocumentElement().getChildNodes();
		for (int i = 0 ; i < routes.getLength(); i++) 
			((Element) routes.item(i)).getElementsByTagName("active").item(0).setTextContent("false");
		
		String xmlString = XMLtoString(document);
		saveRoute(xmlString);
	}

	public static void setActiveRoute(String name){
		Document document = loadXMLFromString(routesLoad());
		NodeList routes = document.getDocumentElement().getChildNodes();
		Element route = null;
		for (int i = 0; i < routes.getLength(); i++){
    		String rname = ((Element) routes.item(i)).getElementsByTagName("rname").item(0).getTextContent();
    		((Element) routes.item(i)).getElementsByTagName("active").item(0).setTextContent("false");
			if(rname.equals(name)) route = (Element) routes.item(i); 
		}
			
		route.getElementsByTagName("active").item(0).setTextContent("true");
		String xmlstring = XMLtoString(document);
		saveRoute(xmlstring);
	
	}

	public static Element getRouteElement(String name){
		Document document = loadXMLFromString(routesLoad());
		NodeList routes = document.getDocumentElement().getChildNodes();
		Element route = null;
		for (int i = 0; i < routes.getLength(); i++){
    		String rname = ((Element) routes.item(i)).getElementsByTagName("rname").item(0).getTextContent();
			if(rname.equals(name)) route = (Element) routes.item(i); 
		}
		return route;
	}


	public static void routeFailed(String name){
		Document document = loadXMLFromString(routesLoad());
		
	}
	
	public static void routeCompleted(String name){
		Document document = loadXMLFromString(routesLoad());
		
	}


	public static void updateRouteInfo(){ // long routestarttime, String estimatedmeters){

		/*
		int seconds = (int)((System.currentTimeMillis()-routestarttime)/1000);
		
		Util.log("["+ name + "] estimated: " + estimatedmeters + " meters: " +
				Util.formatFloat((double)routemillimeters/(double)1000) + 
				" delta: " + Math.abs(estimatedmeters - routemillimeters/1000) + " meters ");
		
		Util.log("["+ name + "] estimated: " + estimatedtime   + " seconds : " + seconds     
				+ " delta: " + Math.abs(estimatedtime - seconds) + " seconds ");
		
		if(estimatedtime == 0 && estimatedmeters > 0){
			Util.log("route estimate is zero, meters = " + estimatedmeters, this);
			NavigationUtilities.updateRouteEstimatess(name, seconds, ((estimatedmeters*1000 + routemillimeters/1000)/2));
		} 
		
		if(estimatedmeters == 0 && estimatedtime > 0){
			Util.log("route estimated distance is zero, seconds = " + seconds, this);
			NavigationUtilities.updateRouteEstimatess(name, ((estimatedtime + seconds)/2), routemillimeters);
		} 
		
		if(estimatedmeters > 0 && estimatedtime > 0){
			Util.log("route distance and time greater zero.. compute average", this);
			NavigationUtilities.updateRouteEstimatess(name, ((estimatedtime + seconds)/2),((estimatedmeters*1000 + routemillimeters)/2));
		} 
		
		if(estimatedmeters == 0 && estimatedtime == 0){
			Util.log("route distance and time are zero, use these values", this);
			NavigationUtilities.updateRouteEstimatess(name, seconds, estimatedmeters + routemillimeters);
		}						
	
			
//		if(failed) NavigationUtilities.updateRouteStats(state.get(values.navigationroute), NavigationUtilities.getRouteCount(name)+1, NavigationUtilities.getRouteFails(name)+1);
//		else NavigationUtilities.updateRouteStats(state.get(values.navigationroute), NavigationUtilities.getRouteCount(name)+1, NavigationUtilities.getRouteFails(name));
	*/
		
		String name = State.getReference().get(values.navigationroute);
		Util.log("........... [" + name + "] count: " + NavigationUtilities.getRouteCountString(name) + " fail: " + NavigationUtilities.getRouteFailsString(name), null);

		
	}
	
}
