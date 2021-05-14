package ca.bc.gov.catchment.scripts;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.geotools.data.DataStore;
import org.geotools.data.DataStoreFinder;
import org.geotools.data.DataUtilities;
import org.geotools.data.FeatureReader;
import org.geotools.data.FeatureWriter;
import org.geotools.data.FileDataStoreFactorySpi;
import org.geotools.data.FileDataStoreFinder;
import org.geotools.data.Query;
import org.geotools.data.Transaction;
import org.geotools.data.collection.SpatialIndexFeatureCollection;
import org.geotools.data.collection.SpatialIndexFeatureSource;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.data.simple.SimpleFeatureWriter;
import org.geotools.factory.CommonFactoryFinder;
import org.geotools.feature.DefaultFeatureCollection;
import org.geotools.feature.FeatureCollection;
import org.geotools.feature.SchemaException;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.geometry.jts.JTSFactoryFinder;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.geopkg.FeatureEntry;
import org.geotools.geopkg.GeoPackage;
import org.geotools.geopkg.GeoPkgDataStoreFactory;
import org.geotools.referencing.CRS;
import org.geotools.util.factory.Hints;
import org.hsqldb.lib.Set;
import org.locationtech.jts.densify.Densifier;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.PrecisionModel;
import org.locationtech.jts.simplify.DouglasPeuckerSimplifier;
import org.locationtech.jts.simplify.TopologyPreservingSimplifier;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.FeatureType;
import org.opengis.filter.Filter;
import org.opengis.filter.FilterFactory2;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

import ca.bc.gov.catchment.tin.Edge;
import ca.bc.gov.catchment.tin.Triangle;
import ca.bc.gov.catchment.tin.TriangleBuilder;

public class IdentifyTriangles {

