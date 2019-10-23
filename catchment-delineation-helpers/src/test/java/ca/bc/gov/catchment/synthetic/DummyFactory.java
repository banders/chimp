package ca.bc.gov.catchment.synthetic;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.geotools.data.DataUtilities;
import org.geotools.data.collection.SpatialIndexFeatureCollection;
import org.geotools.data.collection.SpatialIndexFeatureSource;
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
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.tinfour.common.IQuadEdge;
import org.tinfour.common.Vertex;
import org.tinfour.standard.IncrementalTin;

import ca.bc.gov.catchment.algorithms.TrianglesFromEdgesAlg;
import ca.bc.gov.catchment.routes.LineStringRouter;
import ca.bc.gov.catchment.routes.RouteException;

public class DummyFactory {
	
	private static final int SRID = 3005;
	public static final Coordinate RIVER_UPSTREAM_1_START = new Coordinate(7, 0, 10);
	public static final Coordinate RIVER_UPSTREAM_2_START = new Coordinate(8, 0, 10);
	public static final Coordinate RIVER_CONFLUENCE = new Coordinate(7, 1, 10);
	public static final Coordinate RIVER_MAIN_END = new Coordinate(8, 9, 10);
	
	public static SimpleFeatureSource createDummyTinEdges() throws IOException {
		
		//create a dummy TIN
		IncrementalTin tin = new IncrementalTin();
		tin.add(new Vertex(0, 0, 10));
		tin.add(new Vertex(1, 0, 11));
		tin.add(new Vertex(3, 2, 12));
		tin.add(new Vertex(5, 1, 11));
		tin.add(new Vertex(6, 3, 10));
		tin.add(new Vertex(RIVER_CONFLUENCE.getX(), RIVER_CONFLUENCE.getY(), RIVER_CONFLUENCE.getZ()));
		tin.add(new Vertex(RIVER_UPSTREAM_1_START.getX(), RIVER_UPSTREAM_1_START.getY(), RIVER_UPSTREAM_1_START.getZ()));
		tin.add(new Vertex(RIVER_UPSTREAM_2_START.getX(), RIVER_UPSTREAM_2_START.getY(), RIVER_UPSTREAM_2_START.getZ()));
		tin.add(new Vertex(9, 2, 13));
		tin.add(new Vertex(11, 3, 14));
		tin.add(new Vertex(12, 2, 15));
		tin.add(new Vertex(13, 3, 15));
		tin.add(new Vertex(15, 2, 14));
		//
		tin.add(new Vertex(1, 3, 11));
		tin.add(new Vertex(2, 6, 13));
		tin.add(new Vertex(4, 6, 12));
		tin.add(new Vertex(5, 4, 13));
		tin.add(new Vertex(7, 5, 10));
		tin.add(new Vertex(8, 5, 10));
		tin.add(new Vertex(10, 4, 14));
		tin.add(new Vertex(11, 6, 16));
		tin.add(new Vertex(13, 5, 15));
		tin.add(new Vertex(14, 4, 15));
		//
		tin.add(new Vertex(0, 7, 12));
		tin.add(new Vertex(2, 8, 13));
		tin.add(new Vertex(4, 9, 12));
		tin.add(new Vertex(5, 9, 14));
		tin.add(new Vertex(6, 8, 11));
		tin.add(new Vertex(RIVER_MAIN_END.getX(), RIVER_MAIN_END.getY(), RIVER_MAIN_END.getZ()));
		tin.add(new Vertex(9, 7, 12));
		tin.add(new Vertex(10, 9, 12));
		tin.add(new Vertex(13, 7, 13));
		tin.add(new Vertex(14, 8, 14));
		tin.add(new Vertex(15, 7, 13));
		//
		tin.add(new Vertex(0, 10, 13));
		tin.add(new Vertex(1, 12, 12));
		tin.add(new Vertex(2, 10, 12));
		tin.add(new Vertex(4, 11, 14));
		tin.add(new Vertex(5, 11, 12));
		tin.add(new Vertex(7, 12, 10));
		tin.add(new Vertex(9, 10, 10));
		tin.add(new Vertex(11, 10, 11));
		tin.add(new Vertex(12, 12, 13));
		tin.add(new Vertex(13, 13, 12));
		tin.add(new Vertex(14, 11, 13));
		
		GeometryFactory geometryFactory = JTSFactoryFinder.getGeometryFactory();
		DefaultFeatureCollection tinEdges = new DefaultFeatureCollection();
		
		SimpleFeatureType outFeatureType = null;
		try {
			outFeatureType = DataUtilities.createType("tin_edges", "geometry:LineString:srid="+SRID);
		} catch (SchemaException e1) {
			throw new IllegalStateException("Unable to create feature type for tin_edges");
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
	
	public static SimpleFeatureSource createDummyTinPolys(SimpleFeatureSource tinEdges) throws IOException {
		TrianglesFromEdgesAlg alg = new TrianglesFromEdgesAlg(tinEdges, "tin_polys");
		SimpleFeatureCollection fc = alg.getTriangles();
		
		SpatialIndexFeatureCollection indexedCollection = new SpatialIndexFeatureCollection(fc);
		SimpleFeatureSource result = new SpatialIndexFeatureSource(indexedCollection); 
		return result;
	}
	
	public static SimpleFeatureSource createDummyWaterFeatures() throws IOException, RouteException {
		SimpleFeatureSource tinEdges = createDummyTinEdges();
		LineStringRouter router = new LineStringRouter(tinEdges);
		
		List<LineString> routes = new ArrayList<LineString>();
		LineString riverUpstream1Route = router.makeRoute(RIVER_UPSTREAM_1_START, RIVER_CONFLUENCE);
		LineString riverUpstream2Route = router.makeRoute(RIVER_UPSTREAM_2_START, RIVER_CONFLUENCE);
		LineString riverRoute = router.makeRoute(RIVER_CONFLUENCE, RIVER_MAIN_END);
		routes.add(riverUpstream1Route);
		routes.add(riverUpstream2Route);
		routes.add(riverRoute);
		
		//convert geometry to feature
		SimpleFeatureType outFeatureType = null;
		try {
			outFeatureType = DataUtilities.createType("water_features", "geometry:LineString:srid="+SRID);
		} catch (SchemaException e1) {
			throw new IllegalStateException("Unable to create feature type for water_features");
		}
		SimpleFeatureBuilder outFeatureBuilder = new SimpleFeatureBuilder(outFeatureType);

		//add to FeatureCollection
		DefaultFeatureCollection fc = new DefaultFeatureCollection();
		int fid = 0;
		for (LineString route : routes) {
			Object[] values = {route};
			SimpleFeature riverFeature = outFeatureBuilder.buildFeature(""+fid++, values);
			fc.add(riverFeature);
		}
		
		SpatialIndexFeatureCollection indexedFc = new SpatialIndexFeatureCollection(fc);
		SpatialIndexFeatureSource fs = new SpatialIndexFeatureSource(indexedFc);
		
		return fs;
		
	}
	
	public static SimpleFeatureSource createDummyCatchments() throws IOException, RouteException {
		SimpleFeatureSource tinEdges = createDummyTinEdges();
		LineStringRouter router = new LineStringRouter(tinEdges);
		
		List<Geometry> catchmentSections = new ArrayList<Geometry>();
		
		
		Coordinate[] middleSectionIncludes = {
				new Coordinate(5, 11, 12),
				new Coordinate(11, 10, 11)
		};
		Coordinate[] middleSectionExcludes = {
				RIVER_MAIN_END
		};
		Coordinate[] startSectionIncludes = {
				RIVER_CONFLUENCE,
				new Coordinate(5,1, 11),
				middleSectionIncludes[0]
		};
		
		LineString catchmentSection1 = router.makeRoute(startSectionIncludes);
		LineString catchmentSection2 = router.makeRoute(middleSectionIncludes, middleSectionExcludes);
		LineString catchmentSection3 = router.makeRoute(middleSectionIncludes[middleSectionIncludes.length-1], RIVER_CONFLUENCE);
		catchmentSections.add(catchmentSection1);
		catchmentSections.add(catchmentSection2);
		catchmentSections.add(catchmentSection3);		
		
		SimpleFeatureType outFeatureType = null;
		try {
			outFeatureType = DataUtilities.createType("catchment_lines", "geometry:LineString:srid="+SRID);
		} catch (SchemaException e1) {
			throw new IllegalStateException("Unable to create feature type for catchment_lines");
		}
		SimpleFeatureBuilder outFeatureBuilder = new SimpleFeatureBuilder(outFeatureType);
		
		//convert geometries to features, then add to a collection
		DefaultFeatureCollection fc = new DefaultFeatureCollection();
		int fid = 1;
		for (Geometry geom : catchmentSections) {
			Object[] values = {geom};
			SimpleFeature feature = outFeatureBuilder.buildFeature(""+fid++, values);
			fc.add(feature);
		}
		
		
		//convert collection to feature source
		SpatialIndexFeatureCollection indexedFc = new SpatialIndexFeatureCollection(fc);
		SpatialIndexFeatureSource fs = new SpatialIndexFeatureSource(indexedFc);
		
		return fs;
		
	}
}
