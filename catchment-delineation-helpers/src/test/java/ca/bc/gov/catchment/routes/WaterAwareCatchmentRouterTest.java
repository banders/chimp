package ca.bc.gov.catchment.routes;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.geometry.jts.JTSFactoryFinder;
import org.junit.Assert;
import org.junit.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.opengis.feature.simple.SimpleFeature;
import org.tinfour.common.Vertex;

import ca.bc.gov.catchment.fitness.CatchmentValidity;
import ca.bc.gov.catchment.synthetic.DummyFactory;
import ca.bc.gov.catchment.synthetic.TestHelper;
import ca.bc.gov.catchment.tin.TinEdges;
import ca.bc.gov.catchment.utils.SpatialUtils;
import ca.bc.gov.catchment.water.Water;

public class WaterAwareCatchmentRouterTest {

	private static final boolean SAVE_RESULTS = false;
	private static final String SAVE_DIR = "C:\\Temp\\";
	
	private TinEdges tinEdges;
	private SimpleFeatureSource water;
	
	public WaterAwareCatchmentRouterTest() throws RouteException {
		try {
			tinEdges = new TinEdges(DummyFactory.createDummyTinEdges());
			water = DummyFactory.createDummyWaterFeatures();
		} catch (IOException e) {
			System.out.println("Can't create dummy data.");
			e.printStackTrace();
		}
	}
	
	@Test
	public void testReroute() throws IOException, RouteException {
		String testName = "WaterAwareCatchmentRouter-reroute";
		String saveFilename = SAVE_DIR+testName+".gpkg";
		File saveFile = new File(saveFilename);
		if (SAVE_RESULTS && saveFile.exists()) {
			saveFile.delete();
		}
		
		Coordinate coordToMove = new Coordinate(2, 8, 13);
		Coordinate coordToReplaceWith = new Coordinate(4, 9, 12);
		Coordinate[] included = {
				DummyFactory.RIVER_CONFLUENCE,
				new Coordinate(5, 1, 11),
				coordToMove,
				new Coordinate(2, 10, 12)
		};
		LineStringRouter basicRouter = new LineStringRouter(tinEdges);
		LineString originalRoute = basicRouter.makeRoute(included);
		
		Water waterAnalyzer = new Water(water);
		
		WaterAwareCatchmentRouter catchmentRouter = new WaterAwareCatchmentRouter(tinEdges, waterAnalyzer);
		LineString modifiedRoute = catchmentRouter.reroute(originalRoute, coordToMove, coordToReplaceWith, 1, false);

		Assert.assertTrue("modified route expected to be not null", modifiedRoute != null);
		Assert.assertTrue("modified route expected to be different from original route", !originalRoute.equals(modifiedRoute));
		
		CatchmentValidity catchmentValidity = new CatchmentValidity(water);
		
		Assert.assertTrue("modified route expected valid w.r.t. water", catchmentValidity.isRouteValidWrtWater(modifiedRoute));
		
		
		if (SAVE_RESULTS) {
			try {
				List<LineString> routes = new ArrayList<LineString>();
				routes.add(originalRoute);
				routes.add(modifiedRoute);
				SimpleFeatureSource routesFs = TestHelper.createLineStringFeatureSource(routes, "routes");
							
				List<Coordinate> coords = new ArrayList<Coordinate>();
				coords.add(coordToMove);
				coords.add(coordToReplaceWith);
				SimpleFeatureSource pointsFs = TestHelper.createPointFeatureSource(coords);
				
				TestHelper.save(tinEdges.getFeatureSource(), saveFilename);
				TestHelper.save(routesFs, saveFilename);
				TestHelper.save(pointsFs, saveFilename);
				TestHelper.save(water, saveFilename);
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
	
	@Test
	public void testIsCoordinateMovable() {
		
	}
	
}
