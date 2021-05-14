package ca.bc.gov.catchment.algorithms;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.geotools.data.DataUtilities;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.factory.CommonFactoryFinder;
import org.geotools.feature.DefaultFeatureCollection;
import org.geotools.feature.SchemaException;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.geometry.jts.JTSFactoryFinder;
import org.geotools.referencing.CRS;
import org.geotools.util.factory.Hints;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.filter.Filter;
import org.opengis.filter.FilterFactory2;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

import ca.bc.gov.catchment.tin.Edge;
import ca.bc.gov.catchment.tin.Triangle;
import ca.bc.gov.catchment.tin.TriangleBuilder;

public class TrianglesFromEdgesAlg {

	private SimpleFeatureSource triangleEdges;
	private String outTable;
	
	public TrianglesFromEdgesAlg(SimpleFeatureSource triangleEdges, String outTable) {
		this.triangleEdges = triangleEdges;
		this.outTable = outTable;
	}
	
	public SimpleFeatureCollection getTriangles() throws IOException {
		SimpleFeatureCollection inFeatureCollection = triangleEdges.getFeatures();
		DefaultFeatureCollection outPolyFeatureCollection = new DefaultFeatureCollection();

		System.out.println("Composing "+inFeatureCollection.size()+" edges into triangles...");
		SimpleFeatureIterator inIterator = inFeatureCollection.features();
		int nextFid = 0;
		int numProcessed = 0;
		int numNonCoveringTriangles = 0;
		while(inIterator.hasNext()) {
			SimpleFeature inFeature = inIterator.next();
			List<Triangle> touchingTriangles = identifyTouchingTriangles(inFeature, triangleEdges);
			List<Triangle> nonCoveringTriangles = filterOutCoveringTriangles(touchingTriangles);
			numNonCoveringTriangles += nonCoveringTriangles.size();
			
			if (nonCoveringTriangles.size() > 2) {
				System.out.println("Warning: more than two triangles identified touching edge: "+inFeature.getID());
			}
			
			SimpleFeatureType outPolyFeatureType = getPolyFeatureType();
			List<SimpleFeature> polyFeatures = toPolygonFeatures(nonCoveringTriangles, outPolyFeatureType, nextFid);
			outPolyFeatureCollection.addAll(polyFeatures);
			
			//List<SimpleFeature> centroidPointFeatures = toCentroidPointFeatures(nonCoveringTriangles, outCentroidFeatureType, nextFid);
			//outCentroidFeatureCollection.addAll(centroidPointFeatures);
			
			nextFid += numNonCoveringTriangles;
			
			numProcessed++;
			
			if (numProcessed % 100000 == 0) {
				System.out.println(numProcessed+" edges processed. "+numNonCoveringTriangles+" triangles formed. ");
				//break;
			}
			
		}
		inIterator.close();
		return outPolyFeatureCollection;

	}
	
	private SimpleFeatureType getPolyFeatureType() {
		
		//get SRID from the input data set (triangle edges)
		SimpleFeatureType inFeatureType = triangleEdges.getSchema();				
		CoordinateReferenceSystem crs = inFeatureType.getCoordinateReferenceSystem();
		int srid = -1;
		try {
			srid = CRS.lookupEpsgCode(crs, true);
		} catch (FactoryException e1) {
			throw new IllegalStateException("Unable to determine SRID.");
		}
				
		//convert output geometries back into features, then add them to a SimpleFeatureCollection
		SimpleFeatureType outFeatureType = null;
		try {
			outFeatureType = DataUtilities.createType(outTable, 
					"geometry:Polygon:srid="+srid+",triangle_id:int,aspect:int,slope:int"); 
		} catch (SchemaException e) {
			throw new IllegalStateException("Unable to create feature type for triangle polygons");
		}
		
		return outFeatureType;
	}
	
