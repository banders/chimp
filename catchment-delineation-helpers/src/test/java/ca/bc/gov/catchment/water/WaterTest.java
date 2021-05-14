package ca.bc.gov.catchment.water;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.geometry.jts.JTSFactoryFinder;
import org.junit.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.util.Assert;

import ca.bc.gov.catchment.fitness.CatchmentValidity;
import ca.bc.gov.catchment.routes.RouteException;
import ca.bc.gov.catchment.synthetic.DummyFactory;
import ca.bc.gov.catchment.synthetic.TestHelper;

public class WaterTest {

	private GeometryFactory geometryFactory;
	
	public WaterTest() throws IOException, RouteException {
		this.geometryFactory = JTSFactoryFinder.getGeometryFactory();
	}
	
	@Test
	public void testIsConfluence() throws IOException, RouteException {
		SimpleFeatureSource waterFeatures = DummyFactory.createDummyWaterFeatures();
		Water waterAnalyzer = new Water(waterFeatures);
		
		//is confluence
		Assert.isTrue(waterAnalyzer.isConfluence(DummyFactory.RIVER_CONFLUENCE), "coordinate expected to be identified as a confluence point");
		
		//not confluence
		Assert.isTrue(!waterAnalyzer.isConfluence(DummyFactory.RIVER_MAIN_END), "coordinate expected not to be identified as a confluence point");
		Assert.isTrue(!waterAnalyzer.isConfluence(DummyFactory.RIVER_UPSTREAM_1_START), "coordinate expected not to be identified as a confluence point");
		Assert.isTrue(!waterAnalyzer.isConfluence(new Coordinate(0, 0, 10)), "coordinate expected not to be identified as a confluence point");
	}
	
	@Test
	public void testIsTouchingWater1() throws IOException, ParseException {
		
		LineString water1 = (LineString)TestHelper.geometryFromWkt("LineString (1674102.60000000009313226 501149.40000000002328306, 1674064.30000000004656613 501149.59999999997671694, 1673986.10000000009313226 501161.70000000001164153, 1673944.60000000009313226 501162.40000000002328306, 1673881.5 501173.5, 1673830.5 501178.09999999997671694, 1673785.19999999995343387 501184.29999999998835847, 1673736.39999999990686774 501194.29999999998835847, 1673694.60000000009313226 501197.90000000002328306, 1673629.5 501194.59999999997671694, 1673557.39999999990686774 501181.29999999998835847, 1673535.60000000009313226 501178.59999999997671694)");
		List<LineString> water = new ArrayList<LineString>();
		water.add(water1);
		SimpleFeatureSource waterFeatures = TestHelper.createLineStringFeatureSource(water, "water");
		Water waterAnalyzer = new Water(waterFeatures);
		
	//	LineString catchmentLine = (LineString)TestHelper.geometryFromWkt("LineStringZ (1674197.69999999995343387 501236.09999999997671694 nan, 1674172.5 501222.90000000002328306 1158.5501708984375, 1674136.5 501198.90000000002328306 1175.8553466796875, 1674102.19999999995343387 501199.70000000001164153 1200.0289306640625, 1674068.30000000004656613 501200.5 1218.9542236328125, 1674035.39999999990686774 501203.5 1233.9725341796875, 1673986.10000000009313226 501161.70000000001164153 1274.2257080078125, 1673944.60000000009313226 501162.40000000002328306 1286.3062744140625, 1673881.5 501173.5 1334.59326171875, 1673833.10000000009313226 501201.29999999998835847 1347.869873046875, 1673832.5 501224.59999999997671694 1356.727783203125)");
		LineString catchmentLine = (LineString)TestHelper.geometryFromWkt("LineString (1674197.69999999995343387 501236.09999999997671694, 1674172.5 501222.90000000002328306, 1674136.5 501198.90000000002328306, 1674102.19999999995343387 501199.70000000001164153, 1674068.30000000004656613 501200.5, 1674035.39999999990686774 501203.5, 1673986.10000000009313226 501161.70000000001164153, 1673944.60000000009313226 501162.40000000002328306, 1673881.5 501173.5, 1673833.10000000009313226 501201.29999999998835847, 1673832.5 501224.59999999997671694)");
		Assert.isTrue(waterAnalyzer.isTouchingWater(catchmentLine), "catchment line expected to touch water");

	}
	
	@Test
	public void testIsTouchingWater2() throws IOException, ParseException {
		
		LineString water1 = (LineString)TestHelper.geometryFromWkt("LineString (1674102.60000000009313226 501149.40000000002328306, 1674064.30000000004656613 501149.59999999997671694, 1673986.10000000009313226 501161.70000000001164153, 1673944.60000000009313226 501162.40000000002328306, 1673881.5 501173.5, 1673830.5 501178.09999999997671694, 1673785.19999999995343387 501184.29999999998835847, 1673736.39999999990686774 501194.29999999998835847, 1673694.60000000009313226 501197.90000000002328306, 1673629.5 501194.59999999997671694, 1673557.39999999990686774 501181.29999999998835847, 1673535.60000000009313226 501178.59999999997671694)");
		List<LineString> water = new ArrayList<LineString>();
		water.add(water1);
		SimpleFeatureSource waterFeatures = TestHelper.createLineStringFeatureSource(water, "water");
		Water waterAnalyzer = new Water(waterFeatures);
		
		Coordinate c = new Coordinate(1673944.60000000009313226, 501162.40000000002328306, 1286.3062744140625);
		Assert.isTrue(waterAnalyzer.isTouchingWater(c), "coordinate expected to touch water");

	}
	
	@Test
	public void testIsOverlappingWater1() throws IOException, ParseException {
		
		LineString water1 = (LineString)TestHelper.geometryFromWkt("LineString (1674102.60000000009313226 501149.40000000002328306, 1674064.30000000004656613 501149.59999999997671694, 1673986.10000000009313226 501161.70000000001164153, 1673944.60000000009313226 501162.40000000002328306, 1673881.5 501173.5, 1673830.5 501178.09999999997671694, 1673785.19999999995343387 501184.29999999998835847, 1673736.39999999990686774 501194.29999999998835847, 1673694.60000000009313226 501197.90000000002328306, 1673629.5 501194.59999999997671694, 1673557.39999999990686774 501181.29999999998835847, 1673535.60000000009313226 501178.59999999997671694)");
		List<LineString> water = new ArrayList<LineString>();
		water.add(water1);
		SimpleFeatureSource waterFeatures = TestHelper.createLineStringFeatureSource(water, "water");
		Water waterAnalyzer = new Water(waterFeatures);
		
		LineString other = (LineString)TestHelper.geometryFromWkt("LineString (1674102.60000000009313226 501149.40000000002328306, 1674064.30000000004656613 501149.59999999997671694)");
		Assert.isTrue(waterAnalyzer.isOverlappingWater(other), "edge expected to overlap water");

	}
}
