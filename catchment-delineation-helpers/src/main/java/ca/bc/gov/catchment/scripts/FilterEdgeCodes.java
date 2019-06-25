package ca.bc.gov.catchment.scripts;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
import org.geotools.data.FeatureSource;
import org.geotools.data.Query;
import org.geotools.data.Transaction;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.factory.CommonFactoryFinder;
import org.geotools.feature.DefaultFeatureCollection;
import org.geotools.feature.FeatureCollection;
import org.geotools.feature.FeatureIterator;
import org.geotools.feature.SchemaException;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.geometry.jts.JTSFactoryFinder;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.geopkg.FeatureEntry;
import org.geotools.geopkg.GeoPackage;
import org.geotools.referencing.CRS;
import org.locationtech.jts.densify.Densifier;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.CoordinateSequence;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.simplify.DouglasPeuckerSimplifier;
import org.locationtech.jts.simplify.TopologyPreservingSimplifier;
import org.opengis.feature.Feature;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.FeatureType;
import org.opengis.feature.type.PropertyDescriptor;
import org.opengis.filter.Filter;
import org.opengis.filter.FilterFactory2;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.NoSuchAuthorityCodeException;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.operation.TransformException;


import ca.bc.gov.catchments.utils.SaveUtils;
import ca.bc.gov.catchments.utils.SpatialUtils;

/*
 * Test bboxes: 
 * 		21 features:  -115.79381,49.21187,-115.75347,49.24806
 * 		1 feature:    -115.80751,49.23394,-115.79588,49.24468
 * 
 * 	
 * 
 * 
 */
public class FilterEdgeCodes {

	private static final String DEFAULT_STREAM_NETWORKS_FEATURE_TYPE = "STREAM_NETWORKS";
	private static final String DEFAULT_LINEAR_BOUNDARIES_FEATURE_TYPE = "LINEAR_BOUNDARIES";
	private static final String REACH_CATCHMENTS_FEATURE_TYPE = "REACH_CATCHMENTS";
	private static final Envelope VORONOI_BOUNDS = new ReferencedEnvelope(-Math.sin(0.785398),Math.sin(0.785398),-Math.sin(0.785398),Math.sin(0.785398), null);
	//private static final String DEFAULT_WHITELIST_FILTER = "EDGE_TYPE:1000,1050,1100,1150,1300,1325,1350,1375,1400,1410,1425,1450,1475,1500,1525,1550,1600,1800,1825,1850,1875,1900,1925,1950,1975,2000,2100,2300";
				
	
	private static final String GEOPKG_ID = "geopkg";
	
