package ca.bc.gov.catchment.utils;

import org.junit.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.util.Assert;

public class VectorUtilsTest {

	@Test
	public void testGetTrajectory2D_1() {
		Coordinate c1 = new Coordinate(0,0);
		Coordinate c2 = new Coordinate(1,1);
		double t = VectorUtils.getTrajectory2D(c1, c2);
		Assert.isTrue(t == 45, "trajectory expected to be 45 degrees.  found: "+t);
	}
	
	@Test
	public void testGetTrajectory2D_2() {
		Coordinate c1 = new Coordinate(0,0);
		Coordinate c2 = new Coordinate(-1,1);
		double t = VectorUtils.getTrajectory2D(c1, c2);
		Assert.isTrue(t == 135, "trajectory expected to be 135 degrees.  found: "+t);
	}
	
	@Test
	public void testGetTrajectory2D_3() {
		Coordinate c1 = new Coordinate(0,0);
		Coordinate c2 = new Coordinate(-1,-1);
		double t = VectorUtils.getTrajectory2D(c1, c2);
		Assert.isTrue(t == 225, "trajectory expected to be 225 degrees.  found: "+t);
	}
	
	@Test
	public void testGetTrajectory2D_4() {
		Coordinate c1 = new Coordinate(0,0);
		Coordinate c2 = new Coordinate(1,-1);
		double t = VectorUtils.getTrajectory2D(c1, c2);
		Assert.isTrue(t == 315, "trajectory expected to be 315 degrees.  found: "+t);
	}
	
	@Test
	public void testGetTrajectoryDiff1() {
		double t1 = 45;
		double t2 = 60;
		double diff = VectorUtils.getTrajectoryDiff(t1, t2);
		Assert.isTrue(diff == 15, "Expected 15 degrees.  Found "+diff);
	}
	
	@Test
	public void testGetTrajectoryDiff2() {
		double t1 = 60;
		double t2 = 45;
		double diff = VectorUtils.getTrajectoryDiff(t1, t2);
		Assert.isTrue(diff == -15, "Expected -15 degrees.  Found "+diff);
	}
	
	@Test
	public void testGetTrajectoryDiff3() {
		double t1 = 60;
		double t2 = 340; //aka -20
		double diff = VectorUtils.getTrajectoryDiff(t1, t2);
		Assert.isTrue(diff == -80, "Expected -80 degrees.  Found "+diff);
	}
	
	@Test
	public void testGetTrajectoryDiff4() {
		double t1 = 115;
		double t2 = 270; 
		double diff = VectorUtils.getTrajectoryDiff(t1, t2);
		Assert.isTrue(diff == 155, "Expected 155 degrees.  Found "+diff);
	}
	
	@Test
	public void testGetTrajectoryDiff5() {
		double t1 = 330;
		double t2 = 90; 
		double diff = VectorUtils.getTrajectoryDiff(t1, t2);
		Assert.isTrue(diff == 120, "Expected 120 degrees.  Found "+diff);
	}
	
	@Test
	public void testToCompassAngle1() {
		double alpha = VectorUtils.toCompassAngle(-45);
		Assert.isTrue(alpha == 315, "alpha expected to be 315 degrees.  found: "+alpha);
	}
	
	@Test
	public void testCreateLineInDirection() {

		double allowableError = 0.00001;
		
		Coordinate fromCoord = new Coordinate(0,0);

		double angle1 = Math.toDegrees(Math.atan(4.0/3.0)); //53.1 degrees
		LineString line1 = VectorUtils.createLineInDirection(fromCoord, angle1, 5);
		Coordinate toCoord1 = line1.getCoordinateN(1);		
		Assert.isTrue(line1.getLength() == 5, "line length expected to be 5.  found "+line1.getLength());
		Assert.isTrue(Math.abs(toCoord1.x - 3) < allowableError && Math.abs(toCoord1.y - 4) < allowableError, "'toCoord' expected to be (3, 4). found "+toCoord1.x+","+toCoord1.y);
		
		double angle2 = 180 - Math.toDegrees(Math.atan(4.0/3.0)); //126.9 degrees
		LineString line2 = VectorUtils.createLineInDirection(fromCoord, angle2, 5);
		Coordinate toCoord2 = line2.getCoordinateN(1);		
		Assert.isTrue(line2.getLength() == 5, "line length expected to be 5.  found "+line2.getLength());
		Assert.isTrue(Math.abs(toCoord2.x - -3) < allowableError && Math.abs(toCoord2.y - 4) < allowableError, "'toCoord' expected to be (-3, 4). found "+toCoord2.x+","+toCoord2.y);
		
		double angle3 = 180 + Math.toDegrees(Math.atan(4.0/3.0)); 
		LineString line3 = VectorUtils.createLineInDirection(fromCoord, angle3, 5);
		Coordinate toCoord3 = line3.getCoordinateN(1);
		Assert.isTrue(line3.getLength() == 5, "line length expected to be 5.  found "+line3.getLength());
		Assert.isTrue(Math.abs(toCoord3.x - -3) < allowableError && Math.abs(toCoord3.y - -4) < allowableError, "'toCoord' expected to be (-3, -4). found "+toCoord3.x+","+toCoord3.y);
		
		double angle4 = 360 - Math.toDegrees(Math.atan(4.0/3.0)); 
		LineString line4 = VectorUtils.createLineInDirection(fromCoord, angle4, 5);
		Coordinate toCoord4 = line4.getCoordinateN(1);		
		Assert.isTrue(line4.getLength() == 5, "line length expected to be 5.  found "+line4.getLength());
		Assert.isTrue(Math.abs(toCoord4.x - 3) < allowableError && Math.abs(toCoord4.y - -4) < allowableError, "'toCoord' expected to be (3, -4). found "+toCoord4.x+","+toCoord4.y);
	}
}
