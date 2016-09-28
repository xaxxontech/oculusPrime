package oculusPrime;

import java.io.IOException;
import java.io.PrintWriter;

import javax.servlet.*;
import javax.servlet.http.*;

public class RtmpPortRequest extends HttpServlet {
	private Settings settings;

	public void doGet(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
		response.setContentType("text/html");
		PrintWriter out = response.getWriter();
		settings = Settings.getReference();
		out.print(settings.readRed5Setting("rtmp.port"));
		out.close();
	}
}
