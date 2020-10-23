package ca.bc.gov.catchment.algorithms;

import java.util.List;

import org.geotools.geometry.jts.JTSFactoryFinder;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;

public class ElevationEstimator {

	private GeometryFactory geometryFactory;
	
	public ElevationEstimator() {
		this.geometryFactory = JTSFactoryFinder.getGeometryFactory();
	}
	
	public double estimateElevationFromNearbyPoints(Coordinate c, List<Coordinate> nearbyCoords) {
		Point p = geometryFactory.createPoint(c);
		
		//get distance sum of nearest points
		double denom = 0;
		for(Coordinate nearbyCoord : nearbyCoords) {
			Point nearbyPoint = geometryFactory.createPoint(nearbyCoord);
			denom += 1/nearbyPoint.distance(p);
		}
		
		//calc weighted average elevation
		double weightedAverageElevation = 0;
		for(Coordinate nearbyCoord : nearbyCoords) {
			Point nearbyPoint = geometryFactory.createPoint(nearbyCoord);
			double z = nearbyCoord.getZ();
			double dist = nearbyPoint.distance(p);
			
			//if a nearbyPoint is exactly on top of the given point, don't attempt to find a 
			//weighted average of multiple nearby points.  instead just use the value from the single 
			//point at the same location
			if (dist == 0) {
				return z;
			}
			double numerator = z/dist;
			double thisVal = numerator/denom;
			//System.out.println(" elevation:"+z+", dist:"+dist);
			weightedAverageElevation += thisVal;
		}
		
		return weightedAverageElevation;
	}
}
