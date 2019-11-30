package ca.bc.gov.catchment.scripts;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
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
import org.tinfour.common.IConstraint;
import org.tinfour.common.IQuadEdge;
import org.tinfour.common.LinearConstraint;
import org.tinfour.common.Vertex;
import org.tinfour.standard.IncrementalTin;

import ca.bc.gov.catchment.algorithms.NearestNeighbour3DMaker;

/**
 * This script takes two files as input:
 * - the data set with 2D geometries that will be translated into 3D geometries
 * - a point cloud elevation file which is used to estimate elevation for
 *   coordinates in the 2D geometries.
 * The output is a copy of the input, except all geometries are changed to 3D
 * with elevation estimated from the point cloud. 
 * 
 * @author Brock Anderson
 *
 */

public class EstimateElevation {

	private static final String GEOPKG_ID = "geopkg";
	private static final int K_NEIGHBOURS = 3;
	private static final int DEFAULT_SEARCH_RADIUS = 200;

	public static void main(String[] args) {
		
		// create Options object
		Options options = new Options();
		options.addOption("i", true, "Input GeoPackage file containing the data to that needs elevation added");
		options.addOption("elevationFile", true, "Input GeoPackage file with 3D point cloud");
		options.addOption("o", true, "Output GeoPackage file");
		options.addOption("inTable", true, "table name from primary input file");
		options.addOption("elevationTable", true, "elevation table name");
		options.addOption("outTable", true, "output table name");
		options.addOption("bbox", true, "bbox (minx,miny,maxx,maxy)");
		options.addOption("bboxcrs", true, "e.g. EPSG:3005");
		options.addOption("searchRadius", true, "distance in same unit as input data crs");
		CommandLineParser parser = new DefaultParser();
		HelpFormatter formatter = new HelpFormatter();
		
		String inPrimaryFilename = null;
		String inElevationFilename = null;
		String outFilename = null;
		String inPrimaryTable = null;
		String inElevationTable = null;
		String outTable = null;
		String bboxStr = null;
		String bboxCrs = null;
		int bboxSrid = -1;
		ReferencedEnvelope boundsToProcess = null;
		double searchRadius = -1;
		
		try {
			CommandLine cmd = parser.parse( options, args);
			inPrimaryFilename = cmd.getOptionValue("i");
			inElevationFilename = cmd.getOptionValue("elevationFile");
			outFilename = cmd.getOptionValue("o");	
			inPrimaryTable = cmd.getOptionValue("inTable");
			inElevationTable = cmd.getOptionValue("elevationTable");
			outTable = cmd.getOptionValue("outTable");
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
		System.out.println("- primary input file: "+inPrimaryFilename);
		System.out.println("- primary table: "+inPrimaryTable);
		System.out.println("- elevation file: "+inElevationFilename);
		System.out.println("- elevation table: "+inElevationTable);
		System.out.println("- out file: "+outFilename);
		System.out.println("- out table: "+outTable);
		if (bboxStr != null) {
			System.out.println("- bbox: "+bboxStr+" ("+bboxCrs+")");	
		}
				
		//Open primary input file
		//---------------------------------------------------------------------
		
		Map<String, String> primaryInputDatastoreParams = new HashMap<String, String>();
		primaryInputDatastoreParams.put("dbtype", GEOPKG_ID);
		primaryInputDatastoreParams.put("database", inPrimaryFilename);
		
		DataStore primaryDatastore = null;
		try {
			primaryDatastore = DataStoreFinder.getDataStore(primaryInputDatastoreParams);
		} catch (IOException e) {
			System.out.println("Unable to open input file: "+inPrimaryFilename);
			e.printStackTrace();
			System.exit(1);
		}
		
		if (primaryDatastore == null) {
			System.out.println("Unable to open input datastore");
			System.exit(1);
		}
			
		//Open elevation file
		//---------------------------------------------------------------------
		
		DataStore elevationDatastore = null;
		Map<String, String> elevationDatastoreParams = new HashMap<String, String>();
		elevationDatastoreParams.put("dbtype", GEOPKG_ID);
		elevationDatastoreParams.put("database", inElevationFilename);
		
		try {
			elevationDatastore = DataStoreFinder.getDataStore(elevationDatastoreParams);
		} catch (IOException e) {
			System.out.println("Unable to open input file: "+elevationDatastoreParams);
			e.printStackTrace();
			System.exit(1);
		}
		
		if (elevationDatastore == null) {
			System.out.println("Unable to open input datastore");
			System.exit(1);
		}

		
		// Collect some information about the point cloud data
		//---------------------------------------------------------------------
		
		SimpleFeatureType inFeatureType = null;
		try {
			inFeatureType = primaryDatastore.getSchema(inPrimaryTable);			
		} catch (IOException e) {
			System.out.println("Unable to get schema for feature type "+inPrimaryTable+" in the input datastore");
			e.printStackTrace();
			System.exit(1);
		}
		
		String inGeometryPropertyName = inFeatureType.getGeometryDescriptor().getLocalName();
		
		CoordinateReferenceSystem crs = inFeatureType.getCoordinateReferenceSystem();
		int inSrid = -1;
		try {
			inSrid = CRS.lookupEpsgCode(crs, true);
		} catch (FactoryException e1) {
			System.out.println("Unable to lookup SRID for feature type "+inPrimaryTable);
			System.exit(1);
		}
		
		SimpleFeatureSource inFeatureSource = null;
		try {
			inFeatureSource = primaryDatastore.getFeatureSource(inPrimaryTable);
		} catch (IOException e1) {
			System.out.println("Unable to get in feature source: "+inPrimaryTable);
			e1.printStackTrace();
			System.exit(1);
		}
			
		// Collect some information about the breakline data
		//---------------------------------------------------------------------
		
		SimpleFeatureType elevationFeatureType = null;
		String elevationGeometryPropertyName = null;
		SimpleFeatureSource elevationFeatureSource = null;
		if (elevationDatastore != null) {
			
			try {
				elevationFeatureType = elevationDatastore.getSchema(inElevationTable);			
			} catch (IOException e) {
				System.out.println("Unable to get schema for feature type "+inElevationTable+" in the input datastore");
				e.printStackTrace();
				System.exit(1);
			}
			
			elevationGeometryPropertyName = elevationFeatureType.getGeometryDescriptor().getLocalName();
			
			CoordinateReferenceSystem elevationCrs = elevationFeatureType.getCoordinateReferenceSystem();
			int elevationSrid = -1;
			try {
				elevationSrid = CRS.lookupEpsgCode(elevationCrs, true);
			} catch (FactoryException e1) {
				System.out.println("Unable to lookup SRID for feature type "+inElevationTable);
				System.exit(1);
			}
			
			if (inSrid != elevationSrid) {
				System.out.println("SRID of input files must match.");
				System.exit(1);
			}
			
			try {
				elevationFeatureSource = elevationDatastore.getFeatureSource(inElevationTable);
			} catch (IOException e1) {
				System.out.println("Unable to get in feature source: "+inElevationTable);
				e1.printStackTrace();
				System.exit(1);
			}
		}
		
		//Setup the output data
		//---------------------------------------------------------------------

		//Create output geopackage
		File outFile = new File(outFilename);
		Map<String, String> outputDatastoreParams = new HashMap<String, String>();
		outputDatastoreParams.put("dbtype", GEOPKG_ID);
		outputDatastoreParams.put("database", outFilename);
		
		GeoPackage outGeoPackage = null;
		try {
			outGeoPackage = new GeoPackage(outFile);
			outGeoPackage.init();
		} catch (IOException e3) {
			System.out.println("Unable to create geopackage "+outFilename);
			e3.printStackTrace();
			System.exit(1);
		}
		
		int outSrid = inSrid;
		//SimpleFeatureType outFeatureType = inFeatureType;
		//DefaultFeatureCollection outFeatureCollection = new DefaultFeatureCollection(outTable, outFeatureType);
		

		//Processing
		//---------------------------------------------------------------------

		Hints filterHints = new Hints( Hints.FEATURE_2D, true ); // force 2D queries
		FilterFactory2 filterFactory = CommonFactoryFinder.getFilterFactory2(filterHints);
		SimpleFeatureIterator inFeatureIterator = null;
		
		try {
			
			SimpleFeatureCollection inFeatureCollection = null;
			if (boundsToProcess != null) {
				System.out.println("Selecting point cloud features in bbox...");
				Filter bboxFilter = filterFactory.bbox(filterFactory.property(inGeometryPropertyName), boundsToProcess);
				inFeatureCollection = inFeatureSource.getFeatures(bboxFilter);
			}
			else {
				inFeatureCollection = inFeatureSource.getFeatures();
			}
		
			//ensure the elevation data has a spatial index for faster processing
			System.out.println("Indexing elevation data...");
			SpatialIndexFeatureCollection fastElevationFeatureCollection = new SpatialIndexFeatureCollection(elevationFeatureSource.getFeatures());
			SpatialIndexFeatureSource fastElevationFeatureSource = new SpatialIndexFeatureSource(fastElevationFeatureCollection);
			
			System.out.println("Adding elevation to input geometries...");
			NearestNeighbour3DMaker threeDMaker = new NearestNeighbour3DMaker(fastElevationFeatureSource, K_NEIGHBOURS);
			SimpleFeatureCollection outFeatureCollection = threeDMaker.make3dCopy(inFeatureCollection);
			
			//save feature collection to output file
			//-----------------------------------------------------------------			
			FeatureEntry outEntry = new FeatureEntry();
			outEntry.setSrid(outSrid);
			outEntry.setBounds(outFeatureCollection.getBounds());
			outEntry.setZ(true); //store a z coordinate for each geometry
			
            System.out.println("Saving "+outTable+"...");
            System.out.println(" - Writing "+outFeatureCollection.size()+" features");
            outGeoPackage.add(outEntry, outFeatureCollection);
            System.out.println(" - Done");
            System.out.println("Adding spatial index on "+outTable+"...");
            outGeoPackage.createSpatialIndex(outEntry);
            System.out.println(" - Done");	  
			
		} catch(IOException e) {
			e.printStackTrace();
			System.exit(1);
		} finally {
			if (inFeatureIterator != null) {
				inFeatureIterator.close();
			}
		}
		
		
		System.out.println("All done");
		
	}
	
}
