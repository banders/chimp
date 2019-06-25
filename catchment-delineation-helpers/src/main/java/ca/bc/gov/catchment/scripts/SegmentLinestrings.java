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
 * Test bboxes: 
 * 		21 features:  -115.79381,49.21187,-115.75347,49.24806
 * 		1 feature:    -115.80751,49.23394,-115.79588,49.24468
 * 
 * 	
 * 
 * 
 */
public class SegmentLinestrings {
	
	private static final String GEOPKG_ID = "geopkg";
	
	public static void main(String[] args) {
		
		// create Options object
		Options options = new Options();
		options.addOption("i", true, "Input GeoPackage file");
		options.addOption("o", true, "Output GeoPackage file");
		options.addOption("bbox", true, "Bounding box representing area to process (format: 'xmin,ymin,xmax,ymax')");
		options.addOption("bboxcrs", true, "CRS of the bounding box.  e.g. 'EPSG:3005' or 'EPSG:4326'");
		options.addOption("tables", true, "Name of tables in input file to process");
		CommandLineParser parser = new DefaultParser();
		HelpFormatter formatter = new HelpFormatter();
		
		String inputGeoPackageFilename = null;
		String outputGeoPackageFilename = null;
		String bboxStr = null;
		String bboxCrs = null;
		int bboxSrid = -1;
		String tables[] = null;
		String outTableNameUnsegmented = "water_features";
		String outTableNameSegmented = "water_features_segmented";
		
		try {
			CommandLine cmd = parser.parse( options, args);
			inputGeoPackageFilename = cmd.getOptionValue("i");
			outputGeoPackageFilename = cmd.getOptionValue("o");
			bboxStr = cmd.getOptionValue("bbox");
			bboxCrs = cmd.getOptionValue("bboxcrs");
			String tablesCsv = cmd.getOptionValue("tables");
			tables = tablesCsv.split(",");
		} catch (ParseException e2) {
			formatter.printHelp( SegmentLinestrings.class.getSimpleName(), options );
		}

		//validate inputs
		if (inputGeoPackageFilename == null) {
			formatter.printHelp( SegmentLinestrings.class.getSimpleName(), options );
			System.exit(1);
		}
		if (bboxStr == null) {
			formatter.printHelp( SegmentLinestrings.class.getSimpleName(), options );
			System.exit(1);
		}
		if (bboxCrs == null) {
			formatter.printHelp( SegmentLinestrings.class.getSimpleName(), options );
			System.exit(1);
		}
		
		System.out.println("Inputs:");
		System.out.println("- in file: "+inputGeoPackageFilename);
		System.out.println("- in tables: "+tables);
		if (outputGeoPackageFilename != null) {
			System.out.println("- out geopackage file: "+outputGeoPackageFilename);
		}
		System.out.println("- bbox: "+bboxStr);
		System.out.println("- bbox srs: "+bboxCrs);
		
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
		
		
		Map<String, String> inputDatastoreParams = new HashMap<String, String>();
		inputDatastoreParams.put("dbtype", GEOPKG_ID);
		inputDatastoreParams.put("database", inputGeoPackageFilename);
		
		
		//setup feature type for output geopackage
		//---------------------------------------------------------------------
		
		SimpleFeatureType unsegmentedFeatureType = null;
		try {
			String attrs = "geometry:LineString:srid="+bboxSrid;
			unsegmentedFeatureType = DataUtilities.createType(outTableNameUnsegmented, attrs);
		} catch (SchemaException e1) {
			System.out.println("Unable to create feature type "+outTableNameUnsegmented);
			System.exit(1);
		}
		
		SimpleFeatureType segmentedFeatureType = null;
		try {
			String attrs = "geometry:LineString:srid="+bboxSrid;
			segmentedFeatureType = DataUtilities.createType(outTableNameSegmented, attrs);
		} catch (SchemaException e1) {
			System.out.println("Unable to create feature type "+outTableNameSegmented);
			System.exit(1);
		}
		
		DefaultFeatureCollection unsegmentedFeatures = new DefaultFeatureCollection(outTableNameUnsegmented, unsegmentedFeatureType);
		DefaultFeatureCollection segmentedFeatures = new DefaultFeatureCollection(outTableNameSegmented, segmentedFeatureType);
		
		//Setup inputs
		//---------------------------------------------------------------------
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
			
		System.out.println("Scanning input tables");
		for (int i = 0; i < tables.length; i++) {
			String table = tables[i];
			System.out.println(" - "+table);			
			FeatureSource inFeatureSource = null;
			ReferencedEnvelope inBounds = null;
			try {
				inFeatureSource = inDatastore.getFeatureSource(table);
				inBounds = inFeatureSource.getBounds();
			} catch (IOException e1) {
				System.out.println("Unable to read "+table+" features");
				e1.printStackTrace();
				System.exit(1);
			}
			
			int streamDataEpsgCode;
			try {
				streamDataEpsgCode = CRS.lookupEpsgCode(inBounds.getCoordinateReferenceSystem(), true);
				System.out.println("   - Data CRS: EPSG:"+streamDataEpsgCode);
				System.out.println("   - Data bounds");
				System.out.println("       EPSG:"+streamDataEpsgCode+": ["+inBounds.getMinX()+","+inBounds.getMinY()+","+inBounds.getMaxX()+","+inBounds.getMaxY()+"]");
				if (streamDataEpsgCode != 4326) {
					ReferencedEnvelope streamDataBounds4326 = reproject(inBounds, "EPSG:4326");
					System.out.println("       ESGP:4326: ["+streamDataBounds4326.getMinX()+","+streamDataBounds4326.getMinY()+","+streamDataBounds4326.getMaxX()+","+streamDataBounds4326.getMaxY()+"]");
				}
			} catch (FactoryException e1) {
				e1.printStackTrace();
				System.exit(1);
			}
			
			
			ReferencedEnvelope bboxInDataCrs = null;
			try {
				bboxInDataCrs = parseBbox(bboxStr, bboxCrs, inBounds.getCoordinateReferenceSystem());
			} catch (Exception e) {
				e.printStackTrace();
				System.out.println(e);
				System.exit(1);
			}
			
			SimpleFeatureBuilder unsegmentedFeatureBuilder = new SimpleFeatureBuilder(unsegmentedFeatureType);
			GeometryFactory geometryFactory = JTSFactoryFinder.getGeometryFactory();
			Geometry boundingPolygon = geometryFactory.createPolygon(new Coordinate[] {
					new Coordinate(bboxInDataCrs.getMinX(), bboxInDataCrs.getMinY()),
					new Coordinate(bboxInDataCrs.getMaxX(), bboxInDataCrs.getMinY()),
					new Coordinate(bboxInDataCrs.getMaxX(), bboxInDataCrs.getMaxY()),
					new Coordinate(bboxInDataCrs.getMinX(), bboxInDataCrs.getMaxY()),
					new Coordinate(bboxInDataCrs.getMinX(), bboxInDataCrs.getMinY()),
					});
			
			try {
				FeatureCollection inFeatureCollection = filterFeatures(inFeatureSource, boundingPolygon);
				FeatureIterator streamIterator = inFeatureCollection.features();			
				System.out.println("   - "+inFeatureCollection.size() + " features in "+table);
				while (streamIterator.hasNext()) {            	
	            	//get the input feature
	            	SimpleFeature inFeature = (SimpleFeature)streamIterator.next();            	
	            	List<SimpleFeature> segmentFeatureList = splitIntoSegments(inFeature, segmentedFeatureType);
	        		Object[] attrs = {inFeature.getDefaultGeometry()};
	        		SimpleFeature featureCopy = unsegmentedFeatureBuilder.buildFeature(inFeature.getID(), attrs);
	        		unsegmentedFeatures.add(featureCopy);
	        		segmentedFeatures.addAll(segmentFeatureList);
	            }
				streamIterator.close();
			} catch (IOException e) {
				e.printStackTrace();
				System.exit(1);
			}
		}
		

		//Save output feature collection to file
		//---------------------------------------------------------------------
		
		try {
			         
			//save geopackage
			if (outputGeoPackageFilename != null) {
				System.out.println("Saving GeoPackage: "+outputGeoPackageFilename);
				System.out.println(" - "+outTableNameUnsegmented + ": "+unsegmentedFeatures.size() + " features");
				SaveUtils.saveToGeoPackage(outputGeoPackageFilename, unsegmentedFeatures);
				System.out.println(" - "+outTableNameSegmented + ": "+segmentedFeatures.size() + " features");
				SaveUtils.saveToGeoPackage(outputGeoPackageFilename, segmentedFeatures);				
			}
			
			//cleanup
			inDatastore.dispose();
			
		} catch (IOException e) {
			e.printStackTrace();
			System.exit(1);
		}
		
		
		System.out.print("All Done");
	}
	
