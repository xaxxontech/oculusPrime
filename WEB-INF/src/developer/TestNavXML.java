package developer;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.Vector;

import javax.xml.parsers.ParserConfigurationException;

import org.junit.Test;

public class TestNavXML {
	
	String path = "F:\\robot\\archive\\robot backup\\oculusPrime\\conf\\navigationroutes.xml";
	/*
	@Test
	public void testCompareToSkeletonXML() throws Exception {
		
		NavigationUtilities.navroutesfile = new File(path); // override path for junit test  
		
	    final String myControlXML = NavigationUtilities.routesLoad(); 
	    
//	   NavigationUtilities.setRouteCount("hallz", NavigationUtilities.getRouteCount("hallz")+1 );

	    String myTestXML = NavigationUtilities.routesLoad(); 
	    
	    DifferenceListener myDifferenceListener = new IgnoreTextAndAttributeValuesDifferenceListener();
	    Diff myDiff = new Diff(myControlXML, myTestXML); 
	    
	    System.out.println("diff identical: " + myDiff);
	    myDiff.overrideDifferenceListener(myDifferenceListener);
	
	    assertTrue("test XML matches control skeletonn  : ", myDiff.similar());
	    assertTrue("test XML matches control identical  : ", myDiff.identical());

	    //------- update file
	    
	    System.out.println("diff identical: " + myDiff);
	    
	    NavigationUtilities.setRouteCount("hallz", NavigationUtilities.getRouteCount("hallz")+1 );

	    myTestXML = NavigationUtilities.routesLoad(); 
	    myDiff = new Diff(myControlXML, myTestXML); 
	    
	    System.out.println("diff different: " + myDiff);
	    
	}
	*/
	
