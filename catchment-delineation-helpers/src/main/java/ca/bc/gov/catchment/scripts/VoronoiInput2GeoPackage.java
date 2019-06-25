package ca.bc.gov.catchment.scripts;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
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
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.CoordinateSequence;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.io.WKTReader;
import org.locationtech.jts.simplify.DouglasPeuckerSimplifier;
import org.opengis.feature.Feature;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.FeatureType;
import org.opengis.filter.Filter;
import org.opengis.filter.FilterFactory2;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.NoSuchAuthorityCodeException;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.operation.TransformException;


/*
 * Test bboxes: 
 * 		21 features:  -115.79381,49.21187,-115.75347,49.24806
 * 		1 feature:    -115.80751,49.23394,-115.79588,49.24468
 * 
 * 	
 * 
 * 
 */
public class VoronoiInput2GeoPackage {

	private static final String GEOPKG_ID = "geopkg";
	private static final String OUTPUT_TABLE = "water_features_segmented";
	
	
	public static void main(String[] args) {
		
		// create Options object
		Options options = new Options();
		options.addOption("i", true, "Input Txt file");
		options.addOption("o", true, "Output GeoPackage file");
		CommandLineParser parser = new DefaultParser();
		HelpFormatter formatter = new HelpFormatter();
		
		String inputTxtFilename = null;
		String outputGeopackageFilename = null;
		int bboxSrid = 3005;
		
		try {
			CommandLine cmd = parser.parse( options, args);
			inputTxtFilename = cmd.getOptionValue("i");
			outputGeopackageFilename = cmd.getOptionValue("o");	
		} catch (ParseException e2) {
			formatter.printHelp( WKTList2GeoPackage.class.getSimpleName(), options );
		}

		//validate inputs
		if (inputTxtFilename == null) {
			formatter.printHelp( WKTList2GeoPackage.class.getSimpleName(), options );
			System.exit(1);
		}
		if (outputGeopackageFilename == null) {
			formatter.printHelp( WKTList2GeoPackage.class.getSimpleName(), options );
			System.exit(1);
		}

		
		
		System.out.println("Inputs:");
		System.out.println("- in file: "+inputTxtFilename);
		System.out.println("- out file: "+outputGeopackageFilename);
		
		//Open input file
		BufferedReader inReader = null;
		try {
			inReader = new BufferedReader(new FileReader(inputTxtFilename));
		} catch (IOException e) {
			System.out.println("Unable to open input file: "+inputTxtFilename);
			e.printStackTrace();
			System.exit(1);
		}
		
		//Create output geopackage
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
				
		try {
			//int streamDataEpsgCode = CRS.lookupEpsgCode(streamDataBounds.getCoordinateReferenceSystem(), true);
			System.out.println("Input data summary:");
			//System.out.println(" - "+STREAM_NETWORKS_FEATURE_TYPE);
			//System.out.println("   - Data CRS: EPSG:"+streamDataEpsgCode);
			//System.out.println("   - Data bounds");
			//System.out.println("       EPSG:"+streamDataEpsgCode+": ["+streamDataBounds.getMinX()+","+streamDataBounds.getMinY()+","+streamDataBounds.getMaxX()+","+streamDataBounds.getMaxY()+"]");
			//if (streamDataEpsgCode != 4326) {
			//	ReferencedEnvelope streamDataBounds4326 = reproject(streamDataBounds, "EPSG:4326");
			//	System.out.println("       ESGP:4326: ["+streamDataBounds4326.getMinX()+","+streamDataBounds4326.getMinY()+","+streamDataBounds4326.getMaxX()+","+streamDataBounds4326.getMaxY()+"]");
			//}
			
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		SimpleFeatureType outFeatureType = null;
		try {
			outFeatureType = DataUtilities.createType(OUTPUT_TABLE, "geometry:LineString");
		} catch (SchemaException e1) {
			System.out.println("Unable to create feature type "+OUTPUT_TABLE);
			System.exit(1);
		}
		
		
		SimpleFeatureBuilder featureBuilder = new SimpleFeatureBuilder(outFeatureType);
		
		DefaultFeatureCollection outFeatureCollection = new DefaultFeatureCollection(OUTPUT_TABLE, outFeatureType);

		
		
		//iterate over input, converting each line segment to a geometry
		
		try {
			String voronoiInLine = null;
			int lineNum = 0;
			int numSkipped = 0;
			while ((voronoiInLine = inReader.readLine()) != null) {
				String id = lineNum+"";
				//convert input line into a geometry
				Geometry geometry = null;
				
				try {
					//cleaning appears to be unnecessary because all the lines that were invalid before
					// are still invalid after
					String wkt = voronoiInLineToWkt(voronoiInLine);
					GeometryFactory geometryFactory = JTSFactoryFinder.getGeometryFactory();	
					WKTReader reader = new WKTReader(geometryFactory);
					geometry = reader.read(wkt);
					
					if (geometry == null) {
						throw new IllegalArgumentException("unable to parse WKT");
					}
					if(!geometry.isValid() || geometry.isEmpty()) {
						//throw new IllegalArgumentException("invalid geometry");
					}
				}
				catch (Exception e) {
					//e.printStackTrace();
					System.out.println(" skipping. "+e.getMessage()+". '"+voronoiInLine+"'");
					numSkipped++;
					continue;
				}
				geometry.setSRID(bboxSrid);
				
				//add the geometry to a feature
				Object[] attributeValues = new Object[] { geometry };
				SimpleFeature feature = featureBuilder.buildFeature(id, attributeValues);
				
				outFeatureCollection.add(feature);

				
				lineNum++;
			}
			inReader.close();
			System.out.println(numSkipped + " skipped");
			
			//write to output
			if (outFeatureCollection.size() > 0) {
				System.out.println("Saving "+OUTPUT_TABLE+"...");
				SimpleFeatureCollection edgesCollection = DataUtilities.simple(outFeatureCollection);
				FeatureEntry voronoiEdgesEntry = new FeatureEntry();
				voronoiEdgesEntry.setSrid(bboxSrid);
				voronoiEdgesEntry.setBounds(edgesCollection.getBounds());
				
	            System.out.println(" - Writing "+edgesCollection.size()+" features");
	            outGeoPackage.add(voronoiEdgesEntry, edgesCollection);
	            System.out.println(" - Done");
	            System.out.println("Adding spatial index on "+OUTPUT_TABLE+"...");
	            outGeoPackage.createSpatialIndex(voronoiEdgesEntry);
	            System.out.println(" - Done");	
			}
                        
            
		} catch (IOException e) {
			e.printStackTrace();
			System.exit(1);
		}
		
		
		System.out.print("All Done");
	}
	
	private static String voronoiInLineToWkt(String voronoiInLine) {
		//input example: s x1 y2  x2 y2
		//output example: LINESTRING (30 10, 10 30, 40 40)
		
		String[] pieces = voronoiInLine.split(" ");
		float x1 = Float.parseFloat(pieces[1]);
		float y1 = Float.parseFloat(pieces[2]);
		float x2 = Float.parseFloat(pieces[4]);
		float y2 = Float.parseFloat(pieces[5]);
		
		String wkt = "LINESTRING ("+x1+" "+y1+", "+x2+" "+y2+")";
		return wkt;
		
	}

}
