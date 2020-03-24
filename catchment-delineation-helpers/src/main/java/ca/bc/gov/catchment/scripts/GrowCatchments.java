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
import ca.bc.gov.catchment.algorithms.RidgeCleaner;
import ca.bc.gov.catchment.algorithms.RidgeGrower;
import ca.bc.gov.catchment.algorithms.DeadEndPreventerRidgeGrower;
import ca.bc.gov.catchment.algorithms.HybridRidgeGrower;
import ca.bc.gov.catchment.algorithms.LookAheadRidgeGrower;
import ca.bc.gov.catchment.algorithms.MedialAxisRidgeGrower;
import ca.bc.gov.catchment.algorithms.HybridRidgeGrowerOld;
import ca.bc.gov.catchment.fitness.AvgElevationLengthPenaltySectionFitness;
import ca.bc.gov.catchment.fitness.AvgElevationSectionFitness;
import ca.bc.gov.catchment.fitness.ElevationJunctionFitness;
import ca.bc.gov.catchment.fitness.ElevationSectionFitness;
import ca.bc.gov.catchment.fitness.ElevationLenghPenaltySectionFitness;
import ca.bc.gov.catchment.fitness.JunctionFitness;
import ca.bc.gov.catchment.fitness.PartialSumTouchingJunctionFitness;
import ca.bc.gov.catchment.fitness.RidgeColorSectionFitness;
import ca.bc.gov.catchment.fitness.SectionFitness;
import ca.bc.gov.catchment.fitness.RidgeSectionFitness;
import ca.bc.gov.catchment.fitness.SondheimSectionFitness;
import ca.bc.gov.catchment.fitness.SumTouchingJunctionFitness;
import ca.bc.gov.catchment.improvement.BestInRadiusJunctionImprover;
import ca.bc.gov.catchment.improvement.BestOfNSetImprover;
import ca.bc.gov.catchment.improvement.EvolutionSetImprover;
import ca.bc.gov.catchment.improvement.ImprovementCoverage;
import ca.bc.gov.catchment.improvement.ImprovementMetrics;
import ca.bc.gov.catchment.improvement.Junction;
import ca.bc.gov.catchment.improvement.JunctionImprover;
import ca.bc.gov.catchment.improvement.JunctionModification;
import ca.bc.gov.catchment.improvement.SectionImprover;
import ca.bc.gov.catchment.improvement.SectionModification;
import ca.bc.gov.catchment.improvement.SetImprover;
import ca.bc.gov.catchment.improvement.SimulatedAnnealingJunctionImprover;
import ca.bc.gov.catchment.improvement.SimulatedAnnealingSectionImprover;
import ca.bc.gov.catchment.tin.TinEdges;
import ca.bc.gov.catchment.tin.TinPolys;
import ca.bc.gov.catchment.utils.SaveUtils;
import ca.bc.gov.catchment.utils.SpatialUtils;
import ca.bc.gov.catchment.water.Water;

/**
 * Creates catchment boundaries by "growing" ridges from each confluence, following 
 * the patch suggested by a fitness function 
 * @author Brock Anderson
 *
 */

public class GrowCatchments {

	private static final String GEOPKG_ID = "geopkg";

	private static final String JUNCTIONS_TABLE = "junctions";
	private static TinEdges tinEdges;
	private static Water water;
	private static SimpleFeatureSource waterFeatureSource;
	private static int srid = -1;
	
