package ca.bc.gov.catchments.utils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import org.geotools.data.DataUtilities;
import org.geotools.data.collection.SpatialIndexFeatureCollection;
import org.geotools.data.collection.SpatialIndexFeatureSource;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.feature.DefaultFeatureCollection;
import org.geotools.feature.SchemaException;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.geometry.jts.JTSFactoryFinder;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.tinfour.common.IQuadEdge;
import org.tinfour.common.Vertex;
import org.tinfour.standard.IncrementalTin;

import ca.bc.gov.catchment.algorithms.TrianglesFromEdgesAlg;

public class SpatialUtils {

	public static SimpleFeature copyFeature(SimpleFeature feature, SimpleFeatureType newFeatureType) {
		SimpleFeatureBuilder fb = new SimpleFeatureBuilder(newFeatureType);
		Geometry geometry = (Geometry)feature.getDefaultGeometry();
		Object[] attributeValues = new Object[] { geometry };
		SimpleFeature copiedFeature = fb.buildFeature(feature.getID(), attributeValues);
		return copiedFeature;
	}

	/**
	 * Extracts just the geometry from each features, then returns a collection of those geometries
	 * @param fc
	 * @return
	 */
	public static List<SimpleFeature> simpleFeatureCollectionToFeatList(SimpleFeatureCollection fc) {
		List<SimpleFeature> features = new ArrayList<SimpleFeature>();
		SimpleFeatureIterator it = fc.features();
		while(it.hasNext()) {
			SimpleFeature feature = it.next();
			features.add(feature);
		}
		it.close();
		return features;
	}
	
	/**
	 * Extracts just the geometry from each features, then returns a collection of those geometries
	 * @param fc
	 * @return
	 */
	public static Collection<Geometry> simpleFeatureCollectionToGeomCollection(SimpleFeatureCollection fc) {
		Collection<Geometry> geometries = new ArrayList<Geometry>();
		SimpleFeatureIterator it = fc.features();
		while(it.hasNext()) {
			SimpleFeature feature = it.next();
			Geometry geometry = (Geometry)feature.getDefaultGeometry();
			geometries.add(geometry);
		}
		it.close();
		return geometries;
	}
	

	public static SimpleFeature geomToFeature(Geometry geom, SimpleFeatureType type, String fid) {
		SimpleFeatureBuilder featureBuilder = new SimpleFeatureBuilder(type);
		Object[] attrValues = {geom};
		SimpleFeature feature = featureBuilder.buildFeature(fid, attrValues);
		return feature;
	}
	
	public static SimpleFeatureCollection geomCollectionToSimpleFeatureCollection(Collection<Geometry> geometries, SimpleFeatureType featureType) {
		
		DefaultFeatureCollection featureCollection = new DefaultFeatureCollection();
		
		int nextFid = 0;
		for(Geometry geometry : geometries) {
			SimpleFeature feature = geomToFeature(geometry, featureType, nextFid+"");			
			featureCollection.add(feature);
			nextFid++;
		}
		
		return featureCollection;
	}
	
	public static Coordinate[] removeDuplicateCoordinates(Coordinate[] coordinates) {
		List<Coordinate> coordList = new ArrayList<Coordinate>();
		Coordinate prev = null;
		for(int i =0; i < coordinates.length; i++) {
			Coordinate c = coordinates[i];
			if (prev == null || (prev.getX() != c.getX() || prev.getY() != c.getY())) {
				coordList.add(c);
			}
			prev = c;
		}
		Coordinate[] result = coordList.toArray(new Coordinate[coordList.size()]);
		return result;
	}
	
	

	public static LineString toLineString(Coordinate c1, Coordinate c2) {
		List<Coordinate> coords = new ArrayList<Coordinate>();
		coords.add(c1);
		coords.add(c2);
		return toLineString(coords);
	}
	
	public static LineString toLineString(List<Coordinate> in) {
		GeometryFactory geometryFactory = JTSFactoryFinder.getGeometryFactory();
		Coordinate[] coords = toCoordinateArray(in);
		return geometryFactory.createLineString(coords);
	}
	
	public static List<LineString> toSegments(LineString s) {
		List<LineString> segments = new ArrayList<LineString>();
		Coordinate prev = null;
		for(Coordinate c : s.getCoordinates()) {
			if (prev != null) {
				LineString segment = toLineString(prev, c);
				segments.add(segment);
			}
			prev = c;
		}
		return segments;
	}

	public static LineString[] toLineStringArray(List<LineString> in) {
		if (in == null) {
			return new LineString[0];
		}
		
		LineString[] result = new LineString[in.size()];		
		int index = 0;
		for(LineString ls : in) {
			result[index] = ls;
			index++;
		}
		return result;
	}
		
	public static Coordinate[] toCoordinateArray(List<Coordinate> in) {
		if (in == null) {
			return new Coordinate[0];
		}
		
		Coordinate[] coords = new Coordinate[in.size()];		
		int index = 0;
		for(Coordinate c : in) {
			coords[index] = c;
			index++;
		}
		return coords;
	}
	
	public static List<Coordinate> toCoordinateList(Coordinate[] in) {
		List<Coordinate> coords = new ArrayList<Coordinate>();
		if (in == null) {
			return coords;
		}
		
		for(Coordinate c : in) {
			coords.add(c);
		}
		return coords;
	}
	
	public static SimpleFeatureCollection renameFeatureType(SimpleFeatureCollection fc, String tableName) throws SchemaException {
		SimpleFeatureType originalFeatureType = fc.getSchema();		
		String spec = DataUtilities.encodeType(originalFeatureType);
		SimpleFeatureType newFeatureType = DataUtilities.createType(tableName, spec);
				
		DefaultFeatureCollection outFc = new DefaultFeatureCollection();
		
		SimpleFeatureIterator inIt = fc.features();
		while(inIt.hasNext()) {
			SimpleFeature f = inIt.next();
			SimpleFeature modified = SimpleFeatureBuilder.retype(f, newFeatureType);
			outFc.add(modified);
		}
		inIt.close();
		
		
		return outFc;	
	}
	
	public static boolean hasCoordinate(Geometry g, Coordinate c) {
		GeometryFactory geometryFactory = JTSFactoryFinder.getGeometryFactory();
		Point p = geometryFactory.createPoint(c);
		boolean hasCoordinate = g.distance(p) == 0;
		return hasCoordinate;		
	}
	
	public static boolean is3D(Geometry g) {
		Coordinate[] coords = g.getCoordinates();
		for(Coordinate coord : coords) {
			if (Double.isNaN(coord.getZ())) {
				return false;
			}
		}
		return true;
	}
}
