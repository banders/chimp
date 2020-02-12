package ca.bc.gov.catchment.algorithms;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;
import org.opengis.feature.simple.SimpleFeature;

import ca.bc.gov.catchment.utils.SpatialUtils;
import ca.bc.gov.catchment.utils.VectorUtils;

public class SinuosityQuantifier {

	public SinuosityQuantifier() {
		
	}
	
	/**
	 * returns a number >= 1.  1 means the line is straight (no sinuosity) and higher values mean more sinuos.
	 * @param f
	 * @return
	 */
	public double getSinuosity(SimpleFeature f) {
		Geometry g = (Geometry)f.getDefaultGeometry();
		return getSinuosity(g);
	}
	
	public double getSinuosity(Geometry g) {
		Coordinate[] coords = g.getCoordinates();
		if (coords.length < 2) {
			return 1;
		}
		Coordinate first = coords[0];
		Coordinate last = coords[coords.length-1];
		LineString shortestPath = SpatialUtils.toLineString(first, last);
		
		double sinuosity = g.getLength() / shortestPath.getLength();
		return sinuosity;
	}
	
	public double getSinuosityOld(Geometry g) {
		if (g.getNumPoints() < 3) {
			return 0;
		}
		double left = 0;
		double right = 0;
		int numLeft = 0;
		int numRight = 0;
		
		Coordinate prevC = null;
		double prevTrajectory = Double.NaN;
		for (Coordinate c : g.getCoordinates()) {
			if (prevC != null) {
				double trajectory = VectorUtils.getTrajectory2D(prevC, c);
				double trajectoryDiff = VectorUtils.getTrajectoryDiff(prevTrajectory, trajectory);
				if (!Double.isNaN(prevTrajectory)) {
					if (trajectoryDiff < 0) {
						left += Math.abs(trajectoryDiff);
						numLeft++;
					}
					else if (trajectoryDiff > 0) {
						right += Math.abs(trajectoryDiff);
						numRight++;
					}
				}
				
				prevTrajectory = trajectory;
			}
			prevC = c;
		}
		
		int numDirections = 0;
		double fractionLeft = 0;
		if (numLeft > 0) {
			double avgLeft = left/numLeft;
			fractionLeft = avgLeft/180; //in [0-1]
			numDirections++;
		}
		
		double fractionRight = 0;
		if (numRight > 0) {
			double avgRight = right/numRight;
			fractionRight = avgRight/180; //in [0-1]
			numDirections++;
		}
		
		if (numDirections == 0) {
			return 0;
		}
		
		double s = (fractionRight + fractionLeft) / numDirections; //in [0-1]
		return s;
	}
	
	
}
