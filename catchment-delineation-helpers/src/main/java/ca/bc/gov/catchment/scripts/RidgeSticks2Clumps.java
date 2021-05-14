package ca.bc.gov.catchment.scripts;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;

import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.geotools.data.DataUtilities;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.factory.CommonFactoryFinder;
import org.geotools.feature.SchemaException;
import org.geotools.geometry.jts.JTSFactoryFinder;
import org.geotools.referencing.CRS;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryCollection;
import org.locationtech.jts.geom.GeometryFactory;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.filter.Filter;
import org.opengis.filter.FilterFactory2;
import org.opengis.referencing.FactoryException;

import ca.bc.gov.catchment.algorithms.BufferAlg;
import ca.bc.gov.catchment.utils.SaveUtils;
import ca.bc.gov.catchment.utils.SpatialUtils;
import ca.bc.gov.catchment.water.Water;

public class RidgeSticks2Clumps extends CLItoAlgorithmBridge {

	private static final double DEFAULT_STICK_BUFFER_DISTANCE = 10;
	private static final double DEFAULT_MIN_AREA = 0;

	
	public static void main(String[] argv) {
		CLItoAlgorithmBridge transformer = new RidgeSticks2Clumps();
		
		//add extra command-line options to specify a second data set
		Options customOptions = new Options();
		customOptions.addOption(new Option("waterFile", true, "geopackage file with water features"));
		customOptions.addOption(new Option("waterTable", true, "target table in 'waterFilename'"));
		customOptions.addOption(new Option("minArea", true, "only clumps with area greater than the given value.  area unit must be consistent with distance unit of input geometries.  default is "+DEFAULT_MIN_AREA));
		customOptions.addOption(new Option("bufferDistance", true, "distance to buffer around each stick. unit must be consistent with distance unit of input geometries.  default is "+DEFAULT_STICK_BUFFER_DISTANCE));		
		transformer.start(argv, customOptions);
		
	}

	@Override
	public SimpleFeatureCollection transformBatch(SimpleFeatureSource inFeatureSource) throws IOException {
		
		String waterFile = getOptionValue("waterFile");
		String waterTable = getOptionValue("waterTable");
		double minArea = getOptionValue("minArea") != null ? Double.parseDouble(getOptionValue("minArea")) : DEFAULT_MIN_AREA;
		double bufferDistance = getOptionValue("bufferDistance") != null ? Double.parseDouble(getOptionValue("bufferDistance")) : DEFAULT_STICK_BUFFER_DISTANCE;
		
		//buffer ridge sticks
		message("Buffering ridge sticks");
		
		boolean variableStickBufferDistance = false;
		
		SimpleFeatureCollection bufferedSticksFc = null;
		if (!variableStickBufferDistance) {
			bufferedSticksFc = bufferSticksByFixedDistance(inFeatureSource.getFeatures(), bufferDistance);
			message(" - Note: using fixed buffer distance "+bufferDistance);
		}		
		else {
			double minBufferDist = bufferDistance;
			double maxBufferDist = 2*bufferDistance;
			bufferedSticksFc = bufferSticksByVariableDistance(inFeatureSource.getFeatures(), minBufferDist, maxBufferDist);
			message(" - Note: using variable buffer distance between "+minBufferDist +" - "+maxBufferDist+", depending on ridge stick slope");
	
		}				
		
		message(" - Done.  "+bufferedSticksFc.size() +" buffered ridge sticks");


		message("Saving intermediary result (buffered sticks)...");
		try {
			SaveUtils.saveToGeoPackage(getOutFilename(), bufferedSticksFc);
		} catch (IOException e) {
			message("Unable to save intermediary result to: "+getOutFilename()+" (table '"+getOutTable()+"')");
			e.printStackTrace();
		}
		
		//filter out buffered sticks touching water
		if (waterFile != null && waterTable != null) {
			message("Filtering out buffered sticks that touch water");
			Water water = Water.fromGeopackage(waterFile, waterTable);
			SimpleFeatureCollection bufferedSticksFilteredFc = water.filterNotTouchingWater(bufferedSticksFc);
			String outTable = bufferedSticksFilteredFc.getSchema().getName() + "_filtered";
			try {
				bufferedSticksFilteredFc = SpatialUtils.renameFeatureType(bufferedSticksFilteredFc, outTable);
			} catch (SchemaException e) {
				throw new IllegalStateException("unable to change feature type");
			}

			message("Saving intermediary result (buffered sticks filtered)...");
			try {
				SaveUtils.saveToGeoPackage(getOutFilename(), bufferedSticksFilteredFc);
			} catch (IOException e) {
				message("Unable to save intermediary result to: "+getOutFilename()+" (table '"+getOutTable()+"')");
				e.printStackTrace();
			}

			message("filtering out buffered sticks that touch water reduced set size from "+bufferedSticksFc.size()+" to "+bufferedSticksFilteredFc.size());
			bufferedSticksFc = bufferedSticksFilteredFc;
		
		}	
		else {
			message("Not filtering out buffered sticks that touch water");
		}
		
		//clump
		message("Clumping buffered ridge sticks");
		SimpleFeatureCollection clumpsFc = makeClumps(bufferedSticksFc);
		message(" - Done.  "+clumpsFc.size() +" clumps");
		
		//filter out small clumps
		FilterFactory2 ff = CommonFactoryFinder.getFilterFactory2();
		String clumpsGeometryProperty = clumpsFc.getSchema().getGeometryDescriptor().getLocalName();
		Filter areaFilter = ff.greaterOrEqual(
				ff.function("Area", ff.property(clumpsGeometryProperty)), 
				ff.literal(minArea)
				);
		SimpleFeatureCollection filteredClumpsFc = clumpsFc.subCollection(areaFilter);
	    
		return filteredClumpsFc;
		
	}
	
