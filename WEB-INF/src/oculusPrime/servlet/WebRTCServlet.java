package oculusPrime.servlet;

import oculusPrime.Settings;
import oculusPrime.Util;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.*;
import javax.servlet.http.*;

public class WebRTCServlet extends HttpServlet {

	volatile List<String> msgFromServer = new ArrayList<>();
	volatile List<String> msgFromClient = new ArrayList<>();

	static final long TIMEOUT = 20000;
	volatile long clientRequestID = 0;
	volatile long serverRequestID = 0;

	public void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException { doPost(req,res); }

	public void doPost(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {

		response.setContentType("text/html");
		PrintWriter out = response.getWriter();

		if (request.getParameter("clearvars") != null) {
			msgFromServer.clear();
			msgFromClient.clear();
			clientRequestID = newID();
			serverRequestID = newID();
			Util.log("clearvars", this);
		}

		// msg from client
		else if (request.getParameter("msgfromclient") != null) {
			String msg = request.getParameter("msgfromclient").toString();
			msgFromClient.add(msg);
			Util.debug("msgFromClient #"+msgFromClient.size()+": "+msg, this);

			// wait and reply with any server message
			out.print(sendServerMessage(request, TIMEOUT));
		}

		// msg from server
		else if (request.getParameter("msgfromserver") != null) {
			String msg = request.getParameter("msgfromserver").toString();
			msgFromServer.add(msg);
			Util.debug("msgFromServer #"+msgFromServer.size()+": "+msg, this);

			// wait and reply with any client message
			out.print(sendClientMessage(request, TIMEOUT));
		}

		// server requesting client msg, wait for response
        else if (request.getParameter("requestclientmsg") != null) {
//			Util.debug("requestclientmsg", this);

			out.print(sendClientMessage(request, TIMEOUT));
        }

		// client requesting server msg, wait for response
		else if (request.getParameter("requestservermsg") != null) {
//			Util.debug("requestservermsg", this);

			out.print(sendServerMessage(request, TIMEOUT));
		}

		out.close();
	}

	String sendClientMessage(HttpServletRequest request, long timeout) {

		long id = newID();
		serverRequestID = id;
		String msg = "";

//		long timeout = System.currentTimeMillis();
//		if (request.getParameter("timeout") != null)
//			timeout += Long.parseLong(request.getParameter("timeout"));
//		else
//			timeout += TIMEOUT;

		timeout += System.currentTimeMillis();

		while (id == serverRequestID && System.currentTimeMillis() < timeout && msgFromClient.isEmpty())
			Util.delay(1);

		if (!msgFromClient.isEmpty()) {
			msg = msgFromClient.get(0);
			msgFromClient.remove(0);
			Util.debug("msgFromClient read, size: "+msgFromClient.size(), this);
		}

//		else {
//			Util.log("requestclientmsg timed out or another requestclientmsg arrived", this);
//		}

		return msg;
	}

	String sendServerMessage(HttpServletRequest request, long timeout) {

		long id = newID();
		clientRequestID = id;
		String msg = "";

//		long timeout = System.currentTimeMillis();
//		if (request.getParameter("timeout") != null)
//			timeout += Long.parseLong(request.getParameter("timeout"));
//		else
//			timeout += TIMEOUT;

		timeout += System.currentTimeMillis();

		while (id == clientRequestID && System.currentTimeMillis() < timeout && msgFromServer.isEmpty())
			Util.delay(1);

		if (!msgFromServer.isEmpty()) {
			msg = msgFromServer.get(0);
			msgFromServer.remove(0);
			Util.debug("msgFromServer read, size: "+msgFromServer.size(), this);
		}

//		else {
//			Util.log("requestservermsg timed out or another requestservermsg arrived", this);
//		}

		return msg;
	}

	private long newID() {
		return System.nanoTime();
	}

}
