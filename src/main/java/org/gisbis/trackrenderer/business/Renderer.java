package org.gisbis.trackrenderer.business;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import javax.annotation.Resource;
import javax.ejb.EJB;
import javax.ejb.Stateless;
import javax.sql.DataSource;

import org.apache.commons.collections4.CollectionUtils;
import org.geotools.geometry.jts.JTS;
import org.geotools.geometry.jts.WKTReader2;
import org.geotools.geometry.jts.WKTWriter2;
import org.geotools.referencing.CRS;
import org.geotools.referencing.crs.DefaultGeographicCRS;
import org.geotools.renderer.lite.RendererUtilities_;
import org.gisbis.geo.AthinesTransform;
import org.gisbis.trackrenderer.model.Tracker;
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

import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.extern.java.Log;

@Log
@Stateless
public class Renderer {
	
	@EJB
	Trackers trackers;
	
	@Resource(name="jdbc/trackrenderer")
	private DataSource dataSource;
	
	public BufferedImage renderAllDay ( int width, int height, Envelope e4326, int year, int month, int day, List<Integer> ids, boolean logging ) throws Exception {
		List<PointTime> pointTimes = new LinkedList<PointTime>();
		String sql = getSQLAllDay(e4326, year, month, day, ids);
		try ( Connection c = dataSource.getConnection(); Statement s = c.createStatement(); ResultSet rs = s.executeQuery(sql); ) {
			while ( rs.next() ) {
				String geomText = rs.getString("geom");
				pointTimes.add(new PointTime(rs.getInt("ids"), rs.getTimestamp("get_time"), wktReader.read(geomText)));
			}
		} catch ( SQLException e ) {
			log.info(e.getMessage()+"\nsql\n"+sql);
			throw e;
		}
		
		if ( !CollectionUtils.isEmpty(pointTimes) )
			pointTimes = 
				pointTimes.stream()
					.map(pt->
						{
							try {
								return 
									new PointTime(
											pt.id,
											pt.timestamp,	
											JTS.transform(
												pt.coordinate,
												from4326to3395()));
							} catch (Exception e) {
								e.printStackTrace();
								throw new NullPointerException("failed convert geometry "+pt.coordinate);
							}
						})
					.collect(Collectors.toList());
		
		Envelope e3395 = JTS.transform(e4326, from4326to3395());
		
		
		
		AthinesTransform at = Objects.requireNonNull( new AthinesTransform(width, height, new Coordinate(e3395.getMinX(), e3395.getMinY()), new Coordinate(e3395.getMaxX(), e3395.getMaxY())), "Failed get athines transform for screen coordinate");
		
		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g2d = image.createGraphics();
		g2d.setComposite(AlphaComposite.Clear);
		g2d.fillRect(0, 0, width, height);
		g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f));
		g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g2d.setStroke(new BasicStroke(3, BasicStroke.CAP_ROUND, BasicStroke.JOIN_MITER));
		
		GeometryFactory gf = new GeometryFactory();
		
		boolean writeLabel = RendererUtilities_.getPPM(e4326, width, height) > 3.0 ;
		Envelope e = new Envelope(0, width, 0, height);
		int pId = 0;
		PointTime ppt = null;
		LineString l = null;
		for ( PointTime pt : pointTimes ) {
			pt = new PointTime(pt.id, pt.timestamp, gf.createPoint(at.transform(pt.coordinate.getCentroid().getCoordinate())));
			//System.out.println(pt);
			if ( pt.id != pId ) {
				if ( ppt != null && writeLabel )
					drawPointTime(width, height, g2d, ppt);
				ppt = null;
				pId = pt.id;
			}
			
			Coordinate c = pt.coordinate.getCentroid().getCoordinate();
			Coordinate pc = ppt != null ? ppt.coordinate.getCentroid().getCoordinate() : null;
			
			if ( c!= null && pc != null )
				l = gf.createLineString(new Coordinate[] {c, pc});
			
			if ( l != null && e.intersects(l.getEnvelopeInternal()) ) // ( ( c.x > 0 && c.x < width && c.y > 0 && c.y < height ) || ( pc.x > 0 && pc.x < width && pc.y > 0 && pc.y < height ) ) )
				drawLine(g2d, l);
			
			if ( ppt != null && writeLabel )
				drawPointTime(width, height, g2d, ppt);
			ppt = pt;
		}
		if ( ppt != null && writeLabel )
			drawPointTime(width, height, g2d, ppt);
		//System.out.println("writeLabel = "+writeLabel);
		
		g2d.dispose();
	    
		return image;
	}
	
	private void drawLine ( Graphics2D g2d, LineString line ) {
		g2d.setPaint(Color.orange);
		Coordinate previousCoordinate = null;
		for ( int i=0; i<line.getNumPoints(); i++ ) {
			Point point = line.getPointN(i);
			Coordinate coordinate = new Coordinate(point.getX(), point.getY());
			
			if ( previousCoordinate != null ) {
				g2d.drawLine((int)previousCoordinate.x, (int)previousCoordinate.y, (int)coordinate.x, (int)coordinate.y);
				
				
				if ( Math.pow(previousCoordinate.x - coordinate.x, 2) + Math.pow(previousCoordinate.y - coordinate.y, 2) > 36 ) {
					Graphics2D g = (Graphics2D) g2d.create();
					int size = -5;
				    double dx = previousCoordinate.x - coordinate.x, dy = previousCoordinate.y - coordinate.y;
				    double angle = Math.atan2(dy, dx);
				    int len = (int) Math.sqrt(dx*dx + dy*dy);
				    AffineTransform at = AffineTransform.getTranslateInstance((coordinate.x-dx/2), (coordinate.y-dy/2));
				    at.concatenate(AffineTransform.getRotateInstance(angle));
				    g.transform(at);
				    g.fillPolygon(new int[] {len, len-size, len-size, len}, new int[] {0, -size, size, 0}, 4);
				}
			}
			previousCoordinate = coordinate;
		}
	}
	
	public void drawPointTime ( int width, int height, Graphics2D g2d, PointTime pt ) {		
		Coordinate c = pt.coordinate.getCentroid().getCoordinate();
		if ( c.x > 0 && c.x < width && c.y > 0 && c.y < height ) {
			g2d.setPaint(Color.black);
			FontMetrics fontMetrics = g2d.getFontMetrics();
			String label = 
				//""+pointTime.id+" "+
				dfOnlyTime.format(pt.timestamp);
			g2d.drawString(label, (float) ( c.x - fontMetrics.stringWidth(label) / 2.0 ) , (float) (c.y + fontMetrics.getHeight() / 4.0 ) );
		}
	}

	public BufferedImage render ( int width, int height, Envelope e4326, int year, int month, int day, List<Integer> ids ) throws Exception {
		String sql = getSQLTrack(e4326, year, month, day, ids);		
		List<Geometry> geoms = new LinkedList<Geometry>();
		try ( Connection c = dataSource.getConnection(); Statement s = c.createStatement(); ResultSet rs = s.executeQuery(sql); ) {
			while ( rs.next() ) {
				String geomText = rs.getString("geom");
				geoms.add(
					wktReader.read(geomText));
			}
		} catch ( SQLException e ) {
			log.info(e.getMessage()+"\nsql\n"+sql);
			throw e;
		}
		
		if ( !CollectionUtils.isEmpty(geoms) )
			geoms = 
				geoms.stream()
					.map(g->
						{
							try {
								return JTS.transform(
									g,
									from4326to3395());
							} catch (Exception e) {
								e.printStackTrace();
								throw new NullPointerException("failed convert geometry "+g);
							}
						})
					.collect(Collectors.toList());
		
		List<PointTime> pointTimes = new LinkedList<PointTime>();
		if ( RendererUtilities_.getPPM(e4326, width, height) > 3.0 ) {
			sql = getSQLTimePoint(e4326, year, month, day, ids);
			//log.info(sql);
			try ( Connection c = dataSource.getConnection(); Statement s = c.createStatement(); ResultSet rs = s.executeQuery(sql); ) {
				while ( rs.next() ) {
					String geomText = rs.getString("geom");
					pointTimes.add(new PointTime(rs.getInt("ids"), rs.getTimestamp("get_time"), wktReader.read(geomText)));
				}
			} catch ( SQLException e ) {
				log.info(e.getMessage()+"\nsql\n"+sql);
				throw e;
			}
			
			if ( !CollectionUtils.isEmpty(pointTimes) )
				pointTimes = 
					pointTimes.stream()
						.map(pt->
							{
								try {
									return 
										new PointTime(
												pt.id,
												pt.timestamp,	
												JTS.transform(
													pt.coordinate,
													from4326to3395()));
								} catch (Exception e) {
									e.printStackTrace();
									throw new NullPointerException("failed convert geometry "+pt.coordinate);
								}
							})
						.collect(Collectors.toList());
		}
		
		Envelope e3395 = JTS.transform(e4326, from4326to3395());
		
		
		
		AthinesTransform at = Objects.requireNonNull( new AthinesTransform(width, height, new Coordinate(e3395.getMinX(), e3395.getMinY()), new Coordinate(e3395.getMaxX(), e3395.getMaxY())), "Failed get athines transform for screen coordinate");
		
		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g2d = image.createGraphics();
		g2d.setComposite(AlphaComposite.Clear);
		g2d.fillRect(0, 0, width, height);
		g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f));
		g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		
		g2d.setStroke(new BasicStroke(3, BasicStroke.CAP_ROUND, BasicStroke.JOIN_MITER));
		
		g2d.setPaint(Color.orange);		
		for ( Geometry geom : geoms ) 
			printRecursive(g2d, geom, at, false);
		
		g2d.setPaint(Color.black);
		for ( PointTime pointTime : pointTimes )
			drawPointTime(g2d, pointTime, at);
		
		g2d.dispose();
	    
		return image;
	}
	
	private static SimpleDateFormat dfOnlyTime = new SimpleDateFormat ("HH:mm");
	
	private void drawPointTime ( Graphics2D g2d, PointTime pointTime, AthinesTransform athinesTransform ) {
		FontMetrics fontMetrics = g2d.getFontMetrics();
		Coordinate coordinate =
			athinesTransform.transform(
				pointTime.coordinate.getCentroid().getCoordinate());
		String label = 
			//""+pointTime.id+" "+
			dfOnlyTime.format(pointTime.timestamp);
		g2d.drawString(label, (float) ( coordinate.x - fontMetrics.stringWidth(label) / 2.0 ) , (float) (coordinate.y + fontMetrics.getHeight() / 4.0 ) );
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
			if ( previousCoordinate != null ) {
				g2d.drawLine((int)previousCoordinate.x, (int)previousCoordinate.y, (int)coordinate.x, (int)coordinate.y);
				
				
				if ( Math.pow(previousCoordinate.x - coordinate.x, 2) + Math.pow(previousCoordinate.y - coordinate.y, 2) > 36 ) {
					Graphics2D g = (Graphics2D) g2d.create();
					int size = -5;
				    double dx = previousCoordinate.x - coordinate.x, dy = previousCoordinate.y - coordinate.y;
				    double angle = Math.atan2(dy, dx);
				    int len = (int) Math.sqrt(dx*dx + dy*dy);
				    AffineTransform at = AffineTransform.getTranslateInstance((coordinate.x-dx/2), (coordinate.y-dy/2));
				    at.concatenate(AffineTransform.getRotateInstance(angle));
				    g.transform(at);
				    g.fillPolygon(new int[] {len, len-size, len-size, len}, new int[] {0, -size, size, 0}, 4);
				}
			}
			previousCoordinate = coordinate;
		}
	}
	
	private static MathTransform from4326to3395 = null;
	
	private static MathTransform from4326to3395() throws Exception {
		if ( from4326to3395 == null )
			from4326to3395 = CRS.findMathTransform(DefaultGeographicCRS.WGS84, CRS.decode("EPSG:3395"));
		return from4326to3395;		
	}
	
	private static GeometryFactory gf = new GeometryFactory();
	
	private static WKTWriter2 wktWriter = new WKTWriter2();
	
	private static WKTReader2 wktReader = new WKTReader2();
	
	private String getSQLAllDay ( Envelope e4326, int year, int month, int day, List<Integer> ids ) throws Exception {
		Collection<Tracker> trackersList = trackers.getTrackersList().values();
		if ( !CollectionUtils.isEmpty(ids) ) {
			trackersList =
				trackersList
					.stream()
					.filter(t->ids.contains(Integer.valueOf(t.getId())))
					.collect(Collectors.toCollection(LinkedList::new));
		}
		
		if ( CollectionUtils.isEmpty(trackersList) )
			return null;
		
		return
			"select * \n"+
			"from ( \n"+
			trackersList
				.parallelStream()
				.map(t->
						"select "+t.getId()+" as ids, get_time, concat('point(', cast(point_y as char(20)),' ',cast(point_x as char(20)), ')') as geom \n"+
						"from tracking.`"+t.getImei()+"` \n"+
						"where get_time >= STR_TO_DATE('"+day+"/"+month+"/"+year+"','%d/%m/%Y') and get_time < date_add(STR_TO_DATE('"+day+"/"+month+"/"+year+"','%d/%m/%Y'), interval 1 day)")
				.collect(Collectors.joining(" \nunion all \n"))+
			" \n) aaa\n"+
			"order by ids, get_time";
	}
	
	private String getSQLTimePoint ( Envelope e4326, int year, int month, int day, List<Integer> ids ) throws Exception {
		Collection<Tracker> trackersList = trackers.getTrackersList().values();
		if ( !CollectionUtils.isEmpty(ids) ) {
			trackersList =
				trackersList
					.stream()
					.filter(t->ids.contains(Integer.valueOf(t.getId())))
					.collect(Collectors.toCollection(LinkedList::new));
		}
		return 
			trackersList
				.parallelStream()
				.map(t->
						"select "+t.getId()+" as ids, get_time, concat('point(', cast(point_y as char(20)),' ',cast(point_x as char(20)), ')') as geom \n"+
						"from tracking.`"+t.getImei()+"` \n"+
						"where get_time >= STR_TO_DATE('"+day+"/"+month+"/"+year+"','%d/%m/%Y') and get_time < date_add(STR_TO_DATE('"+day+"/"+month+"/"+year+"','%d/%m/%Y'), interval 1 day) \n"+
						"	and point_y <= "+e4326.getMaxX()+" and point_y >= "+e4326.getMinX()+" and point_x <= "+e4326.getMaxY()+" and point_x >= "+e4326.getMinY())
				.collect(Collectors.joining(" \nunion all \n"));
	}
	
	private String getSQLTrack ( Envelope e4326, int year, int month, int day, List<Integer> ids ) throws Exception {
		Collection<Tracker> trackersList = trackers.getTrackersList().values();
		if ( !CollectionUtils.isEmpty(ids) ) {
			trackersList =
				trackersList
					.stream()
					.filter(t->ids.contains(Integer.valueOf(t.getId())))
					.collect(Collectors.toCollection(LinkedList::new));
		}
		return
			"select AsText(geom) geom \n"+
			"from ( \n"+	
			"select id, geom \n from ( \n"+
			trackersList
				.parallelStream()
				.map(t->
						"select "+t.getId()+" as id, ST_GeomFromText(concat('linestring(', group_concat(p separator ','),')'), 4326) as geom  \n"+
						"from ( \n"+
						"	select concat(cast(point_y as char(20)),' ',cast(point_x as char(20))) p \n"+
						"	from tracking.`"+t.getImei()+"` \n"+
						"	where get_time >= STR_TO_DATE('"+day+"/"+month+"/"+year+"','%d/%m/%Y') and get_time < date_add(STR_TO_DATE('"+day+"/"+month+"/"+year+"','%d/%m/%Y'), interval 1 day) \n"+
						"	order by get_time desc \n"+
						") aaa \n"+
						"having count(*) > 1")
				.collect(Collectors.joining("\nunion all\n"))+
			" \n) bbb \nwhere geom is not null"+
			" \n) ccc \n"+
			"where ST_Intersects(geom, ST_PolyFromText('"+wktWriter.write(gf.toGeometry(e4326))+"',4326)) = 1";
	}
	
	
	
}

@AllArgsConstructor
class PointTime {
	
	int id;
	
	@NonNull
	Timestamp timestamp;
	
	@NonNull
	Geometry coordinate;
	
	private static SimpleDateFormat df = new SimpleDateFormat ("dd/MM/yyyy HH:mm:ss");
	
	@Override
	public String toString() {
		return "PointTime [id = "+id+" timestamp=" + df.format(timestamp) + ", coordinate=" + coordinate + "]";
	}
}