	private static List<Triangle> identifyTouchingTriangles(
			SimpleFeature inFeature, 
			SimpleFeatureSource tinEdgeFeatureSource) throws IOException {
		
		Hints filterHints = new Hints( Hints.FEATURE_2D, true ); // force 2D queries
		FilterFactory2 filterFactory = CommonFactoryFinder.getFilterFactory2(filterHints);
		GeometryFactory geometryFactory = JTSFactoryFinder.getGeometryFactory();
		
		Geometry inGeometry = (Geometry)inFeature.getDefaultGeometry();
		Coordinate[] inCoords = inGeometry.getCoordinates();
		Coordinate coordA = inCoords[0];
		Coordinate coordB = inCoords[1];
		
		String inGeometryPropertyName = tinEdgeFeatureSource.getSchema().getGeometryDescriptor().getLocalName();
		Point p1 = geometryFactory.createPoint(coordA);
		Point p2 = geometryFactory.createPoint(coordB);
		
		Filter touchesP1Filter = filterFactory.touches(filterFactory.property(inGeometryPropertyName), filterFactory.literal(p1));
		Filter touchesP2Filter = filterFactory.touches(filterFactory.property(inGeometryPropertyName), filterFactory.literal(p2));
		Filter allTouchesFilter = filterFactory.or(touchesP1Filter, touchesP2Filter);
		SimpleFeatureCollection touchingFeatures = tinEdgeFeatureSource.getFeatures(allTouchesFilter);
		
		Edge rootEdge = new Edge(coordA, coordB);
		TriangleBuilder tb = new TriangleBuilder(rootEdge);

		//processed edges touching first point of root edge
		SimpleFeatureIterator touchIt = touchingFeatures.features();
		while(touchIt.hasNext()) {
			SimpleFeature touchingFeature = touchIt.next();
			Geometry geom = (Geometry)touchingFeature.getDefaultGeometry();
			Edge edge = new Edge(geom.getCoordinates());
			tb.addEdge(edge);
		}
		touchIt.close();
		
		List<Triangle> completeTriangles = tb.getCompleteTriangles();
		
		return completeTriangles;
	}
	
	private static List<Triangle> filterOutCoveringTriangles(List<Triangle> triangles) {
		List<Triangle> result = new ArrayList<Triangle>();
		for(Triangle t1 : triangles) {
			Geometry g1 = t1.toPolygon();
			boolean keep = true;
			for(Triangle t2 : triangles) {
				if (t1 == t2) {
					continue;
				}
				Geometry g2 = t2.toPolygon();
				if (g1.covers(g2)) {
					keep = false;
					break;
				}
			}
			if (keep) {
				result.add(t1);
			}
		}
		if (triangles.size() > 2 && result.size() > 2) {
			System.out.println("failed to filter out covering feature.  "+triangles.size() +" -> "+result.size());
		}
		return result;
	}
	
	private static List<SimpleFeature> toPolygonFeatures(List<Triangle> triangles, SimpleFeatureType outPolyFeatureType, int nextFid) {
		List<SimpleFeature> results = new ArrayList<SimpleFeature>();
		SimpleFeatureBuilder featureBuilder = new SimpleFeatureBuilder(outPolyFeatureType);
		for(Triangle t : triangles) {
			nextFid++;
			
			Object[] attrValues = new Object[4];
				
			double slope = Double.NaN;
			double aspect = Double.NaN;
			if (t.is3D()) {
				try {
					double[] slopeAndAspect = t.getSlopeAndAspect();
					slope = slopeAndAspect[0];
					aspect = slopeAndAspect[1];
				}
				catch (Exception e) {
					//should not occur if triangle is 3D
					e.printStackTrace();
				}
			}
			attrValues[0] = t.toPolygon();
			attrValues[1] = t.getId();
			attrValues[2] = slope;
			attrValues[3] = aspect;

			SimpleFeature triangleFeature = featureBuilder.buildFeature(t.getId()+"", attrValues);
			results.add(triangleFeature);
		}
		return results;						
	}
}
