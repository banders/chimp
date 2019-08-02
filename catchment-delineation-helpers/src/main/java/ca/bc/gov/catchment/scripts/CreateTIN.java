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

/**
 * Creates a delaunay TIN from a given point cloud, optionally 
 * constrained by a set of break lines.
 * @author Brock Anderson
 *
 */

public class CreateTIN {

	private static final String GEOPKG_ID = "geopkg";

	public static void main(String[] args) {
		
		// create Options object
		Options options = new Options();
		options.addOption("pointCloudFile", true, "Input GeoPackage file with point cloud");
		options.addOption("breakLinesFile", true, "Input GeoPackage file with break lines");
		options.addOption("o", true, "Output GeoPackage file");
		options.addOption("pointCloudTable", true, "point cloud table name");
		options.addOption("breakLinesTable", true, "breaklines table name");
		options.addOption("outTable", true, "output table name");
		options.addOption("bbox", true, "bbox (minx,miny,maxx,maxy)");
		options.addOption("bboxcrs", true, "e.g. EPSG:3005");
		CommandLineParser parser = new DefaultParser();
		HelpFormatter formatter = new HelpFormatter();
		
		String inPointCloudFilename = null;
		String inBreakLinesFilename = null;
		String outputGeopackageFilename = null;
		String pointCloudTable = null;
		String breakLineTable = null;
		String outTable = null;
		String bboxStr = null;
		String bboxCrs = null;
		int bboxSrid = -1;
		ReferencedEnvelope boundsToProcess = null;
		
		try {
			CommandLine cmd = parser.parse( options, args);
			inPointCloudFilename = cmd.getOptionValue("pointCloudFile");
			inBreakLinesFilename = cmd.getOptionValue("breakLinesFile");
			outputGeopackageFilename = cmd.getOptionValue("o");	
			pointCloudTable = cmd.getOptionValue("pointCloudTable");
			breakLineTable = cmd.getOptionValue("breakLinesTable");
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
		System.out.println("- point cloud input file: "+inPointCloudFilename);
		System.out.println("- point cloud table: "+pointCloudTable);
		System.out.println("- breakline input file: "+inBreakLinesFilename);
		System.out.println("- breakline table: "+breakLineTable);
		System.out.println("- out file: "+outputGeopackageFilename);
		System.out.println("- out table: "+outTable);
		if (bboxStr != null) {
			System.out.println("- bbox: "+bboxStr+" ("+bboxCrs+")");	
		}
				
		//Open point cloud input file
		//---------------------------------------------------------------------
		
		Map<String, String> pointCloudInputDatastoreParams = new HashMap<String, String>();
		pointCloudInputDatastoreParams.put("dbtype", GEOPKG_ID);
		pointCloudInputDatastoreParams.put("database", inPointCloudFilename);
		
		DataStore pointCloudDatastore = null;
		try {
			pointCloudDatastore = DataStoreFinder.getDataStore(pointCloudInputDatastoreParams);
		} catch (IOException e) {
			System.out.println("Unable to open input file: "+inPointCloudFilename);
			e.printStackTrace();
			System.exit(1);
		}
		
		if (pointCloudDatastore == null) {
			System.out.println("Unable to open input datastore");
			System.exit(1);
		}
			
		//Open breakline input file
		//---------------------------------------------------------------------
		
		DataStore breakLineDatastore = null;
		if (inBreakLinesFilename != null) {
			System.out.println("Breakline constraints will be applied");
			Map<String, String> breakLineInputDatastoreParams = new HashMap<String, String>();
			breakLineInputDatastoreParams.put("dbtype", GEOPKG_ID);
			breakLineInputDatastoreParams.put("database", inBreakLinesFilename);
			
			try {
				breakLineDatastore = DataStoreFinder.getDataStore(breakLineInputDatastoreParams);
			} catch (IOException e) {
				System.out.println("Unable to open input file: "+inBreakLinesFilename);
				e.printStackTrace();
				System.exit(1);
			}
			
			if (breakLineDatastore == null) {
				System.out.println("Unable to open input datastore");
				System.exit(1);
			}
		}
		else {
			System.out.println("No breakline constraints are provided.");
		}
		
		// Collect some information about the point cloud data
		//---------------------------------------------------------------------
		
		SimpleFeatureType pointCloudFeatureType = null;
		try {
			pointCloudFeatureType = pointCloudDatastore.getSchema(pointCloudTable);			
		} catch (IOException e) {
			System.out.println("Unable to get schema for feature type "+pointCloudTable+" in the input datastore");
			e.printStackTrace();
			System.exit(1);
		}
		
		String pointCloudGeometryPropertyName = pointCloudFeatureType.getGeometryDescriptor().getLocalName();
		
		CoordinateReferenceSystem crs = pointCloudFeatureType.getCoordinateReferenceSystem();
		int pointCloudSrid = -1;
		try {
			pointCloudSrid = CRS.lookupEpsgCode(crs, true);
		} catch (FactoryException e1) {
			System.out.println("Unable to lookup SRID for feature type "+pointCloudTable);
			System.exit(1);
		}
		
		SimpleFeatureSource pointCloudFeatureSource = null;
		try {
			pointCloudFeatureSource = pointCloudDatastore.getFeatureSource(pointCloudTable);
		} catch (IOException e1) {
			System.out.println("Unable to get in feature source: "+pointCloudTable);
			e1.printStackTrace();
			System.exit(1);
		}
			
		// Collect some information about the breakline data
		//---------------------------------------------------------------------
		
		SimpleFeatureType breakLineFeatureType = null;
		String breakLineGeometryPropertyName = null;
		SimpleFeatureSource breakLineFeatureSource = null;
		if (breakLineDatastore != null) {
			
			try {
				breakLineFeatureType = breakLineDatastore.getSchema(breakLineTable);			
			} catch (IOException e) {
				System.out.println("Unable to get schema for feature type "+breakLineTable+" in the input datastore");
				e.printStackTrace();
				System.exit(1);
			}
			
			breakLineGeometryPropertyName = breakLineFeatureType.getGeometryDescriptor().getLocalName();
			
			CoordinateReferenceSystem breakLineCrs = breakLineFeatureType.getCoordinateReferenceSystem();
			int breakLineSrid = -1;
			try {
				breakLineSrid = CRS.lookupEpsgCode(breakLineCrs, true);
			} catch (FactoryException e1) {
				System.out.println("Unable to lookup SRID for feature type "+breakLineTable);
				System.exit(1);
			}
			
			try {
				breakLineFeatureSource = breakLineDatastore.getFeatureSource(breakLineTable);
			} catch (IOException e1) {
				System.out.println("Unable to get in feature source: "+breakLineTable);
				e1.printStackTrace();
				System.exit(1);
			}
		}
		
		//Setup the output data
		//---------------------------------------------------------------------

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
		
		SimpleFeatureType outFeatureType = null;
		try {
			outFeatureType = DataUtilities.createType(outTable, "geometry:LineString");
		} catch (SchemaException e1) {
			System.out.println("Unable to create feature type "+outTable);
			System.exit(1);
		}
		
		int outSrid = pointCloudSrid;
		
		SimpleFeatureBuilder outFeatureBuilder = new SimpleFeatureBuilder(outFeatureType);		
		DefaultFeatureCollection outFeatureCollection = new DefaultFeatureCollection(outTable, outFeatureType);
		

		//Processing
		//---------------------------------------------------------------------

		System.out.println("Loading vertices into TIN");
		IncrementalTin tin = new IncrementalTin();
		
		Hints filterHints = new Hints( Hints.FEATURE_2D, true ); // force 2D queries
		FilterFactory2 filterFactory = CommonFactoryFinder.getFilterFactory2(filterHints);
		GeometryFactory geometryFactory = JTSFactoryFinder.getGeometryFactory();
		
		try {
			
			//get a feature collection of all points in point cloud (or just those within the given bbox
			//if a bbox filter was specified)
			//-----------------------------------------------------------------			
			SimpleFeatureCollection pointCloudFeatureCollection = null;
			if (boundsToProcess != null) {
				System.out.println("Selecting point cloud features in bbox...");
				Filter bboxFilter = filterFactory.bbox(filterFactory.property(pointCloudGeometryPropertyName), boundsToProcess);
				pointCloudFeatureCollection = pointCloudFeatureSource.getFeatures(bboxFilter);
			}
			else {
				pointCloudFeatureCollection = pointCloudFeatureSource.getFeatures();
			}
			
			//add all points from point cloud to the TIN
			SimpleFeatureIterator pointCloudIt = pointCloudFeatureCollection.features();
			int numPointsInCloud = 0;
			while(pointCloudIt.hasNext()) {
				SimpleFeature pointCloudFeature = pointCloudIt.next();
				Point point = (Point)pointCloudFeature.getDefaultGeometry();
				Coordinate coord = point.getCoordinate();
				Vertex tinFourVertex = new Vertex(coord.getX(), coord.getY(), coord.getZ());
				tin.add(tinFourVertex);
				numPointsInCloud++;
			}
			System.out.println("Added "+numPointsInCloud+" points from cloud");
			pointCloudIt.close();
			
			//update the TIN to account for break line constraints
			//-----------------------------------------------------------------
			if (breakLineFeatureSource != null) {
				//get a feature collection of all breaklines  (or just those within the given bbox
				//if a bbox filter was specified)
				SimpleFeatureCollection breakLineFeatureCollection = null;
				if (boundsToProcess != null) {
					System.out.println("Selecting breakline features in bbox...");
					Filter bboxFilter = filterFactory.bbox(filterFactory.property(breakLineGeometryPropertyName), boundsToProcess);
					breakLineFeatureCollection = breakLineFeatureSource.getFeatures(bboxFilter);
				}
				else {
					breakLineFeatureCollection = breakLineFeatureSource.getFeatures();
				}
				
				List<IConstraint> constraints = new ArrayList<IConstraint>();
				SimpleFeatureIterator breakLineIt = breakLineFeatureCollection.features();
				while(breakLineIt.hasNext()) {
					SimpleFeature breakLineFeature = breakLineIt.next();
					LineString breakLineGeom = (LineString)breakLineFeature.getDefaultGeometry();
					Coordinate[] breakLineCoords = breakLineGeom.getCoordinates();
					IConstraint constraint = coordsToLinearConstraint(breakLineCoords);					
					constraints.add(constraint);
				}
				breakLineIt.close();
				
				System.out.println("Adding "+constraints.size()+" breakline constraints");
				boolean restoreDelaunayConforming = true;
				tin.addConstraints(constraints, restoreDelaunayConforming);
			}
			
			//Add all TIN edges to a feature collection
			//-----------------------------------------------------------------			
			int nextFid = 0;
			Iterator<IQuadEdge> edgeIt = tin.edges().iterator();
			int num2D = 0;
			int num3D = 0;
			while(edgeIt.hasNext()) {
				
				//get the edge's endpoints
				IQuadEdge edge = edgeIt.next();
				Vertex a = edge.getA();
				Vertex b = edge.getB();
				
				//create a linestring geometry with these two endpoints
				Coordinate c1 = new Coordinate(a.getX(), a.getY(), a.getZ());
				Coordinate c2 = new Coordinate(b.getX(), b.getY(), b.getZ());
				if (Double.isNaN(a.getZ()) || Double.isNaN(b.getZ())) {
					num2D++;
				}
				else {
					num3D++;
				}
				Coordinate[] coordArr = {c1, c2};

				LineString lineString = geometryFactory.createLineString(coordArr);
				
				Object[] attributes = {lineString};
				SimpleFeature outFeature = outFeatureBuilder.buildFeature(""+nextFid++, attributes);
				outFeatureCollection.add(outFeature);
			}
			
			System.out.println(num2D+" 2D edges. "+num3D+" 3D edges.");
			
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
		}
		
		System.out.println("All done");
		
	}
	
	private static LinearConstraint coordsToLinearConstraint(Coordinate[] coords) {
		List<Vertex> vertices = new ArrayList<Vertex>();
		for(int i = 0; i < coords.length; i++) {
			Coordinate coord = coords[i];
			Vertex tinFourVertex = coordToTinFourVertex(coord);
			vertices.add(tinFourVertex);			
		}
		LinearConstraint constraint = new LinearConstraint(vertices);
		return constraint;
	}
	
	private static Vertex coordToTinFourVertex(Coordinate coord) {
		Vertex tinfourVertex = new Vertex(coord.getX(), coord.getY(), coord.getZ());
		return tinfourVertex;
	}
	
	
}
