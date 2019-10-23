package ca.bc.gov.catchment.tin;

import org.junit.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.util.Assert;

public class TriangleTest {

	@Test
	public void testGetOrthoCenterA() {
		Triangle t = new Triangle(
			new Coordinate(0,0),
			new Coordinate(3,6),
			new Coordinate(6,0)
		);
		Coordinate orthoCenter = t.getOrthoCenter2D();
		System.out.println(orthoCenter);
		Assert.isTrue(orthoCenter.getX() == 3 && orthoCenter.getY() == 1.5, "Incorrect orthocenter");
	}
	
	@Test
	public void testAspect1() {
		
		//triangle with aspect due north (+90 degrees)
		Triangle t = new Triangle(
			new Coordinate(0, 0, 10),
			new Coordinate(5, 0, 10),
			new Coordinate(3, 3, 9)
		);
		double aspect = t.getAspect();
		Assert.isTrue(aspect == 90, "Expected aspect=90.  Found aspect="+aspect);
	}
	
	@Test
	public void testAspect2() {
		
		//triangle with aspect due south (-90 degrees or +270 degrees)
		Triangle t = new Triangle(
			new Coordinate(0, 0, 10),
			new Coordinate(5, 0, 10),
			new Coordinate(3, -3, 9)
		);
		double aspect = t.getAspect();
		Assert.isTrue(aspect == -90, "Expected aspect=-90.  Found aspect="+aspect);
	}
	
	@Test
	public void testAspect3() {

		//triangle with aspect somewhere in quadrant 2 (90-180 degrees)
		Triangle t = new Triangle(
			new Coordinate(0, 0, 10),
			new Coordinate(5, 0, 10.1),
			new Coordinate(2, 4, 5)
		);
		double aspect = t.getAspect();
		Assert.isTrue(aspect > 90, "Expected aspect > 90.  Found aspect="+aspect);
	}
	
	@Test
	public void testAspect4() {
		
		//triangle with aspect due east (0 degrees)
		Triangle t = new Triangle(
			new Coordinate(0, 0, 10),
			new Coordinate(5, 0, 9),
			new Coordinate(5, 5, 9)
		);
		double aspect = t.getAspect();
		Assert.isTrue(aspect == 0, "Expected aspect=0.  Found aspect="+aspect);
	}
	
	@Test
	public void testEqual() {
		Triangle t1 = new Triangle(
				new Coordinate(0, 0, 10),
				new Coordinate(5, 0, 9),
				new Coordinate(5, 5, 9)
			);
		Triangle t2 = new Triangle(
				new Coordinate(0, 0, 10),
				new Coordinate(5, 0, 9),
				new Coordinate(5, 5, 9)
			);
		Triangle t3 = new Triangle(
				new Coordinate(0, 0, 10),
				new Coordinate(5, 5, 9),
				new Coordinate(5, 0, 9)
				
			);
		Assert.isTrue(t1.equals(t2), "expected triangles to be equal");
		Assert.isTrue(t1.equals(t3), "expected triangles to be equal");
	}
}
