package ca.bc.gov.catchment.utils;

import org.junit.Test;
import org.locationtech.jts.geom.Coordinate;
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
}