	/*
	 * splits a LineString feature into its segments, one feature for each.  Note: the attributes from the 
	 * original feature aren't copied into the new features.
	 */
	private static List<SimpleFeature> splitIntoSegments(SimpleFeature inFeature, SimpleFeatureType outFeatureType) {
		List<SimpleFeature> result = new ArrayList<SimpleFeature>();
		GeometryFactory geometryFactory = JTSFactoryFinder.getGeometryFactory();
		SimpleFeatureBuilder featureBuilder = new SimpleFeatureBuilder(outFeatureType);
		
		Geometry inGeometry = (Geometry)inFeature.getDefaultGeometry();
		Coordinate[] coordinates = inGeometry.getCoordinates();
		Coordinate prevCoord = null;
		int index = 0;
		for(Coordinate coord : coordinates) {
			index++;
			if (prevCoord != null) {
				//create new geometry
				Coordinate[] segmentCoords = {prevCoord, coord};
				Geometry segmentGeometry = geometryFactory.createLineString(segmentCoords);
				
				//create new feature
				String newId = inFeature.getID()+"-"+index;
				Object[] attributeValues = new Object[] { segmentGeometry };
				SimpleFeature segmentFeature = featureBuilder.buildFeature(newId, attributeValues);
				
				//add feature to collection
				result.add(segmentFeature);
			}
			prevCoord = coord;
		}
		return result;
	}
	
