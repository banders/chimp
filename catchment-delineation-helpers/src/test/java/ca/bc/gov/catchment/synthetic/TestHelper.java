package ca.bc.gov.catchment.synthetic;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.geotools.data.DataUtilities;
import org.geotools.data.collection.CollectionFeatureSource;
import org.geotools.data.collection.SpatialIndexFeatureCollection;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.feature.DefaultFeatureCollection;
import org.geotools.feature.SchemaException;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.geometry.jts.JTSFactoryFinder;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKTReader;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.geometry.PositionFactory;

import ca.bc.gov.catchments.utils.SaveUtils;

public class TestHelper {

	public static SimpleFeatureSource createLineStringFeatureSource(LineString route, String tableName) { 
		List<LineString> routes = new ArrayList<LineString>();
		routes.add(route);
		return createLineStringFeatureSource(routes, tableName);
	}
	
	public static SimpleFeatureSource createLineStringFeatureSource(List<LineString> routes, String tableName) { 
	
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
	
	public static SimpleFeatureSource createPointFeatureSource(List<Coordinate> coords) { 
		
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
	
	public static void save(SimpleFeatureSource fs, String filename) throws IOException {
		SaveUtils.saveToGeoPackage(filename, fs.getFeatures());
	}
	
	public static void save(SimpleFeatureCollection fc, String filename) throws IOException {
		SaveUtils.saveToGeoPackage(filename, fc);
	}
	
	public static Geometry geometryFromWkt(String wkt) throws ParseException {
		GeometryFactory geometryFactory = JTSFactoryFinder.getGeometryFactory();
		WKTReader reader = new WKTReader( geometryFactory );
		Geometry geom = reader.read(wkt);
		return geom;
	}
	
}
