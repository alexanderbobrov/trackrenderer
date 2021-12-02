package org.gisbis.trackrenderer.business;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

import javax.annotation.Resource;
import javax.ejb.EJB;
import javax.ejb.Stateless;
import javax.sql.DataSource;

import org.geotools.geometry.jts.JTS;
import org.geotools.geometry.jts.WKTReader2;
import org.geotools.geometry.jts.WKTWriter2;
import org.geotools.referencing.CRS;
import org.geotools.referencing.crs.DefaultGeographicCRS;
import org.gisbis.geo.AthinesTransform;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryCollection;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.MultiLineString;
import org.locationtech.jts.geom.MultiPoint;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.opengis.referencing.operation.MathTransform;

import lombok.extern.java.Log;

@Log
@Stateless
public class Renderer {
	
	@EJB
	Trackers trackers;
	
	@Resource(name="jdbc/trackrenderer")
	private DataSource dataSource;

	public BufferedImage render ( int width, int height, Envelope e4326, int year, int month, int day ) throws Exception {
		String sql =
			"select AsText(geom) geom \n"+
			"from ( \n"+
			getSQL(year, month, day)+
			"\n) ccc \n"+
			"where ST_Intersects(geom, ST_GeomFromText('"+wktWriter.write(gf.toGeometry(e4326))+"',4326)) = 1";
		
		List<Geometry> geoms4326 = new LinkedList<Geometry>();
		try ( Connection c = dataSource.getConnection(); Statement s = c.createStatement(); ResultSet rs = s.executeQuery(sql); ) {
			while ( rs.next() ) {
				String geomText = rs.getString("geom");
				geoms4326.add(
					wktReader.read(geomText));
			}
		} catch ( SQLException e ) {
			log.info(e.getMessage()+"\nsql\n"+sql);
			throw e;
		}
		
		List<Geometry> geoms3395 = new LinkedList<Geometry>();
		for ( Geometry geom : geoms4326 )
			geoms3395.add(
				JTS.transform(
					geom,
					to3395from4326()));
		
		Envelope e3395 = JTS.transform(e4326, to3395from4326());
		
		AthinesTransform at = new AthinesTransform(width, height, new Coordinate(e3395.getMinX(), e3395.getMinY()), new Coordinate(e3395.getMaxX(), e3395.getMaxY()));
		
		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g2d = image.createGraphics();
		g2d.setComposite(AlphaComposite.Clear);
		g2d.fillRect(0, 0, width, height);
		g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f));
		
		g2d.setPaint(Color.red);
		
		for ( Geometry geom : geoms3395 ) 
			printRecursive(g2d, geom, at, false);
		
		g2d.dispose();
	    
		return image;
	}
	
	private void printRecursive ( Graphics2D g2d, Geometry geom, AthinesTransform athinesTransform, boolean logging ) {
		if ( geom instanceof Point ) {
			//Point point = (Point) geom;
			//drawPoint(g2d, point, athinesTransform, logging);
		} else if ( geom instanceof LinearRing ) {
			LineString lineString = (LineString) geom;
			drawLine(g2d, lineString, athinesTransform, logging);
		} else if ( geom instanceof LineString ) {
			LineString lineString = (LineString) geom;
			drawLine(g2d, lineString, athinesTransform, logging);
		} else if ( geom instanceof Polygon ) {
			Polygon polygon = (Polygon) geom;
			printRecursive(g2d, polygon.getExteriorRing(), athinesTransform, logging);
			for ( int i=0; i<polygon.getNumInteriorRing(); i++ )
				printRecursive(g2d, polygon.getInteriorRingN(i), athinesTransform, logging);
		} else if ( geom instanceof MultiPoint ) {
			MultiPoint multiPoint = (MultiPoint) geom;
			for ( int i=0; i<multiPoint.getNumPoints(); i++ )
				printRecursive(g2d, multiPoint.getGeometryN(i), athinesTransform, logging);
		} else if ( geom instanceof MultiLineString ) {
			MultiLineString multiLineString = (MultiLineString) geom;
			for ( int i=0; i<multiLineString.getNumGeometries(); i++ )
				printRecursive(g2d, multiLineString.getGeometryN(i), athinesTransform, logging);
		} else if ( geom instanceof MultiPolygon ) {
			MultiPolygon multiPolygon = (MultiPolygon) geom;
			for ( int i=0; i<multiPolygon.getNumGeometries(); i++ )
				printRecursive(g2d, multiPolygon.getGeometryN(i), athinesTransform, logging);
		} else if ( geom instanceof GeometryCollection ) {
			GeometryCollection geometryCollection = (GeometryCollection) geom;
			for ( int i=0; i<geometryCollection.getNumGeometries(); i++ )
				printRecursive(g2d, geometryCollection.getGeometryN(i), athinesTransform, logging);
		} else {
			g2d.drawString("unknown geometry type = {"+geom.getClass()+"}", 2, 30);
			//Assert.shouldNeverReachHere("Unsupported Geometry implementation:" + geom.getClass());
		}
	}
	
	private void drawLine ( Graphics2D g2d, LineString line, AthinesTransform athinesTransform, boolean logging ) {
		Coordinate previousCoordinate = null;
		for ( int i=0; i<line.getNumPoints(); i++ ) {
			Point point = line.getPointN(i);
			Coordinate coordinate = new Coordinate(point.getX(), point.getY());
			
			coordinate = athinesTransform.transform(coordinate);
			if ( previousCoordinate != null )
				g2d.drawLine((int)previousCoordinate.x, (int)previousCoordinate.y, (int)coordinate.x, (int)coordinate.y);
			previousCoordinate = coordinate;
		}
	}
	
	private static MathTransform to3395from4326 = null;
	
	private static MathTransform to3395from4326() throws Exception {
		if ( to3395from4326 == null )
			to3395from4326 = CRS.findMathTransform(DefaultGeographicCRS.WGS84, CRS.decode("EPSG:3395"));
		return to3395from4326;
	}
	
	private static GeometryFactory gf = new GeometryFactory();
	
	private static WKTWriter2 wktWriter = new WKTWriter2();
	
	private static WKTReader2 wktReader = new WKTReader2();
	
	private String getSQL ( int year, int month, int day ) throws Exception {
		return
			"select id, geom \n from ( \n"+
			trackers.getTrackersList().values()
				.parallelStream()
				.map(t->
						"select "+t.getId()+" as id, ST_GeomFromText(concat('linestring(', group_concat(p separator ','),')'),4326) as geom  \n"+
						"from ( \n"+
						"	select concat(cast(point_y as char(20)),' ',cast(point_x as char(20))) p \n"+
						"	from tracking.`"+t.getImei()+"` \n"+
						"	where get_time >= STR_TO_DATE('"+day+"/"+month+"/"+year+"','%d/%m/%Y') and get_time < date_add(STR_TO_DATE('"+day+"/"+month+"/"+year+"','%d/%m/%Y'), interval 1 day) \n"+
						"	order by get_time desc \n"+
						") aaa")
				.collect(Collectors.joining("\nunion all\n"))+
			" \n) bbb \nwhere geom is not null";
	}
	
}