	public static void main(String[] args) {
		
		// create Options object
		Options options = new Options();
		options.addOption("i", true, "Input GeoPackage file");
		options.addOption("o", true, "Output GeoPackage file");
		options.addOption("tables", true, "Name of tables in input file to process");
		options.addOption("whitelistfilter", true, "[attr]:val1,val2");
		options.addOption("blacklistfilter", true, "[attr]:val1,val2");
		CommandLineParser parser = new DefaultParser();
		HelpFormatter formatter = new HelpFormatter();
		
		String inputGeopackageFilename = null;
		String outputGeopackageFilename = null;
		String tables[] = null;
		String tablesCsv = null;
		String whitelist = null;
		String blacklist = null;
		
		try {
			CommandLine cmd = parser.parse( options, args);
			inputGeopackageFilename = cmd.getOptionValue("i");
			outputGeopackageFilename = cmd.getOptionValue("o");
			tablesCsv = cmd.getOptionValue("tables");
			tables = tablesCsv.split(",");
			whitelist = cmd.getOptionValue("whitelistfilter");
			blacklist = cmd.getOptionValue("blacklistfilter");
		} catch (ParseException e2) {
			formatter.printHelp( FilterEdgeCodes.class.getSimpleName(), options );
		}

		//validate inputs
		if (inputGeopackageFilename == null) {
			formatter.printHelp( FilterEdgeCodes.class.getSimpleName(), options );
			System.exit(1);
		}
		if (whitelist != null && blacklist != null) {
			System.out.println("Can only specify one of [whitelistfilter, blacklistfilter]");
			System.exit(1);
		}
		
		System.out.println("Inputs:");
		System.out.println("- in file: "+inputGeopackageFilename);
		System.out.println("- in tables: "+tablesCsv);
		System.out.println("- out geopackage file: "+outputGeopackageFilename);
		if (whitelist != null) {
			System.out.println("- whitelistfilter: "+whitelist);
		}
		if (blacklist != null) {
			System.out.println("- blacklistfilter: "+whitelist);
		}
		

		Map<String, String> inputDatastoreParams = new HashMap<String, String>();
		inputDatastoreParams.put("dbtype", GEOPKG_ID);
		inputDatastoreParams.put("database", inputGeopackageFilename);
		System.out.println(inputDatastoreParams);
		
		//Open input datastore
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
			
		//Create output datastore
	
		File outFile = new File(outputGeopackageFilename);
		Map<String, String> outputDatastoreParams = new HashMap<String, String>();
		outputDatastoreParams.put("dbtype", GEOPKG_ID);
		outputDatastoreParams.put("database", outputGeopackageFilename);

		GeoPackage outGeoPackage = null;
		try {
			outGeoPackage = new GeoPackage(outFile);
			outGeoPackage.init();
		} catch (IOException e3) {
			System.out.println("Unable to create geopackage "+outputGeopackageFilename);
			e3.printStackTrace();
			System.exit(1);
		}
		
		
		for(String featureTypeName : tables) {
			Date t0 = new Date();
			
			System.out.println("Processing "+featureTypeName);

			SimpleFeatureType featureType;
			try {
				featureType = inDatastore.getSchema(featureTypeName);
			} catch (IOException e) {
				System.out.println("Unable to get schema for feature type "+featureTypeName+" in the input datastore");
				e.printStackTrace();
				continue;
			}
			
			ReferencedEnvelope bounds = null;
			SimpleFeatureSource featureSource = null;
			try {
				featureSource = inDatastore.getFeatureSource(featureTypeName);
				bounds = featureSource.getBounds();
			} catch (IOException e3) {
				// TODO Auto-generated catch block
				System.out.println("Unable to get bounds from input");
				e3.printStackTrace();
				continue;
			}
			
			//initialize new schema in the output datastore

			FeatureEntry entry = new FeatureEntry();
			entry.setBounds(bounds);			

			Query readerQuery = new Query(featureTypeName);

			//iterate over each feature in the input datastore.  
			//Copy the feature.  Simplify the geometry of the copy. Add the copy to a
			//feature collection in memory.
			try {

				System.out.println(" - Filtering...");
				int numFeaturesIn = featureSource.getFeatures().size();
				SimpleFeatureCollection outFeatureCollection = filterFeatures(featureSource, whitelist, blacklist);
				int numFeaturesOut = outFeatureCollection.size();
				System.out.println("   - Reduced from "+numFeaturesIn+" to " +numFeaturesOut);
	            
	            System.out.println(" - Saving...");
	            outGeoPackage.add(entry, outFeatureCollection);
	            System.out.println(" - Adding spatial index...");
	            outGeoPackage.createSpatialIndex(entry);
	            System.out.println(" - Done");	  
	            
	    		Date t1 = new Date();
	    		long runTimeMs = t1.getTime() - t0.getTime();
	    		
	    		
	    		System.out.println("   - "+outFeatureCollection.size()+" features processed");
	    		System.out.println("   - run time: "+runTimeMs+" ms");	
	            
			} catch (IOException e) {
				System.out.println("Unable to read "+featureTypeName);
				e.printStackTrace();
				System.exit(1);
			}
		}

		outGeoPackage.close();
		System.out.println("All done");
	}
	
	/**
	 * Gets a feature collection with the following filters applied:
	 *  - GEOMETRY "within" the given bounding polygon, and
	 *  - EDGE_TYPE equal to any value from edgeTypeWhitelist
	 */
	private static SimpleFeatureCollection filterFeatures(SimpleFeatureSource featureSource,  String whitelist, String blacklist) throws IOException {
		
		FilterFactory2 filterFactory = CommonFactoryFinder.getFilterFactory2();
		FeatureType schema = featureSource.getSchema();
		Filter edgeCodeFilter = null;

		if (whitelist != null) {
			String propertyName = parseFilterProperty(whitelist);
			if (schema.getDescriptor(propertyName) != null) {
				String[] propertyValues = parseFilterValues(whitelist);
				List<Filter> propertyFilters = new ArrayList<Filter>();
				for (String value : propertyValues) {
					Filter edgeFilter = filterFactory.equals(filterFactory.property(propertyName), filterFactory.literal(value));
					propertyFilters.add(edgeFilter);
				}
				edgeCodeFilter = filterFactory.or(propertyFilters);
			}
		}
		else if (blacklist != null) {
			String propertyName = parseFilterProperty(blacklist);
			if (schema.getDescriptor(propertyName) != null) {
				String[] propertyValues = parseFilterValues(blacklist);
				List<Filter> propertyFilters = new ArrayList<Filter>();
				for (String value : propertyValues) {
					Filter edgeFilter = filterFactory.notEqual(filterFactory.property(propertyName), filterFactory.literal(value));
					propertyFilters.add(edgeFilter);
				}
				Filter nullFilter = filterFactory.isNull(filterFactory.property(propertyName));
				Filter blacklistFilter = filterFactory.and(propertyFilters);
				//the filter to allow null values must be added explicitly, because 
				//the NOT EQUAL filter doesn't consider null not to be equal to a given (non-null) literal.  
				//is this a geotools bug?
				edgeCodeFilter = filterFactory.or(blacklistFilter, nullFilter);
			}
		}
		
		if (edgeCodeFilter == null) {
			return featureSource.getFeatures();
		}
		else
			return featureSource.getFeatures(edgeCodeFilter);
		
	}
	

