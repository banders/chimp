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

import ca.bc.gov.catchment.CatchmentLines;
import ca.bc.gov.catchment.algorithms.NearestNeighbour3DMaker;
import ca.bc.gov.catchment.fitness.AvgElevationSectionFitness;
import ca.bc.gov.catchment.fitness.ElevationJunctionFitness;
import ca.bc.gov.catchment.fitness.ElevationSectionFitness;
import ca.bc.gov.catchment.fitness.ElevationLenghPenaltySectionFitness;
import ca.bc.gov.catchment.fitness.JunctionFitness;
import ca.bc.gov.catchment.fitness.SectionFitness;
import ca.bc.gov.catchment.fitness.RidgeSectionFitness;
import ca.bc.gov.catchment.fitness.SondheimSectionFitness;
import ca.bc.gov.catchment.fitness.TouchingJunctionFitness;
import ca.bc.gov.catchment.improvement.CatchmentSetImprover;
import ca.bc.gov.catchment.improvement.JunctionImprover;
import ca.bc.gov.catchment.improvement.JunctionModification;
import ca.bc.gov.catchment.improvement.SectionImprover;
import ca.bc.gov.catchment.improvement.SectionModification;
import ca.bc.gov.catchment.improvement.SimulatedAnnealingCatchmentSetImprover;
import ca.bc.gov.catchment.improvement.SimulatedAnnealingJunctionImprover;
import ca.bc.gov.catchment.improvement.SimulatedAnnealingSectionImprover;
import ca.bc.gov.catchment.improvement.ZipperCatchmentSetImprover;
import ca.bc.gov.catchment.tin.TinEdges;
import ca.bc.gov.catchment.tin.TinPolys;
import ca.bc.gov.catchment.water.WaterAnalyzer;
import ca.bc.gov.catchments.utils.SaveUtils;
import ca.bc.gov.catchments.utils.SpatialUtils;

/**
 * Creates a delaunay TIN from a given point cloud, optionally 
 * constrained by a set of break lines.
 * @author Brock Anderson
 *
 */

public class ImproveCatchments {

	private static final String GEOPKG_ID = "geopkg";
	private static final int MAX_ITERATIONS = 1;
	private static final double SEARCH_RADIUS = 200;
	
	private static TinEdges tinEdges;
	private static TinPolys tinPolys;
	private static SimpleFeatureSource tinPolysFeatureSource;
	private static WaterAnalyzer waterAnalyzer;
	private static SimpleFeatureSource waterFeatureSource;

