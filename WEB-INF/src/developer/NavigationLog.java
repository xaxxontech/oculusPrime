package developer;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.InputStreamReader;

import oculusPrime.Settings;
import oculusPrime.State;
import oculusPrime.State.values;
import oculusPrime.Util;

/**
 * read file, nuke </body></html>at end, add new stuff, add end back, rewrite file
 *
 * create item for every completed route
 * only create waypoint item if alert or problem
 *
 * css: completedroute,
 */
public class NavigationLog {

    public static final String navigationlogpath =  Settings.redhome+Util.sep + "webapps" + Util.sep +
            "oculusPrime"+ Util.sep + "navigationlog" + Util.sep + "index.html";

// testing:
// public static final String navigationlogpath = System.getenv("HOME")+Util.sep+"temp"+Util.sep+"navroutelog/index.html";

    public static final String ALERTSTATUS = "ALERT";
    public static final String ERRORSTATUS = "ERROR";
    public static final String COMPLETEDSTATUS = "Completed";
    public static final String INFOSTATUS = "Info";
    public static final String PHOTOSTATUS = "Photo";
    
//  private static final String PIPE = " <span style='color: #999999'>|</span> ";
   
    private static final String PIPE = " &nbsp; ";
    private static final String ITEM = "<!--item-->";
    private static final String FILEEND = "</body></html>";
    private static final int maxitems = 300;
    private static volatile boolean newItemBusy = false;
    
    // use if only needing to write a simple message 
    public synchronized static void newItem(final String status, final String msg){
    	newItem(status, msg, System.currentTimeMillis(), null, /*State.getReference().get(values.roscurrentgoal),*/
			   State.getReference().get(values.navigationroute), 0, 0, 0);
    }
    
    public static synchronized void newItem(final String status, final String msg, final long starttime, final String waypoint,
                        final String routename, final int consecutiveroute, final double routedistance, final int rotations) {

        new Thread(new Runnable() { public void run() {
            long timeout = System.currentTimeMillis() + 5000;
            while (newItemBusy && System.currentTimeMillis() < timeout) Util.delay(1);  // wait
            if (newItemBusy) {
                Util.log("error, newitembusy timed out", this);
                return;
            }

            newItemBusy = true;

            String id = String.valueOf(System.nanoTime());
            String str = "<div id='"+id+"' ";
            str += "class='"+status.toLowerCase()+"' ";

            str += "onclick=\"clicked(this.id);\" ";
            str += ">"+Util.getTime() + PIPE;
            String rname = routename;
            if (rname == null) rname = "undefined";
            str += "Route: "+rname;
            if (status != null) str += PIPE+status;
            str += "</div>\n";

            // expand
            str += "<div id='"+id+"_expand' class='"+status.toLowerCase()+"expand' " +
                    "style='display: none; padding-top: 5px; padding-left: 20px'>\n";
            if (msg != null) str += msg+"<br>\n";
            if (waypoint != null) str += "Waypoint: "+waypoint+"<br>\n";
            str += "Consecutive Route: "+consecutiveroute+"<br>\n";
            
            if(status.equals(COMPLETEDSTATUS.toString()) && routename != null){ 
            	str += "Route Count: " + Navigation.getRouteCount(routename) + " <br>\n";
            	int fails = Navigation.getRouteFails(routename);
            	if(fails > 0) str += "Route Failures: " + fails + "<br>\n"; 	
            }
            
            if( !status.equals(INFOSTATUS.toString()) && !status.equals(ERRORSTATUS.toString())
            		&& !status.equals(NavigationLog.PHOTOSTATUS.toString())){
            	long st = starttime;
            	if (st==0) st = System.currentTimeMillis();
            	str += "Elapsed time: "+(int) ((System.currentTimeMillis()-st)/1000/60)+" minutes <br>\n";
            }
            
            if(routedistance > 0) str += "Route distance: " + Util.formatFloat(routedistance/(double)1000, 2) + " meters <br>\n";
            if(rotations > 0) str += "Recovery Rotations: " + rotations; 
            str += "</div>\n";
            writeFile(str);
            newItemBusy = false;
        } }).start();
    }

    private static void writeFile(String newitem) {
        File file = new File(navigationlogpath);
        new File(file.getParentFile().getAbsolutePath()).mkdirs(); // make sure folder exists, returns if exists
        String entirefile = "";
        if (!file.exists()) entirefile = createFile(file);
        else {
            try {
                FileInputStream filein = new FileInputStream(navigationlogpath);
                BufferedReader reader = new BufferedReader(new InputStreamReader(filein));
                String line;
                int items = 0;
                while ((line = reader.readLine()) != null) {
                    if (line.contains(ITEM))
                        items ++;

                    if (items < maxitems || line.contains(FILEEND))
                        entirefile += line + "\n";
                }
                
                reader.close();

            } catch (Exception e) { Util.printError(e); }
        }

        // read file, splice newitem into file, after 1st div
        String old = entirefile.substring(0, entirefile.indexOf("</div>")+7);
        String end = entirefile.substring(entirefile.indexOf("</div>")+7);
        entirefile = old + ITEM + "\n" + newitem + end;
        
        // write file
        try {
            FileWriter fw = new FileWriter(file);
            fw.append(entirefile);
            fw.close();
        } catch (Exception e) { Util.printError(e); }
    }

