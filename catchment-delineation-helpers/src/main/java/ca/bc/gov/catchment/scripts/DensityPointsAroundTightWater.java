package ca.bc.gov.catchment.scripts;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.geotools.data.DataStore;
import org.geotools.data.DataStoreFinder;
import org.geotools.data.DataUtilities;
import org.geotools.data.collection.SpatialIndexFeatureCollection;
import org.geotools.data.collection.SpatialIndexFeatureSource;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.factory.CommonFactoryFinder;
import org.geotools.feature.DefaultFeatureCollection;
import org.geotools.feature.FeatureCollection;
import org.geotools.feature.SchemaException;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.geometry.jts.JTSFactoryFinder;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.geopkg.FeatureEntry;
import org.geotools.geopkg.GeoPackage;
import org.geotools.referencing.CRS;
import org.geotools.util.factory.Hints;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.filter.Filter;
import org.opengis.filter.FilterFactory2;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

import ca.bc.gov.catchment.algorithms.RandomPointsBuilder;
import ca.bc.gov.catchment.utils.SaveUtils;
import ca.bc.gov.catchment.utils.SpatialUtils;
import ca.bc.gov.catchment.water.Water;

public class DensityPointsAroundTightWater extends CLItoAlgorithmBridge {

	private static final double DENSIFY_RADIUS = 10;
	private static final double DENSIFY_DENSITY = 0.5;
	
	
/**
 * Input:
 *  - a geopackage with water lines
 * Output:
 *  - a geopackage of new points of the specified density near to areas with 
 *    water features that are very close to one another
 * 
 */
public static void main(String[] args) {
		
		// create Options object
		Options options = new Options();
		options.addOption("i", true, "Input water GeoPackage file");
		options.addOption("o", true, "Output point GeoPackage file");
		options.addOption("inTable", true, "input table name");
		options.addOption("outTable", true, "output table name");
		options.addOption("bbox", true, "bbox (minx,miny,maxx,maxy)");
		options.addOption("bboxcrs", true, "e.g. EPSG:3005");		
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
		
		try {
			CommandLine cmd = parser.parse( options, args);
			inputGeopackageFilename = cmd.getOptionValue("i");
			outputGeopackageFilename = cmd.getOptionValue("o");	
			inTableName = cmd.getOptionValue("inTable");
			outTableName = cmd.getOptionValue("outTable");	
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
		if (bboxStr != null) {
			System.out.println("- bbox: "+bboxStr+" ("+bboxCrs+")");	
		}		
		
		Water water = openWater(inputGeopackageFilename, inTableName, boundsToProcess);
		

		//Output
		//---------------------------------------------------------------------
		
		SimpleFeatureType outFeatureType = null;
		try {
			outFeatureType = DataUtilities.createType(outTableName, "geometry:Point:srid="+bboxSrid);			
		} 
		catch(SchemaException e) {
			System.out.println("Unable create output feature type");
			System.exit(1);
		}
		
		//Processing
		//---------------------------------------------------------------------

		List<Coordinate> outCoords = new ArrayList<Coordinate>();
			
		//get a list of confluence points
		List<Coordinate> confluences = null;
		try {
			System.out.println("Identifying confluences...");
			confluences = water.getConfluences();
			System.out.println(" - Found "+confluences.size()+" confluences");
		} 
		catch (IOException e) {
			System.out.println("Unable to get a list of confluences");
			System.exit(1);
		}
				
		//TODO: get a list of other points where two water features are close to one another
		
		//combine confluences and other points into a
		//list of 'target points'.  the target points are the points around which
		//new points will be added
		List<Coordinate> targetCoords = new ArrayList<Coordinate>();
		targetCoords.addAll(confluences);
		
		//iterate over 'targetCoords', and generate a new collection of random points around each
		System.out.println("Processing target areas...");
		for (Coordinate targetCoord : targetCoords) {
			System.out.print(".");
			
			//generate random coords around the target coord
			Envelope envelope = new Envelope(
					targetCoord.getX()-DENSIFY_RADIUS/2, 
					targetCoord.getX()+DENSIFY_RADIUS/2, 
					targetCoord.getY()-DENSIFY_RADIUS/2, 					
					targetCoord.getY()+DENSIFY_RADIUS/2
				);
			
			
			RandomPointsBuilder randomBuilder = new RandomPointsBuilder(envelope, DENSIFY_DENSITY);		
			List<Coordinate> randomCoords = randomBuilder.getPoints();
			
			
			//add the random coords for the current target coord to the master list of 
			//coordinates for output
			outCoords.addAll(randomCoords);	
			
			
		}
		
		SimpleFeatureCollection outFeatures = SpatialUtils.coordListToSimpleFeatureCollection(outCoords, outFeatureType);
		
		
		//save catchment lines	
		System.out.println();
		System.out.println("saving...");
		try {
			SaveUtils.saveToGeoPackage(outputGeopackageFilename, outFeatures, true);
		} catch(IOException e) {
			System.out.println("Unable to save");
			System.exit(1);
		}
		
	}
	

	/**
	 * Opens the water features file and converts it to a Water object
	 * @param inWaterFilename
	 * @param waterTable
	 * @return
	 */
	private static Water openWater(String inWaterFilename, String waterTable, ReferencedEnvelope boundsToProcess) {

		Map<String, String> waterInputDatastoreParams = new HashMap<String, String>();
		waterInputDatastoreParams.put("dbtype", "geopkg");
		waterInputDatastoreParams.put("database", inWaterFilename);
		
		DataStore waterDatastore = null;
		try {
			waterDatastore = DataStoreFinder.getDataStore(waterInputDatastoreParams);
		} catch (IOException e) {
			System.out.println("Unable to open input file: "+inWaterFilename);
			e.printStackTrace();
			System.exit(1);
		}
		
		if (waterDatastore == null) {
			System.out.println("Unable to open input water datastore");
			System.exit(1);
		}
		
		SimpleFeatureSource waterFeatureSource = null;
		try {
			waterFeatureSource = waterDatastore.getFeatureSource(waterTable);
		} catch (IOException e1) {
			System.out.println("Unable to get in feature source: "+waterTable);
			e1.printStackTrace();
			System.exit(1);
		}
		
		String waterGeometryPropertyName = waterFeatureSource.getSchema().getGeometryDescriptor().getLocalName();
	
		//create a bbox filter
		Hints filterHints = new Hints( Hints.FEATURE_2D, true ); // force 2D queries
		FilterFactory2 filterFactory = CommonFactoryFinder.getFilterFactory2(filterHints);
		
		Filter bboxFilter = null;
		Filter bufferedBboxFilter = null;
		if (boundsToProcess != null) {
			System.out.println("Defining bbox limit...");
			
			bboxFilter = filterFactory.bbox(
					filterFactory.property(waterGeometryPropertyName), 
					boundsToProcess);								
		}	
		
		//add spatial index and apply bbox filter
		SpatialIndexFeatureSource fastWaterFeatureSource = null;
		try {
			SpatialIndexFeatureCollection waterFeatureCollection = new SpatialIndexFeatureCollection(waterFeatureSource.getFeatures(bboxFilter));
			fastWaterFeatureSource = new SpatialIndexFeatureSource(waterFeatureCollection);
		} catch (IOException e) {
			System.out.println("Unable to add spatial index");
			System.exit(1);
		}
		
		Water water = new Water(fastWaterFeatureSource);
		return water;
	}
}
