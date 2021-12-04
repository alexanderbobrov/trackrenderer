package org.geotools.renderer.lite;

import org.geotools.referencing.GeodeticCalculator;
import org.geotools.referencing.crs.DefaultGeographicCRS;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryCollection;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.Polygon;

public final class RendererUtilities_ {
	
	/** Сколько в среднем пикселей на метр */
	public static double getPPM ( Envelope env, int width, int height ) {
		return  Math.pow(Math.pow(width,2) + Math.pow(height,2), 0.5) / geodeticDiagonalDistance(env);
	}
	
	public static double geodeticDiagonalDistance ( Envelope env ) {
		if (env.getWidth() < 180 && env.getHeight() < 180) {
			return getGeodeticSegmentLength(env.getMinX(), env.getMinY(), env.getMaxX(), env.getMaxY());
		} else {
            // we cannot compute geodetic distance for distances longer than a hemisphere,
            // we have to build a set of lines connecting the two points that are smaller to
            // get a value that makes any sense rendering wise by crossing the original line with
            // a set of quadrants that are 180x180
            double distance = 0;
            GeometryFactory gf = new GeometryFactory();
            LineString ls =
                    gf.createLineString(
                            new Coordinate[] {
                                new Coordinate(env.getMinX(), env.getMinY()),
                                new Coordinate(env.getMaxX(), env.getMaxY())
                            });
            int qMinX = -1;
            int qMaxX = 1;
            int qMinY = -1;
            int qMaxY = 1;
            // we must consider at least a pair of quadrants in each direction other wise lines
            // which don't cross both the equator and prime meridian are
            // measured as 0 length. But for some cases we need to consider still more hemispheres.
            qMinX =
                    Math.min(
                            qMinX,
                            (int)
                                    (Math.signum(env.getMinX())
                                            * Math.ceil(Math.abs(env.getMinX() / 180.0))));
            qMaxX =
                    Math.max(
                            qMaxX,
                            (int)
                                    (Math.signum(env.getMaxX())
                                            * Math.ceil(Math.abs(env.getMaxX() / 180.0))));
            qMinY =
                    Math.min(
                            qMinY,
                            (int)
                                    (Math.signum(env.getMinY())
                                            * Math.ceil(Math.abs((env.getMinY() + 90) / 180.0))));
            qMaxY =
                    Math.max(
                            qMaxY,
                            (int)
                                    (Math.signum(env.getMaxY())
                                            * Math.ceil(Math.abs((env.getMaxY() + 90) / 180.0))));
            for (int i = qMinX; i < qMaxX; i++) {
                for (int j = qMinY; j < qMaxY; j++) {
                    double minX = i * 180.0;
                    double minY = j * 180.0 - 90;
                    double maxX = minX + 180;
                    double maxY = minY + 180;
                    LinearRing ring =
                            gf.createLinearRing(
                                    new Coordinate[] {
                                        new Coordinate(minX, minY),
                                        new Coordinate(minX, maxY),
                                        new Coordinate(maxX, maxY),
                                        new Coordinate(maxX, minY),
                                        new Coordinate(minX, minY)
                                    });
                    Polygon p = gf.createPolygon(ring, null);
                    Geometry intersection = p.intersection(ls);
                    if (!intersection.isEmpty()) {
                        if (intersection instanceof LineString) {
                            LineString ils = ((LineString) intersection);
                            double d = getGeodeticSegmentLength(ils);
                            distance += d;
                        } else if (intersection instanceof GeometryCollection) {
                            GeometryCollection igc = ((GeometryCollection) intersection);
                            for (int k = 0; k < igc.getNumGeometries(); k++) {
                                Geometry child = igc.getGeometryN(k);
                                if (child instanceof LineString) {
                                    double d = getGeodeticSegmentLength((LineString) child);
                                    distance += d;
                                }
                            }
                        }
                    }
                }
            }

            return distance;
        }
    }
	
	public static double getGeodeticSegmentLength(LineString ls) {
        Coordinate start = ls.getCoordinateN(0);
        Coordinate end = ls.getCoordinateN(1);
        return getGeodeticSegmentLength(start.x, start.y, end.x, end.y);
    }
	
	public static double getGeodeticSegmentLength(
            double minx, double miny, double maxx, double maxy) {
        final GeodeticCalculator calculator = new GeodeticCalculator(DefaultGeographicCRS.WGS84);
        double rminx = rollLongitude(minx);
        double rminy = rollLatitude(miny);
        double rmaxx = rollLongitude(maxx);
        double rmaxy = rollLatitude(maxy);
        calculator.setStartingGeographicPoint(rminx, rminy);
        calculator.setDestinationGeographicPoint(rmaxx, rmaxy);
        return calculator.getOrthodromicDistance();
    }
	
	public static double rollLongitude(final double x) {
        double rolled = x - (((int) (x + Math.signum(x) * 180)) / 360) * 360.0;
        return rolled;
    }

    public static double rollLatitude(final double x) {
        double rolled = x - (((int) (x + Math.signum(x) * 90)) / 180) * 180.0;
        return rolled;
    }
	
}