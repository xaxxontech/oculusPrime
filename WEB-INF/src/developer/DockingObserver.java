package developer;

import oculusPrime.Application;
import oculusPrime.AutoDock;
import oculusPrime.Observer;
import oculusPrime.State;

/** Manage auto docking events like timeout and lost target */
public class DockingObserver implements Observer {

	private State state = State.getReference();
	private Application app = null;
	private boolean docking = false;
	private long start = 0;
	private long end = 0; 

	/** register for state changes */
	public DockingObserver(Application a) {
		app = a;
		state.addObserver(this);
	}

	@Override
	public void updated(final String key) {

		if (key == null) return;
		
//		if(state.getBoolean(State.values.losttarget.name())){
//			
//			System.out.println("target lost, trying home");
//			
//			// state.delete(State.losttarget);
//			
//			// Util.dockingTest(app, port, docker);
//		}

		if (state.getBoolean(State.values.autodocking.name())) {
			if (!docking) {
				docking = true;
				System.out.println("started autodocking...");
				start = System.currentTimeMillis();
			}
		}

		if (state.get(State.values.dockstatus.name()) != null) {
			if (state.get(State.values.dockstatus).equals(AutoDock.DOCKED)) {
				if (docking) {
					
					System.out.println("done docking");
					end = System.currentTimeMillis();

					app.message("docking took " + ((end - start) / 1000) + " seconds", null, null);
					System.out.println("docking took: " + ((end - start) / 1000) + " seconds");
					docking = false;
					
				}
			}
		}
	}
}