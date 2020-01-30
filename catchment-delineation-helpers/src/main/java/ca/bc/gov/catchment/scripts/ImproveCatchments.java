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
import org.opengis.filter.identity.FeatureId;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.tinfour.common.IConstraint;
import org.tinfour.common.IQuadEdge;
import org.tinfour.common.LinearConstraint;
import org.tinfour.common.Vertex;
import org.tinfour.standard.IncrementalTin;

import ca.bc.gov.catchment.CatchmentLines;
import ca.bc.gov.catchment.algorithms.NearestNeighbour3DMaker;
import ca.bc.gov.catchment.fitness.AvgElevationLengthPenaltySectionFitness;
import ca.bc.gov.catchment.fitness.AvgElevationSectionFitness;
import ca.bc.gov.catchment.fitness.ElevationJunctionFitness;
import ca.bc.gov.catchment.fitness.ElevationSectionFitness;
import ca.bc.gov.catchment.fitness.ElevationLenghPenaltySectionFitness;
import ca.bc.gov.catchment.fitness.JunctionFitness;
import ca.bc.gov.catchment.fitness.PartialSumTouchingJunctionFitness;
import ca.bc.gov.catchment.fitness.SectionFitness;
import ca.bc.gov.catchment.fitness.RidgeSectionFitness;
import ca.bc.gov.catchment.fitness.SondheimSectionFitness;
import ca.bc.gov.catchment.fitness.SumTouchingJunctionFitness;
import ca.bc.gov.catchment.improvement.ImprovementCoverage;
import ca.bc.gov.catchment.improvement.ImprovementMetrics;
import ca.bc.gov.catchment.improvement.Junction;
import ca.bc.gov.catchment.improvement.JunctionImprover;
import ca.bc.gov.catchment.improvement.JunctionModification;
import ca.bc.gov.catchment.improvement.SectionImprover;
import ca.bc.gov.catchment.improvement.SectionModification;
import ca.bc.gov.catchment.improvement.SimulatedAnnealingJunctionImprover;
import ca.bc.gov.catchment.improvement.SimulatedAnnealingSectionImprover;
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
	private static final int MAX_JUNCTION_ITERATIONS = 20;
	private static final int MAX_SECTION_ITERATIONS = 10;
	private static final float MIN_JUNCTION_IMPROVEMENT_PERCENT = 0.5f;
	private static final float MIN_SECTION_IMPROVEMENT_PERCENT = MIN_JUNCTION_IMPROVEMENT_PERCENT / 20;	
	private static final double SEARCH_RADIUS = 200;
	private static final double BUFFER_DISTANCE_METRES = 200;
	private static final int MIN_NO_IMPROVEMENT_COUNT_TO_SKIP_SECTION = 2;
	private static final int MIN_NO_IMPROVEMENT_COUNT_TO_SKIP_JUNCTION = 3;
	private static final int NUM_TESTS_PER_JUNCTION = 30;
	private static final int NUM_TESTS_PER_SECTION = 100;
	
	private static TinEdges tinEdges;
	private static TinPolys tinPolys;
	private static SimpleFeatureSource tinPolysFeatureSource;
	private static WaterAnalyzer waterAnalyzer;
	private static SimpleFeatureSource waterFeatureSource;
	private static ImprovementCoverage sectionImprovementCoverage;
	private static ImprovementCoverage junctionImprovementCoverage;
	private static SectionFitness sectionFitness;
	private static Map<FeatureId, Integer> sectionNoImprovementCount;
	private static Map<String, Integer> junctionNoImprovementCount;

	public static void main(String[] args) {
		
		// create Options object
		Options options = new Options();
		options.addOption("catchmentsFile", true, "Input GeoPackage file with initial catchments");
		options.addOption("waterFile", true, "Input GeoPackage file with water features");
		options.addOption("tinEdgesFile", true, "Input GeoPackage file with TIN");
		options.addOption("tinPolysFile", true, "Input GeoPackage file with TIN");
		options.addOption("outJunctionImprovementCoverageFile", true, "Output GeoPackage file for junction improvement coverage");
		options.addOption("outSectionImprovementCoverageFile", true, "Output GeoPackage file for section improvement coverage");
		options.addOption("o", true, "Output GeoPackage file");
		options.addOption("catchmentsTable", true, "catchments table name");
		options.addOption("waterTable", true, "water table name");
		options.addOption("tinEdgesTable", true, "TIN edges table name");
		options.addOption("tinPolysTable", true, "TIN polys table name");
		options.addOption("outTable", true, "output table name");
		options.addOption("outImprovementCoverageTable", true, "output table name for improvement coverage");
		options.addOption("bbox", true, "bbox (minx,miny,maxx,maxy)");
		options.addOption("bboxcrs", true, "e.g. EPSG:3005");
		CommandLineParser parser = new DefaultParser();
		HelpFormatter formatter = new HelpFormatter();
		
		String inCatchmentsFilename = null;
		String inWaterFilename = null;
		String inTinEdgesFilename = null;
		String inTinPolysFilename = null;
		String outputGeopackageFilename = null;
		String outputSectionImprovementCoverageGeopackageFile = null;
		String outputJunctionImprovementCoverageGeopackageFile = null;
		String catchmentsTable = null;
		String waterTable = null;
		String tinEdgesTable = null;
		String tinPolysTable = null;
		String outTable = null;
		String outImprovementCoverageTable = null;
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
			outputSectionImprovementCoverageGeopackageFile = cmd.getOptionValue("outSectionImprovementCoverageFile");	
			outputJunctionImprovementCoverageGeopackageFile = cmd.getOptionValue("outJunctionImprovementCoverageFile");	
			catchmentsTable = cmd.getOptionValue("catchmentsTable");
			waterTable = cmd.getOptionValue("waterTable");
			tinEdgesTable = cmd.getOptionValue("tinEdgesTable");
			tinPolysTable = cmd.getOptionValue("tinPolysTable");
			outTable = cmd.getOptionValue("outTable");
			outImprovementCoverageTable = cmd.getOptionValue("outImprovementCoverageTable", "improvement_coverage");
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
		
		Date start = new Date();
		
		Hints filterHints = new Hints( Hints.FEATURE_2D, true ); // force 2D queries
		FilterFactory2 filterFactory = CommonFactoryFinder.getFilterFactory2(filterHints);
		
		try {
			
			//define a bbox filter
			Filter bboxFilter = null;
			Filter bufferedBboxFilter = null;
			if (boundsToProcess != null) {
				System.out.println("Defining bbox limit...");
				
				
				bboxFilter = filterFactory.bbox(
						filterFactory.property(inCatchmentGeometryPropertyName), 
						boundsToProcess);
								
			}	
			
			SpatialIndexFeatureCollection catchmentFeatureCollection = new SpatialIndexFeatureCollection(inCatchmentFeatureSource.getFeatures(bboxFilter));
			SpatialIndexFeatureSource fastCatchmentFeatureSource = new SpatialIndexFeatureSource(catchmentFeatureCollection);
			
			ReferencedEnvelope bufferedBounds = new ReferencedEnvelope(fastCatchmentFeatureSource.getBounds());
			bufferedBounds.expandBy(BUFFER_DISTANCE_METRES);
			bufferedBboxFilter = filterFactory.bbox(
					filterFactory.property(inCatchmentGeometryPropertyName), 
					bufferedBounds);
			
			SpatialIndexFeatureCollection waterFeatureCollection = new SpatialIndexFeatureCollection(waterFeatureSource.getFeatures());
			SpatialIndexFeatureSource fastWaterFeatureSource = new SpatialIndexFeatureSource(waterFeatureCollection);
			
			tinPolys = new TinPolys(tinPolysFeatureSource, bufferedBboxFilter);
			tinEdges = new TinEdges(tinEdgesFeatureSource, bufferedBboxFilter);
			waterAnalyzer = new WaterAnalyzer(fastWaterFeatureSource);
			
			//fitness functions
			SectionFitness globalFitness = new AvgElevationSectionFitness(tinPolys);

			CatchmentLines catchmentLines = new CatchmentLines(fastCatchmentFeatureSource, bboxFilter);
			double initialFitness = globalFitness.fitnessAvg(catchmentLines.getOriginalFeatures());
			
			System.out.println("improving junctions...");
			junctionNoImprovementCount = new HashMap<String, Integer>();
			for(int iterationNum = 1; iterationNum <= MAX_JUNCTION_ITERATIONS; iterationNum++) {
				
				double roundStartGlobalFitness = globalFitness.fitnessAvg(catchmentLines.getUpdatedFeatures());
				
				ImprovementMetrics metrics = improveJunctions(catchmentLines, NUM_TESTS_PER_JUNCTION);
				
				/*
				String outFilename1 = outputGeopackageFilename.replace(".gpkg", "-j"+iterationNum+".gpkg");
				SimpleFeatureCollection outFeatureCollection1 = catchmentLines.getUpdatedFeatures();
				outFeatureCollection1 = SpatialUtils.renameFeatureType(outFeatureCollection1, outTable);
				SaveUtils.saveToGeoPackage(outFilename1, outFeatureCollection1, true);
				*/
				
				double roundEndGlobalFitness = globalFitness.fitnessAvg(catchmentLines.getUpdatedFeatures());
				double percentImprovementThisRound = (roundEndGlobalFitness - roundStartGlobalFitness)/roundEndGlobalFitness * 100;
				System.out.println("junction round "+iterationNum+" complete");
				System.out.println(" improvements: ");
				System.out.println("  - # requested: "+metrics.getNumImprovementRequests());
				System.out.println("  - # succeeded: "+metrics.getNumImproved());
				System.out.println(" alternatives tested: ");
				System.out.println("  - # total: "+metrics.getNumAlternativesTested());
				System.out.println("  - # valid: "+metrics.getNumValidAlternativesTested());
				System.out.println(" runtime: ");
				System.out.println("  - total for all improvement requests: "+metrics.getRuntimeMs()+ " ms");
				System.out.println("  - average per improvement request: "+metrics.getAvgRuntimeMsPerTest()+ " ms");
				System.out.println("  - average per improvement request alternative: "+metrics.getAvgRuntimeMsPerTest()+ " ms");				
				System.out.println(" global fitness:");
				System.out.println("  - start: "+roundStartGlobalFitness);
				System.out.println("  - end: "+roundEndGlobalFitness);
				System.out.println("  - improved by: "+percentImprovementThisRound+"%");
				
				if(metrics.getNumImprovementRequests() == 0) {
					System.out.println("Junction improvements no improvements were requested in the previous round");
					break;
				}
				/*
				if (percentImprovementThisRound < MIN_JUNCTION_IMPROVEMENT_PERCENT) {
					System.out.println("Junction improvements halted because latest improvement < "+MIN_JUNCTION_IMPROVEMENT_PERCENT);
					break;
				}
				*/
			}
				
			System.out.println("improving sections...");
			sectionNoImprovementCount = new HashMap<FeatureId, Integer>();
			for(int iterationNum = 1; iterationNum <= MAX_SECTION_ITERATIONS; iterationNum++) {
				
				double roundStartGlobalFitness = globalFitness.fitnessAvg(catchmentLines.getUpdatedFeatures());
				
				ImprovementMetrics metrics = improveSections(catchmentLines, NUM_TESTS_PER_SECTION);
				
				/*
				String outFilename2 = outputGeopackageFilename.replace(".gpkg", "-s"+iterationNum+".gpkg");
				SimpleFeatureCollection outFeatureCollection2 = catchmentLines.getUpdatedFeatures();
				outFeatureCollection2 = SpatialUtils.renameFeatureType(outFeatureCollection2, outTable);
				SaveUtils.saveToGeoPackage(outFilename2, outFeatureCollection2, true);
				*/
				
				double roundEndGlobalFitness = globalFitness.fitnessAvg(catchmentLines.getUpdatedFeatures());
				double percentImprovementThisRound = (roundEndGlobalFitness - roundStartGlobalFitness)/roundEndGlobalFitness * 100;			
				System.out.println("section round "+iterationNum+" complete");
				System.out.println(" improvements: ");
				System.out.println("  - # requested: "+metrics.getNumImprovementRequests());
				System.out.println("  - # succeeded: "+metrics.getNumImproved());
				System.out.println(" alternatives tested: ");
				System.out.println("  - # total: "+metrics.getNumAlternativesTested());
				System.out.println("  - # valid: "+metrics.getNumValidAlternativesTested());				
				System.out.println(" runtime: ");
				System.out.println("  - total for all improvement requests: "+metrics.getRuntimeMs()+ " ms");
				System.out.println("  - average per improvement request: "+metrics.getAvgRuntimeMsPerRequest()+ " ms");
				System.out.println("  - average per improvement request alternative: "+metrics.getAvgRuntimeMsPerTest()+ " ms");				
				System.out.println(" global fitness:");
				System.out.println("  - start: "+roundStartGlobalFitness);
				System.out.println("  - end: "+roundEndGlobalFitness);
				System.out.println("  - improved by: "+percentImprovementThisRound+"%");
				
				if(metrics.getNumImprovementRequests() == 0) {
					System.out.println("Junction improvements no improvements were requested in the previous round");
					break;
				}
				/*
				if (percentImprovementThisRound < MIN_SECTION_IMPROVEMENT_PERCENT) {
					System.out.println("section improvements halted because latest improvement < "+MIN_SECTION_IMPROVEMENT_PERCENT);
					break;
				}
				*/
			}
			
			Date end = new Date();
			
			//saving improved catchments
			//-----------------------------------------------------------------
			SimpleFeatureCollection outFeatureCollection = catchmentLines.getUpdatedFeatures();
			outFeatureCollection = SpatialUtils.renameFeatureType(outFeatureCollection, outTable);
			
			SaveUtils.saveToGeoPackage(outputGeopackageFilename, outFeatureCollection, true);
			
			
			//saving improvement coverage
			//-----------------------------------------------------------------
			if (sectionImprovementCoverage != null) {
				System.out.println("section improvement coverage fraction (total): "+sectionImprovementCoverage.getTotalCoverageFraction());
				System.out.println("section improvement coverage fraction (valid only): "+sectionImprovementCoverage.getValidCoverageFraction());
				if (outputSectionImprovementCoverageGeopackageFile != null) {
					SimpleFeatureSource improvementCoverageSource = sectionImprovementCoverage.toFeatureSource();
					SimpleFeatureCollection improvementCoverageCollection = improvementCoverageSource.getFeatures();
					improvementCoverageCollection = SpatialUtils.renameFeatureType(improvementCoverageCollection, outImprovementCoverageTable);				
					SaveUtils.saveToGeoPackage(outputSectionImprovementCoverageGeopackageFile, improvementCoverageCollection, true);
				}
			}
			if (junctionImprovementCoverage != null) {
				System.out.println("junction improvement coverage fraction (total): "+junctionImprovementCoverage.getTotalCoverageFraction());
				System.out.println("junction improvement coverage fraction (valid only): "+junctionImprovementCoverage.getValidCoverageFraction());
				if (outputJunctionImprovementCoverageGeopackageFile != null) {
					SimpleFeatureSource improvementCoverageSource = junctionImprovementCoverage.toFeatureSource();
					SimpleFeatureCollection improvementCoverageCollection = improvementCoverageSource.getFeatures();
					improvementCoverageCollection = SpatialUtils.renameFeatureType(improvementCoverageCollection, outImprovementCoverageTable);				
					SaveUtils.saveToGeoPackage(outputJunctionImprovementCoverageGeopackageFile, improvementCoverageCollection, true);
				}
			}
			
			
			System.out.println("initial avg catchment elevation:"+initialFitness);
			double finalFitness = globalFitness.fitnessAvg(catchmentLines.getUpdatedFeatures());
			System.out.println("final avg catchment fitness:"+finalFitness);
			
			double runtimeMinutes = (end.getTime() - start.getTime())/(1000.0*60);
			System.out.println("run time (minutes): "+runtimeMinutes); //minutes
			
		} catch(IOException e) {
			e.printStackTrace();
			System.exit(1);
		} catch(SchemaException e) {
			e.printStackTrace();
			System.exit(1);
		}
		
		System.out.println("All done");
		
	}
	
	private static ImprovementMetrics improveJunctions(CatchmentLines catchmentLines, int numSteps) throws IOException {
		ImprovementMetrics metricsTotal = new ImprovementMetrics();
		
		SectionFitness sectionFitness = new AvgElevationSectionFitness(tinPolys);
		//SectionFitness sectionFitness = new AvgElevationLengthPenaltySectionFitness(tinPolys);
		//JunctionFitness junctionFitness = new SumTouchingJunctionFitness(sectionFitness);
		JunctionFitness junctionFitness = new PartialSumTouchingJunctionFitness(sectionFitness, 3);
		
		SimulatedAnnealingJunctionImprover junctionImprover = new SimulatedAnnealingJunctionImprover(
				catchmentLines,
				tinEdges,
				waterFeatureSource,									
				junctionFitness,
				SEARCH_RADIUS,
				numSteps
				);
		
		if (junctionImprovementCoverage != null) {
			junctionImprover.setImprovementCoverage(junctionImprovementCoverage);
		}
		
		List<Coordinate> junctions = catchmentLines.getJunctions(waterAnalyzer);
				
		System.out.println("attempting to improve "+junctions.size()+" junctions");
		for(Coordinate junctionCoord : junctions) {
			
			List<SimpleFeature> touchingSections = catchmentLines.getSectionsTouchingJunction(junctionCoord);
			Junction junction = new Junction(junctionCoord, touchingSections);
			
			System.out.println("---");
			if (getNoImprovementCount(junction) >= MIN_NO_IMPROVEMENT_COUNT_TO_SKIP_JUNCTION) {
				System.out.println("skipping. this junction has "+getNoImprovementCount(junction)+" prior no-improvement events");
				continue;
			}
			JunctionModification modification = null;
			try {
				modification = junctionImprover.improve(junction);
				metricsTotal.merge(modification.getImprovementMetrics());
			}
			catch(Exception e) {
				e.printStackTrace();
			}
			 
			if (modification != null && modification.isModified()) {
				System.out.println("  moved junction "+modification.getOriginalJunction().getCoordinate()+" to "+modification.getModifiedJunction().getCoordinate());
				for (SimpleFeature touchingSection : modification.getModifiedJunction().getTouchingSections()) {
					//System.out.println("  updated section with fid="+touchingSection.getIdentifier());
					catchmentLines.addOrUpdate(touchingSection);
				}
			} else {
				incrementNoImprovementCount(junction);
			}
		}
		
		junctionImprovementCoverage = junctionImprover.getImprovementCoverage();
		
		return metricsTotal;
	}
	
	private static ImprovementMetrics improveSections(CatchmentLines catchmentLines, int numSteps) throws IOException {
		
		ImprovementMetrics metricsTotal = new ImprovementMetrics();
		
		//sectionFitness = new AvgElevationSectionFitness(tinPolys);
		sectionFitness = new AvgElevationLengthPenaltySectionFitness(tinPolys);
		//SectionFitness sectionFitness = new RidgeSectionFitness(tinPolys);			
		//sectionFitness = new ElevationLenghPenaltySectionFitness(tinPolys);
		//SectionFitness sectionFitness = new SondheimSectionFitness(tinPolys);
		
		SimulatedAnnealingSectionImprover sectionImprover = new SimulatedAnnealingSectionImprover(
				catchmentLines, 
				tinEdges, 
				waterFeatureSource, 
				sectionFitness, 
				SEARCH_RADIUS, 
				numSteps
				);
		
		if (sectionImprovementCoverage != null) {
			sectionImprover.setImprovementCoverage(sectionImprovementCoverage);
		}
		
		SimpleFeatureCollection sections = catchmentLines.getOriginalFeatures();
		SimpleFeatureIterator sectionIt = sections.features();
		while(sectionIt.hasNext()) {
			System.out.println("---");
			SimpleFeature section = sectionIt.next();	
			if (getNoImprovementCount(section) >= MIN_NO_IMPROVEMENT_COUNT_TO_SKIP_SECTION) {
				System.out.println("skipping.  assume this section cannot be improved further.");
				continue;
			}
			section = catchmentLines.getLatest(section);
			SectionModification modification = null;
			try {
				modification = sectionImprover.improve(section);
				metricsTotal.merge(modification.getImprovementMetrics());
			} 
			catch (Exception e) {
				System.out.println("no improvements were possible. ");
				e.printStackTrace();
			}
			
			if (modification != null && modification.isModified()) {
				catchmentLines.addOrUpdate(modification.getModifiedSection());
				for (SimpleFeature touchingSection : modification.getModifiedTouchingSections()) {
					//System.out.println(" updated section with fid="+touchingSection.getIdentifier());
					catchmentLines.addOrUpdate(touchingSection);
					throw new IllegalStateException("This should never occur -- touching sections don't get modified by SectionImprover");
				}	
			}
			else {
				incrementNoImprovementCount(section);
			}
			
		}
		sectionIt.close();		
		
		sectionImprovementCoverage = sectionImprover.getImprovementCoverage();
		
		return metricsTotal;
	}
	
	// ------------------------------------------------------------------------
	// Functions to support testing of the end condition
	// ------------------------------------------------------------------------
	
	private static void incrementNoImprovementCount(Junction junction) {
		String key = junction.getID();
		if(junctionNoImprovementCount.containsKey(key)) {
			int count = junctionNoImprovementCount.get(key);
			count++;
			junctionNoImprovementCount.put(key, count);
		}
		else {
			junctionNoImprovementCount.put(key, 1);
		}
	}
	
	private static void incrementNoImprovementCount(SimpleFeature section) {
		FeatureId key = section.getIdentifier();
		if(sectionNoImprovementCount.containsKey(key)) {
			int count = sectionNoImprovementCount.get(key);
			count++;
			sectionNoImprovementCount.put(key, count);
		}
		else {
			sectionNoImprovementCount.put(key, 1);
		}
	}
	
	private static int getNoImprovementCount(Junction junction) {
		String key = junction.getID();
		if(junctionNoImprovementCount.containsKey(key)) {
			return junctionNoImprovementCount.get(key);
		}
		return 0;
	}
	
	private static int getNoImprovementCount(SimpleFeature section) {
		FeatureId key = section.getIdentifier();
		if(sectionNoImprovementCount.containsKey(key)) {
			return sectionNoImprovementCount.get(key);
		}
		return 0;
	}
	
}
