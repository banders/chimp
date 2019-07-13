package ca.bc.gov.catchment.scripts;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.geotools.data.DataStore;
import org.geotools.data.DataStoreFinder;
import org.geotools.data.DataUtilities;
import org.geotools.data.DefaultTransaction;
import org.geotools.data.FeatureReader;
import org.geotools.data.FeatureSource;
import org.geotools.data.FeatureWriter;
import org.geotools.data.FileDataStoreFactorySpi;
import org.geotools.data.FileDataStoreFinder;
import org.geotools.data.Query;
import org.geotools.data.Transaction;
import org.geotools.data.collection.SpatialIndexFeatureCollection;
import org.geotools.data.collection.SpatialIndexFeatureSource;
import org.geotools.data.collection.TreeSetFeatureCollection;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.data.simple.SimpleFeatureReader;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.data.simple.SimpleFeatureWriter;
import org.geotools.factory.CommonFactoryFinder;
import org.geotools.factory.GeoTools;
import org.geotools.factory.Hints;
import org.geotools.feature.DefaultFeatureCollection;
import org.geotools.feature.FeatureCollection;
import org.geotools.feature.FeatureIterator;
import org.geotools.feature.SchemaException;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.geometry.jts.GeometryBuilder;
import org.geotools.geometry.jts.JTSFactoryFinder;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.geopkg.FeatureEntry;
import org.geotools.geopkg.GeoPackage;
import org.geotools.geopkg.GeoPkgDataStoreFactory;
import org.geotools.referencing.CRS;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.CoordinateSequenceFactory;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.locationtech.jts.simplify.DouglasPeuckerSimplifier;
import org.locationtech.jts.simplify.TopologyPreservingSimplifier;
import org.opengis.feature.Feature;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.FeatureType;
import org.opengis.filter.Filter;
import org.opengis.filter.FilterFactory;
import org.opengis.filter.FilterFactory2;
import org.opengis.filter.sort.SortBy;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

/**
 * Combines vertices from the following data sets into a point cloud:
 * - elevation points (points)
 * - water features such as rivers and lakes (lines and/or polygons)
 * - initial catchment boundaries (lines and/or polygons)
 * Vertices from the elevation data set will be 3D, although vertices from the
 * other data sets will probably only be 2D.  The output will be a point cloud 
 * in which some points have a Z value and others do not.
 * The following attributes are set for each point:
 * 	- is_elevation (boolean)
 *  - is_water (boolean)
 *  - is_catchment (boolean)
 *  - is_confluence (boolean)
 * When the input data contains multiple points at the same 2D location
 * @author Brock
 *
 */

public class EstimatePointCloudElevation {

	private static final String GEOPKG_ID = "geopkg";
	private static final String DEFAULT_OUTPUT_TABLE = "point_cloud_3d";
	private static final String OUT_GEOMETRY_PROPERTY_NAME = "geometry";
	private static final String TYPE_CODE_ELEVATION = "E";
	private static final String TYPE_CODE_WATER = "W";
	private static final String TYPE_CODE_CATCHMENT = "C";
	private static final String ATTR_IS_ELEVATION = "is_elevation";
	private static final String ATTR_IS_WATER = "is_water";
	private static final String ATTR_IS_CATCHMENT = "is_catchment";
	private static final String ATTR_IS_CONFLUENCE = "is_confluence";
	private static final boolean EXCLUDE_FEATURES_WITH_NO_NEARBY_ELEVATION = true;
	
	private static double DEFAULT_SEARCH_RADIUS = 100; 
	private static final int K_NEIGHBOURS = 3;

