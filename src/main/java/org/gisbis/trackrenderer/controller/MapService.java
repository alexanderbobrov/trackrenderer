package org.gisbis.trackrenderer.controller;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
			response.setCharacterEncoding("utf-8");
			PrintWriter out = response.getWriter();
			out.print(
				"<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"+
				"<xsd:schema xmlns:bis=\"aaa\" xmlns:gml=\"http://www.opengis.net/gml\" xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\" elementFormDefault=\"qualified\" targetNamespace=\"aaa\">\r\n" + 
				"  <xsd:import namespace=\"http://www.opengis.net/gml\" />\r\n" + 
				"  <xsd:complexType name=\"aaaType\">\r\n" + 
				"    <xsd:complexContent>\r\n" + 
				"      <xsd:extension base=\"gml:AbstractFeatureType\">\r\n" + 
				"        <xsd:sequence>\r\n" + 
				"          <xsd:element maxOccurs=\"1\" minOccurs=\"0\" name=\"id\" nillable=\"true\" type=\"xsd:int\"/>\r\n" + 
				"          <xsd:element maxOccurs=\"1\" minOccurs=\"0\" name=\"geom\" nillable=\"true\" type=\"gml:GeometryPropertyType\"/>\r\n" + 
				"        </xsd:sequence>\r\n" + 
				"      </xsd:extension>\r\n" + 
				"    </xsd:complexContent>\r\n" + 
				"  </xsd:complexType>\r\n" + 
				"  <xsd:element name=\"aaa\" substitutionGroup=\"gml:_Feature\" type=\"aaa:aaaType\"/>\r\n" + 
				"</xsd:schema>\r\n" + 
				"");
		}
	}
	
	protected void getMap ( HttpServletRequest request, HttpServletResponse response, boolean logging ) throws IOException {
		int width = NumberUtils.toInt(getParameter(request,"WIDTH"), 640);
		int height = NumberUtils.toInt(getParameter(request,"HEIGHT"), 480);
		BufferedImage image = null;
		try {
			CoordinateReferenceSystem sCrs = null; try { sCrs =  CRS.decode(getParameter(request, "srs")); } catch ( Exception e ) { e.printStackTrace(); sCrs = CRS.decode("EPSG:3857"); }
			
			BBoxKvpParser bBoxKvpParser = new BBoxKvpParser();
			ReferencedEnvelope sEnvelope = Objects.requireNonNull((ReferencedEnvelope) bBoxKvpParser.parse(getParameter(request, "BBOX")), "Failed get bbox"); //TODO how to normalise
			
			Envelope e4326 = JTS.transform(sEnvelope, CRS.findMathTransform(sCrs, DefaultGeographicCRS.WGS84));
			
			List<Integer> trackIds = getIntegersParam("cf", request);
			
			String viewparams = request.getParameter("viewparams"); // year:2019;month:2;day:8
			Integer year = null; try { Matcher matcher = Pattern.compile("year\\:(\\d++)",Pattern.CASE_INSENSITIVE | Pattern.MULTILINE).matcher(viewparams); if ( matcher.find() ) year = Integer.parseInt(matcher.group(1)); } catch (Exception e) { } //try { year = Integer.parseInt(request.getParameter("year")); } catch (Exception e) { e.printStackTrace(); } 
			Integer month = null; try { Matcher matcher = Pattern.compile("month\\:(\\d++)",Pattern.CASE_INSENSITIVE | Pattern.MULTILINE).matcher(viewparams); if ( matcher.find() ) month = Integer.parseInt(matcher.group(1)); } catch (Exception e) { } //try { month = Integer.parseInt(request.getParameter("month")); } catch (Exception e) { }
			Integer day = null; try { Matcher matcher = Pattern.compile("day\\:(\\d++)",Pattern.CASE_INSENSITIVE | Pattern.MULTILINE).matcher(viewparams); if ( matcher.find() ) day = Integer.parseInt(matcher.group(1)); } catch (Exception e) { } //try { day = Integer.parseInt(request.getParameter("day")); } catch (Exception e) { }
			
			image = renderer.render(width, height, e4326, year, month, day, trackIds);
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
	
	public List<Integer> getIntegersParam ( String name, HttpServletRequest request ) {
		Objects.requireNonNull(request, "Null request object");
		String s = getParameter(request, name);
		if ( StringUtils.isEmpty(s) ) {
			return null;
		} else {
			return findIntegers(s);
		}
	}
	
	public static List<Integer> findIntegers ( String s ) {
		List<Integer> result = new LinkedList<Integer>();
		
		if ( s != null ) {
			Matcher m = Pattern.compile("(-?\\d++)",Pattern.CASE_INSENSITIVE | Pattern.MULTILINE).matcher(s);
			while ( m.find() )
				result.add(Integer.parseInt(m.group(1)));
		}
		return result;
	}

}
