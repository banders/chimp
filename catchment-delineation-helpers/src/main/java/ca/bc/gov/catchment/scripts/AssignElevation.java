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

public class AssignElevation {

	private static final String GEOPKG_ID = "geopkg";
	private static final String DEFAULT_OUTPUT_TABLE = "point_cloud_3d";
	
	private static final int MIN_NEIGHBOURS = 1;
	private static final int K_NEIGHBOURS = 1;
	private static final double DEFAULT_SEARCH_RADIUS = 0.1;
	
	public static void main(String[] args) {
		
		// create Options object
		Options options = new Options();
		options.addOption("pointCloud3DFile", true, "Input GeoPackage file");
		options.addOption("i", true, "Output GeoPackage file");
		options.addOption("o", true, "Output GeoPackage file");
		options.addOption("inPointCloud3DTable", true, "input table name");
		options.addOption("inTable", true, "input table name");
		options.addOption("outTable", true, "output table name");
		options.addOption("bbox", true, "bbox (minx,miny,maxx,maxy)");
		options.addOption("bboxcrs", true, "e.g. EPSG:3005");
		options.addOption("searchRadius", true, "distance in same unit as input data crs");
		options.addOption("onFailedPoint", true, "what to do if a point cannot be converted to 3D. one of [exit,omit]"); 
		CommandLineParser parser = new DefaultParser();
		HelpFormatter formatter = new HelpFormatter();
		
		String pointCloud3DFile = null;
		String inFile = null;
		String outputGeopackageFilename = null;
		String inPointCloud3DTable = null;
		String inTable = null;
		String outTable = null;
		String bboxStr = null;
		String bboxCrs = null;
		int bboxSrid = -1;
		String onFailedPoint = null;
		ReferencedEnvelope boundsToProcess = null;
		
		double searchRadius = -1;
		
		try {
			CommandLine cmd = parser.parse( options, args);
			pointCloud3DFile = cmd.getOptionValue("pointCloud3DFile");
			inFile = cmd.getOptionValue("i");
			outputGeopackageFilename = cmd.getOptionValue("o");	
			inPointCloud3DTable = cmd.getOptionValue("inPointCloud3DTable");
			inTable = cmd.getOptionValue("inTable");
			outTable = cmd.getOptionValue("outTable", DEFAULT_OUTPUT_TABLE);
			bboxStr = cmd.getOptionValue("bbox");
			bboxCrs = cmd.getOptionValue("bboxcrs");
			String searchRadiusStr = cmd.getOptionValue("searchRadius");
			onFailedPoint = cmd.getOptionValue("onFailedPoint", "exit");
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
		System.out.println("- pointCloud3DFile: "+pointCloud3DFile);
		System.out.println("- inFile: "+inFile);
		System.out.println("- out file: "+outputGeopackageFilename);
		System.out.println("- inPointCloud3DTable: "+inPointCloud3DTable);
		System.out.println("- inTable: "+inTable);
		System.out.println("- out table: "+outTable);
		System.out.println("- searchRadius: "+searchRadius);
		if (bboxStr != null) {
			System.out.println("- bbox: "+bboxStr+" ("+bboxCrs+")");	
		}

		//Open main input datastore
		//---------------------------------------------------------------------
		
		Map<String, String> inDatastoreParams = new HashMap<String, String>();
		inDatastoreParams.put("dbtype", GEOPKG_ID);
		inDatastoreParams.put("database", inFile);
		
		DataStore inDatastore = null;
		try {
			inDatastore = DataStoreFinder.getDataStore(inDatastoreParams);
		} catch (IOException e) {
			System.out.println("Unable to open input file: "+inFile);
			e.printStackTrace();
			System.exit(1);
		}
		
		if (inDatastore == null) {
			System.out.println("Unable to open input datastore");
			System.exit(1);
		}
		
		//Open inPointCloud3D datastore
		//---------------------------------------------------------------------
		
		Map<String, String> inPointCloudDatastoreParams = new HashMap<String, String>();
		inPointCloudDatastoreParams.put("dbtype", GEOPKG_ID);
		inPointCloudDatastoreParams.put("database", pointCloud3DFile);
		
		DataStore inPointCloudDatastore = null;
		try {
			inPointCloudDatastore = DataStoreFinder.getDataStore(inPointCloudDatastoreParams);
		} catch (IOException e) {
			System.out.println("Unable to open input file: "+pointCloud3DFile);
			e.printStackTrace();
			System.exit(1);
		}
		
		if (inPointCloudDatastore == null) {
			System.out.println("Unable to open input datastore");
			System.exit(1);
		}
				

		// Collect some information about the main input data
		//---------------------------------------------------------------------
		
		SimpleFeatureType inFeatureType = null;
		try {
			inFeatureType = inDatastore.getSchema(inTable);			
		} catch (IOException e) {
			System.out.println("Unable to get schema for feature type "+inTable+" in the input datastore");
			e.printStackTrace();
			System.exit(1);
		}
		
		String inGeometryPropertyName = inFeatureType.getGeometryDescriptor().getLocalName();
		
		CoordinateReferenceSystem inCrs = inFeatureType.getCoordinateReferenceSystem();
		int inSrid = -1;
		try {
			inSrid = CRS.lookupEpsgCode(inCrs, true);
		} catch (FactoryException e1) {
			System.out.println("Unable to lookup SRID for feature type "+inTable);
			System.exit(1);
		}
		
		SimpleFeatureSource inFeatureSource = null;
		try {
			inFeatureSource = inDatastore.getFeatureSource(inTable);
			System.out.println("Indexing input features...");
			SpatialIndexFeatureCollection fastFeatureCollection = new SpatialIndexFeatureCollection(inFeatureSource.getFeatures());
			inFeatureSource = new SpatialIndexFeatureSource(fastFeatureCollection);
		} catch (IOException e1) {
			System.out.println("Unable to get in feature source: "+inTable);
			e1.printStackTrace();
			System.exit(1);
		}
		
		
		// Collect some information about the pointCloud3D input data
		//---------------------------------------------------------------------
		
		SimpleFeatureType inPointCloud3DFeatureType = null;
		try {
			inPointCloud3DFeatureType = inPointCloudDatastore.getSchema(inPointCloud3DTable);			
		} catch (IOException e) {
			System.out.println("Unable to get schema for feature type "+inPointCloud3DTable+" in the input datastore");
			e.printStackTrace();
			System.exit(1);
		}
		
		String inPointCloud3DGeometryPropertyName = inPointCloud3DFeatureType.getGeometryDescriptor().getLocalName();
		
		CoordinateReferenceSystem inPointCloud3DCrs = inPointCloud3DFeatureType.getCoordinateReferenceSystem();
		int inPointCloudSrid = -1;
		try {
			inPointCloudSrid = CRS.lookupEpsgCode(inPointCloud3DCrs, true);
		} catch (FactoryException e1) {
			System.out.println("Unable to lookup SRID for feature type "+inPointCloud3DTable);
			System.exit(1);
		}
		
		SimpleFeatureSource inPointCloud3DFeatureSource = null;
		try {
			inPointCloud3DFeatureSource = inPointCloudDatastore.getFeatureSource(inPointCloud3DTable);
			System.out.println("Indexing point cloud...");
			SpatialIndexFeatureCollection fastFeatureCollection = new SpatialIndexFeatureCollection(inPointCloud3DFeatureSource.getFeatures());
			inPointCloud3DFeatureSource = new SpatialIndexFeatureSource(fastFeatureCollection);
		} catch (IOException e1) {
			System.out.println("Unable to get in feature source: "+inPointCloud3DTable);
			e1.printStackTrace();
			System.exit(1);
		}
		
		//Output
		//---------------------------------------------------------------------
		
		int outSrid = inSrid;
		
		SimpleFeatureType outFeatureType = null;
		try {
			outFeatureType = DataUtilities.createType(outTable, 
					"geometry:LineString:srid="+outSrid);
		} catch (SchemaException e1) {
			System.out.println("Unable to create feature type: "+outTable);
			System.exit(1);
		}
		
		SimpleFeatureBuilder outFeatureBuilder = new SimpleFeatureBuilder(outFeatureType);		
		DefaultFeatureCollection outFeatureCollection = new DefaultFeatureCollection(outTable, outFeatureType);
		
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
		
		SimpleFeatureIterator inIterator = inFeatureCollection.features();
		try {
			
			int numProcessed= 0;
			int num2D = 0;
			int num3D = 0;
			while(inIterator.hasNext()) {
				numProcessed++;
				SimpleFeature inFeature = inIterator.next();
				Geometry inGeometry = (Geometry)inFeature.getDefaultGeometry();
				
				Coordinate[] coordsUpdated = null;
				try {
					coordsUpdated = to3D(inGeometry.getCoordinates(), inPointCloud3DFeatureSource, searchRadius);
				} catch (IllegalStateException e) {
					if (onFailedPoint.equals("omit")) {
						continue;
					}
					System.out.println("Unable to convert at least one geometry to 3D");
					System.exit(1);
				}
				
				String type = inGeometry.getGeometryType();
				Geometry outGeometry = null;
				if (type.equals("LineString")) {
					outGeometry = geometryFactory.createLineString(coordsUpdated);
				}
				else {
					System.out.println("Unsupported geometry type: "+type);
					System.exit(1);
				}
				
				if (Double.isNaN(coordsUpdated[0].getZ())) {
					num2D++;
				}
				else {
					num3D++;;
				}
				
				Object[] attributes = {outGeometry};
				SimpleFeature outFeature = outFeatureBuilder.buildFeature(inFeature.getID(), attributes);
				outFeatureCollection.add(outFeature);
				
				if (numProcessed % 10000 == 0) {
					System.out.println("Processed "+numProcessed+" 2D: "+num2D+", 3D: "+num3D);
				}
			}
		} catch (Exception e) {
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
		System.out.println("out table: "+outTable);
		outEntry.setTableName(outTable);
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
	
	private static Coordinate[] to3D(Coordinate[] inCoords, SimpleFeatureSource elevationPointSource, double searchRadius) throws IOException {
		Coordinate[] outCoords = new Coordinate[inCoords.length];
		for(int i = 0; i < inCoords.length; i++) {
			outCoords[i] = to3D(inCoords[i], elevationPointSource, searchRadius);
		}
		return outCoords;
	}
	
	private static Coordinate to3D(Coordinate inCoord, SimpleFeatureSource elevationPointSource, double searchRadius) throws IOException {

		GeometryFactory geometryFactory = JTSFactoryFinder.getGeometryFactory();
		Point inPoint = geometryFactory.createPoint(inCoord);
		
		Hints filterHints = new Hints( Hints.FEATURE_2D, true ); // force 2D queries
		FilterFactory2 filterFactory = CommonFactoryFinder.getFilterFactory2(filterHints);
		
		SimpleFeatureType elevationFeatureType = elevationPointSource.getSchema();
		String elevationGeometryProperty = elevationFeatureType.getGeometryDescriptor().getLocalName();

		SimpleFeatureCollection nearbyElevationPoints = null;
			Filter nearFilter = filterFactory.dwithin(
				filterFactory.property(elevationGeometryProperty), 
				filterFactory.literal(inPoint), 
				searchRadius, 
				"meter");
			
		nearbyElevationPoints = elevationPointSource.getFeatures(nearFilter);
		
		if (nearbyElevationPoints == null || nearbyElevationPoints.size() < MIN_NEIGHBOURS) {
			throw new IllegalStateException("Unable to find at least "+MIN_NEIGHBOURS+" elevation points near to: "+inCoord.x+","+inCoord.y);
		}
		
		//if we find fewer nearby points than K_NEIGHBORS, use all the points that we found 
		int numPointsToUse = Math.min(K_NEIGHBOURS, nearbyElevationPoints.size());
		
		List<SimpleFeature> kNearestPoints = getKNearest(inPoint, nearbyElevationPoints, numPointsToUse);
		
		double elevation = estimateElevationFromNearbyPoints(inPoint, kNearestPoints);
				
		double outZ = Double.isNaN(inCoord.getZ()) ? elevation : inCoord.getZ();
		Coordinate outCoord = new Coordinate(inCoord.getX(), inCoord.getY(), outZ);
		return outCoord;
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
		int i = 0;
		for(SimpleFeature f : nearbyPoints) {
			i++;
			Geometry g = (Geometry)f.getDefaultGeometry();
			Coordinate coord = g.getCoordinate();
			double z = coord.getZ();
			double dist = g.distance(point);
			if (dist == 0) {
				//when an elevation point is exactly on top of the input point 
				//we have division-by-zero problems .  To avoid this, don't 
				//do a weighted average.  just return the z value of the point that matches exactly.
				return z; 
			}
			else {
				//distance is not 0, so compute the weighted average elevation
				//based on distance
				double numerator = z/dist;
				double thisVal = numerator/denom;
				//System.out.println(" elevation:"+z+", dist:"+dist);
				weightedAverageElevation += thisVal;
			}
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
