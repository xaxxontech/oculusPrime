package oculusPrime.servlet;

import oculusPrime.Settings;
import oculusPrime.Util;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.*;
import javax.servlet.http.*;

public class WebRTCServlet extends HttpServlet {

	List<String> msgFromServer = new ArrayList<>();
	List<String> msgFromClient = new ArrayList<>();

	public void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException { doPost(req,res); }

	public void doPost(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {

		response.setContentType("text/html");
		PrintWriter out = response.getWriter();

		if (request.getParameter("clearvars") != null) {
			msgFromServer.clear();
			msgFromClient.clear();
		}

		else if (request.getParameter("msgfromclient") != null) {
			String msg = request.getParameter("msgfromclient").toString();
			msgFromClient.add(msg);
//			out.print("ok");
			Util.log("msgFromClient #"+msgFromClient.size()+": "+msg, this);
		}

		else if (request.getParameter("msgfromserver") != null) {
			String msg = request.getParameter("msgfromserver").toString();
			msgFromServer.add(msg);
//			out.print("ok");
			Util.log("msgFromServer #"+msgFromServer.size()+": "+msg, this);
		}

        else if (request.getParameter("requestclientmsg") != null && !msgFromClient.isEmpty()) {
			out.print(msgFromClient.get(0));
			msgFromClient.remove(0);
			Util.log("msgFromClient read, size: "+msgFromClient.size(), this);
        }

		else if (request.getParameter("requestservermsg") != null && !msgFromServer.isEmpty()) {
			out.print(msgFromServer.get(0));
			msgFromServer.remove(0);
			Util.log("msgFromServer read, size: "+msgFromServer.size(), this);
		}

		out.close();
	}

	public static String getBody(HttpServletRequest request) throws IOException {

		String body = null;
		StringBuilder stringBuilder = new StringBuilder();
		BufferedReader bufferedReader = null;

		try {
			InputStream inputStream = request.getInputStream();
			if (inputStream != null) {
				bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
				char[] charBuffer = new char[128];
				int bytesRead = -1;
				while ((bytesRead = bufferedReader.read(charBuffer)) > 0) {
					stringBuilder.append(charBuffer, 0, bytesRead);
				}
			} else {
				stringBuilder.append("");
			}
		} catch (IOException ex) {
			throw ex;
		} finally {
			if (bufferedReader != null) {
				try {
					bufferedReader.close();
				} catch (IOException ex) {
					throw ex;
				}
			}
		}

		body = stringBuilder.toString();
		return body;
	}
}
