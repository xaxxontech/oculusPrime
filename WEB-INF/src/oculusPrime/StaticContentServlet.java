package oculusPrime;

import java.io.*;
import javax.servlet.*;
import javax.servlet.http.*;

// from http://stackoverflow.com/questions/132052/servlet-for-serving-static-content
public class StaticContentServlet extends HttpServlet {

	private static final long serialVersionUID = 1L;
	private static BanList ban = BanList.getRefrence();;

	public void doGet(final HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {

		if (!ban.knownAddress(req.getRemoteAddr())) {
			Util.log("unknown address: sending to login: " + req.getRemoteAddr(), this);
			resp.sendRedirect("/oculusPrime");   
			return;
		}

		RequestDispatcher rd = getServletContext().getNamedDispatcher("default");
		HttpServletRequest wrapped = new HttpServletRequestWrapper(req) {
			public String getServletPath() {
				return req.getServletPath();
			}
		};

		rd.forward(wrapped, resp);
	}
}