package ca.bc.gov.catchment.routes;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Random;

import org.geotools.data.DataUtilities;
import org.geotools.data.collection.CollectionFeatureSource;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.feature.DefaultFeatureCollection;
import org.geotools.feature.SchemaException;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.geometry.jts.JTSFactoryFinder;
import org.junit.Assert;
import org.junit.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;

import ca.bc.gov.catchment.routes.LineStringRouter;
import ca.bc.gov.catchment.synthetic.DummyFactory;
import ca.bc.gov.catchments.utils.SaveUtils;
import ca.bc.gov.catchments.utils.SpatialUtils;

public class LineStringRouterTest {

	private static final boolean SAVE_RESULTS = false;
	private static final String SAVE_DIR = "C:\\Temp\\";
	
	private SimpleFeatureSource tinEdges;
	
	public LineStringRouterTest() {
		try {
			tinEdges = DummyFactory.createDummyTinEdges();
		} catch (IOException e) {
			System.out.println("Unable to setup LineStringRouterTest.  Can't create dummy TIN.");
			e.printStackTrace();
		}
	}
	
	private void testMakeRoute(int numPoints) {
		String testName = "LineStringRouter-test-"+numPoints+"pt";
		String saveFilename = SAVE_DIR+testName+".gpkg";
		File saveFile = new File(saveFilename);
		if (SAVE_RESULTS && saveFile.exists()) {
			saveFile.delete();
		}
		
		LineStringRouter router = new LineStringRouter(tinEdges);
		LineString route = null;
		Coordinate[] coords = null;
		try {
			coords = pickCoordinates(tinEdges, numPoints);
			System.out.println("Routing connecting these points: "+coordinatesToString(coords));
			route = router.makeRoute(coords);
			System.out.println(" route: "+route);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			//e.printStackTrace();
			System.out.println(" no route found");
			Assert.fail("Failed to create route. " +e.getMessage());
		}
	
		//validate route		
		if (!routeContainsCoords(route, coords)) {
			Assert.fail("Route doesn't contain the required coordinates");
		}
		
		if (SAVE_RESULTS) {
			try {
				List<LineString> routes = new ArrayList<LineString>();
				routes.add(route);
				SimpleFeatureSource routesFs = createLineStringFeatureSource(routes, "routes");
				save(tinEdges, saveFilename);
				save(routesFs, saveFilename);
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
	
	@Test
	public void testMakeRouteTwoPoints() {
		testMakeRoute(2);
	}
	
	@Test
	public void testMakeRouteThreePoints() {
		testMakeRoute(3);
	}
	
	@Test
	public void testMakeRouteFourPoints() {
		testMakeRoute(4);
	}
	
	//@Test
	public void testMakeRouteFivePoints() {
		testMakeRoute(5);
	}
	
	//@Test
	public void testMakeRouteSixPoints() {
		testMakeRoute(6);
	}
	
	//@Test
	public void testMakeRouteSevenPoints() {
		testMakeRoute(7);
	}
	
	//@Test
	public void testMakeRouteTenPoints() {
		testMakeRoute(10);
	}
	
	@Test
	public void testAlternativeRoutesNewMidpoint() throws RouteException {
		String testName = "LineStringRouter-test-alternative-midpoint";
		String saveFilename = SAVE_DIR+testName+".gpkg";
		LineStringRouter router = new LineStringRouter(tinEdges);
		LineString initialRoute = null;
		try {
			Coordinate[] startAndEnd = pickCoordinates(tinEdges, 2);
			System.out.println("Routing between these points: "+coordinatesToString(startAndEnd));
			initialRoute = router.makeRoute(startAndEnd);
		} catch (Exception e) {
			e.printStackTrace();
		}

		int numCoords = initialRoute.getNumPoints();
		int middleIndex = numCoords/2;
		Coordinate pointToRemove = initialRoute.getCoordinateN(middleIndex);
		List<LineString> alternativeRoutes = null;
		try {
			System.out.println("Generating alternatives");
			alternativeRoutes = router.alternativeRoutes(initialRoute, pointToRemove);
			System.out.println(" "+alternativeRoutes.size()+" alternative(s)");
		} catch (IOException e) {
			e.printStackTrace();
			Assert.fail("Unable to generate alternative route");
		}
		
		//validate alternative routes
		for (LineString alternative : alternativeRoutes) {
			Coordinate[] coords = alternative.getCoordinates();
			List<Coordinate> coordList = SpatialUtils.toCoordinateList(coords);
			if (coordList.contains(pointToRemove)) {
				Assert.fail("Alternative route isn't correct.  It hasn't ben rerouted around "+pointToRemove);
			}
		}
		
		if (SAVE_RESULTS) {
			try {
				List<LineString> routes = new ArrayList<LineString>();
				routes.add(initialRoute);
				routes.addAll(alternativeRoutes);
				SimpleFeatureSource routesFs = createLineStringFeatureSource(routes, "routes");
				
				List<Coordinate> coords = new ArrayList<Coordinate>();
				coords.add(pointToRemove);
				SimpleFeatureSource pointsFs = createPointFeatureSource(coords);
				
				save(tinEdges, saveFilename);
				save(routesFs, saveFilename);
				save(pointsFs, saveFilename);
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
	
	@Test
	public void testAlternativeRoutesNewEndpointFails() throws RouteException {
		LineStringRouter router = new LineStringRouter(tinEdges);
		LineString initialRoute = null;
		try {
			Coordinate[] startAndEnd = pickCoordinates(tinEdges, 2);
			System.out.println("Routing between these points: "+coordinatesToString(startAndEnd));
			initialRoute = router.makeRoute(startAndEnd);
		} catch (Exception e) {
			e.printStackTrace();
		}

		Coordinate firstPoint = initialRoute.getCoordinateN(0);
		Coordinate lastPoint = initialRoute.getCoordinateN(initialRoute.getNumPoints()-1);
		
		try {
			System.out.println("Generating alternatives");
			router.alternativeRoutes(initialRoute, firstPoint);
			Assert.fail("alternative route expected to fail if changing endpoint");
		} catch (IllegalArgumentException e) {
			//do nothing
		} catch(IOException e) {
			Assert.fail("An IllegalArgumentException was expected, but a different error occurred.");
		}
		
		try {
			System.out.println("Generating alternatives");
			router.alternativeRoutes(initialRoute, lastPoint);
			Assert.fail("alternative route expected to fail if changing endpoint");
		} catch (IllegalArgumentException e) {
			//do nothing
		} catch (IOException e) {
			Assert.fail("An IllegalArgumentException was expected, but a different error occurred.");
		}
		
	}
	
	@Test
	public void testMoveCommonEndpoint() throws IOException, RouteException {
		String testName = "LineStringRouter-test-move-common-endpoint";
		String saveFilename = SAVE_DIR+testName+".gpkg";
		File saveFile = new File(saveFilename);
		if (SAVE_RESULTS && saveFile.exists()) {
			saveFile.delete();
		}
		
		LineStringRouter router = new LineStringRouter(tinEdges);
		List<LineString> initialRoutes = new ArrayList<LineString>();
		
		Coordinate[] randomCoords = pickCoordinates(tinEdges, 5);
		Coordinate commonCoordinate = randomCoords[1];
		Coordinate[] coordsA = {commonCoordinate, randomCoords[0]};
		Coordinate[] coordsB = {commonCoordinate, randomCoords[3]};
		
		try {
			initialRoutes.add(router.makeRoute(coordsA));
			initialRoutes.add(router.makeRoute(coordsB));
		} catch (Exception e) {
			e.printStackTrace();
		}

		//pick a new common coordinate
		Coordinate newCommonCoordinate = null;
		List<Coordinate> connectedCoords = router.getConnectedCoords(commonCoordinate);
		for (Coordinate c : connectedCoords) {
			if (!router.isCoordinateOf(c, initialRoutes.get(0)) && 
				!router.isCoordinateOf(c, initialRoutes.get(1))) {
					newCommonCoordinate = c;
				}
		}
		
		System.out.println("initial routes");
		for(LineString route : initialRoutes) {
			System.out.println(route);
		}
		System.out.println("moving common point "+commonCoordinate+" to "+newCommonCoordinate);
		
		if (newCommonCoordinate == null) {
			throw new IllegalStateException("Unable to find a new coordinate to move the common endpoint to");
		}
		
		List<LineString> newRoutes = null;
		try {
			System.out.println("Moving common endpoint");
			newRoutes = router.moveJunction(initialRoutes, commonCoordinate, newCommonCoordinate);
		} catch (IOException e) {
			e.printStackTrace();
			Assert.fail("Unable to move common endpoint");
		}
		
		System.out.println("updated routes");
		for(LineString route : newRoutes) {
			System.out.println(route);
		}
		
		if (SAVE_RESULTS) {
			try {
				
				SimpleFeatureSource initialRoutesFs = createLineStringFeatureSource(initialRoutes, "routes_initial");
				SimpleFeatureSource updatedRoutesFs = createLineStringFeatureSource(newRoutes, "routes_updated");
				
				List<Coordinate> coords = new ArrayList<Coordinate>();
				coords.add(commonCoordinate);
				coords.add(newCommonCoordinate);
				SimpleFeatureSource pointsFs = createPointFeatureSource(coords);
				
				save(tinEdges, saveFilename);
				save(initialRoutesFs, saveFilename);
				save(updatedRoutesFs, saveFilename);
				save(pointsFs, saveFilename);
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
		//validate alternative routes
		for (LineString route : newRoutes) {
			Coordinate[] coords = route.getCoordinates();
			List<Coordinate> coordList = SpatialUtils.toCoordinateList(coords);
			if (!coordList.contains(newCommonCoordinate)) {
				Assert.fail("Route isn't correct.  It doesn't contain the new coordinate "+newCommonCoordinate);
			}
			if (coordList.contains(commonCoordinate)) {
				Assert.fail("Route isn't correct.  It should not contain the old common coordinate "+commonCoordinate);
			}
			if (!router.doesRouteFollowTinEdges(route)) {
				Assert.fail("Route doesn't follow TIN: "+route);
			}
		}
	}
	
	// Helpers
	//-------------------------------------------------------------------------
	
	private String coordinatesToString(Coordinate [] coords) {
		String routeStr = "";
		for(Coordinate c : coords) {
			routeStr += c + " ";
		}
		return routeStr;
	}
	
	
	private Coordinate[] pickCoordinates(SimpleFeatureSource tinEdges, int n) throws IOException {
		Collection<Coordinate> resultCollection = new ArrayList<Coordinate>();
		SimpleFeatureCollection fc = tinEdges.getFeatures();
		SimpleFeatureIterator it = fc.features();
		int numEdges = fc.size();
		int gap = numEdges / (n-1) - 1; //random.nextInt(numEdges);
		int index = 0;
		int nextPick = index;
		//System.out.println("numEdges: "+numEdges);
		//System.out.println("gap: "+gap);
		//System.out.println("index: "+index);
		while(it.hasNext()) {	
			//System.out.println("index:"+index+", nextPick:"+nextPick);
			SimpleFeature f = it.next();
			if (index == nextPick) {				
				Geometry g = (Geometry)f.getDefaultGeometry();
				Coordinate[] coords = g.getCoordinates();
				Coordinate c1 = coords[0];
				Coordinate c2 = coords[1];
				if (!resultCollection.contains(c1)) {
					resultCollection.add(c1);
					nextPick = (nextPick + gap) % numEdges;
				}
				else if (!resultCollection.contains(c2)) {
					resultCollection.add(c2);
					nextPick = (nextPick + gap) % numEdges;
				}
				else {
					nextPick++;
				}
			}
			index++;
			if (resultCollection.size() == n) {
				break;
			}
		}
		if (resultCollection.size() == n) {
			 Coordinate[] result = resultCollection.toArray(new Coordinate[resultCollection.size()]);
			 return result;
		}
		throw new IllegalStateException("Unable to find "+n+" unique coordinates.");
	}
	/**
	 * returns true if the given linestring contains all the given coordinates
	 * @param route
	 * @param coordsToCheck
	 * @return
	 */
	private boolean routeContainsCoords(LineString route, Coordinate[] coordsToCheck) {
		List<Coordinate> routeCoords = SpatialUtils.toCoordinateList(route.getCoordinates());
		for(Coordinate coord : coordsToCheck) {
			if (!routeCoords.contains(coord)) {
				return false;
			}
		}
		return true;
	}
	
	private void save(SimpleFeatureSource fs, String filename) throws IOException {
		SaveUtils.saveToGeoPackage(filename, fs.getFeatures());
	}
	
	private SimpleFeatureSource createLineStringFeatureSource(List<LineString> routes, String tableName) { 
	
		//save route
		DefaultFeatureCollection routeFc = new DefaultFeatureCollection();
		SimpleFeatureType routeFeatType = null;
		try {
			routeFeatType = DataUtilities.createType(tableName, "geometry:LineString");
		} catch (SchemaException e1) {
			System.out.println("Unable to create feature type "+tableName);
			System.exit(1);
		}
		SimpleFeatureBuilder routeFeatBuilder = new SimpleFeatureBuilder(routeFeatType);
		int nextId = 0;
		for(LineString route: routes) {
			Object[] values = {route};
			routeFc.add(routeFeatBuilder.buildFeature(""+nextId++, values));
		}
		CollectionFeatureSource fs = new CollectionFeatureSource(routeFc);
		return fs;
	}
	
	private SimpleFeatureSource createPointFeatureSource(List<Coordinate> coords) { 
		
		//save route
		DefaultFeatureCollection fc = new DefaultFeatureCollection();
		SimpleFeatureType featType = null;
		try {
			featType = DataUtilities.createType("key_points", "geometry:Point");
		} catch (SchemaException e1) {
			System.out.println("Unable to create feature type "+"key_points");
			System.exit(1);
		}
		GeometryFactory geometryFactory = JTSFactoryFinder.getGeometryFactory(); 
		SimpleFeatureBuilder routeFeatBuilder = new SimpleFeatureBuilder(featType);
		int nextId = 0;
		for(Coordinate c: coords) {
			Geometry g = geometryFactory.createPoint(c);
			Object[] values = {g};
			fc.add(routeFeatBuilder.buildFeature(""+nextId++, values));
		}
		CollectionFeatureSource fs = new CollectionFeatureSource(fc);
		return fs;
	}
	
	
	
}
