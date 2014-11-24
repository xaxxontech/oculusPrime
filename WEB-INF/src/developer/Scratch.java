package developer;

import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class Scratch {

        public static void main(String[] args) {

        	String path = "https://dev.pathwaydesign.com/downloads/";
        	
        	String str = "<tr><td valign=top><img src=/icons/compressed.gif alt=[   ]>"
        			+ "</td><td><a href='oculusprime_server_v0.6.tar.gz'>oculusprime_server_v0.6.tar.gz</a>";
        	Pattern pat = Pattern.compile("oculusprime_server_v.{0,12}\\.tar\\.gz");
        	String result = null;
			Matcher mat = pat.matcher(str);
			while (mat.find()) {
				result = mat.group();
				break;
			}
			
        	result = path + result;
			System.out.println(result);    		

        }
}
