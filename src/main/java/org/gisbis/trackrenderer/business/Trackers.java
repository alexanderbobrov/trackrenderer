package org.gisbis.trackrenderer.business;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Semaphore;

import javax.annotation.Resource;
import javax.ejb.Stateless;
import javax.sql.DataSource;

import org.gisbis.trackrenderer.model.Tracker;

import lombok.extern.java.Log;

@Log
@Stateless
public class Trackers {
	
	@Resource(name="jdbc/trackrenderer")
	private DataSource dataSource;
	
	private static Map<Integer, Tracker> trackersList = null;
	
	/** Время последнего считывания  */
	private static long lastUpdateTime = System.currentTimeMillis();
	
	private static Semaphore trackerSemaphore = new Semaphore(1);
	
	public Map<Integer, Tracker> getTrackersList ( ) throws Exception {
		if ( trackersList == null || System.currentTimeMillis() - lastUpdateTime > 60000 ) { // Если 
			trackerSemaphore.acquire();
			try {
				if ( trackersList == null || System.currentTimeMillis() - lastUpdateTime > 60000 ) {
					trackersList = getTrackersListFromBase();
					lastUpdateTime = System.currentTimeMillis();
				}
			} finally {
				trackerSemaphore.release();
			}
		}
		
		return trackersList;
	}
	
	private static String sqlExtractTrackers =
			"select \n"+
			"	id, imei, phone, name \n"+
			"from ( \n"+
			"	select \n"+
			"		sources.source_id as id, \n"+
			"		sources.source_imei as imei, \n"+
			"		sources.phone as phone, \n"+
			"		objects.label as name, \n"+
			"		row_number() over ( partition by objects.source_id ) rn \n"+
			"	from google.sources \n"+
			"	left join google.objects on objects.source_id = sources.source_id \n"+
			") aaa \n"+
			"where rn = 1";
	
	private Map<Integer, Tracker> getTrackersListFromBase ( ) throws Exception {
		HashMap<Integer, Tracker> result = new HashMap<Integer, Tracker>();
		
		try ( Connection c = dataSource.getConnection(); Statement s = c.createStatement(); ResultSet rs = s.executeQuery(sqlExtractTrackers); ) {
			while ( rs.next() ) {
				Tracker tracker =				
					new Tracker(
							rs.getInt("id"), 
							rs.getString("imei"), 
							rs.getString("phone"), 
							rs.getString("name"));
				result.put(tracker.getId(), tracker);
			}
		} catch ( SQLException e ) {
			log.info(e.getMessage()+"\nsql\n"+sqlExtractTrackers);
			throw e;
		}
		
		return result;
	}
	
}
