package oculusPrime;

import oculusPrime.State.values;

public class StateObserver implements Observer {
	
	public enum modes{ greater, lessthan, equals, changed };

	public static int total = 0;
	public static int alive = 0;

	
	private static State state = State.getReference();
	private boolean waiting = true;
	private values member = null;
	private String target = null;
	private long start;
	private modes type;
	
	
	/**
	 * block until timeout or until member == target
	 * 
	 * @param member state key
	 * @param target block until timeout or until member == target
	 * @param timeout is the ms to wait before giving up 
	 * @return true if the member was set to the target in less than the given timeout 
	 */
	
	
	public StateObserver(modes mode){
		state.addObserver(this);
		type = mode;
	}
		
	public StateObserver(){
		state.addObserver(this);
		type = modes.equals;
	}
	
	
	public boolean block(final values member){ 
		total++;
		alive++;
		this.member = member;
		start = System.currentTimeMillis();
		while(waiting) Util.delay(100);
		Util.log("blocking updated: " + member.name(), this);
		alive--;
		return true; 
	} 
	
	public boolean block(final values member, long timeout){ 
		total++;
		alive++;
		this.member = member;
		start = System.currentTimeMillis();
		while(waiting){	
			if(System.currentTimeMillis()-start > timeout){ 
				Util.debug("block() timeout: " + member.name(), this);
				alive--;
				return false;
			} else {
				// Util.debug("blocking waiting: " + member.name());
				Util.delay(1);
			}
		}	
		
		alive--;
		Util.log("block(): " + member.name() + " changed in: " + ((System.currentTimeMillis()-start)/1000) + "sec ", this);
		return true; 
	} 
	
	public boolean block(final values member, final String target, long timeout){
		total++;
		alive++;
		this.member = member;
		this.target = target;
		start = System.currentTimeMillis();
		while(waiting){	
			if(System.currentTimeMillis()-start > timeout){ 
				Util.debug("block() timeout: " + member.name() + " for target: " + target, this);
				alive--;
				return false;
			} else {
				// Util.debug("blocking waiting: " + member.name());
				Util.delay(1);
			}
		}
		
	///	Util.log("blocking cleared: " + member.name(), this);
		alive--;
		Util.log("block(): " + member.name() + " equals target in: " + ((System.currentTimeMillis()-start)/1000) + "sec ", this);
		return true; 
	} 
	/*
	public boolean blockGreaterThan(final values member, final String target, long timeout){ 
		this.member = member;
		this.target = target;
		start = System.currentTimeMillis();
		while(waiting){	
			if(System.currentTimeMillis()-start > timeout){ 
				Util.log("block() timeout: " + member.name(), this);
				return false;
			} else {
				Util.debug("blocking waiting: " + member.name());
				Util.delay(1000);
			}
		}
		
		Util.log("blocking cleared: " + member.name(), this);
		return true; 
	}
*/
	@Override
	public void updated(String key){
		if(key.equals(member.name())){
						
			// Util.debug("..state updated: " + key + " total = " + total + " alive = " + alive, this);
		
			switch(type){
			
			case equals:
				String current = state.get(member); 
				if(current != null){
					if(target.equalsIgnoreCase(current)){
						Util.debug("block(): [" + member.name() + "] equals target in: " + ((System.currentTimeMillis()-start)/1000) + " sec ");
						waiting = false;
					}
				}
				break;
				
			case changed:
				Util.debug("block(): [" + member.name() + "] changed in:  " + ((System.currentTimeMillis()-start)/1000) + " sec ");
				waiting = false;
				break;
				
				/*
			case greater:
				if( ! state.exists(key)) break; 
				int value = state.getInteger(member); 
				if( value > Integer.parseInt(target)){
					
					Util.debug("block(): " + member.name() + " greater:  " + ((System.currentTimeMillis()-start)/1000) + "sec ");
					waiting = false;
					
				}
				break;
				
			case lessthan:
				if( ! state.exists(key)) break; 
				value = state.getInteger(member); 
				if( value < Integer.parseInt(target)){
					
					Util.debug("block(): " + member.name() + " lessthan:  " + ((System.currentTimeMillis()-start)/1000) + "sec ");
					waiting = false;
					
				}
				break;
				*/
		
			}
		}
	}
}