	public static void main(String[] args) {
		
		// create Options object
		Options options = new Options();
		options.addOption("catchmentsFile", true, "Input GeoPackage file with initial catchments");
		options.addOption("waterFile", true, "Input GeoPackage file with water features");
		options.addOption("tinEdgesFile", true, "Input GeoPackage file with TIN");
		options.addOption("tinPolysFile", true, "Input GeoPackage file with TIN");
		options.addOption("o", true, "Output GeoPackage file");
		options.addOption("catchmentsTable", true, "catchments table name");
		options.addOption("waterTable", true, "water table name");
		options.addOption("tinEdgesTable", true, "TIN edges table name");
		options.addOption("tinPolysTable", true, "TIN polys table name");
		options.addOption("outTable", true, "output table name");
		options.addOption("bbox", true, "bbox (minx,miny,maxx,maxy)");
		options.addOption("bboxcrs", true, "e.g. EPSG:3005");
		CommandLineParser parser = new DefaultParser();
		HelpFormatter formatter = new HelpFormatter();
		
		String inCatchmentsFilename = null;
		String inWaterFilename = null;
		String inTinEdgesFilename = null;
		String inTinPolysFilename = null;
		String outputGeopackageFilename = null;
		String catchmentsTable = null;
		String waterTable = null;
		String tinEdgesTable = null;
		String tinPolysTable = null;
		String outTable = null;
		String bboxStr = null;
		String bboxCrs = null;
		int bboxSrid = -1;
		ReferencedEnvelope boundsToProcess = null;
		
		try {
			CommandLine cmd = parser.parse( options, args);
			inCatchmentsFilename = cmd.getOptionValue("catchmentsFile");
			inWaterFilename = cmd.getOptionValue("waterFile");
			inTinEdgesFilename = cmd.getOptionValue("tinEdgesFile");
			inTinPolysFilename = cmd.getOptionValue("tinPolysFile");
			outputGeopackageFilename = cmd.getOptionValue("o");	
			catchmentsTable = cmd.getOptionValue("catchmentsTable");
			waterTable = cmd.getOptionValue("waterTable");
			tinEdgesTable = cmd.getOptionValue("tinEdgesTable");
			tinPolysTable = cmd.getOptionValue("tinPolysTable");
			outTable = cmd.getOptionValue("outTable");
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
		System.out.println("- catchments input file: "+inCatchmentsFilename);
		System.out.println("- catchments table: "+catchmentsTable);
		System.out.println("- water input file: "+inWaterFilename);
		System.out.println("- water table: "+waterTable);
		System.out.println("- TIN edges input file: "+inTinEdgesFilename);
		System.out.println("- TIN edges table: "+tinEdgesTable);
		System.out.println("- TIN polys input file: "+inTinPolysFilename);
		System.out.println("- TIN polys table: "+tinPolysTable);
		System.out.println("- out file: "+outputGeopackageFilename);
		System.out.println("- out table: "+outTable);
		if (bboxStr != null) {
			System.out.println("- bbox: "+bboxStr+" ("+bboxCrs+")");	
		}
				
		//Open catchments input file
		//---------------------------------------------------------------------
		
		Map<String, String> catchmentInputDatastoreParams = new HashMap<String, String>();
		catchmentInputDatastoreParams.put("dbtype", GEOPKG_ID);
		catchmentInputDatastoreParams.put("database", inCatchmentsFilename);
		
		DataStore catchmentInputDatastore = null;
		try {
			catchmentInputDatastore = DataStoreFinder.getDataStore(catchmentInputDatastoreParams);
		} catch (IOException e) {
			System.out.println("Unable to open input file: "+inCatchmentsFilename);
			e.printStackTrace();
			System.exit(1);
		}
		
		if (catchmentInputDatastore == null) {
			System.out.println("Unable to open input datastore");
			System.exit(1);
		}
			
		SimpleFeatureType inCatchmentFeatureType = null;
		try {
			inCatchmentFeatureType = catchmentInputDatastore.getSchema(catchmentsTable);			
		} catch (IOException e) {
			System.out.println("Unable to get schema for feature type "+catchmentsTable+" in the input datastore");
			e.printStackTrace();
			System.exit(1);
		}
		
		String inCatchmentGeometryPropertyName = inCatchmentFeatureType.getGeometryDescriptor().getLocalName();
		
		SimpleFeatureSource inCatchmentFeatureSource = null;
		try {
			inCatchmentFeatureSource = catchmentInputDatastore.getFeatureSource(catchmentsTable);
		} catch (IOException e1) {
			System.out.println("Unable to get in feature source: "+catchmentsTable);
			e1.printStackTrace();
			System.exit(1);
		}
		
		//Open water input file
		//---------------------------------------------------------------------
		
		Map<String, String> waterInputDatastoreParams = new HashMap<String, String>();
		waterInputDatastoreParams.put("dbtype", GEOPKG_ID);
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
		
		waterFeatureSource = null;
		try {
			waterFeatureSource = waterDatastore.getFeatureSource(waterTable);
		} catch (IOException e1) {
			System.out.println("Unable to get in feature source: "+waterTable);
			e1.printStackTrace();
			System.exit(1);
		}
	
		//Open TIN edges input file
		//---------------------------------------------------------------------
		
		Map<String, String> tinEdgesInputDatastoreParams = new HashMap<String, String>();
		tinEdgesInputDatastoreParams.put("dbtype", GEOPKG_ID);
		tinEdgesInputDatastoreParams.put("database", inTinEdgesFilename);
		
		DataStore tinEdgesDatastore = null;
		try {
			tinEdgesDatastore = DataStoreFinder.getDataStore(tinEdgesInputDatastoreParams);
		} catch (IOException e) {
			System.out.println("Unable to open input file: "+inTinEdgesFilename);
			e.printStackTrace();
			System.exit(1);
		}
		
		if (tinEdgesDatastore == null) {
			System.out.println("Unable to open input TIN edges datastore");
			System.exit(1);
		}
		
		SimpleFeatureSource tinEdgesFeatureSource = null;
		try {
			tinEdgesFeatureSource = tinEdgesDatastore.getFeatureSource(tinEdgesTable);
		} catch (IOException e1) {
			System.out.println("Unable to get in feature source: "+tinEdgesTable);
			e1.printStackTrace();
			System.exit(1);
		}
	
		//Open TIN polys input file
		//---------------------------------------------------------------------
		
		Map<String, String> tinPolysInputDatastoreParams = new HashMap<String, String>();
		tinPolysInputDatastoreParams.put("dbtype", GEOPKG_ID);
		tinPolysInputDatastoreParams.put("database", inTinPolysFilename);
		
		DataStore tinPolysDatastore = null;
		try {
			tinPolysDatastore = DataStoreFinder.getDataStore(tinPolysInputDatastoreParams);
		} catch (IOException e) {
			System.out.println("Unable to open input file: "+inTinPolysFilename);
			e.printStackTrace();
			System.exit(1);
		}
		
		if (tinPolysDatastore == null) {
			System.out.println("Unable to open input TIN edges datastore");
			System.exit(1);
		}
		
		tinPolysFeatureSource = null;
		try {
			tinPolysFeatureSource = tinPolysDatastore.getFeatureSource(tinPolysTable);
		} catch (IOException e1) {
			System.out.println("Unable to get in feature source: "+tinPolysTable);
			e1.printStackTrace();
			System.exit(1);
		}
		
		//Processing
		//---------------------------------------------------------------------		
		
		Hints filterHints = new Hints( Hints.FEATURE_2D, true ); // force 2D queries
		FilterFactory2 filterFactory = CommonFactoryFinder.getFilterFactory2(filterHints);
		
		try {
			
			//limit input catchments to given bbox (if specified)	
			SimpleFeatureCollection inCatchmentFeatureCollection = null;
			if (boundsToProcess != null) {
				System.out.println("Selecting input catchments in bbox...");
				Filter bboxFilter = filterFactory.bbox(
						filterFactory.property(inCatchmentGeometryPropertyName), 
						boundsToProcess);
				inCatchmentFeatureCollection = inCatchmentFeatureSource.getFeatures(bboxFilter);
			}
			else {
				inCatchmentFeatureCollection = inCatchmentFeatureSource.getFeatures();
			}
			
			SpatialIndexFeatureCollection indexedFc = new SpatialIndexFeatureCollection(inCatchmentFeatureCollection);
			SimpleFeatureSource catchmentsFeatureSource = new SpatialIndexFeatureSource(indexedFc);
			tinPolys = new TinPolys(tinPolysFeatureSource);
			
			//convert all catchment coords to 3D
			//TODO: offload this responsibility to a separate script that should be run before
			//ImproveCatchments
			/*
			System.out.println("converting input to 3D...");
			NearestNeighbour3DMaker elevationAdder = new NearestNeighbour3DMaker(tinEdgesFeatureSource, 3);
			catchmentsFeatureSource = elevationAdder.make3dCopy(catchmentsFeatureSource);			
			*/
			CatchmentLines catchmentLines = new CatchmentLines(catchmentsFeatureSource);	
			
			tinEdges = new TinEdges(tinEdgesFeatureSource);
			waterAnalyzer = new WaterAnalyzer(waterFeatureSource);
			
			for(int iterationNum = 0; iterationNum < MAX_ITERATIONS; iterationNum++) {
				improveJunctions(catchmentLines, 15);
				
				String outFilename1 = outputGeopackageFilename.replace(".gpkg", "-j"+iterationNum+".gpkg");
				SimpleFeatureCollection outFeatureCollection1 = catchmentLines.getUpdatedFeatures();
				outFeatureCollection1 = SpatialUtils.renameFeatureType(outFeatureCollection1, outTable);
				SaveUtils.saveToGeoPackage(outFilename1, outFeatureCollection1, true);
				
				improveSections(catchmentLines, 50);
				
				String outFilename2 = outputGeopackageFilename.replace(".gpkg", "-s"+iterationNum+".gpkg");
				SimpleFeatureCollection outFeatureCollection2 = catchmentLines.getUpdatedFeatures();
				outFeatureCollection2 = SpatialUtils.renameFeatureType(outFeatureCollection2, outTable);
				SaveUtils.saveToGeoPackage(outFilename2, outFeatureCollection2, true);
			}
			
			//saving
			//-----------------------------------------------------------------
			SimpleFeatureCollection outFeatureCollection = catchmentLines.getUpdatedFeatures();
			outFeatureCollection = SpatialUtils.renameFeatureType(outFeatureCollection, outTable);
			
			SaveUtils.saveToGeoPackage(outputGeopackageFilename, outFeatureCollection, true);
			
		} catch(IOException e) {
			e.printStackTrace();
			System.exit(1);
		} catch(SchemaException e) {
			e.printStackTrace();
			System.exit(1);
		}
		
		System.out.println("All done");
		
	}
	
	private static CatchmentLines improveJunctions(CatchmentLines catchmentLines, int numSteps) throws IOException {
		SectionFitness sectionFitness = new AvgElevationSectionFitness(tinPolys);
		JunctionFitness junctionFitness = new TouchingJunctionFitness(sectionFitness);
		
		JunctionImprover junctionImprover = new SimulatedAnnealingJunctionImprover(
				catchmentLines,
				tinEdges,
				waterFeatureSource,									
				junctionFitness,
				SEARCH_RADIUS,
				numSteps
				);
		
		System.out.println("improving junctions...");
		for(Coordinate junction : catchmentLines.getJunctions(waterAnalyzer)) {	
			JunctionModification junctionModification = null;
			try {
				junctionModification = junctionImprover.improve(junction);
			}
			catch(Exception e) {
				e.printStackTrace();
				continue;
			}
			if (junctionModification.getModifiedJunction() != junctionModification.getOriginalJunction()) {
				System.out.println("  moved junction "+junctionModification.getOriginalJunction()+" to "+junctionModification.getModifiedJunction());
				for (SimpleFeature touchingSection : junctionModification.getModifiedSections()) {
					//System.out.println("  updated section with fid="+touchingSection.getIdentifier());
					catchmentLines.addOrUpdate(touchingSection);
				}
			}				
			System.out.println("---");
		}
		
		return catchmentLines;
	}
	
	private static CatchmentLines improveSections(CatchmentLines catchmentLines, int numSteps) throws IOException {
		//SectionFitness sectionFitness = new RidgeSectionFitness(tinPolys);
		SectionFitness sectionFitness = new ElevationLenghPenaltySectionFitness(tinPolys);
		//SectionFitness sectionFitness = new SondheimSectionFitness(tinPolys);
		
		SectionImprover sectionImprover = new SimulatedAnnealingSectionImprover(
				catchmentLines, 
				tinEdges, 
				waterFeatureSource, 
				sectionFitness, 
				SEARCH_RADIUS, 
				numSteps
				);
		
		System.out.println("improving sections...");
		SimpleFeatureCollection sections = catchmentLines.getOriginalFeatures();
		SimpleFeatureIterator sectionIt = sections.features();
		while(sectionIt.hasNext()) {
			SimpleFeature section = sectionIt.next();				
			section = catchmentLines.getLatest(section);
			SectionModification modification = null;
			try {
				modification = sectionImprover.improve(section);
			} 
			catch (Exception e) {
				continue;
			}
			
			catchmentLines.addOrUpdate(modification.getModifiedSection());
			for (SimpleFeature touchingSection : modification.getModifiedTouchingSections()) {
				//System.out.println(" updated section with fid="+touchingSection.getIdentifier());
				catchmentLines.addOrUpdate(touchingSection);
				throw new IllegalStateException("This should never occur -- touching sections don't get modified by SectionImprover");
			}
			System.out.println("---");
		}
		sectionIt.close();		
		
		
		return catchmentLines;
	}
	
}
