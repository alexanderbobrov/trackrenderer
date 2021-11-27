package org.gisbis.trackrenderer.controller;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.Objects;

import javax.ejb.EJB;
import javax.imageio.ImageIO;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.geoserver.wfs.kvp.BBoxKvpParser;
import org.geotools.geometry.jts.JTS;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.referencing.CRS;
import org.geotools.referencing.crs.DefaultGeographicCRS;
import org.gisbis.trackrenderer.business.Renderer;
import org.locationtech.jts.geom.Envelope;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

import lombok.extern.java.Log;

/** */
@Log
@WebServlet("/MapService")
public class MapService extends HttpServlet {
	private static final long serialVersionUID = 1L;
	
	@EJB
	Renderer renderer;
	
	private static final String getParameter ( HttpServletRequest request, String paramName ) {
		for ( String realParamName : Collections.list(request.getParameterNames()) ) 
			if ( StringUtils.equalsIgnoreCase(realParamName, paramName) ) {
				paramName = realParamName;
				break;
			}
		return request.getParameter(paramName);
	}

	protected void service(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		boolean logging = "1".equals(request.getParameter("log"));
		if ( logging ) log.info("url\n"+request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort() + request.getRequestURI() + "?" + request.getQueryString());
		
		String requestType = getParameter(request, "REQUEST");
		
		if ( "GetMap".equalsIgnoreCase(requestType) ) {
			getMap(request, response, logging);
		} else if ( "DescribeFeatureType".equalsIgnoreCase(requestType) ) {
			
		}
	}
	
	protected void getMap ( HttpServletRequest request, HttpServletResponse response, boolean logging ) throws IOException {
		int width = NumberUtils.toInt(getParameter(request,"WIDTH"), 640);
		int height = NumberUtils.toInt(getParameter(request,"HEIGHT"), 480);
		BufferedImage image = null;
		try {
			CoordinateReferenceSystem sCrs = null; try { sCrs =  CRS.decode(getParameter(request, "srs")); } catch ( Exception e ) { e.printStackTrace(); sCrs = CRS.decode("EPSG:3857"); }
			
			BBoxKvpParser bBoxKvpParser = new BBoxKvpParser();
			ReferencedEnvelope sEnvelope = Objects.requireNonNull((ReferencedEnvelope) bBoxKvpParser.parse(getParameter(request, "BBOX")), "Failed get bbox"); 
			
			Envelope e4326 = JTS.transform(sEnvelope, CRS.findMathTransform(sCrs, DefaultGeographicCRS.WGS84));
			
			int year = 2021;
			int month = 11;
			int day = 23;
			
			image = renderer.render(width, height, e4326, year, month, day);
		} catch ( Exception e ) {
			e.printStackTrace();
			
			image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
    		Graphics2D g2d = image.createGraphics();
    		g2d.setComposite(AlphaComposite.Clear);
    		g2d.fillRect(0, 0, width, height);
    		g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f));
    		g2d.setPaint(Color.red);
    		
    		g2d.drawString("Error!!!!", 2, 10);
    		g2d.drawString(e.getMessage(), 2, 20);
		}
		
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		ImageIO.write(image, "png", baos);
		response.setContentType("image/png");
		response.setHeader("Content-Disposition", "inline; filename=rendertracks.png");
		response.setIntHeader("Content-Length", baos.size());
		response.getOutputStream().write(baos.toByteArray(), 0, baos.size());
		response.setStatus(HttpServletResponse.SC_OK);
		baos.flush();
		baos.close();
	}

}