    private static String createFile(File file) {
        String str = "<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Transitional//EN\" \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd\">\n";
        str += "<!-- DO NOT MODIFY THIS FILE, THINGS WILL BREAK -->\n";
        str += "<html><head>\n";
        str +="<title>Oculus Prime Navigation Route Log</title>\n" +
                "<meta http-equiv=\"Pragma\" content=\"no-cache\">\n" +
                "<meta http-equiv=\"Cache-Control\" Content=\"no-cache\">\n" +
                "<meta http-equiv=\"Expires\" content=\"-1\">\n";
        str += "<style type=\"text/css\">\n" +
                "body { padding-bottom: 10px; margin: 0px; padding: 0px} \n" +
                "body, p, ol, ul, td, tr {\n" +
                "font-family: verdana, arial, helvetica, sans-serif;}\n";
        str += "."+COMPLETEDSTATUS.toLowerCase()+" {background-color: #ADEFAD; cursor: pointer; " +
                "padding-top: 3px; padding-bottom: 3px; padding-left: 15px; padding-right: 10px; " +
                "border-top: 1px solid #ffffff; }\n";
        str += "."+COMPLETEDSTATUS.toLowerCase()+"expand {background-color: #CDFACD; padding-bottom: 3px}\n";
        str += "."+INFOSTATUS.toLowerCase()+" {background-color: #C2EBFF; cursor: pointer; " +
                "padding-top: 3px; padding-bottom: 3px; padding-left: 15px; padding-right: 10px; " +
                "border-top: 1px solid #ffffff; }\n";
        str += "."+INFOSTATUS.toLowerCase()+"expand {background-color: #DBF3FF; padding-bottom: 3px}\n";
        str += "."+ALERTSTATUS.toLowerCase()+" {background-color: #FF8533; cursor: pointer; " +
                "padding-top: 3px; padding-bottom: 3px; padding-left: 15px; padding-right: 10px; " +
                "border-top: 1px solid #ffffff; }\n";
        str += "."+ALERTSTATUS.toLowerCase()+"expand {background-color: #FFAB73; padding-bottom: 3px}\n";
        str += "."+ERRORSTATUS.toLowerCase()+" {background-color: #FF3333; cursor: pointer; " +
                "padding-top: 3px; padding-bottom: 3px; padding-left: 15px; padding-right: 10px; " +
                "border-top: 1px solid #ffffff; }\n";
        str += "."+ERRORSTATUS.toLowerCase()+"expand {background-color: #FC7E7E; padding-bottom: 3px}\n";
        str += "."+PHOTOSTATUS.toLowerCase()+" {background-color: #CC00CC; cursor: pointer; " +
                "padding-top: 3px; padding-bottom: 3px; padding-left: 15px; padding-right: 10px; " +
                "border-top: 1px solid #ffffff; }\n";
        str += "."+PHOTOSTATUS.toLowerCase()+"expand {background-color: #770077; padding-bottom: 3px}\n";

        str += "</style>\n";
        str += "<script type=\"text/javascript\">\n";
        str += "var scrollpos = 0;\n" +
                "\n" +
                "function loaded() {\n" +
                "\tvar url = document.URL;\n" +
                "\tvar id = window.location.hash.substring(1);\n" +
                "\tif (id != '') {\n" +
                "\t\tid += \"_expand\";\n" +
                "\t\tdocument.getElementById(id).style.display='';\n" +
                "\t }\n" +
                "}\n" +
                "\n" +
                "function clicked(id) {\n" +
                "\tscrollpos = document.documentElement.scrollTop || document.body.scrollTop;\n" +
                "\tlocation.hash = id;\n" +
                "\tdocument.getElementById(id+\"_expand\").style.display='';\n" +
                "\tdocument.documentElement.scrollTop = document.body.scrollTop = scrollpos;\n" +
                "}";
        str += "</script>\n";
        str += "</head><body onload=\"loaded();\">\n";
        str += "<div style='padding-top: 5px; padding-bottom: 5px; padding-left: 15px; cursor: pointer;' ";
        str += "onclick=\"window.open(document.URL.replace(/#.*$/, ''), '_self'); \" ";
        str += ">Oculus Prime Navigation Log</div>\n";
        str += FILEEND;
        return str;
    }
}