	public static void main(String[] args) {
		
		// create Options object
		Options options = new Options();
		options.addOption("waterFile", true, "Input GeoPackage file with water features");
		options.addOption("tinEdgesFile", true, "Input GeoPackage file with TIN");
		options.addOption("o", true, "Output GeoPackage file");
		options.addOption("waterTable", true, "water table name");
		options.addOption("tinEdgesTable", true, "TIN edges table name");
		options.addOption("outTable", true, "output table name");
		options.addOption("bbox", true, "bbox (minx,miny,maxx,maxy)");
		options.addOption("bboxcrs", true, "e.g. EPSG:3005");
		CommandLineParser parser = new DefaultParser();
		HelpFormatter formatter = new HelpFormatter();
		
		String inWaterFilename = null;
		String inTinEdgesFilename = null;
		String outputFilename = null;
		String waterTable = null;
		String tinEdgesTable = null;
		String outTable = null;
		String bboxStr = null;
		String bboxCrs = null;
		int bboxSrid = -1;
		ReferencedEnvelope boundsToProcess = null;
		
		try {
			CommandLine cmd = parser.parse( options, args);
			inWaterFilename = cmd.getOptionValue("waterFile");
			inTinEdgesFilename = cmd.getOptionValue("tinEdgesFile");
			outputFilename = cmd.getOptionValue("o");		
			waterTable = cmd.getOptionValue("waterTable");
			tinEdgesTable = cmd.getOptionValue("tinEdgesTable");
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
		System.out.println("- water input file: "+inWaterFilename);
		System.out.println("- water table: "+waterTable);
		System.out.println("- TIN edges input file: "+inTinEdgesFilename);
		System.out.println("- TIN edges table: "+tinEdgesTable);
		System.out.println("- out file: "+outputFilename);
		System.out.println("- out table: "+outTable);
		if (bboxStr != null) {
			System.out.println("- bbox: "+bboxStr+" ("+bboxCrs+")");	
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
	
		SimpleFeatureType waterFeatureType = waterFeatureSource.getSchema();
		
		CoordinateReferenceSystem crs = waterFeatureType.getCoordinateReferenceSystem();
		try {
			srid = CRS.lookupEpsgCode(crs, true);
		} catch (FactoryException e1) {
			System.out.println("Unable to lookup SRID for water features");
			System.exit(1);
		}
		
		String waterGeometryPropertyName = waterFeatureType.getGeometryDescriptor().getLocalName();
		
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
						filterFactory.property(waterGeometryPropertyName), 
						boundsToProcess);								
			}	
			
			SpatialIndexFeatureCollection waterFeatureCollection = new SpatialIndexFeatureCollection(waterFeatureSource.getFeatures(bboxFilter));
			SpatialIndexFeatureSource fastWaterFeatureSource = new SpatialIndexFeatureSource(waterFeatureCollection);

			water = new Water(fastWaterFeatureSource);
			tinEdges = new TinEdges(tinEdgesFeatureSource, bufferedBboxFilter);			
			
			//grow ridges.
			//RidgeGrower ridgeGrower = new MedialAxisRidgeGrower(water, tinEdges);
			RidgeGrower ridgeGrower = new HybridRidgeGrower(water, tinEdges);
			SimpleFeatureCollection ridges = ridgeGrower.growRidges();
			
			System.out.println("done. grew "+ridges.size()+" ridges.");
			ridges = SpatialUtils.renameFeatureType(ridges, outTable);
			
		
			//identify junctions
			SpatialIndexFeatureCollection fc = new SpatialIndexFeatureCollection(ridges);
			SpatialIndexFeatureSource fs = new SpatialIndexFeatureSource(fc);
			CatchmentLines catchmentLines = new CatchmentLines(fs);
			List<Coordinate> junctionCoords = catchmentLines.getJunctions(water);			
			SimpleFeatureType junctionFeatureType = DataUtilities.createType(JUNCTIONS_TABLE, "geometry:Point:srid="+srid);
			SimpleFeatureCollection junctions = SpatialUtils.coordListToSimpleFeatureCollection(junctionCoords, junctionFeatureType);
			
			//save catchment lines			
			System.out.println("saving...");
			SaveUtils.saveToGeoPackage(outputFilename, ridges, true);
			SaveUtils.saveToGeoPackage(outputFilename, junctions, true);
			
		}
		catch(IOException e) {
			e.printStackTrace();
			System.exit(1);
		}
		catch(SchemaException e) {
			e.printStackTrace();
			System.exit(1);
		}
		
		Date end = new Date();
		System.out.println("Done.  Run time: "+(end.getTime() - start.getTime())/1000+"s");
		
	}
	
	
}