	/**
	 * add a fixed buffer amount around the each feature in the given collection.
	 * @param ridgeSticks
	 * @param bufferDistance
	 * @return
	 */
	private SimpleFeatureCollection bufferSticksByFixedDistance(SimpleFeatureCollection ridgeSticks, double bufferDistance) {
						
		//buffer each "ridge stick" to convert into polygons.  Merge all overlapping polygons into 
		//polygon "clumps"
		
		String bufferedRidgeSticksOutTable = ridgeSticks.getSchema().getName()+"_buffered";
		BufferAlg bufferAlg = new BufferAlg();
		SimpleFeatureCollection bufferedSticksFc = null;
		try {
			bufferedSticksFc = bufferAlg.bufferAllByFixedDistance(
					ridgeSticks, 
					bufferDistance,
					bufferedRidgeSticksOutTable);
		} catch (IOException e) {
			throw new IllegalStateException("failed to apply buffer to ridge sticks");
		}
		return bufferedSticksFc;
	}
	
	/**
	 * add a variable buffer amount around each feature.  the buffer amount is calculated as
	 * a function of the shallowest of the two adjacent slopes.  sticks with steeper adjacent slopes
	 * will be given larger buffers.
	 * @param ridgeSticks
	 * @param bufferDistance
	 * @return
	 */
	private SimpleFeatureCollection bufferSticksByVariableDistance(SimpleFeatureCollection ridgeSticks, double minBufferDist, double maxBufferDist) {
						
		//buffer each "ridge stick" to convert into polygons.  Merge all overlapping polygons into 
		//polygon "clumps"
		
		String bufferedRidgeSticksOutTable = ridgeSticks.getSchema().getName()+"_buffered";
		BufferAlg bufferAlg = new BufferAlg();
		SimpleFeatureCollection bufferedSticksFc = null;
		try {
			bufferedSticksFc = bufferAlg.bufferAllByVariableDistance(
					ridgeSticks,
					minBufferDist,
					maxBufferDist,
					bufferedRidgeSticksOutTable);
		} catch (IOException e) {
			throw new IllegalStateException("failed to apply buffer to ridge sticks");
		}
		return bufferedSticksFc;
	}
	
	private SimpleFeatureCollection makeClumps(SimpleFeatureCollection bufferedSticksFc) {

		//convert the resulting buffered sticks feature collection into a standard java collection of geometries
		//because that is the format needed by the CascadedPolygonUnion algorithm below.
		Collection<Geometry> bufferedStickGeoms = SpatialUtils.simpleFeatureCollectionToGeomCollection(bufferedSticksFc);
		
		//combine touching buffered sticks into clumps
		GeometryFactory geometryFactory = JTSFactoryFinder.getGeometryFactory();		
	    GeometryCollection geometryCollection =
	          (GeometryCollection) geometryFactory.buildGeometry(bufferedStickGeoms);
	    GeometryCollection clumpsAsGeomCollection = (GeometryCollection)geometryCollection.union();
	    
	    //convert the GeometryCollection to a Collection<Geometry>
	    Collection<Geometry> clumps = new ArrayList<Geometry>();
	    for (int i = 0; i < clumpsAsGeomCollection.getNumGeometries(); i++) {
	    	Geometry geom = clumpsAsGeomCollection.getGeometryN(i);
	    	clumps.add(geom);
	    }
	    
		//get the SRS of the input data (so we can use it to create an output feature type
		SimpleFeatureType inFeatureType = bufferedSticksFc.getSchema();
		int srid = -1;
		try {
			srid = CRS.lookupEpsgCode(inFeatureType.getCoordinateReferenceSystem(), true);
		} catch (FactoryException e1) {
			message("Unable to lookup SRID for feature type "+inFeatureType.getTypeName());
			throw new IllegalStateException("unable to determine SRID from input feature collection");
		}
		
		//create the output feature type
		SimpleFeatureType outFeatureType = null;
		try {
			outFeatureType = DataUtilities.createType(getOutTable(), "geometry:Polygon:srid="+srid);
		} catch (SchemaException e) {
			throw new IllegalStateException("unable to create feature type "+getOutTable());
		}
	    
	    //convert the Collection<Geometry> to a SimpleFeatureCollection
	    SimpleFeatureCollection clumpsFc = SpatialUtils.geomCollectionToSimpleFeatureCollection(clumps, outFeatureType);
	    return clumpsFc;
	}
	
}
