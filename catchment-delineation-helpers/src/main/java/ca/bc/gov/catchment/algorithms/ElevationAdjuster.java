package ca.bc.gov.catchment.algorithms;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.geotools.geometry.jts.JTSFactoryFinder;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
import org.opengis.feature.simple.SimpleFeature;

import ca.bc.gov.catchment.utils.SpatialUtils;
import ca.bc.gov.catchment.water.Water;

public class ElevationAdjuster {

	private static final double MAX_DISTANCE = 500;
	private static final int MAX_CACHE_SIZE = 500;
	
	private Water water;
	private GeometryFactory geometryFactory;
	private Map<Coordinate, Double> distanceToWaterCache;
		
	public ElevationAdjuster(Water water) {
		this.water = water;
		this.geometryFactory = JTSFactoryFinder.getGeometryFactory();
		
		distanceToWaterCache = new LinkedHashMap<Coordinate, Double>(MAX_CACHE_SIZE, .75F, true) {
		    public boolean removeEldestEntry(Map.Entry<Coordinate, Double> eldest) {
		        return size() > MAX_CACHE_SIZE;
		    }
		};
	}
	
	public double getAdjustedZ(Coordinate c) {
		return adjustZ(c).getZ();
	}
	
	public Coordinate adjustZ(Coordinate c) {
		
		Double distance = distanceToWaterCache.get(c);
		if (distance == null) {
			Point p = geometryFactory.createPoint(c);
			SimpleFeature nearestWater = water.getNearestWater(p);
			if (nearestWater == null) {
				distance = calcAdjustment(MAX_DISTANCE);
			}
			else {
				Geometry nearestGeom = (Geometry)nearestWater.getDefaultGeometry();
				distance = p.distance(nearestGeom);	
				distanceToWaterCache.put(c, distance);
			}
		}
		
		double adjustment = calcAdjustment(distance);
		double adjustedZ = c.getZ() + adjustment;
		Coordinate result = new Coordinate(c.getX(), c.getY(), adjustedZ);
		//System.out.println(c+" -> "+result + ", dist:"+distance+", adjustment:"+adjustment);
	
		return result;
	}
	
	public Geometry adjustZ(Geometry g) {
		List<Coordinate> outCoords = new ArrayList<Coordinate>();
		for(Coordinate c : g.getCoordinates()) {
			Coordinate outCoord = adjustZ(c);
			outCoords.add(outCoord);
		}
		if (g.getGeometryType().equals("LineString")) {
			return SpatialUtils.toLineString(outCoords);
		}
		else if (g.getGeometryType().equals("Point")) {
			return geometryFactory.createPoint(outCoords.get(0));
		}
		else {
			throw new IllegalArgumentException("unsupported geometry type: "+g.getGeometryType());
		}
	}
	
	private double calcAdjustment(double distance) {
		if (distance > MAX_DISTANCE) {
			distance = MAX_DISTANCE;
		}
		return 1.0/20 * distance;
	}
}
