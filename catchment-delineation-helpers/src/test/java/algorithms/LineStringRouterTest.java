package algorithms;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Random;

import org.geotools.data.DataUtilities;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.feature.DefaultFeatureCollection;
import org.geotools.feature.SchemaException;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.junit.Assert;
import org.junit.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;

import ca.bc.gov.catchments.utils.SaveUtils;
import ca.bc.gov.catchments.utils.SpatialUtils;

public class LineStringRouterTest {

	private static final String SAVE_DIR = "C:\\Temp\\";
	
	private SimpleFeatureSource tinEdges;
	
	public LineStringRouterTest() {
		try {
			tinEdges = SpatialUtils.createDummyTin();
		} catch (IOException e) {
			System.out.println("Unable to setup LineStringRouterTest.  Can't create dummy TIN.");
			e.printStackTrace();
		}
	}
	
	@Test
	public void testMakeRouteTwoPoints() {
		String testName = "LineStringRouter-test-2pt";
		LineStringRouter router = new LineStringRouter(tinEdges);
		LineString route = null;
		try {
			Coordinate[] startAndEnd = pickCoordinates(tinEdges, 2);
			System.out.println("Routing between these points: "+coordinatesToString(startAndEnd));
			route = router.makeRoute(startAndEnd);
			System.out.println(" route: "+route);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			//e.printStackTrace();
			System.out.println(" no route found");
			Assert.fail("Failed to create route. " +e.getMessage());
		}
	
		try {
			save(tinEdges, route, SAVE_DIR+testName+".gpkg", true);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	@Test
	public void testMakeRouteThreePoints() {
		String testName = "LineStringRouter-test-3pt";
		LineStringRouter router = new LineStringRouter(tinEdges);
		LineString route = null;
		try {
			Coordinate[] coords = pickCoordinates(tinEdges, 3);
			System.out.println("Routing between these points: "+coordinatesToString(coords));
			route = router.makeRoute(coords);
			System.out.println(" route: "+route);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			//e.printStackTrace();
			System.out.println(" no route found");
			Assert.fail("Failed to create route. " +e.getMessage());
		}
	
		try {
			save(tinEdges, route, SAVE_DIR+testName+".gpkg", true);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	@Test
	public void testMakeRouteFivePoints() {
		String testName = "LineStringRouter-test-5pt";
		LineStringRouter router = new LineStringRouter(tinEdges);
		LineString route = null;
		try {
			Coordinate[] coords = pickCoordinates(tinEdges, 5);
			System.out.println("Routing between these points: "+coordinatesToString(coords));
			route = router.makeRoute(coords);
			System.out.println(" route: "+route);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			//e.printStackTrace();
			System.out.println(" no route found");
			Assert.fail("Failed to create route. " +e.getMessage());
		}
	
		try {
			save(tinEdges, route, SAVE_DIR+testName+".gpkg", true);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	@Test
	public void testMakeRouteSixPoints() {
		String testName = "LineStringRouter-test-6pt";
		LineStringRouter router = new LineStringRouter(tinEdges);
		LineString route = null;
		try {
			Coordinate[] coords = pickCoordinates(tinEdges, 6);
			System.out.println("Routing between these points: "+coordinatesToString(coords));
			route = router.makeRoute(coords);
			System.out.println(" route: "+route);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			//e.printStackTrace();
			System.out.println(" no route found");
			Assert.fail("Failed to create route. " +e.getMessage());
		}
	
		try {
			save(tinEdges, route, SAVE_DIR+testName+".gpkg", true);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	@Test
	public void testMakeRouteSevenPoints() {
		String testName = "LineStringRouter-test-7pt";
		LineStringRouter router = new LineStringRouter(tinEdges);
		LineString route = null;
		try {
			Coordinate[] coords = pickCoordinates(tinEdges, 7);
			System.out.println("Routing between these points: "+coordinatesToString(coords));
			route = router.makeRoute(coords);
			System.out.println(" route: "+route);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			//e.printStackTrace();
			System.out.println(" no route found");
			Assert.fail("Failed to create route. " +e.getMessage());
		}
	
		try {
			save(tinEdges, route, SAVE_DIR+testName+".gpkg", true);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	@Test
	public void testMakeRouteTenPoints() {
		String testName = "LineStringRouter-test-10pt";
		LineStringRouter router = new LineStringRouter(tinEdges);
		LineString route = null;
		try {
			Coordinate[] coords = pickCoordinates(tinEdges, 10);
			System.out.println("Routing between these points: "+coordinatesToString(coords));
			route = router.makeRoute(coords);
			System.out.println(" route: "+route);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			//e.printStackTrace();
			System.out.println(" no route found");
			Assert.fail("Failed to create route. " +e.getMessage());
		}
	
		try {
			save(tinEdges, route, SAVE_DIR+testName+".gpkg", true);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
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
		return pickCoordinates(tinEdges, n, 0);
	}
	
	private Coordinate[] pickCoordinates(SimpleFeatureSource tinEdges, int n, int seed) throws IOException {
		Random random = new Random(seed);
		Collection<Coordinate> resultCollection = new ArrayList<Coordinate>();
		SimpleFeatureCollection fc = tinEdges.getFeatures();
		SimpleFeatureIterator it = fc.features();
		int numEdges = fc.size();
		int gap = numEdges / (n-1) - 1; //random.nextInt(numEdges);
		int index = 0;
		int nextPick = index;
		//System.out.println("numEdges: "+numEdges);
		//System.out.println("gap: "+gap);
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
	
	private void save(SimpleFeatureSource tinEdges, LineString route, String filename, boolean overwrite) throws IOException {
		File f = new File(filename);
		if (overwrite && f.exists()) {
			f.delete();
		}
		
		//save TIN
		SaveUtils.saveToGeoPackage(filename, tinEdges.getFeatures());
		
		//save route
		DefaultFeatureCollection routeFc = new DefaultFeatureCollection();
		SimpleFeatureType routeFeatType = null;
		try {
			routeFeatType = DataUtilities.createType("route", "geometry:LineString");
		} catch (SchemaException e1) {
			System.out.println("Unable to create feature type "+"route");
			System.exit(1);
		}
		SimpleFeatureBuilder routeFeatBuilder = new SimpleFeatureBuilder(routeFeatType);
		Object[] values = {route};
		routeFc.add(routeFeatBuilder.buildFeature("1", values));
		SaveUtils.saveToGeoPackage(filename, routeFc);
	}
	
}