	public static void main(String[] args) {
		
		// create Options object
		Options options = new Options();
		options.addOption("i", true, "Input GeoPackage file");
		options.addOption("o", true, "Output GeoPackage file");
		options.addOption("inTable", true, "input table name");
		options.addOption("outTable", true, "output table name");
		options.addOption("bbox", true, "bbox (minx,miny,maxx,maxy)");
		options.addOption("bboxcrs", true, "e.g. EPSG:3005");
		options.addOption("searchRadius", true, "distance in same unit as input data crs");
		CommandLineParser parser = new DefaultParser();
		HelpFormatter formatter = new HelpFormatter();
		
		String inputGeopackageFilename = null;
		String outputGeopackageFilename = null;
		String inTableName = null;
		String outTableName = null;
		String bboxStr = null;
		String bboxCrs = null;
		int bboxSrid = -1;
		ReferencedEnvelope boundsToProcess = null;
		double searchRadius = -1;
		
		try {
			CommandLine cmd = parser.parse( options, args);
			inputGeopackageFilename = cmd.getOptionValue("i");
			outputGeopackageFilename = cmd.getOptionValue("o");	
			inTableName = cmd.getOptionValue("inTable");
			outTableName = cmd.getOptionValue("outTable", DEFAULT_OUTPUT_TABLE);
			bboxStr = cmd.getOptionValue("bbox");
			bboxCrs = cmd.getOptionValue("bboxcrs");
			String searchRadiusStr = cmd.getOptionValue("searchRadius");
			if (searchRadiusStr == null) {
				searchRadius = DEFAULT_SEARCH_RADIUS;
			}
			else {
				searchRadius = Double.parseDouble(searchRadiusStr);
			}
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
		System.out.println("- in table: "+inTableName);
		System.out.println("- out file: "+outputGeopackageFilename);
		System.out.println("- out table: "+outTableName);
		if (bboxStr != null) {
			System.out.println("- bbox: "+bboxStr+" ("+bboxCrs+")");	
		}

		
		//Open input datastore
		//---------------------------------------------------------------------
		
		Map<String, String> inputDatastoreParams = new HashMap<String, String>();
		inputDatastoreParams.put("dbtype", GEOPKG_ID);
		inputDatastoreParams.put("database", inputGeopackageFilename);
		
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
			
		// Collect some information about the input data
		//---------------------------------------------------------------------
		
		SimpleFeatureType inFeatureType = null;
		try {
			inFeatureType = inDatastore.getSchema(inTableName);			
		} catch (IOException e) {
			System.out.println("Unable to get schema for feature type "+inTableName+" in the input datastore");
			e.printStackTrace();
			System.exit(1);
		}
		
		String inGeometryPropertyName = inFeatureType.getGeometryDescriptor().getLocalName();
		
		CoordinateReferenceSystem crs = inFeatureType.getCoordinateReferenceSystem();
		int srid = -1;
		try {
			srid = CRS.lookupEpsgCode(crs, true);
		} catch (FactoryException e1) {
			System.out.println("Unable to lookup SRID for feature type "+inTableName);
			System.exit(1);
		}
		
		SimpleFeatureSource inFeatureSource = null;
		try {
			inFeatureSource = inDatastore.getFeatureSource(inTableName);
		} catch (IOException e1) {
			System.out.println("Unable to get in feature source: "+inTableName);
			e1.printStackTrace();
			System.exit(1);
		}
		
		//Processing
		//---------------------------------------------------------------------

		Hints filterHints = new Hints( Hints.FEATURE_2D, true ); // force 2D queries
		FilterFactory2 filterFactory = CommonFactoryFinder.getFilterFactory2(filterHints);
		GeometryFactory geometryFactory = JTSFactoryFinder.getGeometryFactory();
		
		SimpleFeatureCollection inFeatureCollection = null;
		try {
			if (boundsToProcess != null) {
				System.out.println("Selecting input features in bbox...");
				Filter bboxFilter = filterFactory.bbox(filterFactory.property(inGeometryPropertyName), boundsToProcess);
				inFeatureCollection = inFeatureSource.getFeatures(bboxFilter);
			}
			else {
				inFeatureCollection = inFeatureSource.getFeatures();
			}	
		} 
		catch (IOException e) {
			System.out.println("Unable to get input features");
			e.printStackTrace();
			System.exit(1);
		}
		
		SimpleFeatureType outFeatureType = null;
		try {
			outFeatureType = DataUtilities.createType(outTableName, 
					"geometry:Point:srid="+srid+","+ATTR_IS_ELEVATION+","+ATTR_IS_WATER+","+ATTR_IS_CATCHMENT+","+ATTR_IS_CONFLUENCE);
		} catch (SchemaException e1) {
			System.out.println("Unable to create feature type: "+outTableName);
			System.exit(1);
		}

		DefaultFeatureCollection outFeatureCollection = new DefaultFeatureCollection(outTableName, outFeatureType);
		SimpleFeatureIterator inIterator = inFeatureCollection.features();
		try {
			
			int numProcessed = 0;
			int numToProcess = inFeatureCollection.size();
			while(inIterator.hasNext()) {
				numProcessed++;
				SimpleFeature inFeature = inIterator.next();
				Geometry inGeometry = (Geometry)inFeature.getDefaultGeometry();
				Coordinate inCoord = inGeometry.getCoordinate();
				
				SimpleFeature outFeature = SimpleFeatureBuilder.retype(inFeature, outFeatureType);
				boolean alreadyHasElevation = inFeature.getAttribute(ATTR_IS_ELEVATION).equals("true");
				if (!alreadyHasElevation) {
					double elevation = -1;
					try {
						elevation = estimateElevation(inGeometry, inFeatureSource, searchRadius);
					} catch (IllegalStateException e) {
						continue;
					}					
					//ystem.out.println(" estimated elevation: "+elevation);
					Coordinate outCoord = new Coordinate(inCoord.x, inCoord.y, elevation);
					Point outPoint = geometryFactory.createPoint(outCoord);
					outFeature.setDefaultGeometry(outPoint);
				}
				else {
					//System.out.println("Elevation feature copied to output");
				}
				
				outFeatureCollection.add(outFeature);
				
				if (numProcessed % 500 == 0) {
					System.out.println("Processed "+numProcessed);
				}
			}
		} catch (Exception e) {
			System.out.println("Unable to estimate elevation from nearby features");
			e.printStackTrace();
			System.exit(1);
		}
		inIterator.close();
		System.out.println("processing complete");

		//Save output
		//---------------------------------------------------------------------
	
		File outFile = new File(outputGeopackageFilename);
		GeoPackage outGeoPackage = null;
		FeatureEntry outEntry = new FeatureEntry();
		System.out.println("out table: "+outTableName);
		outEntry.setTableName(outTableName);
		outEntry.setBounds(boundsToProcess);
		outEntry.setZ(true); //store a z coordinate for each geometry
		
		try {
			outGeoPackage = new GeoPackage(outFile);
			outGeoPackage.init();
		} catch (IOException e3) {
			System.out.println("Unable to create geopackage "+outputGeopackageFilename);
			e3.printStackTrace();
			System.exit(1);
		}
		
		//create feature type in out file
		try {
			outGeoPackage.add(outEntry, outFeatureCollection);
		} catch (IOException e) {
			System.out.println("Unable to add feature collection to output");
			e.printStackTrace();
			System.exit(1);
		}
		
		try {
			outGeoPackage.createSpatialIndex(outEntry);
		} catch (IOException e) {
			System.out.println("Unable to create spatial index");
			e.printStackTrace();
			System.exit(1);
		}
		
		outGeoPackage.close();
		System.out.println("Created output file");

		
		System.out.println("All done");
		
	}
	
	private static double estimateElevation(final Geometry point, SimpleFeatureSource inFeatureSource, double searchRadius) throws IOException {
		Coordinate inCoord = point.getCoordinate();
		Hints filterHints = new Hints( Hints.FEATURE_2D, true ); // force 2D queries
		FilterFactory2 filterFactory = CommonFactoryFinder.getFilterFactory2(filterHints);
		
		SimpleFeatureType inFeatureType = inFeatureSource.getSchema();
		String inGeomProperty = inFeatureType.getGeometryDescriptor().getLocalName();
		Filter isElevationFilter = filterFactory.like(
				filterFactory.property(ATTR_IS_ELEVATION),
				"true");

		SimpleFeatureCollection nearbyElevationPoints = null;
			Filter nearFilter = filterFactory.dwithin(
				filterFactory.property(inGeomProperty), 
				filterFactory.literal(point), 
				searchRadius, 
				"meter");
			
		Filter nearByElevationFilter = filterFactory.and(isElevationFilter, nearFilter);
		nearbyElevationPoints = inFeatureSource.getFeatures(nearByElevationFilter);
		
		if (nearbyElevationPoints == null || nearbyElevationPoints.size() < K_NEIGHBOURS) {
			throw new IllegalStateException("Unable to find "+K_NEIGHBOURS+" elevation points near to: "+inCoord.x+","+inCoord.y);
		}
		
		List<SimpleFeature> kNearestPoints = getKNearest(point, nearbyElevationPoints, K_NEIGHBOURS);
		
		double elevation = estimateElevationFromNearbyPoints(point, kNearestPoints);
		return elevation;
		
	}
	
	private static double estimateElevationFromNearbyPoints(Geometry point, List<SimpleFeature> nearbyPoints) {
		//get distance sum of nearest points
		double denom = 0;
		for(SimpleFeature f : nearbyPoints) {
			Geometry g = (Geometry)f.getDefaultGeometry();
			denom += 1/g.distance(point);
		}
		
		//calc weighted average elevation
		double weightedAverageElevation = 0;
		for(SimpleFeature f : nearbyPoints) {
			Geometry g = (Geometry)f.getDefaultGeometry();
			Coordinate coord = g.getCoordinate();
			double z = coord.getZ();
			double dist = g.distance(point);
			double numerator = z/dist;
			double thisVal = numerator/denom;
			//System.out.println(" elevation:"+z+", dist:"+dist);
			weightedAverageElevation += thisVal; 
		}
		
		return weightedAverageElevation;
	}
	
	private static List<SimpleFeature> getKNearest(final Geometry point, SimpleFeatureCollection fc, int k) {
		List<SimpleFeature> sorted = new ArrayList<SimpleFeature>();
		SimpleFeatureIterator nearbyIt = fc.features();
		while(nearbyIt.hasNext()) {
			SimpleFeature nearbyElevationFeature = nearbyIt.next();
			sorted.add(nearbyElevationFeature);			  
		}
		nearbyIt.close();
		
		sorted.sort(new Comparator<SimpleFeature>() {
			public int compare(SimpleFeature f1, SimpleFeature f2) {
				Geometry g1 = (Geometry)f1.getDefaultGeometry();
				Geometry g2 = (Geometry)f2.getDefaultGeometry();
				double dist1 = g1.distance(point);
				double dist2 = g2.distance(point);
				return dist1 > dist2 ? 1 : -1;
			}			
		});
		
		if (sorted.size() < k) {
			throw new IllegalArgumentException("list must have at least "+k+" features.");
		}
		
		
		List<SimpleFeature> result = new ArrayList<SimpleFeature>();
		for(int i = 0;  i < k; i++) {
			result.add(sorted.get(i));
		}
		return result;		
		
	}
}
