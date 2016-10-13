package developer;

import static org.junit.Assert.*;

import java.io.File;
import java.util.Vector;

import org.junit.Test;

public class TestNavXML {
	
	String path = "F:\\robot\\archive\\robot backup\\oculusPrime\\conf\\navigationroutes.xml";
	
	// Navigation.navroutesfile = NavigationUtilities.routesLoad("F:\\robot\\archive\\robot backup\\oculusPrime\\conf\\navigationroutes.xml");


	@Test
	public void readRouteDetails(){

		NavigationUtilities.navroutesfile = new File(path); // override path for junit test  
		String xml = NavigationUtilities.routesLoad();//path);

		Vector<String> routes = NavigationUtilities.getRoutes(xml);
		for(int i = 0 ; i < routes.size() ; i++){
			
			String name = routes.get(i);
			System.out.println("\n---- route name: " + name + " -----");
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
			System.out.println("\n---- test write files: " + name + " -----");
			
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
