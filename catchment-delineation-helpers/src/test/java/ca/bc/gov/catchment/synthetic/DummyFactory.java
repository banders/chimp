package ca.bc.gov.catchment.synthetic;

import java.io.IOException;
import java.util.ArrayList;
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
import ca.bc.gov.catchment.routes.LineStringRouter;
import ca.bc.gov.catchment.routes.RouteException;
import ca.bc.gov.catchment.tin.TinEdges;
import ca.bc.gov.catchment.tin.Triangle;
import ca.bc.gov.catchment.utils.SpatialUtils;

public class DummyFactory {
	
	private static final int SRID = 3005;
	public static final Coordinate RIVER_UPSTREAM_1_START = new Coordinate(7, 0, 10);
	public static final Coordinate RIVER_UPSTREAM_2_START = new Coordinate(8, 0, 10);
	public static final Coordinate RIVER_CONFLUENCE = new Coordinate(7, 1, 10);
	public static final Coordinate RIVER_MAIN_END = new Coordinate(8, 9, 10);
	
	public static SimpleFeatureSource createDummyTinEdges() throws IOException {
		
		SimpleFeatureSource pointCloud3d = createDummyPointCloud3d();
		
		//create a dummy TIN
		IncrementalTin tin = new IncrementalTin();
		SimpleFeatureCollection pointCloudFc = pointCloud3d.getFeatures();
		SimpleFeatureIterator pointCloudIt = pointCloudFc.features();
		while(pointCloudIt.hasNext()) {
			SimpleFeature feature = pointCloudIt.next();
			Point point = (Point)feature.getDefaultGeometry();
			Coordinate coord = point.getCoordinate();
			Vertex vertex = new Vertex(coord.getX(), coord.getY(), coord.getZ());
			tin.add(vertex);
		}
		pointCloudIt.close();		
		
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
		TinEdges tinEdges = new TinEdges(createDummyTinEdges());
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
		TinEdges tinEdges = new TinEdges(createDummyTinEdges());
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
		Coordinate[] endSectionIncludes = {
				middleSectionIncludes[middleSectionIncludes.length-1],
				new Coordinate(13, 5, 15),
				RIVER_CONFLUENCE
		};
		
		LineString catchmentSection1 = router.makeRoute(startSectionIncludes);
		LineString catchmentSection2 = router.makeRoute(middleSectionIncludes, middleSectionExcludes);
		LineString catchmentSection3 = router.makeRoute(endSectionIncludes);
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
	
	/**
	 * Creates a TIN polygon feature source from two triangles.
	 * @param t1
	 * @param t2
	 * @param tableName
	 * @return
	 * @throws IOException
	 */
	public static SimpleFeatureSource createTinPolys(Triangle t1, Triangle t2, String tableName) throws IOException {
		DefaultFeatureCollection fc = new DefaultFeatureCollection();
		
		SimpleFeatureType featureType = null;
		try {
			featureType = DataUtilities.createType(tableName, "geometry:Polygon");
		} catch (SchemaException e1) {
			throw new IllegalStateException("Unable to create feature type "+tableName);
		}
		
		Geometry g1 = t1.toPolygon();
		Geometry g2 = t2.toPolygon();
		
		SimpleFeature f1 = SpatialUtils.geomToFeature(g1, featureType, "1");
		SimpleFeature f2 = SpatialUtils.geomToFeature(g2, featureType, "2");
		fc.add(f1);
		fc.add(f2);
		
		SpatialIndexFeatureCollection indexedFc = new SpatialIndexFeatureCollection(fc);
		SpatialIndexFeatureSource result = new SpatialIndexFeatureSource(indexedFc);
		return result;
	}
	
	public static SimpleFeatureSource createTinPolys(List<Triangle> triangles, String tableName) throws IOException {
		DefaultFeatureCollection fc = new DefaultFeatureCollection();
		
		SimpleFeatureType featureType = null;
		try {
			featureType = DataUtilities.createType(tableName, "geometry:Polygon");
		} catch (SchemaException e1) {
			throw new IllegalStateException("Unable to create feature type "+tableName);
		}
		
		int index = 0;
		for(Triangle t : triangles) {
			Geometry g = t.toPolygon();		
			SimpleFeature f = SpatialUtils.geomToFeature(g, featureType, ""+index);		
			fc.add(f);
			index++;
		}
		
		SpatialIndexFeatureCollection indexedFc = new SpatialIndexFeatureCollection(fc);
		SpatialIndexFeatureSource result = new SpatialIndexFeatureSource(indexedFc);
		return result;
	}
	
	
	public static SimpleFeatureSource createDummyPointCloud3d() throws IOException {
		
		Coordinate[] coords = {
			new Coordinate(0, 0, 10),
			new Coordinate(1, 0, 11),
			new Coordinate(3, 2, 12),
			new Coordinate(5, 1, 11),
			new Coordinate(6, 3, 10),
			new Coordinate(RIVER_CONFLUENCE.getX(), RIVER_CONFLUENCE.getY(), RIVER_CONFLUENCE.getZ()),
			new Coordinate(RIVER_UPSTREAM_1_START.getX(), RIVER_UPSTREAM_1_START.getY(), RIVER_UPSTREAM_1_START.getZ()),
			new Coordinate(RIVER_UPSTREAM_2_START.getX(), RIVER_UPSTREAM_2_START.getY(), RIVER_UPSTREAM_2_START.getZ()),
			new Coordinate(9, 2, 13),
			new Coordinate(11, 3, 14),
			new Coordinate(12, 2, 15),
			new Coordinate(13, 3, 15),
			new Coordinate(15, 2, 14),
			//
			new Coordinate(1, 3, 11),
			new Coordinate(2, 6, 13),
			new Coordinate(4, 6, 1),
			new Coordinate(5, 4, 13),
			new Coordinate(7, 5, 10),
			new Coordinate(8, 5, 10),
			new Coordinate(10, 4, 14),
			new Coordinate(11, 6, 16),
			new Coordinate(13, 5, 15),
			new Coordinate(14, 4, 15),
			//
			new Coordinate(0, 7, 12),
			new Coordinate(2, 8, 13),
			new Coordinate(4, 9, 12),
			new Coordinate(5, 9, 14),
			new Coordinate(6, 8, 11),
			new Coordinate(RIVER_MAIN_END.getX(), RIVER_MAIN_END.getY(), RIVER_MAIN_END.getZ()),
			new Coordinate(9, 7, 10),
			new Coordinate(10, 9, 12),
			new Coordinate(13, 7, 13),
			new Coordinate(14, 8, 14),
			new Coordinate(15, 7, 13),
			//
			new Coordinate(0, 10, 13),
			new Coordinate(1, 12, 12),
			new Coordinate(2, 10, 12),
			new Coordinate(4, 11, 14),
			new Coordinate(5, 11, 12),
			new Coordinate(7, 12, 10),
			new Coordinate(9, 10, 10),
			new Coordinate(11, 10, 11),
			new Coordinate(12, 12, 13),
			new Coordinate(13, 13, 12),
			new Coordinate(14, 11, 13)
		};
				
		SimpleFeatureType outFeatureType = null;
		try {
			outFeatureType = DataUtilities.createType("point_cloud_3d", "geometry:Point:srid="+SRID);
		} catch (SchemaException e1) {
			throw new IllegalStateException("Unable to create feature type for point_cloud_3d");
		}
		SimpleFeatureBuilder outFeatureBuilder = new SimpleFeatureBuilder(outFeatureType);
		GeometryFactory geometryFactory = JTSFactoryFinder.getGeometryFactory();
		
		//convert geometries to features, then add to a collection
		DefaultFeatureCollection fc = new DefaultFeatureCollection();
		int fid = 1;
		for (Coordinate coord : coords) {
			Point point = geometryFactory.createPoint(coord);
			Object[] values = {point};
			SimpleFeature feature = outFeatureBuilder.buildFeature(""+fid++, values);
			fc.add(feature);
		}
		
		//convert collection to feature source
		SpatialIndexFeatureCollection indexedFc = new SpatialIndexFeatureCollection(fc);
		SpatialIndexFeatureSource fs = new SpatialIndexFeatureSource(indexedFc);
		
		return fs;
		
	}
	
}
