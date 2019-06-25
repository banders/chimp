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
import org.locationtech.jts.simplify.DouglasPeuckerSimplifier;
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
 * This script takes as input one table from a given geopackage with features
 * that exist as 2-point line strings, and converts that into a file that can be 
 * read into CGAL's voronoi algorithm, like this:
 * 
 * s <x coord> <y coord>
 * s <x coord> <y coord>
 * s <x coord> <y coord>
 * 
 */
public class PrepCgalVoronoiInput {

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
		options.addOption("o", true, "Output Text file");
		options.addOption("table", true, "name of table");
		CommandLineParser parser = new DefaultParser();
		HelpFormatter formatter = new HelpFormatter();
		
		String inputGeoPackageFilename = null;
		String outputTxtFilename = null;
		String bboxStr = null;
		String bboxCrs = null;
		int bboxSrid = -1;
		String table = null;
		String whitelist = null;
		String blacklist = null;
		String outTableName = "water_features_segmented";
		
		try {
			CommandLine cmd = parser.parse( options, args);
			inputGeoPackageFilename = cmd.getOptionValue("i");
			outputTxtFilename = cmd.getOptionValue("o");
			table = cmd.getOptionValue("table");
			whitelist = cmd.getOptionValue("whitelistfilter");
			blacklist = cmd.getOptionValue("blacklistfilter");
		} catch (ParseException e2) {
			formatter.printHelp( PrepCgalVoronoiInput.class.getSimpleName(), options );
		}

		//validate inputs
		if (inputGeoPackageFilename == null) {
			formatter.printHelp( PrepCgalVoronoiInput.class.getSimpleName(), options );
			System.exit(1);
		}
		if (outputTxtFilename == null) {
			formatter.printHelp( PrepCgalVoronoiInput.class.getSimpleName(), options );
			System.exit(1);
		}

		
		System.out.println("Inputs:");
		System.out.println("- in file: "+inputGeoPackageFilename);
		System.out.println(" - in table: "+table);
		System.out.println("- out text file: "+outputTxtFilename);
		
		
		Map<String, String> inputDatastoreParams = new HashMap<String, String>();
		inputDatastoreParams.put("dbtype", GEOPKG_ID);
		inputDatastoreParams.put("database", inputGeoPackageFilename);
		
		//Open input datastore
		DataStore inDatastore = null;
		try {
			inDatastore = DataStoreFinder.getDataStore(inputDatastoreParams);
		} catch (IOException e) {
			System.out.println("Unable to open input file: "+inputGeoPackageFilename);
			e.printStackTrace();
			System.exit(1);
		}
		
		if (inDatastore == null) {
			System.out.println("Unable to open input datastore");
			System.exit(1);
		}
			
		//Create output datastore
		BufferedWriter textFileWriter = null;
		try {
			textFileWriter = new BufferedWriter(new FileWriter(outputTxtFilename, false));
		} catch (IOException e) {
			System.out.println("Unable to open output file: "+outputTxtFilename);
			e.printStackTrace();
			System.exit(1);
		}
		
		FeatureSource inFeatureSource = null;
		ReferencedEnvelope inDataBounds = null;
		try {
			inFeatureSource = inDatastore.getFeatureSource(table);
			inDataBounds = inFeatureSource.getBounds();
		} catch (IOException e1) {
			System.out.println("Unable to read "+table+" features");
			e1.printStackTrace();
			System.exit(1);
		}
			
		try {
			int streamDataEpsgCode = CRS.lookupEpsgCode(inDataBounds.getCoordinateReferenceSystem(), true);
			System.out.println("Input data summary:");
			System.out.println(" - "+table);
			System.out.println("   - Data CRS: EPSG:"+streamDataEpsgCode);
			System.out.println("   - Data bounds");
			System.out.println("       EPSG:"+streamDataEpsgCode+": ["+inDataBounds.getMinX()+","+inDataBounds.getMinY()+","+inDataBounds.getMaxX()+","+inDataBounds.getMaxY()+"]");
			if (streamDataEpsgCode != 4326) {
				ReferencedEnvelope streamDataBounds4326 = reproject(inDataBounds, "EPSG:4326");
				System.out.println("       ESGP:4326: ["+streamDataBounds4326.getMinX()+","+streamDataBounds4326.getMinY()+","+streamDataBounds4326.getMaxX()+","+streamDataBounds4326.getMaxY()+"]");
			}
			
			
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		//output to files (txt and geopackage)
		//---------------------------------------------------------------------
		
		System.out.println("Writing to output...");
		
		try {
			GeometryFactory geometryFactory = JTSFactoryFinder.getGeometryFactory();
			double minx = inDataBounds.getMinX() - 10;
			double miny = inDataBounds.getMinY() - 10;
			double maxx = inDataBounds.getMaxX() + 10;
			double maxy = inDataBounds.getMaxY() + 10;
			Geometry boundingPolygon = geometryFactory.createPolygon(new Coordinate[] {
					new Coordinate(minx, miny),
					new Coordinate(maxx, miny),
					new Coordinate(maxx, maxy),
					new Coordinate(minx, maxy),
					new Coordinate(minx, miny),
					});
						
			System.out.println(" - 4 segments defining bbox of the data");
			
			//output the geometry of the target bbox itself
			writeGeometry(textFileWriter, boundingPolygon);
			
			//streams
			//-------
			FeatureCollection inFeatureCollection = inFeatureSource.getFeatures();
			FeatureIterator inIterator = inFeatureCollection.features();			
			System.out.println(" - "+inFeatureCollection.size() + " features from the input");
			while (inIterator.hasNext()) {            	
            	//get the input feature
            	SimpleFeature inFeature = (SimpleFeature)inIterator.next();            	
            	Geometry inGeometry = (Geometry)inFeature.getDefaultGeometry();
            	if (inGeometry.getNumPoints() != 2) {
            		throw new IllegalArgumentException("All geometries expected to be two-point line segments");
            	}
            	writeGeometry(textFileWriter, inGeometry);
            	
            }
			inIterator.close();
			
					
			System.out.println("Saved Text File: "+outputTxtFilename);
            
			
			//cleanup
			textFileWriter.close();
			inDatastore.dispose();
			
		} catch (IOException e) {
			e.printStackTrace();
			System.exit(1);
		}
		
		
		System.out.print("All Done");
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
