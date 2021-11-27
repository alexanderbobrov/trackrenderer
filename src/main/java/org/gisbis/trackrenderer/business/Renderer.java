package org.gisbis.trackrenderer.business;

import java.awt.image.BufferedImage;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.stream.Collectors;

import javax.annotation.Resource;
import javax.ejb.EJB;
import javax.ejb.Stateless;
import javax.sql.DataSource;

import org.geotools.geometry.jts.WKTWriter2;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.GeometryFactory;

import lombok.extern.java.Log;

@Log
@Stateless
public class Renderer {
	
	@EJB
	Trackers trackers;
	
	@Resource(name="jdbc/trackrenderer")
	private DataSource dataSource;

	public BufferedImage render ( int width, int height, Envelope e4326, int year, int month, int day ) throws Exception {
		String bboxGeom = wktWriter.write(gf.toGeometry(e4326));
		
		String sql =
			"select geom \n"+
			"from ( \n"+
			getSQL(year, month, day)+
			"\n) ccc \n"+
			"where ST_Intersects(geom, ST_GeomFromText('"+bboxGeom+"',4326)) = 1";
		
		try ( Connection c = dataSource.getConnection(); Statement s = c.createStatement(); ResultSet rs = s.executeQuery(sql); ) {
			while ( rs.next() ) {
				
			}
		} catch ( SQLException e ) {
			log.info(e.getMessage()+"\nsql\n"+sql);
			throw e;
		}
		
		return null;
	}
	
	private static GeometryFactory gf = new GeometryFactory();
	
	private static WKTWriter2 wktWriter = new WKTWriter2();
	
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
