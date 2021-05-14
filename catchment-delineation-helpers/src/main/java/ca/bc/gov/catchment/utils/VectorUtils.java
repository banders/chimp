package ca.bc.gov.catchment.utils;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.LineString;

public class VectorUtils {

	public static double getTrajectory2D(Coordinate c1, Coordinate c2) {
		double opposite = c2.getY() - c1.getY();
		double adjacent = c2.getX() - c1.getX();
		double alphaDegrees = Math.toDegrees(Math.atan(opposite / adjacent));
		
		if (adjacent < 0) {
			alphaDegrees += 180;
		}
		
		alphaDegrees = toCompassAngle(alphaDegrees);
		return alphaDegrees;
	}
	
	/**
	 * if difference is positive, the trajectory has turned left.  if the difference is
	 * negative the trajectory has turned right.
	 * @param t1
	 * @param t2
	 * @return a number in range [-180,+180]
	 */
	public static double getTrajectoryDiff(double t1, double t2) {
		t1 = toCompassAngle(t1);
		t2 = toCompassAngle(t2);
		double diff = t2 - t1;
		if (diff > 180) {
			diff = diff - 360;
		}
		if (diff < -180) {
			diff = diff + 360;
		}
		return diff;
	}
	
	/**
	 * converts an angle in degrees to a number in the range [0,360].
	 * Usefo to standardize values like:
	 * 	 -45 degrees -> 315 degrees 
	 */
	public static double toCompassAngle(double angle) {
		if (angle < 0) {
			int n = (int)(-angle / 360)+1;
			return n*360 + angle;
		}
		return angle % 360;
	}
	
	/**
	 * gets the angle (in degrees) of the edge starting from 'fromCoord'.  Result is in
	 * [0-360] where 0 is east, 90 is north.
	 * @param fromCoord
	 * @param f
	 * @return
	 */
	public static double angle2D(Coordinate fromCoord, LineString edge) {
		Coordinate[] coords = edge.getCoordinates();
		if (coords.length != 2) {
			throw new IllegalArgumentException("edge must have exactly two vertices");
		}
		Coordinate firstCoord = coords[0];
		Coordinate secondCoord = coords[1];
		Coordinate toCoord = null;
		if (firstCoord.equals(fromCoord)) {
			toCoord = secondCoord;
		}
		else if (secondCoord.equals(fromCoord)) {
			toCoord = firstCoord;
		}
		else {
			throw new IllegalArgumentException("'fromCoord' must be in the edge");
		}
		
		double opposite = toCoord.getY() - fromCoord.getY();
		double adjacent = toCoord.getX() - fromCoord.getX();
		double angleDegrees = Math.toDegrees(Math.atan2(opposite, adjacent));
		if (angleDegrees < 0) {
			angleDegrees +=360;
		}
		return angleDegrees;
	}
	
	/**
	 * Creates a line of length 1 from 'fromCoord' in the direction of 'compasAngleDegrees'
	 * @param fromCoord
	 * @param compassAngleDegrees
	 * @return
	 */
	public static LineString createLineInDirection(Coordinate fromCoord, double compassAngleDegrees, double length) {
		compassAngleDegrees = compassAngleDegrees % 360;
		double angleRadians = Math.toRadians(compassAngleDegrees);
		double y = Math.sin(angleRadians) * length;
		double x = Math.cos(angleRadians) * length;
	
		Coordinate toCoord = new Coordinate(fromCoord.x+x, fromCoord.y+y);
		LineString line = SpatialUtils.toLineString(fromCoord, toCoord);
		return line;
	}
}
