package ca.bc.gov.catchment.fitness;

import java.io.IOException;

import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.geometry.jts.JTSFactoryFinder;
import org.junit.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
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
	public void testCatchmentSegmentTouchesWaterAtNonConfluence() throws IOException {
		//these coordinates were chosen based on the water features
		//defined in the DummyFactory
		Coordinate[] invalidCoords = {
				new Coordinate(5, 11, 12),
				new Coordinate(8, 9, 10),
				new Coordinate(10, 9, 12)
		};
		LineString invalidRoute = geometryFactory.createLineString(invalidCoords);
		boolean isValid = validityChecker.isRouteValidWrtWater(invalidRoute);
		Assert.isTrue(!isValid, "synthetic catchment is expected to be invalid");
	}
}
