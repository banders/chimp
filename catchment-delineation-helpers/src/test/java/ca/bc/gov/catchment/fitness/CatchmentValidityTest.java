package ca.bc.gov.catchment.fitness;

import java.io.IOException;

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

public class CatchmentValidityTest {

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
		SimpleFeatureSource catchments = DummyFactory.createDummyCatchments();
				
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
		SimpleFeatureSource catchments = DummyFactory.createDummyCatchments();
		//invalid (touches at one point)
		Coordinate[] route2Coords = {
				new Coordinate(2, 8, 13),
				new Coordinate(2, 10, 12),
				new Coordinate(4, 9, 12),
				new Coordinate(4, 11, 14),
				new Coordinate(5, 11, 12)
		};		
		LineString route2 = geometryFactory.createLineString(route2Coords);
		boolean isValid2 = validityChecker.isRouteValidWrtCatchments(route2, catchments.getFeatures());
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
		SimpleFeatureSource catchments = DummyFactory.createDummyCatchments();
		//invalid (crosses at a point)
		Coordinate[] route4Coords = {
				new Coordinate(2, 8, 13),
				new Coordinate(2, 10, 12),
				new Coordinate(4, 9, 12),
				new Coordinate(6, 8, 11)
		};		
		LineString route4 = geometryFactory.createLineString(route4Coords);
		boolean isValid4 = validityChecker.isRouteValidWrtCatchments(route4, catchments.getFeatures());
		Assert.isTrue(!isValid4, "synthetic catchment is expected to be invalid");
	}
	
	
}
