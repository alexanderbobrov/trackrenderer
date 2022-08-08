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
		if ( ( trackersList == null ) || ( System.currentTimeMillis() - lastUpdateTime > 60000 ) ) { // Если 
			trackerSemaphore.acquire();
			try {
				if ( ( trackersList == null ) || ( System.currentTimeMillis() - lastUpdateTime > 60000 ) ) {
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
			"	sources.source_id as id, \n"+
			"	sources.source_imei as imei, \n"+
			"	sources.phone as phone, \n"+
			"	(select objects.label as name from google.objects where objects.source_id = sources.source_id limit 1) as name \n"+
			"from google.sources";
	
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
				//log.info(""+tracker);
				result.put(tracker.getId(), tracker);
			}
		} catch ( SQLException e ) {
			log.info(e.getMessage()+"\nsql\n"+sqlExtractTrackers);
			throw e;
		}
		
		return result;
	}
	
}
