package ca.bc.gov.catchment.utils;

import org.geotools.geometry.jts.JTSFactoryFinder;
import org.junit.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.util.Assert;

import ca.bc.gov.catchment.synthetic.TestHelper;

public class SpatialUtilsTest {

	
	@Test
	public void testGetSlope1() {

		GeometryFactory geometryFactory = JTSFactoryFinder.getGeometryFactory();
		
		Coordinate[] coords = {
           new Coordinate(0, 0, 0),
           new Coordinate(0, 1, 4)
		};
		LineString s = geometryFactory.createLineString(coords);
		double slope = SpatialUtils.getSlope(s);
		Assert.isTrue(slope == 4, "slope expected to be 4. found "+slope);
		
	}
	
	@Test
	public void testGetSlope2() {

		GeometryFactory geometryFactory = JTSFactoryFinder.getGeometryFactory();
		
		Coordinate[] coords = {
           new Coordinate(0, 0, 0),
           new Coordinate(0, 1, -4)
		};
		LineString s = geometryFactory.createLineString(coords);
		double slope = SpatialUtils.getSlope(s);
		Assert.isTrue(slope == -4, "slope expected to be -4. found "+slope);
		
	}
	
	@Test
	public void testGetSlope3() {

		GeometryFactory geometryFactory = JTSFactoryFinder.getGeometryFactory();
		
		Coordinate[] coords = {
           new Coordinate(0, 0, 0),
           new Coordinate(0, -1, 4)
		};
		LineString s = geometryFactory.createLineString(coords);
		double slope = SpatialUtils.getSlope(s);
		Assert.isTrue(slope == 4, "slope expected to be 4. found "+slope);
		
	}
	
	@Test
	public void testGetSlope4() {

		GeometryFactory geometryFactory = JTSFactoryFinder.getGeometryFactory();
		
		Coordinate[] coords = {
           new Coordinate(0, 0, 0),
           new Coordinate(0, 0.5, 0), //this coordinate is expected to be irrelevant to the result
           new Coordinate(0, 2, 4)
		};
		LineString s = geometryFactory.createLineString(coords);
		double slope = SpatialUtils.getSlope(s);
		Assert.isTrue(slope == 2, "slope expected to be 2. found "+slope);
		
	}
	
	@Test
	public void testGetOutwardNormalCompassAngle1() throws ParseException {
		
		//polygon vertices are clockwise
		Polygon poly = (Polygon)TestHelper.geometryFromWkt("Polygon ((0 0, 0 1, 0 2, 1 2, 2 2, 2 1, 2 0, 1 0, 0 0))");
		
		Coordinate c1 = new Coordinate (0, 1);
		double angle1 = SpatialUtils.getOutwardNormalCompassAngle(c1, poly);
		Assert.isTrue(angle1 == 180, "angle expected to be 180.  found "+angle1);
		
		Coordinate c2 = new Coordinate (1, 2);
		double angle2 = SpatialUtils.getOutwardNormalCompassAngle(c2, poly);
		Assert.isTrue(angle2 == 90, "angle expected to be 90.  found "+angle2);
		
		Coordinate c3 = new Coordinate (2, 1);
		double angle3 = SpatialUtils.getOutwardNormalCompassAngle(c3, poly);
		Assert.isTrue(angle3 == 0, "angle expected to be 0.  found "+angle3);
		
		Coordinate c4 = new Coordinate (1, 0);
		double angle4 = SpatialUtils.getOutwardNormalCompassAngle(c4, poly);
		Assert.isTrue(angle4 == 270, "angle expected to be 270.  found "+angle4);		
		
		//top right corner of poly should have normal angle point 45 degrees (up and right)
		Coordinate c5 = new Coordinate (2, 2);
		double angle5 = SpatialUtils.getOutwardNormalCompassAngle(c5, poly);
		Assert.isTrue(angle5 == 45, "angle expected to be 45.  found "+angle5);		
	}
	
	@Test
	/**
	 * This is mostly a duplicate of 'testGetOutwardNormalCompassAngle1', except the 
	 * polygon is defined in the reserve direction
	 * @throws ParseException
	 */
	public void testGetOutwardNormalCompassAngle2() throws ParseException {
		
		//polygon vertices are counter-clockwise
		Polygon poly = (Polygon)TestHelper.geometryFromWkt("Polygon ((0 0, 1 0, 2 0, 2 1, 2 2, 1 2, 0 2, 0 1, 0 0))");
		
		Coordinate c1 = new Coordinate (0, 1);
		double angle1 = SpatialUtils.getOutwardNormalCompassAngle(c1, poly);
		Assert.isTrue(angle1 == 180, "angle expected to be 180.  found "+angle1);
		
		Coordinate c2 = new Coordinate (1, 2);
		double angle2 = SpatialUtils.getOutwardNormalCompassAngle(c2, poly);
		Assert.isTrue(angle2 == 90, "angle expected to be 90.  found "+angle2);
		
		Coordinate c3 = new Coordinate (2, 1);
		double angle3 = SpatialUtils.getOutwardNormalCompassAngle(c3, poly);
		Assert.isTrue(angle3 == 0, "angle expected to be 0.  found "+angle3);
		
		Coordinate c4 = new Coordinate (1, 0);
		double angle4 = SpatialUtils.getOutwardNormalCompassAngle(c4, poly);
		Assert.isTrue(angle4 == 270, "angle expected to be 270.  found "+angle4);		
		
		//top right corner of poly should have normal angle point 45 degrees (up and right)
		Coordinate c5 = new Coordinate (2, 2);
		double angle5 = SpatialUtils.getOutwardNormalCompassAngle(c5, poly);
		Assert.isTrue(angle5 == 45, "angle expected to be 45.  found "+angle5);		
	}
}


