package oculusPrime;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.Part;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.io.OutputStream;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;


public class NetworkServlet extends HttpServlet {

    static BanList ban = null;

    public void init(ServletConfig config) throws ServletException {
        super.init(config);
        ban = BanList.getRefrence();
    }

    public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        doGet(request, response);
    }

    public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

        if (!ban.knownAddress(request.getRemoteAddr())) {
            Util.log("unknown address: sending to login: " + request.getRemoteAddr(), this);
            response.sendRedirect("/oculusPrime");
            return;
        }

//        Util.log ("getRequestURL: "+request.getRequestURL().toString(), this);
//        Util.log("getQueryString: "+request.getQueryString(), this);
//        Util.log("getRequestURI: "+request.getRequestURI(), this);
//        Util.log("getScheme: "+request.getScheme(), this);
//        Util.log("getServerName: "+ request.getServerName(), this);
//        Util.log("getServerPort: "+ request.getServerPort(), this);
//        Util.log("getContextPath: "+request.getContextPath(), this);
//        Util.log("getServletPath: "+request.getServletPath(), this);
//        Util.log("getPathInfo: "+request.getPathInfo(), this);
//        Util.log("getQueryString: "+request.getQueryString(), this);

//        response.sendRedirect(request.getRequestURL().toString());

        try {
            String url = "http://localhost";

//            String params = request.getQueryString();
// //           if (params != null) url += "?"+params;
//            Util.log(url+"?"+params, this);

            HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();


            String postData = "";
            Enumeration<String> parameterNames = request.getParameterNames();
            while (parameterNames.hasMoreElements()) {
                if (postData.length()!=0) postData += "&";
                String paramName = parameterNames.nextElement();
//                Util.log("paramName: "+paramName, this);
                postData += paramName;
                String[] paramValues = request.getParameterValues(paramName);
                for (int i = 0; i < paramValues.length; i++) {
                    postData += "=";
//                    Util.log("paramValue: "+paramValues[i], this);
                    postData += paramValues[i];
                }
            }

//            Util.log("PostData: "+postData, this); // careful, contains plain text password

            // manage params if any
            if (postData.length() != 0) {
                // send params via post
                con.setRequestMethod("POST");
                con.setDoOutput(true);
                OutputStream os = con.getOutputStream();
                os.write(postData.getBytes());
                os.flush();
                os.close();

                // redirect to '/' if certain params (mirroring similar conditions in Jetty doGet())
                String action = request.getParameter("action");
                String router = request.getParameter("router");
                String password = request.getParameter("password");

                if (router != null && password != null) {
                    response.sendRedirect(request.getRequestURL().toString());
                }
                else if(action != null) {
                    if (!action.equals("status") && !action.equals("connect") &&
                            !action.equals("config") && !action.equals("wifiready")) {

                        response.sendRedirect(request.getRequestURL().toString());
                    }
                }
            }

            // render response
            int responseCode = con.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {

                String charset = "ISO-8859-1";
                Reader r = new InputStreamReader(con.getInputStream(), charset);

                PrintWriter out = response.getWriter();

                while (true) {
                    int ch = r.read();
                    if (ch < 0)
                        break;
                    out.print((char) ch);
                }
            }


        } catch (Exception e) {
            e.printStackTrace();
        }

//        response.sendRedirect(request.getRequestURL().toString());

    }

}