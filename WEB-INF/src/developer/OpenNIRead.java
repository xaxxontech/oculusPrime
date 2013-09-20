package developer;

import oculusPrime.Application;
import oculusPrime.Settings;

import org.OpenNI.*;

public class OpenNIRead implements IObserver<ErrorStateEventArgs>{
	
	//private static final String SAMPLES_XML = "openNIconfig.xml"; // for running main();
	private static Context context;
	private static DepthGenerator depth;
	private static DepthMetaData depthMD;
	private boolean depthCamInit = false;
	public  boolean depthCamGenerating = false;
	private static Application app;
	
	public OpenNIRead(Application a) {
		app = a;
	}
	
	@Override
	public void update(IObservable<ErrorStateEventArgs> arg0,
			ErrorStateEventArgs arg1) {
		System.out.printf("Global error state has changed: %s", arg1.getCurrentError());
		System.exit(1);		
	}
	
	public void startDepthCam() {
		
		oculusPrime.Util.log("start depth cam", this);
		
		String sep = "\\"; // windows
		if (Settings.os.equals("linux")) { sep = "/"; }
		String SAMPLES_XML = System.getenv("RED5_HOME") + sep+"webapps"+sep+"oculus"+sep+"openNIconfig.xml";

		try {
			if (depthCamInit == false) {
				OutArg<ScriptNode> scriptNodeArg = new OutArg<ScriptNode>();
				context = Context.createFromXmlFile(SAMPLES_XML, scriptNodeArg);
				context.getErrorStateChangedEvent().addObserver(this);
				depth = (DepthGenerator)context.findExistingNode(NodeType.DEPTH);
				depthMD = new DepthMetaData();
				depthCamInit =true;
				depthCamGenerating = true;
				app.message("depth Cam initialize complete",null,null);
			}
			else { depth.startGenerating(); depthCamGenerating=true; }
		}
		catch (Throwable e) { e.printStackTrace(); }
	}
	
	public void stopDepthCam()  {
		try {
//			depthCamInit = false;
			depth.stopGenerating();
//			context.release();
//			context = null;
			app.message("depth Cam shutdown complete",null,null);
			depthCamGenerating= false;
		} catch (StatusException e) {
			e.printStackTrace();
		}
	}
	
	public int[] readHorizDepth(int y) {
		/*
		try {
			context.waitAnyUpdateAll();
				// sometimes throws: org.OpenNI.StatusException: Xiron OS got an event timeout!
		} catch (StatusException e) {
			e.printStackTrace();
		}
		*/
		depth.getMetaData(depthMD);
		int[] result = new int[depthMD.getXRes()];
		int p=0;
		for (int x=0; x < depthMD.getXRes(); x++) {
			result[p]= depthMD.getData().readPixel(x, y); // depthMD.getYRes() / 2);
			p++;
		}
		return result;
	}

//	public static void main(String[] args) // shows ctr pixel value, sample
//	{
//		try 
//		{
//			OutArg<ScriptNode> scriptNodeArg = new OutArg<ScriptNode>();
//			context = Context.createFromXmlFile(SAMPLES_XML, scriptNodeArg);
//			OpenNIRead pThis = new OpenNIRead();
//			context.getErrorStateChangedEvent().addObserver(pThis);
//			depth = (DepthGenerator)context.findExistingNode(NodeType.DEPTH);
//			depthMD = new DepthMetaData();
//		
//			while (true)
//			{
//				context.waitAnyUpdateAll();
//				depth.getMetaData(depthMD);
//				System.out.printf("Frame %d Middle point is: %d.\n", depthMD.getFrameID(), depthMD.getData().readPixel(depthMD.getXRes() / 2, depthMD.getYRes() / 2));
//			}
//		} 
//		catch (Throwable e) { e.printStackTrace(); }
//	}
	
}
