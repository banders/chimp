package ca.bc.gov.catchment.scripts;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
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
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
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
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

/**
 * Adds all lines from the given input data set to the given output data set.  If the output data
 * set already exists, it is appended to.  No attempt is made to filter out duplicates.  New feature IDs
 * are assigned to all features copied to the output data set.
 * @author Brock
 *
 */

public class SegmentAndAppendToSet {

	private static final String GEOPKG_ID = "geopkg";
	private static final boolean EXCLUDE_ZERO_LENGTH_SEGMENTS = true;
	
	public static void main(String[] args) {
		
		// create Options object
		Options options = new Options();
		options.addOption("i", true, "Input GeoPackage file");
		options.addOption("o", true, "Output GeoPackage file");
		options.addOption("inTable", true, "input table name");
		options.addOption("outTable", true, "output table name");
		options.addOption("outLabel", true, "label to assign to all output features copied from the given input");
		options.addOption("bbox", true, "bbox (minx,miny,maxx,maxy)");
		options.addOption("bboxcrs", true, "e.g. EPSG:3005");
		options.addOption("o", true, "Output GeoPackage file");
		CommandLineParser parser = new DefaultParser();
		HelpFormatter formatter = new HelpFormatter();
		
		String inputGeopackageFilename = null;
		String outputGeopackageFilename = null;
		String inTableName = null;
		String outTableName = null;
		String outLabel = null;
		String bboxStr = null;
		String bboxCrs = null;
		int bboxSrid = -1;
		ReferencedEnvelope boundsToProcess = null;
		
		try {
			CommandLine cmd = parser.parse( options, args);
			inputGeopackageFilename = cmd.getOptionValue("i");
			outputGeopackageFilename = cmd.getOptionValue("o");	
			inTableName = cmd.getOptionValue("inTable");
			outTableName = cmd.getOptionValue("outTable");
			outLabel = cmd.getOptionValue("outLabel");
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
		System.out.println("- in table: "+inTableName);
		System.out.println("- out file: "+outputGeopackageFilename);
		System.out.println("- out table: "+outTableName);
		System.out.println("- out label: "+outLabel);
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
			
		//Prepare output datastore
		//---------------------------------------------------------------------
	
		File outFile = new File(outputGeopackageFilename);
		GeoPackage outGeoPackage = null;
		FeatureEntry outEntry = new FeatureEntry();
		outEntry.setTableName(outTableName);
		outEntry.setBounds(boundsToProcess);
		outEntry.setZ(true); //store a z coordinate for each geometry
		
		SimpleFeatureType outFeatureType = null;
		try {
			outFeatureType = DataUtilities.createType(outTableName, 
					"geometry:LineString:srid="+srid+",label");
		} catch (SchemaException e1) {
			System.out.println("Unable to create feature type: "+outTableName);
			System.exit(1);
		}
		
		//if doesn't exist, create		
		if (!outFile.exists()) {
			
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
				outGeoPackage.add(outEntry, new DefaultFeatureCollection(outTableName, outFeatureType));
			} catch (IOException e) {
				System.out.println("Creating empty feature type '"+outTableName+"' in the output file");
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
		}
		
		//open output
		
		Map<String, String> outputDatastoreParams = new HashMap<String, String>();
		outputDatastoreParams.put("dbtype", GEOPKG_ID);
		outputDatastoreParams.put("database", outputGeopackageFilename);

		DataStore outDatastore = null;
		try {
			outDatastore = DataStoreFinder.getDataStore(outputDatastoreParams);
		} catch (IOException e) {
			System.out.println("Unable to open output file: "+outputGeopackageFilename);
			e.printStackTrace();
			System.exit(1);
		}
		
		try {
			outFeatureType = outDatastore.getSchema(outTableName);
		} catch (IOException e2) {
			System.out.println("Unable to load feature type from output file");
		}
		
		if (outDatastore == null) {
			System.out.println("Unable to open output datastore");
			System.exit(1);
		}
		

		//Processing
		//---------------------------------------------------------------------

		System.out.println("Processing started");
		
		Hints filterHints = new Hints( Hints.FEATURE_2D, true ); // force 2D queries
		FilterFactory2 filterFactory = CommonFactoryFinder.getFilterFactory2(filterHints);
		
		GeometryFactory geometryFactory = JTSFactoryFinder.getGeometryFactory();
		SimpleFeatureBuilder outFeatureBuilder = new SimpleFeatureBuilder(outFeatureType);
		
		DefaultFeatureCollection outFeatureCollection = new DefaultFeatureCollection();
		
		int numZeroLengthExcluded = 0;
		try {
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
			inFeatureSource = new SpatialIndexFeatureSource(new SpatialIndexFeatureCollection(inFeatureCollection));
			System.out.println(inFeatureCollection.size()+" input features to be processed");
			SimpleFeatureIterator inIterator = inFeatureCollection.features();
			int inFeatureNum = 0;			
			while(inIterator.hasNext()) {
				inFeatureNum++;
				SimpleFeature inFeature = inIterator.next();
				Geometry inGeometry = (Geometry)inFeature.getDefaultGeometry();
				
				Coordinate[] coordinates = inGeometry.getCoordinates();
				
				//iterate over each vertex in the current feature.
				//combine the current vertex and the previous vertex into a two-point line
				//segment.  Add each segment to the output
				Coordinate prevCoord = null;
				for(Coordinate coord: coordinates) {
					if (prevCoord != null) {
						boolean isZeroLengthSegment = prevCoord.getX() == coord.getX() && prevCoord.getY() == coord.getY();
						if (EXCLUDE_ZERO_LENGTH_SEGMENTS && isZeroLengthSegment) {
							numZeroLengthExcluded++;	
						}
						else {
							//build output geometry
							Coordinate[] segmentCoordinates = {prevCoord, coord};
							LineString outGeometry = geometryFactory.createLineString(segmentCoordinates); 	
							
							//build output feature
							String newId = UUID.randomUUID()+"";
							Object[] attributeValues = new Object[] { outGeometry, outLabel };
							SimpleFeature outFeature = outFeatureBuilder.buildFeature(newId, attributeValues);
							outFeatureCollection.add(outFeature);
						}
					}
					
					prevCoord = coord;
				}
				if (inFeatureNum % 10000 == 0) {
					System.out.println(outFeatureCollection.size()+" segments processed");
				}
			}
			inIterator.close();
		} catch (Exception e) {
			System.out.println("Processing error");
			e.printStackTrace();
			System.exit(1);
		}

		System.out.println(numZeroLengthExcluded+" zero-length segments excluded");
		System.out.println("Saving...");
		try {
			outGeoPackage = new GeoPackage(outFile);
			addToGeopackage(outGeoPackage, outEntry, outFeatureCollection);
			outGeoPackage.close();
		} catch (IOException e3) {
			System.out.println("Unable to save geopackage "+outputGeopackageFilename);
			e3.printStackTrace();
			System.exit(1);
		}		
		
		System.out.println("All done");
		
	}
	
	private static Map<String, SimpleFeature> loadIntoMap(SimpleFeatureSource fs) throws IOException {
		Map<String, SimpleFeature> map = new HashMap<String, SimpleFeature>();
		SimpleFeatureIterator it = fs.getFeatures().features();
		while(it.hasNext()) {
			SimpleFeature f = it.next();
			Geometry g = (Geometry)f.getDefaultGeometry();
			Coordinate coord = g.getCoordinate();
			String hash = coordToHash(coord);
			map.put(hash, f);
		}
		it.close();
		return map;
	}
	
	private static SimpleFeatureCollection mapToFeatureCollection(Map<String, SimpleFeature> map) {
		DefaultFeatureCollection fc = new DefaultFeatureCollection();
		Iterator<String> keyIt = map.keySet().iterator();
		while(keyIt.hasNext()) {
			String key = keyIt.next();
			SimpleFeature value = map.get(key);
			fc.add(value);
		}
		return fc;
	}
	
	private static String coordToHash(Coordinate c) {
		//String hash = UUID.nameUUIDFromBytes((c.x+"_"+c.y).getBytes()).toString();
		String hash = c.x+"_"+c.y;
		return hash;
	}
	
	private static void addToGeopackage(GeoPackage gp, FeatureEntry entry, SimpleFeatureCollection fc) throws IOException {
		
		//identify new features that need to be added, then add them
		int addCount = 0;
		Transaction appendTx = new DefaultTransaction();
		SimpleFeatureWriter appender = gp.writer(entry, true, null, appendTx);
		SimpleFeatureIterator it = fc.features();
		while(it.hasNext()) {
			SimpleFeature featureToAdd = it.next();
			SimpleFeature newFeature = appender.next();
			newFeature.setAttributes(featureToAdd.getAttributes());
			newFeature.setDefaultGeometry(featureToAdd.getDefaultGeometry());
			appender.write();
			addCount++;
			if (addCount % 50000 == 0) {
				System.out.println(addCount +" added");
			}
		}
		appendTx.commit();
		appendTx.close();
		appender.close();
		it.close();
		System.out.println(" - "+ addCount+" segments added");
		
		
	}
}
