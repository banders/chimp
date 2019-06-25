package ca.bc.gov.catchment.scripts;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
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
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.geopkg.FeatureEntry;
import org.geotools.geopkg.GeoPackage;
import org.geotools.geopkg.GeoPkgDataStoreFactory;
import org.geotools.referencing.CRS;
import org.locationtech.jts.densify.Densifier;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.locationtech.jts.simplify.DouglasPeuckerSimplifier;
import org.locationtech.jts.simplify.TopologyPreservingSimplifier;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.FeatureType;
import org.opengis.filter.Filter;
import org.opengis.filter.FilterFactory2;
import org.opengis.geometry.BoundingBox;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

import ca.bc.gov.catchment.voronoi.SpatialIndexFeatureCollection2;

public class CheckCollapse {

	private static final String GEOPKG_ID = "geopkg";
	private static final double DEFAULT_PRECISION_SCALE = 1000; //3 decimal places
	
	public static void main(String[] args) {
		
		// create Options object
		Options options = new Options();
		options.addOption("i", true, "Input GeoPackage file");
		options.addOption("tables", true, "csv list of table names to process");
		CommandLineParser parser = new DefaultParser();
		HelpFormatter formatter = new HelpFormatter();
		
		String inputGeopackageFilename = null;
		String tableNamesCsv = null;
		
		try {
			CommandLine cmd = parser.parse( options, args);
			inputGeopackageFilename = cmd.getOptionValue("i");
			tableNamesCsv = cmd.getOptionValue("tables");
		} catch (ParseException e2) {
			formatter.printHelp( WKTList2GeoPackage.class.getSimpleName(), options );
		}
		
		String[] tableNamesToProcess = tableNamesCsv.split(","); 
		
		System.out.println("Inputs:");
		System.out.println("- in file: "+inputGeopackageFilename);
		System.out.println("- tables: "+tableNamesCsv);		
		
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
		
		FilterFactory2 filterFactory = CommonFactoryFinder.getFilterFactory2();
		int numCrosses = 0;
		int numCovers = 0;
		int numEquals = 0;
		int numOverlaps = 0;
		int numOverlapAndEqual = 0;
		int numOverlapAndNotEqual = 0;
		int numZeroLength = 0;
		for(String featureTypeName : tableNamesToProcess) {
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
			SimpleFeatureSource inFeatureSource = null;
			try {
				inFeatureSource = inDatastore.getFeatureSource(featureTypeName);
			} catch (IOException e3) {
				// TODO Auto-generated catch block
				System.out.println("Unable to get input feature source");
				e3.printStackTrace();
				continue;
			}
						

			//iterate over each feature in the input datastore.  
			//check whether it crosses any other features.  
			try {
				SimpleFeatureCollection inFeatureCollection = inFeatureSource.getFeatures();				
				String geomPropertyName = featureType.getGeometryDescriptor().getLocalName();
				 
				SpatialIndexFeatureCollection fastFeatureCollection = new SpatialIndexFeatureCollection(inFeatureCollection);
				SpatialIndexFeatureSource fastFeatureSource = new SpatialIndexFeatureSource(fastFeatureCollection);
				
				//Crossings
				SimpleFeatureIterator it = inFeatureCollection.features();
	            while (it.hasNext()) {
	            	
	            	//get the input feature
	            	SimpleFeature inFeature = it.next();
	            	Geometry inGeom = (Geometry)inFeature.getDefaultGeometry();
	            	
	            	if (inGeom.getLength() == 0) {
	            		numZeroLength++;
	            		show(inFeature, null, "has zero length");
	            	};
	            	
	            	//check for crosses
	            	Filter crossesFilter = filterFactory.crosses(filterFactory.property(geomPropertyName), filterFactory.literal(inGeom));
	            	SimpleFeatureCollection crossingFeatures = fastFeatureSource.getFeatures(crossesFilter);
	            	numCrosses += crossingFeatures.size();
	            	if (crossingFeatures.size() > 0) {
	            		show(inFeature, crossingFeatures, "crosses");
	            	}
	            	
	            	//check for equals
	            	Filter equalsFilter = filterFactory.equals(filterFactory.property(geomPropertyName), filterFactory.literal(inGeom));
	            	
	            	
	            	//check for covers
	            	Filter coversFilter = filterFactory.overlaps(filterFactory.property(geomPropertyName), filterFactory.literal(inGeom));
	            	SimpleFeatureCollection coveredFeatures = fastFeatureSource.getFeatures(coversFilter);
	            	numCovers += coveredFeatures.size();
	            	if (coveredFeatures.size() > 0) {
	            		show(inFeature, coveredFeatures, "covers");
	            	}
	            	
	            	//check for overlaps
	            	Filter overlapsFilter = filterFactory.overlaps(filterFactory.property(geomPropertyName), filterFactory.literal(inGeom));
	            	SimpleFeatureCollection overlappingFeatures = fastFeatureSource.getFeatures(overlapsFilter);
	            	numOverlaps += overlappingFeatures.size();
	            	if (overlappingFeatures.size() > 0) {
	            		show(inFeature, overlappingFeatures, "overlaps");
	            	}
	            	
	            	Filter notEqualsFilter = filterFactory.notEqual(filterFactory.property(geomPropertyName), filterFactory.literal(inGeom));
	            	Filter overlapsAndNotEqualsFilter = filterFactory.and(overlapsFilter, notEqualsFilter);
	            	SimpleFeatureCollection overlappingAndNotEqualFeatures = fastFeatureSource.getFeatures(overlapsAndNotEqualsFilter);
	            	numOverlapAndNotEqual += overlappingAndNotEqualFeatures.size();
	            	if (overlappingAndNotEqualFeatures.size() > 0) {
	            		show(inFeature, overlappingAndNotEqualFeatures, "overlaps (partially)");
	            	}
	            	
	            	Filter overlapsAndEqualsFilter = filterFactory.and(overlapsFilter, equalsFilter);
	            	SimpleFeatureCollection overlapsAndEqualFeatures = fastFeatureSource.getFeatures(overlapsAndEqualsFilter);
	            	numOverlapAndEqual += overlapsAndEqualFeatures.size();
	            	if (overlapsAndEqualFeatures.size() > 0) {
	            		show(inFeature, overlapsAndEqualFeatures, "equals");
	            	}
	            }
	            it.close();	  

	                     
	            
	    		Date t1 = new Date();
	    		long runTimeMs = t1.getTime() - t0.getTime();
	    		
	    		System.out.println(" - Summary");
	    		System.out.println("   - "+inFeatureCollection.size()+" features processed");
	    		System.out.println("   - # zero length: "+numZeroLength);
	    		System.out.println("   - # Crossing: "+numCrosses);	
	    		System.out.println("   - # Covers: "+numCovers);	
	    		System.out.println("   - # Equals: "+numEquals);	
	    		System.out.println("   - # Overlaps:"+numOverlaps);
	    		System.out.println("     - # full: "+numOverlapAndEqual);
	    		System.out.println("     - # partial: "+numOverlapAndNotEqual);
	    		System.out.println("   - run time  : "+runTimeMs+" ms");	
	    		
	            
			} catch (IOException e) {
				e.printStackTrace();
			}

		}

		System.out.println("All done");
		int numProblems = numZeroLength + numCrosses + numCovers + numEquals + numOverlaps + numOverlapAndEqual + numOverlapAndNotEqual;
		System.exit(numProblems);
		
	}
	 
	
	private static void show(SimpleFeature f1, SimpleFeatureCollection fc, String msg) {
		BoundingBox bbox = f1.getBounds();
		System.out.println(" - "+f1.getID()+" (bbox: "+bbox.getMinX()+","+bbox.getMinY()+","+bbox.getMaxX()+","+bbox.getMaxY()+")");
		
		if (fc != null) {
			SimpleFeatureIterator it = fc.features();
			while(it.hasNext() ) {
				SimpleFeature f2 = it.next();
				System.out.println("   - "+msg+" "+f2.getID());
			}
		}
	}

}
