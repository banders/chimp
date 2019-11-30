package ca.bc.gov.catchment.fitness;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.feature.DefaultFeatureCollection;
import org.geotools.geometry.jts.JTSFactoryFinder;
import org.junit.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKTReader;
import org.locationtech.jts.util.Assert;
import org.opengis.feature.simple.SimpleFeature;

import ca.bc.gov.catchment.routes.RouteException;
import ca.bc.gov.catchment.synthetic.DummyFactory;
import ca.bc.gov.catchment.synthetic.TestHelper;

public class CatchmentValidityTest {

	private static final boolean SAVE_RESULTS = false;
	private static final String SAVE_DIR = "C:\\Temp\\";
	
	private GeometryFactory geometryFactory;
	private SimpleFeatureSource waterFeatures;
	private CatchmentValidity validityChecker;
	
	public CatchmentValidityTest() throws IOException, RouteException {
		this.geometryFactory = JTSFactoryFinder.getGeometryFactory();
		this.waterFeatures = DummyFactory.createDummyWaterFeatures();
		this.validityChecker = new CatchmentValidity(waterFeatures);
	}
	
	@Test
	public void testSyntheticCatchmentsAreValid() throws IOException, RouteException {
		String testName = "CatchmentValidityTest-testSyntheticCatchmentsAreValid";
		String saveFilename = SAVE_DIR+testName+".gpkg";
		
		SimpleFeatureSource catchments = DummyFactory.createDummyCatchments();
				
		if (SAVE_RESULTS) {
			try {
				TestHelper.save(catchments, saveFilename);
				TestHelper.save(waterFeatures, saveFilename);				
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
		SimpleFeatureCollection catchmentsFc = catchments.getFeatures();
		SimpleFeatureIterator catchmentIt = catchmentsFc.features();
		while(catchmentIt.hasNext()) {
			SimpleFeature catchment = catchmentIt.next();
			LineString route = (LineString)catchment.getDefaultGeometry();
			boolean isValid = validityChecker.isRouteValidWrtWater(route);
			Assert.isTrue(isValid, "synthetic catchment not valid");
		}
	}
	
	@Test
	public void testCatchmentSegmentOverlapsWater() throws IOException {
		//these coordinates were chosen based on the water features
		//defined in the DummyFactory
		Coordinate[] invalidCoords = {
				new Coordinate(7, 1, 10),
				new Coordinate(6, 3, 10)
		};
		LineString invalidRoute = geometryFactory.createLineString(invalidCoords);
		boolean isValid = validityChecker.isRouteValidWrtWater(invalidRoute);
		Assert.isTrue(!isValid, "synthetic catchment is expected to be invalid");
	}
	
	@Test
	public void testCatchmentSegmentTouchesWaterAtNonConfluenceEnd() throws IOException {
		//these coordinates were chosen based on the water features
		//defined in the DummyFactory
		Coordinate[] invalidCoords = {
				new Coordinate(5, 11, 12),
				DummyFactory.RIVER_MAIN_END,
				new Coordinate(10, 9, 12)
		};
		LineString invalidRoute = geometryFactory.createLineString(invalidCoords);
		boolean isValid = validityChecker.isRouteValidWrtWater(invalidRoute);
		Assert.isTrue(!isValid, "synthetic catchment is expected to be invalid");
	}
	
	@Test
	public void testCatchmentSegmentTouchesWaterAtMidPoint1() throws IOException {
		//these coordinates were chosen based on the water features
		//defined in the DummyFactory
		Coordinate[] invalidCoords = {
				new Coordinate(6, 8, 11),
				new Coordinate(9, 7, 10),
				new Coordinate(11, 6, 16)
		};
		LineString invalidRoute = geometryFactory.createLineString(invalidCoords);
		boolean isValid = validityChecker.isRouteValidWrtWater(invalidRoute);
		Assert.isTrue(!isValid, "synthetic catchment is expected to be invalid");
	}
	
	@Test
	public void testCatchmentNonOverlapsOtherCatchment1() throws IOException, RouteException {
		SimpleFeatureSource catchments = DummyFactory.createDummyCatchments();
		
		//valid
		Coordinate[] route1Coords = {
				new Coordinate(2, 8, 13),
				new Coordinate(2, 10, 12)
		};		
		LineString route1 = geometryFactory.createLineString(route1Coords);
		boolean isValid1 = validityChecker.isRouteValidWrtCatchments(route1, catchments.getFeatures());
		Assert.isTrue(isValid1, "synthetic catchment is expected to be valid");
		
	}
	
	@Test
	public void testCatchmentNonOverlapsOtherCatchment2() throws IOException, RouteException {
		SimpleFeatureSource catchments = DummyFactory.createDummyCatchments();
		//invalid (crosses at a point)
		Coordinate[] routeCoords = {
				new Coordinate(4, 11, 14),
				new Coordinate(5, 11, 12)
		};		
		LineString route = geometryFactory.createLineString(routeCoords);
		boolean isValid = validityChecker.isRouteValidWrtCatchments(route, catchments.getFeatures());
		Assert.isTrue(isValid, "synthetic catchment is expected to be valid");
	}
	
	@Test
	public void testCatchmentOverlapsOtherCatchment2() throws IOException, RouteException {
		String testName = "CatchmentValidityTest-testCatchmentOverlapsOtherCatchment2";
		String saveFilename = SAVE_DIR+testName+".gpkg";		
		
		SimpleFeatureSource catchments = DummyFactory.createDummyCatchments();
		//invalid (touches at one point)
		Coordinate[] route2Coords = {
				new Coordinate(2, 8, 13),
				new Coordinate(2, 10, 12),
				new Coordinate(4, 9, 12),
				new Coordinate(4, 11, 14),
				new Coordinate(5, 9, 14) //existing catchment midpoint
		};		
		LineString route2 = geometryFactory.createLineString(route2Coords);
		boolean isValid2 = validityChecker.isRouteValidWrtCatchments(route2, catchments.getFeatures());
				
		if (SAVE_RESULTS) {
			try {
				SimpleFeatureSource routes = TestHelper.createLineStringFeatureSource(route2, "routes");				
				TestHelper.save(catchments, saveFilename);
				TestHelper.save(routes, saveFilename);				
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}		
		
		Assert.isTrue(!isValid2, "synthetic catchment is expected to be invalid");
	}
	
	@Test
	public void testCatchmentOverlapsOtherCatchment3() throws IOException, RouteException {
		SimpleFeatureSource catchments = DummyFactory.createDummyCatchments();
		//invalid (covering one line segment)
		Coordinate[] route3Coords = {
				new Coordinate(2, 8, 13),
				new Coordinate(2, 10, 12),
				new Coordinate(4, 9, 12),
				new Coordinate(4, 6, 12)
		};		
		LineString route3 = geometryFactory.createLineString(route3Coords);
		boolean isValid3 = validityChecker.isRouteValidWrtCatchments(route3, catchments.getFeatures());
		Assert.isTrue(!isValid3, "synthetic catchment is expected to be invalid");
	}
	
	@Test
	public void testCatchmentOverlapsOtherCatchment4() throws IOException, RouteException {
		String testName = "CatchmentValidityTest-testCatchmentOverlapsOtherCatchment4";
		String saveFilename = SAVE_DIR+testName+".gpkg";	
		
		SimpleFeatureSource catchments = DummyFactory.createDummyCatchments();
		//invalid (crosses at a point)
		Coordinate[] route4Coords = {
				new Coordinate(2, 8, 13),
				new Coordinate(2, 10, 12),
				new Coordinate(4, 9, 12),
				new Coordinate(5, 9, 14), //existing catchment coordinate
				new Coordinate(6, 8, 11)
		};		
		LineString route4 = geometryFactory.createLineString(route4Coords);
		boolean isValid4 = validityChecker.isRouteValidWrtCatchments(route4, catchments.getFeatures());
		
		if (SAVE_RESULTS) {
			try {
				SimpleFeatureSource routes = TestHelper.createLineStringFeatureSource(route4, "routes");				
				TestHelper.save(catchments, saveFilename);
				TestHelper.save(routes, saveFilename);				
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}	
		
		Assert.isTrue(!isValid4, "synthetic catchment is expected to be invalid");
	}
	
	@Test
	public void testCatchmentsOverlap1() throws ParseException, IOException {
		//the two routes from this test are from a real world test of the BC kootenay watershed
		//in which a catchment-adjustment algorithm produced invalid (overlapping) catchments.
		//we want to ensure this result doesn't happen again, hence the test case.
		//the routes have 2 overlapping segments and several common vertices (excluding endpoints)
		
		//the route to test
		LineString route = (LineString)TestHelper.geometryFromWkt("LineString (1682012.10000000009313226 502431.09999999997671694, 1682025.19056004052981734 502461.68755821231752634, 1681992 502455.29999999998835847, 1681954.6028291042894125 502429.46831788215786219, 1681969.39606382162310183 502337.67031658627092838, 1681897.64219166338443756 502322.42857035342603922, 1681827.11291093425825238 502313.38862988166511059, 1681887.73447653697803617 502399.74080667365342379, 1681875.71299630543217063 502477.78965501487255096, 1681943.08937273826450109 502503.55243614129722118, 1681953.5 502520.29999999998835847)");
		
		//a set of catchments which are invalid w.r.t. the route above
		LineString catchmentSection1 = (LineString)TestHelper.geometryFromWkt("LineString (1681810.69999999995343387 502640.59999999997671694, 1681854.73152385954745114 502649.39198695961385965, 1681923.37798158428631723 502665.24291689228266478, 1681931.42607344943098724 502586.68461274914443493, 1681915.14999999990686774 502551.09999999997671694, 1681875.71299630543217063 502477.78965501487255096, 1681807.42462949128821492 502467.0220178896561265, 1681799.37668169103562832 502545.58024398982524872, 1681863.5416807399597019 502564.88656838331371546, 1681876.80000000004656613 502581.90000000002328306, 1681843.75 502611.25, 1681792.83017560536973178 502620.30112380161881447, 1681722.95905132684856653 502598.24855376034975052, 1681728.30367293069139123 502509.2691229647025466, 1681731.86998912412673235 502434.16636186372488737, 1681817.07824174035340548 502391.69206187408417463, 1681827.11291093425825238 502313.38862988166511059, 1681887.73447653697803617 502399.74080667365342379, 1681954.6028291042894125 502429.46831788215786219, 1681943.08937273826450109 502503.55243614129722118, 1681953.5 502520.29999999998835847)");
		List<LineString> catchmentSections = new ArrayList<LineString>();
		catchmentSections.add(catchmentSection1);		
		SimpleFeatureSource catchments = TestHelper.createLineStringFeatureSource(catchmentSections, "catchments");
		
		boolean isValid = validityChecker.isRouteValidWrtCatchments(route, catchments.getFeatures());
		Assert.isTrue(!isValid, "catchment is expected to be invalid");
	}
	
	@Test
	public void testCatchmentsOverlap2() throws ParseException, IOException {
		//the two routes from this test are from a real world test of the BC kootenay watershed
		//in which a catchment-adjustment algorithm produced invalid (overlapping) catchments.
		//we want to ensure this result doesn't happen again, hence the test case.
		//the routes have 2 overlapping segments and several common vertices (excluding endpoints)
		
		//the route to test
		LineString route = (LineString)TestHelper.geometryFromWkt("LineString (1681719.5 502708, 1681706.63200264051556587 502749.29092537146061659, 1681702.19928062474355102 502823.27546133380383253, 1681776.63001293875277042 502770.35230979789048433, 1681784.16990519221872091 502695.75861469097435474, 1681787.60000000009313226 502655.09999999997671694, 1681722.95905132684856653 502598.24855376034975052, 1681799.37668169103562832 502545.58024398982524872, 1681792.83017560536973178 502620.30112380161881447, 1681810.69999999995343387 502640.59999999997671694)");
		
		//a set of catchments which are invalid w.r.t. the route above
		LineString catchmentSection1 = (LineString)TestHelper.geometryFromWkt("LineString (1681810.69999999995343387 502640.59999999997671694, 1681854.73152385954745114 502649.39198695961385965, 1681923.37798158428631723 502665.24291689228266478, 1681931.42607344943098724 502586.68461274914443493, 1681915.14999999990686774 502551.09999999997671694, 1681875.71299630543217063 502477.78965501487255096, 1681807.42462949128821492 502467.0220178896561265, 1681799.37668169103562832 502545.58024398982524872, 1681863.5416807399597019 502564.88656838331371546, 1681876.80000000004656613 502581.90000000002328306, 1681843.75 502611.25, 1681792.83017560536973178 502620.30112380161881447, 1681722.95905132684856653 502598.24855376034975052, 1681728.30367293069139123 502509.2691229647025466, 1681731.86998912412673235 502434.16636186372488737, 1681817.07824174035340548 502391.69206187408417463, 1681827.11291093425825238 502313.38862988166511059, 1681887.73447653697803617 502399.74080667365342379, 1681954.6028291042894125 502429.46831788215786219, 1681943.08937273826450109 502503.55243614129722118, 1681953.5 502520.29999999998835847)");
		List<LineString> catchmentSections = new ArrayList<LineString>();
		catchmentSections.add(catchmentSection1);		
		SimpleFeatureSource catchments = TestHelper.createLineStringFeatureSource(catchmentSections, "catchments");
		
		boolean isValid = validityChecker.isRouteValidWrtCatchments(route, catchments.getFeatures());
		Assert.isTrue(!isValid, "catchment is expected to be invalid");
	}
	
	@Test
	public void testCatchmentsOverlap3() throws ParseException, IOException {
		
		//the route to test
		LineString route = (LineString)TestHelper.geometryFromWkt("LINESTRING (1680223.7 501053.1, 1680224.5499999998 501092.5, 1680225.4 501131.9, 1680227.5 501145.2, 1680235.4 501181.9, 1680240.4 501198.1, 1680253.7 501230.4, 1680265.989187424 501242.87018021615, 1680278.3 501272.2, 1680308.1 501316.1, 1680325.8 501339.5, 1680349.1 501365.5, 1680367.3 501389.8, 1680387.3 501442, 1680410.5 501509.2, 1680428.8 501544.95, 1680447.1 501580.7, 1680466.7 501607.7, 1680519.680191284 501626.9823013358, 1680594.5873247585 501625.5000060415, 1680657.855284939 501699.09276466817, 1680648.942223093 501776.5320969075)");
		
		//a set of catchments which are invalid w.r.t. the route above
		LineString catchmentSection1 = (LineString)TestHelper.geometryFromWkt("LINESTRING (1680648.942223093 501776.5320969075, 1680718.1 501809.9, 1680793.616995315 501790.0359208649, 1680842.8 501809.2, 1680883.55 501813.75, 1680924.3 501818.3, 1680977.9 501835.1, 1681031.5 501851.9, 1681073.4 501868.6, 1681111.6 501916.9, 1681149.4 501955.1, 1681191.9 501994.25, 1681234.4 502033.4)");
		LineString catchmentSection2 = (LineString)TestHelper.geometryFromWkt("LINESTRING (1680648.942223093 501776.5320969075, 1680626 501817.4, 1680616.9 501859.4, 1680607.8 501901.4, 1680603.65 501947.25, 1680565.9616340916 501998.6205098685, 1680598.4 502056.8, 1680599.6 502101.6, 1680604.5 502149.5, 1680613.6 502176.9, 1680636.4 502225.3, 1680662.2 502267.5, 1680688 502309.7, 1680710.1 502343.9, 1680732.2 502378.1, 1680752.975 502430.89999999997, 1680773.75 502483.69999999995, 1680794.525 502536.5, 1680815.3 502589.3)");
		List<LineString> catchmentSections = new ArrayList<LineString>();
		catchmentSections.add(catchmentSection1);
		catchmentSections.add(catchmentSection2);
		SimpleFeatureSource catchments = TestHelper.createLineStringFeatureSource(catchmentSections, "catchments");
		
		boolean isValid = validityChecker.isRouteValidWrtCatchments(route, catchments.getFeatures());
		Assert.isTrue(isValid, "catchment is expected to be valid");
	}
	
}