	/**
	 * Gets a feature collection with the following filters applied:
	 *  - GEOMETRY "within" the given bounding polygon, and
	 *  - EDGE_TYPE equal to any value from edgeTypeWhitelist
	 */
	private static FeatureCollection filterFeatures(FeatureSource featureSource, Geometry boundingPolygon) throws IOException {
		
		FilterFactory2 filterFactory = CommonFactoryFinder.getFilterFactory2();
		FeatureType schema = featureSource.getSchema();
		String streamGeometryPropertyName = schema.getGeometryDescriptor().getLocalName();			
		//'within' only includes geometries that are fully inside the given bounds.  
		//use 'bbox' if also needing features that cross the bounds.
		//Filter bboxFilter = filterFactory.bbox(filterFactory.property(streamGeometryPropertyName), bboxInDataCrs);
		Filter areaFilter = filterFactory.within(filterFactory.property(streamGeometryPropertyName), filterFactory.literal(boundingPolygon));
		
		
		FeatureCollection streams = featureSource.getFeatures(areaFilter);
		return streams;
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
	
	private static ReferencedEnvelope parseBbox(String bboxStr, String crsInStr, CoordinateReferenceSystem crsOut) {
		double xmin;
		double ymin;
		double xmax;
		double ymax;
		CoordinateReferenceSystem crsIn = null;
		try {
			crsIn = CRS.decode(crsInStr);
		} catch (NoSuchAuthorityCodeException e) {
			throw new IllegalArgumentException("Unable to lookup CRS");
		} catch (FactoryException e) {
			throw new IllegalStateException("Unable to lookup CRS.  An internal error occurred.");
		}
			
		String[] pieces = bboxStr.split(",");
		if (pieces.length != 4) {
			throw new IllegalArgumentException("Unable to parse bbox");
		}
		try {
			xmin = Double.parseDouble(pieces[0]);
			ymin = Double.parseDouble(pieces[1]);
			xmax = Double.parseDouble(pieces[2]);
		    ymax = Double.parseDouble(pieces[3]);
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException("Unable to parse bbox");
		}
		ReferencedEnvelope envelopeInCrs = new ReferencedEnvelope(xmin, xmax, ymin, ymax, crsIn);
		ReferencedEnvelope envelopeOutCrs;
		try {
			envelopeOutCrs = envelopeInCrs.transform(crsOut, false);
		} catch (TransformException e) {
			throw new IllegalStateException("Unable to reproject bbox.");
		} catch (FactoryException e) {
			throw new IllegalStateException("Unable to reproject bbox.  An internal error occurred.");
		}
		return envelopeOutCrs;
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
