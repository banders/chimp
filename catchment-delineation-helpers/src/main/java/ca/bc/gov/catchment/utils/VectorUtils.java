package ca.bc.gov.catchment.utils;

import org.locationtech.jts.geom.Coordinate;

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
}
