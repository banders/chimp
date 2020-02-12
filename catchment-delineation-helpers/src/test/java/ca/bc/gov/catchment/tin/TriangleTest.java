package ca.bc.gov.catchment.tin;

import org.junit.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.util.Assert;

import ca.bc.gov.catchment.utils.SpatialUtils;

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
		Assert.isTrue(aspect > 90 && aspect < 180, "Expected aspect in range 90 to 180.  Found aspect="+aspect);
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
	
	@Test
	public void testGetSlopeRelativeToBaseEdge1() {
		Coordinate c1 = new Coordinate(0, 0, 100);
		Coordinate c2 = new Coordinate(10, 0, 100);
		Edge baseEdge = new Edge(c1, c2);
		
		//slopes up from base
		Triangle t1 = new Triangle(
				c1,
				c2,
				new Coordinate(5, 5, 105)
			);		
		double slope1 = t1.getSlopeRelativeToBaseEdge(baseEdge);
		Assert.isTrue(slope1 > 0 && slope1 < 90, "slope1 was "+slope1+", but expected to be positive (0 to 90) when 'upward from base'");

		//slopes up from base
		Triangle t2 = new Triangle(
				c1,
				c2,
				new Coordinate(5, -5, 105)
			);
		double slope2 = t2.getSlopeRelativeToBaseEdge(baseEdge);
		Assert.isTrue(slope2 > 0 && slope2 < 90, "slope2 was "+slope2+", but expected to be positive (0 to 90) when 'upward from base'");

	}
	
	@Test
	public void testGetSlopeRelativeToBaseEdge2() {
		Coordinate c1 = new Coordinate(0, 0, 100);
		Coordinate c2 = new Coordinate(10, 0, 100);
		Edge baseEdge = new Edge(c1, c2);
		
		//slopes up from base
		Triangle t1 = new Triangle(
				c1,
				c2,
				new Coordinate(3, 5, 110)
			);		
		double slope1 = t1.getSlopeRelativeToBaseEdge(baseEdge);
		Assert.isTrue(slope1 > 0 && slope1 < 90, "slope1 was "+slope1+", but expected to be positive (0 to 90) when 'upward from base'");

		//slopes up from base
		Triangle t2 = new Triangle(
				c1,
				c2,
				new Coordinate(8, -20, 150)
			);
		double slope2 = t2.getSlopeRelativeToBaseEdge(baseEdge);
		Assert.isTrue(slope2 > 0 && slope2 < 90, "slope2 was "+slope2+", but expected to be positive (0 to 90) when 'upward from base'");

	}
	
	@Test
	public void testGetSlopeRelativeToBaseEdge3() {
		Coordinate c1 = new Coordinate(0, 0, 100);
		Coordinate c2 = new Coordinate(10, 0, 100);
		Edge baseEdge = new Edge(c1, c2);
		
		//slopes up from base
		Triangle t1 = new Triangle(
				c1,
				c2,
				new Coordinate(5, 5, 95)
			);		
		double slope1 = t1.getSlopeRelativeToBaseEdge(baseEdge);
		Assert.isTrue(slope1 < 0 && slope1 > -90, "slope1 was "+slope1+", but expected to be negative (-90 to 0) when 'downward from base'");

		//slopes up from base
		Triangle t2 = new Triangle(
				c1,
				c2,
				new Coordinate(5, -5, 95)
			);
		double slope2 = t2.getSlopeRelativeToBaseEdge(baseEdge);
		Assert.isTrue(slope2 < 0 && slope2 > -90, "slope2 was "+slope2+", but expected to be negative (-90 to 0) when 'downward from base'");

	}
	
	@Test
	public void testGetSlopeRelativeToBaseEdge4() {
		Coordinate c1 = new Coordinate(0, 0, 100);
		Coordinate c2 = new Coordinate(10, 0, 100);
		Edge baseEdge = new Edge(c1, c2);
		
		//slopes up from base
		Triangle t1 = new Triangle(
				c1,
				c2,
				new Coordinate(4, 5, 90)
			);		
		double slope1 = t1.getSlopeRelativeToBaseEdge(baseEdge);
		Assert.isTrue(slope1 < 0 && slope1 > -90, "slope1 was "+slope1+", but expected to be negative (-90 to 0) when 'downward from base'");

		//slopes up from base
		Triangle t2 = new Triangle(
				c1,
				c2,
				new Coordinate(2, -5, 40)
			);
		double slope2 = t2.getSlopeRelativeToBaseEdge(baseEdge);
		Assert.isTrue(slope2 < 0 && slope2 > -90, "slope2 was "+slope2+", but expected to be negative (-90 to 0) when 'downward from base'");

	}
	
	@Test
	public void testGetSlopeRelativeToBaseEdge5() {
		Coordinate c1 = new Coordinate(1677207.19999999995343387, 502077.29999999998835847, 945.81418527804578389);
		Coordinate c2 = new Coordinate(1677200.23452296736650169, 502073.10519546363502741, 949);
		Coordinate c3 = new Coordinate(1677170.35000000009313226, 502045.5, 960.04651157794705796);
		Edge baseEdge1 = new Edge(c1, c2);
		Edge baseEdge2 = new Edge(c2, c3);
		Edge baseEdge3 = new Edge(c1, c3);
		
		//slopes down from base
		Triangle t1 = new Triangle(
				c1,
				c2,
				c3
			);	
		
		double slope1 = t1.getSlopeRelativeToBaseEdge(baseEdge1);
		Assert.isTrue(slope1 < 0 && slope1 > -90, "slope1 was "+slope1+", but expected to be negative (-90 to 0) when 'downward from base'");
		
		double slope2 = t1.getSlopeRelativeToBaseEdge(baseEdge2);
		Assert.isTrue(slope2 < 0 && slope2 > -90, "slope2 was "+slope2+", but expected to be negative (-90 to 0) when 'downward from base'");

		double slope3 = t1.getSlopeRelativeToBaseEdge(baseEdge3);
		Assert.isTrue(slope3 > 0 && slope3 < 90, "slope3 was "+slope3+", but expected to be positive (0 to 90) when 'upward from base'");

	}
	
	@Test
	public void testGetSlopeRelativeToBaseEdge6() {
		
		/**
		 * it's slightly non-intuitive, but the slope relative to baseEdge1 is positive.  Although c3
		 * is between than c1 and c2, it is much closer to c1 (the high value on the base edge).  That results in
		 * a slightly positive upward slope
		 */
		
		Coordinate c1 = new Coordinate(1677033.6294514974579215, 500693.713930269703269, 1575);
		Coordinate c2 = new Coordinate(1677018.37873886618763208, 500773.36331682838499546, 1537);
		Coordinate c3 = new Coordinate(1677088.89409003173932433, 500743.10645042266696692, 1571);
		Edge baseEdge1 = new Edge(c1, c2);
		Edge baseEdge2 = new Edge(c2, c3);
		Edge baseEdge3 = new Edge(c1, c3);
		
		//slopes down from base
		Triangle t1 = new Triangle(
				c1,
				c2,
				c3
			);	
		
		double slope1 = t1.getSlopeRelativeToBaseEdge(baseEdge1);
		Assert.isTrue(slope1 > 0 && slope1 < 90, "slope1 was "+slope1+", but expected to be positive (0 to 90) when 'upward from base'");
		
		double slope2 = t1.getSlopeRelativeToBaseEdge(baseEdge2);
		Assert.isTrue(slope2 > 0 && slope2 < 90, "slope2 was "+slope2+", but expected to be positive (0 to 90) when 'downward from base'");

		double slope3 = t1.getSlopeRelativeToBaseEdge(baseEdge3);
		Assert.isTrue(slope3 < 0 && slope3 > -90, "slope3 was "+slope3+", but expected to be negative (-90 to 0) when 'downward from base'");

	}
}