	@Test
	public void readRouteDetails() throws ParserConfigurationException{

		NavigationUtilities.navroutesfile = new File(path); // override path for junit test  
		String xml = NavigationUtilities.routesLoad();//path);

		Vector<String> routes = NavigationUtilities.getRoutes(xml);
		for(int i = 0 ; i < routes.size() ; i++){
			
			String name = routes.get(i);
			System.out.println("\n---- route name: " + name + " -----");
//			System.out.println(NavigationUtilities.getRouteXML(name, xml));
			final String frag = NavigationUtilities.getRouteXML(name, xml);
//			System.out.println("minbetween: " + NavigationUtilities.getTag("minbetween", frag));
//			System.out.println("removted: " + NavigationUtilities.removeTag("minbetween", frag));

			System.out.println("start hour: " + NavigationUtilities.getTag("starthour", frag));
			System.out.println("distance: " + NavigationUtilities.getTag(NavigationUtilities.ESTIMATED_DISTANCE_TAG, frag));
//			System.out.println("distance : " + NavigationUtilities.getRouteDistanceEstimateString(name, xml) + " == " + NavigationUtilities.getRouteDistanceEstimateString(name));

			String starthour = NavigationUtilities.removeTag("starthour", frag);
			String a = NavigationUtilities.removeTag("minbetween", starthour);
			String b = NavigationUtilities.removeTag("startmin", a);
			String c = NavigationUtilities.removeTag("routeduration", b);
			String d = NavigationUtilities.removeTag("routecount", c);
			String e = NavigationUtilities.removeTag("routefail", d);
			String f = NavigationUtilities.removeTag(NavigationUtilities.ESTIMATED_DISTANCE_TAG, e);
			String g = NavigationUtilities.removeTag(NavigationUtilities.ESTIMATED_TIME_TAG, f);
			String h = NavigationUtilities.removeTag(NavigationUtilities.ACTIVE, g);

/*	
	
//			System.out.println("both : " + h);
			String value = h;
			String temp = "";
				
			for(int j = 0 ; ; j++){
				System.out.println("days " + j + " " + value);
				temp = NavigationUtilities.removeTag("day", value);
				if(temp == null) break;
				else value = temp;
			}
	
			for(int j = 0 ; ; j++){
				System.out.println("wayoints" + j + " " + value);
				temp = NavigationUtilities.removeTag("waypoint", value);
				if(temp == null) break;
				else value = temp;
			}
	*/
			
/**/
			System.out.println("waypoints: " + NavigationUtilities.getWaypointsForRoute(name, xml));
		
			System.out.println("count    : " + NavigationUtilities.getRouteCountString(name, xml) + " == " + NavigationUtilities.getRouteCountString(name));
			assertTrue(NavigationUtilities.getRouteCountString(name, xml).equals(NavigationUtilities.getRouteCountString(name)));
			assertTrue(NavigationUtilities.getRouteCount(name, xml) == NavigationUtilities.getRouteCount(name));

			System.out.println("fails    : " + NavigationUtilities.getRouteFailsString(name, xml) + " == " + NavigationUtilities.getRouteFailsString(name));
			assertTrue(NavigationUtilities.getRouteFailsString(name, xml).equals(NavigationUtilities.getRouteFailsString(name)));
			assertTrue(NavigationUtilities.getRouteFails(name, xml) == NavigationUtilities.getRouteFails(name));
			
			System.out.println("distance : " + NavigationUtilities.getRouteDistanceEstimateString(name, xml) + " == " + NavigationUtilities.getRouteDistanceEstimateString(name));
			assertTrue(NavigationUtilities.getRouteDistanceEstimateString(name, xml).equals(NavigationUtilities.getRouteDistanceEstimateString(name)));
			assertTrue(NavigationUtilities.getRouteDistanceEstimate(name, xml) == NavigationUtilities.getRouteDistanceEstimate(name));
			
			System.out.println("time     : " + NavigationUtilities.getRouteTimeEstimateString(name, xml) + " == " + NavigationUtilities.getRouteTimeEstimateString(name));
			assertTrue(NavigationUtilities.getRouteTimeEstimateString(name, xml).equals(NavigationUtilities.getRouteTimeEstimateString(name)));
			assertTrue(NavigationUtilities.getRouteTimeEstimate(name, xml) == NavigationUtilities.getRouteTimeEstimate(name));

			
		}
	}
	

	
	@Test
	public void testFileWrites(){
		
		NavigationUtilities.navroutesfile = new File(path); // override path for junit test  
		Vector<String> routes = NavigationUtilities.getRoutes(); 
		for(int i = 0 ; i < routes.size() ; i++){
			
			String name = routes.get(i);
//			System.out.println("\n---- test write files: " + name + " -----");
			
			NavigationUtilities.setActiveRoute(routes.get(i));
			assertNotNull("active route is null", NavigationUtilities.getActiveRoute());
	//		System.out.println("current active route: " + NavigationUtilities.getActiveRoute());
		
			NavigationUtilities.deactivateAllRoutes();
			assertNull("de-activate route returns null", NavigationUtilities.getActiveRoute());
		
			NavigationUtilities.setActiveRoute(routes.get(i));
	//		System.out.println(" re:set active this route: " + NavigationUtilities.getActiveRoute());
			
			// add and subtract 
		
			System.out.println(" = route count: " + NavigationUtilities.getRouteCountString(name));
			NavigationUtilities.setRouteCount(name, NavigationUtilities.getRouteCount(name)+1 );
			System.out.println(" + route count: " + NavigationUtilities.getRouteCountString(name));
			NavigationUtilities.setRouteCount(name, NavigationUtilities.getRouteCount(name)-1 );
			System.out.println(" - route count: " + NavigationUtilities.getRouteCountString(name));
			
			NavigationUtilities.routeCompleted(name);
			System.out.println(".. route completed: " + NavigationUtilities.getRouteCount(name));
			NavigationUtilities.setRouteCount(name, NavigationUtilities.getRouteCount(name)-1 );

			System.out.println(" = route fails: " + NavigationUtilities.getRouteFailsString(name));
			NavigationUtilities.setRouteFails(name, NavigationUtilities.getRouteFails(name)+1 );
			System.out.println(" + route fails: " + NavigationUtilities.getRouteFailsString(name));
			NavigationUtilities.setRouteFails(name, NavigationUtilities.getRouteFails(name)-1 );
			System.out.println(" - route fails: " + NavigationUtilities.getRouteFailsString(name));
			
			NavigationUtilities.routeFailed(name);
			System.out.println(".. route failed: " + NavigationUtilities.getRouteFails(name));
			NavigationUtilities.setRouteFails(name, NavigationUtilities.getRouteFails(name)-1 );
			
// 			assertEquals(NavigationUtilities.getRouteFails(name), NavigationUtilities.setRouteFails(name, NavigationUtilities.getRouteFails(name)-1 ));
			
		}
	}
}
