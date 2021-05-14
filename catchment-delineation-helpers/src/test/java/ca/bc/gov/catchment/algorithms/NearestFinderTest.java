package ca.bc.gov.catchment.algorithms;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.feature.DefaultFeatureCollection;
import org.junit.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.operation.polygonize.Polygonizer;
import org.locationtech.jts.util.Assert;
import org.opengis.feature.simple.SimpleFeature;

import ca.bc.gov.catchment.synthetic.TestHelper;
import ca.bc.gov.catchment.utils.SpatialUtils;

public class NearestFinderTest {

	@Test
	public void testFindNearest() throws ParseException, IOException {
		
		
		//prepare three lines: two halves of a lake, and river near to the lake (but not touching the lake)
		LineString halfLake1 = (LineString)TestHelper.geometryFromWkt("LineStringZ (1682733.89999999990686774 499918 0, 1682739.5 499912.70000000001164153 0, 1682740.30000000004656613 499898.79999999998835847 0, 1682733.10000000009313226 499884.70000000001164153 0, 1682726.30000000004656613 499881.90000000002328306 0)");
		LineString halfLake2 = (LineString)TestHelper.geometryFromWkt("LineStringZ (1682733.89999999990686774 499918 0, 1682719.10000000009313226 499915.09999999997671694 0, 1682710.10000000009313226 499907 0, 1682711 499892 0, 1682713.80000000004656613 499885.29999999998835847 0, 1682726.30000000004656613 499881.90000000002328306 0)");		
		LineString nearbyRiver = (LineString)TestHelper.geometryFromWkt("LineStringZ (1682671.89999999990686774 500031.09999999997671694 0, 1682677.39999999990686774 500020.09999999997671694 0, 1682682.80000000004656613 500009.20000000001164153 0, 1682681.10000000009313226 499998.40000000002328306 0, 1682679.39999999990686774 499987.70000000001164153 0, 1682667.69999999995343387 499976.59999999997671694 0, 1682656 499965.40000000002328306 0, 1682642.60000000009313226 499962.70000000001164153 0, 1682629.19999999995343387 499960 0, 1682615.80000000004656613 499957.29999999998835847 0, 1682597.10000000009313226 499956.90000000002328306 0, 1682578.5 499956.5 0, 1682561 499955 0, 1682543.5 499953.59999999997671694 0, 1682526 499952.09999999997671694 0, 1682508.5 499950.59999999997671694 0, 1682492.10000000009313226 499944.79999999998835847 0, 1682475.69999999995343387 499939 0, 1682459.19999999995343387 499933.20000000001164153 0, 1682448.80000000004656613 499923.79999999998835847 0, 1682438.39999999990686774 499914.40000000002328306 0, 1682424.19999999995343387 499904.90000000002328306 0, 1682409.89999999990686774 499895.29999999998835847 0, 1682395.69999999995343387 499885.79999999998835847 0, 1682379.80000000004656613 499876.20000000001164153 0, 1682363.80000000004656613 499866.5 0, 1682348 499866.59999999997671694 0, 1682332.19999999995343387 499866.59999999997671694 0, 1682316.39999999990686774 499866.59999999997671694 0)");
		
		List<LineString> lines = new ArrayList<LineString>();
		lines.add(halfLake1);
		lines.add(halfLake2);
		lines.add(nearbyRiver);
				
		//convert the two lake halves into a single polygon
		Polygonizer polygonizer = new Polygonizer();
		polygonizer.add(halfLake1);
		polygonizer.add(halfLake2);
		Collection<Polygon> polys = polygonizer.getPolygons();
		Polygon lake = polys.iterator().next();
		System.out.println(lake);
		
		SimpleFeatureSource fs = TestHelper.createLineStringFeatureSource(lines, "lines");
				
		//choose a point from the lake polygon, and find the feature nearest
		//to that point, but exclude features that the lake touches. 
		//(expect the nearest will be the river)
		NearestFinder nearestFinder = new NearestFinder(fs.getFeatures());		
		Point pointInLakePoly = SpatialUtils.toPoint(lake.getCoordinate());
		Geometry exclude = lake;
		SimpleFeature nearestFeat = nearestFinder.findNearest(pointInLakePoly, exclude);
		Geometry nearestGeom = (Geometry)nearestFeat.getDefaultGeometry();
		

		
		Assert.isTrue(nearestGeom != null, "nearest feature expected to be not null");		
		Assert.isTrue(nearestGeom.equals(nearbyRiver), "nearest feature expected to be the 'nearby river', not the excluded lake");	
		
		
	}
	
}