	private static final String GEOPKG_ID = "geopkg";
	private static final double DEFAULT_TOUCHES_DISTANCE_TOLERANCE = 0.05; //5cm
	
	
	public static void main(String[] args) {
		
		// create Options object
		Options options = new Options();
		options.addOption("i", true, "Input GeoPackage file");
		options.addOption("o", true, "Output GeoPackage file");
		options.addOption("inTable", true, "input table name");
		options.addOption("outPolyTable", true, "output polygon table name");
		options.addOption("outCentroidTable", true, "output polygon table name");
		options.addOption("touchesDistanceTolerance", true, "touches distance tolerance");
		options.addOption("bbox", true, "bbox (minx,miny,maxx,maxy)");
		options.addOption("bboxcrs", true, "e.g. EPSG:3005");
		CommandLineParser parser = new DefaultParser();
		HelpFormatter formatter = new HelpFormatter();
		
		String inputGeopackageFilename = null;
		String outputGeopackageFilename = null;
		String inTable = null;
		String outPolyTable = null;
		String outCentroidTable = null;
		double touchesDistanceTolerance = 0;
		String bboxStr = null;
		String bboxCrs = null;
		int bboxSrid = -1;
		ReferencedEnvelope boundsToProcess = null;
		
		try {
			CommandLine cmd = parser.parse( options, args);
			inputGeopackageFilename = cmd.getOptionValue("i");
			outputGeopackageFilename = cmd.getOptionValue("o");	
			inTable = cmd.getOptionValue("inTable");
			outPolyTable = cmd.getOptionValue("outPolyTable");
			outCentroidTable = cmd.getOptionValue("outCentroidTable");
			touchesDistanceTolerance = Double.parseDouble(cmd.getOptionValue("touchesDistanceTolerance", DEFAULT_TOUCHES_DISTANCE_TOLERANCE+""));
			bboxStr = cmd.getOptionValue("bbox");
			bboxCrs = cmd.getOptionValue("bboxcrs");
		} catch (ParseException e2) {
			formatter.printHelp( WKTList2GeoPackage.class.getSimpleName(), options );
		}
		
		if(bboxStr != null) {
			String[] pieces = bboxStr.split(",");
			double minX = Double.parseDouble(pieces[0]);
			double minY = Double.parseDouble(pieces[1]);
			double maxX = Double.parseDouble(pieces[2]);
			double maxY = Double.parseDouble(pieces[3]);
			boundsToProcess = new ReferencedEnvelope(minX,maxX,minY,maxY, null);
		}
		if (bboxCrs != null) {
			if (bboxCrs.startsWith("EPSG:")) {
				String srid = bboxCrs.substring(5);
				bboxSrid = Integer.parseInt(srid);
			}
			else {
				System.out.println("Unknown bboxcrs: "+bboxCrs);
				System.exit(1);
			}
		}
		
		System.out.println("Inputs:");
		System.out.println("- in file: "+inputGeopackageFilename);
		System.out.println("- out file: "+outputGeopackageFilename);
		System.out.println("- in table: "+inTable);
		System.out.println("- out poly table: "+outPolyTable);
		System.out.println("- out centroid table: "+outCentroidTable);
		System.out.println("- touchesDistanceTolerance: "+touchesDistanceTolerance);
		if (bboxStr != null) {
			System.out.println("- bbox: "+bboxStr+" ("+bboxCrs+")");	
		}
		
		Map<String, String> inputDatastoreParams = new HashMap<String, String>();
		inputDatastoreParams.put("dbtype", GEOPKG_ID);
		inputDatastoreParams.put("database", inputGeopackageFilename);
		System.out.println(inputDatastoreParams);
		
		//Open input datastore
		//---------------------------------------------------------------------
		
		DataStore inDatastore = null;
		try {
			inDatastore = DataStoreFinder.getDataStore(inputDatastoreParams);
		} catch (IOException e) {
			System.out.println("Unable to open input file: "+inputGeopackageFilename);
			e.printStackTrace();
			System.exit(1);
		}
		
		if (inDatastore == null) {
			System.out.println("Unable to open input datastore");
			System.exit(1);
		}
		
		SimpleFeatureType inFeatureType = null;
		try {
			inFeatureType = inDatastore.getSchema(inTable);
		} catch (IOException e) {
			System.out.println("Unable to get schema for feature type "+inTable+" in the input datastore");
			e.printStackTrace();
		}
		
		String inGeometryPropertyName = inFeatureType.getGeometryDescriptor().getLocalName();
		
		CoordinateReferenceSystem crs = inFeatureType.getCoordinateReferenceSystem();
		int inSrid = -1;
		try {
			inSrid = CRS.lookupEpsgCode(crs, true);
		} catch (FactoryException e1) {
			System.out.println("Unable to lookup SRID for feature type "+inFeatureType);
		}
		
		SimpleFeatureSource inFeatureSource = null;
		try {
			inFeatureSource = inDatastore.getFeatureSource(inTable);
		} catch (IOException e1) {
			System.out.println("Unable to get in feature source: "+inTable);
			e1.printStackTrace();
			System.exit(1);
		}
		
		
		//SimpleFeatureSource inFeatureSourceCopy = null;
		try {
			System.out.println("Indexing...");
			SpatialIndexFeatureCollection fastInFeatureCollection = new SpatialIndexFeatureCollection(inFeatureSource.getFeatures());
			inFeatureSource = new SpatialIndexFeatureSource(fastInFeatureCollection);
			//inFeatureSourceCopy = new SpatialIndexFeatureSource(fastInFeatureCollection);
		}
		catch (IOException e) {
			System.out.println("Unable to index in feature source");
			e.printStackTrace();
			System.exit(1);
		}


		
		//Create output datastore
		//---------------------------------------------------------------------
	
		File outFile = new File(outputGeopackageFilename);
		Map<String, String> outputDatastoreParams = new HashMap<String, String>();
		outputDatastoreParams.put("dbtype", GEOPKG_ID);
		outputDatastoreParams.put("database", outputGeopackageFilename);

		SimpleFeatureType outPolyFeatureType = null;
		try {
			//columns:
			// aspect_of_max_slope: downhill direction of steepest slope (0degrees is east, 90degrees is north).  
			// max_slope:           the value of the steepest slope (0 degrees is horizontal, negative is downward from horizontal)
			outPolyFeatureType = DataUtilities.createType(outPolyTable, 
					"geometry:Polygon:srid="+inSrid+",tid,slope,aspect"); 
		} catch (SchemaException e1) {
			System.out.println("Unable to create feature type: "+outPolyTable);
			System.exit(1);
		}
		
		SimpleFeatureType outCentroidFeatureType = null;
		try {
			//columns:
			// aspect_of_max_slope: downhill direction of steepest slope (0degrees is east, 90degrees is north).  
			// max_slope:           the value of the steepest slope (0 degrees is horizontal, negative is downward from horizontal)
			outCentroidFeatureType = DataUtilities.createType(outCentroidTable, 
					"geometry:Point:srid="+inSrid+",tid,slope,aspect"); 
		} catch (SchemaException e1) {
			System.out.println("Unable to create feature type: "+outCentroidTable);
			System.exit(1);
		}
		
		
		//Process
		//---------------------------------------------------------------------
		Hints filterHints = new Hints( Hints.FEATURE_2D, true ); // force 2D queries
		FilterFactory2 filterFactory = CommonFactoryFinder.getFilterFactory2(filterHints);
		
		DefaultFeatureCollection outPolyFeatureCollection = new DefaultFeatureCollection(outPolyTable, outPolyFeatureType);
		DefaultFeatureCollection outCentroidFeatureCollection = new DefaultFeatureCollection(outCentroidTable, outCentroidFeatureType);

		try {
			int nextOutId = 0;
			//iterate over each feature in the input data set
			SimpleFeatureCollection inFeatureCollection = null;
			if (boundsToProcess != null) {
				System.out.println("Selecting input features in bbox...");
				Filter bboxFilter = filterFactory.bbox(filterFactory.property(inGeometryPropertyName), boundsToProcess);
				inFeatureCollection = inFeatureSource.getFeatures(bboxFilter);
			}
			else {
				inFeatureCollection = inFeatureSource.getFeatures();
			}
			System.out.println("Composing "+inFeatureCollection.size()+" edges into triangles...");
			SimpleFeatureIterator inIterator = inFeatureCollection.features();
			int nextFid = 0;
			int numProcessed = 0;
			int numNonCoveringTriangles = 0;
			while(inIterator.hasNext()) {
				SimpleFeature inFeature = inIterator.next();
				List<Triangle> touchingTriangles = identifyTouchingTriangles(inFeature, inFeatureSource);
				List<Triangle> nonCoveringTriangles = filterOutCoveringTriangles(touchingTriangles);
				numNonCoveringTriangles += nonCoveringTriangles.size();
				
				if (nonCoveringTriangles.size() > 2) {
					System.out.println("Warning: more than two triangles identified touching edge: "+inFeature.getID());
				}
				
				List<SimpleFeature> polyFeatures = toPolygonFeatures(nonCoveringTriangles, outPolyFeatureType, nextFid);
				outPolyFeatureCollection.addAll(polyFeatures);
				
				List<SimpleFeature> centroidPointFeatures = toCentroidPointFeatures(nonCoveringTriangles, outCentroidFeatureType, nextFid);
				outCentroidFeatureCollection.addAll(centroidPointFeatures);
				
				nextFid += numNonCoveringTriangles;
				
				numProcessed++;
				
				if (numProcessed % 100000 == 0) {
					System.out.println(numProcessed+" edges processed. "+numNonCoveringTriangles+" triangles formed. ");
					//break;
				}
				
				
			}
			inIterator.close();
		} catch (Exception e) {
			System.out.println("Processing error");
			e.printStackTrace();
			System.exit(1);
		}

		//save
		//---------------------------------------------------------------------
		
		System.out.println("TODO: filter out duplicate triangles");
		
		GeoPackage outGeoPackage = null;
		try {
			outGeoPackage = new GeoPackage(outFile);
			outGeoPackage.init();
		} catch (IOException e3) {
			System.out.println("Unable to create geopackage "+outputGeopackageFilename);
			e3.printStackTrace();
			System.exit(1);
		}
		
		try {
			//save polys
			FeatureEntry outPolyEntry = new FeatureEntry();
			outPolyEntry.setSrid(bboxSrid);
			outPolyEntry.setBounds(outPolyFeatureCollection.getBounds());
			outPolyEntry.setZ(true); //store a z coordinate for each geometry
			
	        System.out.println("Saving "+outPolyTable+"...");
	        System.out.println(" - Writing "+outPolyFeatureCollection.size()+" features");
	        outGeoPackage.add(outPolyEntry, outPolyFeatureCollection);
	        outPolyFeatureCollection.clear();
	
	        System.out.println("Adding spatial index on "+outPolyTable+"...");
	        outGeoPackage.createSpatialIndex(outPolyEntry);
	        System.out.println(" - Done");	 
	        
	        //save centroids
	        FeatureEntry outCentroidEntry = new FeatureEntry();
			outCentroidEntry.setSrid(bboxSrid);
			outCentroidEntry.setBounds(outCentroidFeatureCollection.getBounds());
			outCentroidEntry.setZ(true); //store a z coordinate for each geometry
			
	        
	        System.out.println("Saving "+outCentroidTable+"...");
	        System.out.println(" - Writing "+outCentroidFeatureCollection.size()+" features");
	        outGeoPackage.add(outCentroidEntry, outCentroidFeatureCollection);
	        outCentroidFeatureCollection.clear();
	
	        System.out.println("Adding spatial index on "+outCentroidTable+"...");
	        outGeoPackage.createSpatialIndex(outCentroidEntry);
	        System.out.println(" - Done");	
		}
		catch (IOException e) {
			e.printStackTrace();
		}
		finally {
			outGeoPackage.close();	
		}
		
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
	
	private static List<SimpleFeature> toCentroidPointFeatures(List<Triangle> triangles, SimpleFeatureType outCentroidFeatureType, int nextFid) {
		List<SimpleFeature> results = new ArrayList<SimpleFeature>();
		SimpleFeatureBuilder featureBuilder = new SimpleFeatureBuilder(outCentroidFeatureType);
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
					e.printStackTrace();
				}
			}
			attrValues[0] = t.toCentroidPoint();
			attrValues[1] = t.getId();
			attrValues[2] = slope;
			attrValues[3] = aspect;
			
			SimpleFeature centroidFeature = featureBuilder.buildFeature(t.getId()+"", attrValues);
			results.add(centroidFeature);
		}
		return results;						
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
	
}