	private static String parseFilterProperty(String s) {
		int a = s.indexOf(":");
		if (a == -1) {
			throw new IllegalArgumentException("unknown filter format.  expecting [attr]:val1,val2,val3,...");
		}
		String property = s.substring(0, a);
		return property;
	}
	
	private static String[] parseFilterValues(String s) {
		int a = s.indexOf(":");
		if (a == -1) {
			throw new IllegalArgumentException("unknown filter format.  expecting [attr]:val1,val2,val3,...");
		}
		String valuesCsv = s.substring(a+1);
		String[] values = valuesCsv.split(",");
		return values;
	}
	
	public static void writeGeometry(Writer out, Geometry geometry) throws IOException {
		Coordinate[] coordinates = geometry.getCoordinates();
		Coordinate prevCoord = null;
		for(Coordinate coord : coordinates) {
			if (prevCoord != null) {
				String lineSegmentAsStr = "s " + prevCoord.x + " " + prevCoord.y + "  " + coord.x + " " + coord.y;
				out.write(lineSegmentAsStr+"\n");
			}
			prevCoord = coord;
		}
	}
	
	private static ReferencedEnvelope parseBbox(String bboxStr, String crsInStr, CoordinateReferenceSystem crsOut) {
		double xmin;
		double ymin;
		double xmax;
		double ymax;
		CoordinateReferenceSystem crsIn = null;
		try {
			crsIn = CRS.decode(crsInStr);
		} catch (NoSuchAuthorityCodeException e) {
			throw new IllegalArgumentException("Unable to lookup CRS");
		} catch (FactoryException e) {
			throw new IllegalStateException("Unable to lookup CRS.  An internal error occurred.");
		}
			
		String[] pieces = bboxStr.split(",");
		if (pieces.length != 4) {
			throw new IllegalArgumentException("Unable to parse bbox");
		}
		try {
			xmin = Double.parseDouble(pieces[0]);
			ymin = Double.parseDouble(pieces[1]);
			xmax = Double.parseDouble(pieces[2]);
		    ymax = Double.parseDouble(pieces[3]);
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException("Unable to parse bbox");
		}
		ReferencedEnvelope envelopeInCrs = new ReferencedEnvelope(xmin, xmax, ymin, ymax, crsIn);
		ReferencedEnvelope envelopeOutCrs;
		try {
			envelopeOutCrs = envelopeInCrs.transform(crsOut, false);
		} catch (TransformException e) {
			throw new IllegalStateException("Unable to reproject bbox.");
		} catch (FactoryException e) {
			throw new IllegalStateException("Unable to reproject bbox.  An internal error occurred.");
		}
		return envelopeOutCrs;
	}
	
	private static ReferencedEnvelope reproject(ReferencedEnvelope bounds, String targetCrsStr) {
		CoordinateReferenceSystem targetCrs = null;
		try {
			targetCrs = CRS.decode(targetCrsStr);
		} catch (NoSuchAuthorityCodeException e) {
			throw new IllegalArgumentException("Unable to lookup CRS");
		} catch (FactoryException e) {
			throw new IllegalStateException("Unable to lookup CRS.  An internal error occurred.");
		}
		
		ReferencedEnvelope outEnvelope = null;
		try {
			outEnvelope = bounds.transform(targetCrs, false);
		} catch (Exception e) {
			throw new IllegalStateException("Unable to reproject");
		}
		return outEnvelope;
	}
	
	private static Coordinate normalizeCoordinate(Coordinate coordinate, Envelope inBounds, Envelope outBounds) {
		double inWidth = inBounds.getWidth();
		double outWidth = outBounds.getWidth();
		double inHeight = inBounds.getHeight();
		double outHeight = outBounds.getHeight();
		//System.out.println("source bounds: "+inWidth+","+inHeight+" -> target bounds: "+outWidth+","+outHeight);
		double newX = (coordinate.x - inBounds.getMinX()) * outWidth / inWidth + outBounds.getMinX();
		double newY = (coordinate.y - inBounds.getMinY()) * outHeight / inHeight + outBounds.getMinY();
		Coordinate normalizedCoord = new Coordinate(newX, newY);
		
		//System.out.println(coordinate.x+","+coordinate.y+" -> "+normalizedCoord.x+","+normalizedCoord.y);
		return normalizedCoord;
	}
	

}
