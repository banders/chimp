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
	

	public static SimpleFeature geomToFeature(Geometry geom, SimpleFeatureType type, int fid) {
		SimpleFeatureBuilder featureBuilder = new SimpleFeatureBuilder(type);
		Object[] attrValues = {geom};
		SimpleFeature feature = featureBuilder.buildFeature(""+fid++, attrValues);
		return feature;
	}
	
	public static SimpleFeatureCollection geomCollectionToSimpleFeatureCollection(Collection<Geometry> geometries, SimpleFeatureType featureType) {
		
		DefaultFeatureCollection featureCollection = new DefaultFeatureCollection();
		
		int nextFid = 0;
		for(Geometry geometry : geometries) {
			SimpleFeature feature = geomToFeature(geometry, featureType, nextFid);			
			featureCollection.add(feature);
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
	
	public static SimpleFeatureSource createDummyTinEdges() throws IOException {
	
		//create a dummy TIN
		IncrementalTin tin = new IncrementalTin();
		tin.add(new Vertex(0, 0, 10));
		tin.add(new Vertex(1, 0, 11));
		tin.add(new Vertex(3, 2, 12));
		tin.add(new Vertex(5, 1, 11));
		tin.add(new Vertex(6, 3, 12));
		tin.add(new Vertex(7, 1, 10));
		tin.add(new Vertex(9, 2, 13));
		//
		tin.add(new Vertex(1, 3, 11));
		tin.add(new Vertex(2, 6, 13));
		tin.add(new Vertex(4, 6, 12));
		tin.add(new Vertex(5, 4, 13));
		tin.add(new Vertex(7, 5, 14));
		tin.add(new Vertex(8, 5, 12));
		//
		tin.add(new Vertex(0, 7, 12));
		tin.add(new Vertex(2, 8, 13));
		tin.add(new Vertex(4, 9, 12));
		tin.add(new Vertex(5, 9, 14));
		tin.add(new Vertex(6, 8, 11));
		tin.add(new Vertex(8, 9, 10));
		tin.add(new Vertex(9, 7, 12));
		
		GeometryFactory geometryFactory = JTSFactoryFinder.getGeometryFactory();
		DefaultFeatureCollection tinEdges = new DefaultFeatureCollection();
		
		SimpleFeatureType outFeatureType = null;
		try {
			outFeatureType = DataUtilities.createType("tin_edges", "geometry:LineString");
		} catch (SchemaException e1) {
			throw new IllegalStateException("Unable to create feature type for tin edges");
		}
		SimpleFeatureBuilder outFeatureBuilder = new SimpleFeatureBuilder(outFeatureType);
		
		
		//get an iterator over the TIN edges
		Iterator<IQuadEdge> edgeIt = tin.edges().iterator();
		int nextId = 0;
		while(edgeIt.hasNext()) {
			IQuadEdge edge = edgeIt.next();
			Vertex a = edge.getA();
			Vertex b = edge.getB();
			
			//create a linestring geometry with these two endpoints
			Coordinate c1 = new Coordinate(a.getX(), a.getY(), a.getZ());
			Coordinate c2 = new Coordinate(b.getX(), b.getY(), b.getZ());
			
			Coordinate[] coordArr = {c1, c2};
			LineString geometry = geometryFactory.createLineString(coordArr);
			Object[] values = {geometry};
			SimpleFeature feature = outFeatureBuilder.buildFeature(""+nextId++, values);
			tinEdges.add(feature);
		}
		
		SpatialIndexFeatureCollection indexedCollection = new SpatialIndexFeatureCollection(tinEdges);
		SimpleFeatureSource result = new SpatialIndexFeatureSource(indexedCollection); 
		return result;
	}
	
	public static SimpleFeatureSource createDummyTinPolys() throws IOException {
		SimpleFeatureSource tinEdges = createDummyTinEdges();
		TrianglesFromEdgesAlg alg = new TrianglesFromEdgesAlg(tinEdges, "tin_polys");
		SimpleFeatureCollection fc = alg.getTriangles();
		
		SpatialIndexFeatureCollection indexedCollection = new SpatialIndexFeatureCollection(fc);
		SimpleFeatureSource result = new SpatialIndexFeatureSource(indexedCollection); 
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
}
