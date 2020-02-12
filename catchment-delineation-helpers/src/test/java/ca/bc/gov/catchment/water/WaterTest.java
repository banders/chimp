package ca.bc.gov.catchment.water;

import java.io.IOException;

import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.geometry.jts.JTSFactoryFinder;
import org.junit.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.util.Assert;

import ca.bc.gov.catchment.fitness.CatchmentValidity;
import ca.bc.gov.catchment.routes.RouteException;
import ca.bc.gov.catchment.synthetic.DummyFactory;

public class WaterTest {

	private GeometryFactory geometryFactory;
	private SimpleFeatureSource waterFeatures;
	private Water waterAnalyzer;
	
	public WaterTest() throws IOException, RouteException {
		this.geometryFactory = JTSFactoryFinder.getGeometryFactory();
		this.waterFeatures = DummyFactory.createDummyWaterFeatures();
		this.waterAnalyzer = new Water(waterFeatures);
	}
	
	@Test
	public void testIsConfluence() throws IOException {
		
		//is confluence
		Assert.isTrue(waterAnalyzer.isConfluence(DummyFactory.RIVER_CONFLUENCE), "coordinate expected to be identified as a confluence point");
		
		//not confluence
		Assert.isTrue(!waterAnalyzer.isConfluence(DummyFactory.RIVER_MAIN_END), "coordinate expected not to be identified as a confluence point");
		Assert.isTrue(!waterAnalyzer.isConfluence(DummyFactory.RIVER_UPSTREAM_1_START), "coordinate expected not to be identified as a confluence point");
		Assert.isTrue(!waterAnalyzer.isConfluence(new Coordinate(0, 0, 10)), "coordinate expected not to be identified as a confluence point");
	}
}
