package org.gisbis.trackrenderer.controller;

import java.io.IOException;
import java.io.PrintWriter;

import javax.ejb.EJB;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.gisbis.trackrenderer.business.Trackers;

/** */
@WebServlet("/TrackerList")
public class TrackerList extends HttpServlet {
	private static final long serialVersionUID = 1L;
	
	@EJB
	Trackers trackers;
       
	protected void service(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		response.setCharacterEncoding("utf-8");
		PrintWriter out = response.getWriter();
		
		try {
			trackers.getTrackersList().values()
				.stream()
				.forEach(t->out.println(t.toString()));
		} catch (Exception e) {
			out.println(e.getMessage());
			e.printStackTrace();
		}
	}

}
