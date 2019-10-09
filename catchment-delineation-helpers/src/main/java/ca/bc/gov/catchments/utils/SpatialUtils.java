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
	
	public static SimpleFeatureCollection geomCollectionToSimpleFeatureCollection(Collection<Geometry> geometries, SimpleFeatureType featureType) {
		
		DefaultFeatureCollection featureCollection = new DefaultFeatureCollection();
		SimpleFeatureBuilder featureBuilder = new SimpleFeatureBuilder(featureType);
		int nextFid = 0;
		for(Geometry geometry : geometries) {
			Object[] attrValues = {geometry};
			SimpleFeature feature = featureBuilder.buildFeature(""+nextFid++, attrValues);
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
	
	public static SimpleFeatureSource createDummyTin() throws IOException {
	
		//create a dummy TIN
		IncrementalTin tin = new IncrementalTin();
		tin.add(new Vertex(0, 0, 0));
		tin.add(new Vertex(1, 0, 0));
		tin.add(new Vertex(3, 2, 0));
		tin.add(new Vertex(5, 1, 0));
		tin.add(new Vertex(6, 3, 0));
		tin.add(new Vertex(7, 1, 0));
		tin.add(new Vertex(9, 2, 0));
		//
		tin.add(new Vertex(1, 3, 0));
		tin.add(new Vertex(2, 6, 0));
		tin.add(new Vertex(4, 6, 0));
		tin.add(new Vertex(5, 4, 0));
		tin.add(new Vertex(7, 5, 0));
		tin.add(new Vertex(8, 5, 0));
		//
		tin.add(new Vertex(0, 7, 0));
		tin.add(new Vertex(2, 8, 0));
		tin.add(new Vertex(4, 9, 0));
		tin.add(new Vertex(5, 9, 0));
		tin.add(new Vertex(6, 8, 0));
		tin.add(new Vertex(8, 9, 0));
		tin.add(new Vertex(9, 7, 0));
		
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
	
}
