package ca.bc.gov.catchment.algorithms;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.geotools.data.DataUtilities;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.feature.DefaultFeatureCollection;
import org.geotools.feature.SchemaException;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.referencing.CRS;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.operation.linemerge.LineMerger;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.referencing.FactoryException;

import ca.bc.gov.catchment.tin.Edge;
import ca.bc.gov.catchment.tin.TinPolys;
import ca.bc.gov.catchment.tin.Triangle;
import ca.bc.gov.catchment.utils.SpatialUtils;


/**
 * a simple algorithm to convert ridge sticks (linestrings) to clumps (polygons) 
 * representing areas of nearby sticks.  the algorithm works as follows:
 * - applies a buffer (of the specified distance) around each ridge stick.  buffered
 *  ridge sticks are then merged (into clumps) if they touch.  the clumps are kept if 
 *  a given size criteria is met.  Returns a feature collection of 
 *  * @author Brock
 *
 */
public class BufferAlg {
	
	
	/**
	 * applies a buffer to each geometry in the given collection.  returns a new feature collection of the buffered geometries
	 */
	public SimpleFeatureCollection bufferAllByFixedDistance(SimpleFeatureCollection ridgeSticksFc, double bufferDistance, String outTable) throws IOException {
		Collection<Geometry> bufferedGeoms = new ArrayList<Geometry>();
		SimpleFeatureIterator it = ridgeSticksFc.features();
		while(it.hasNext()) {
			SimpleFeature f = it.next();
			Geometry geom = (Geometry)f.getDefaultGeometry();
			Polygon bufferedGeom = (Polygon)geom.buffer(bufferDistance); 
			bufferedGeoms.add(bufferedGeom);
		}
		it.close();

		//get the SRS of the input data (so we can use it to create an output feature type
		SimpleFeatureType inFeatureType = ridgeSticksFc.getSchema();
		int srid = -1;
		try {
			srid = CRS.lookupEpsgCode(inFeatureType.getCoordinateReferenceSystem(), true);
		} catch (FactoryException e1) {
			System.out.println("Unable to lookup SRID for feature type "+inFeatureType.getTypeName());
			throw new IllegalStateException("unable to determine SRID from input feature collection");
		}
		
		//create the output feature type
		SimpleFeatureType outFeatureType = null;
		try {
			outFeatureType = DataUtilities.createType(outTable, "geometry:Polygon:srid="+srid);
		} catch (SchemaException e) {
			throw new IllegalStateException("unable to create feature type for output");
		}
		
		SimpleFeatureCollection result = SpatialUtils.geomCollectionToSimpleFeatureCollection(bufferedGeoms, outFeatureType);		
		return result;
	}
	
	
	/**
	 * applies a buffer to each geometry in the given collection.  returns a new feature collection of the buffered geometries
	 */
	public SimpleFeatureCollection bufferAllByVariableDistance(SimpleFeatureCollection ridgeSticksFc, double minBufferDist, double maxBufferDist, String outTable) throws IOException {
		Collection<Geometry> bufferedGeoms = new ArrayList<Geometry>();
		SimpleFeatureIterator it = ridgeSticksFc.features();
		while(it.hasNext()) {
			SimpleFeature f = it.next();
			double bufferDistance = calcVariableBufferDistance(f, minBufferDist, maxBufferDist);
			Geometry geom = (Geometry)f.getDefaultGeometry();
			Polygon bufferedGeom = (Polygon)geom.buffer(bufferDistance); 
			bufferedGeoms.add(bufferedGeom);
		}
		it.close();

		//get the SRS of the input data (so we can use it to create an output feature type
		SimpleFeatureType inFeatureType = ridgeSticksFc.getSchema();
		int srid = -1;
		try {
			srid = CRS.lookupEpsgCode(inFeatureType.getCoordinateReferenceSystem(), true);
		} catch (FactoryException e1) {
			System.out.println("Unable to lookup SRID for feature type "+inFeatureType.getTypeName());
			throw new IllegalStateException("unable to determine SRID from input feature collection");
		}
		
		//create the output feature type
		SimpleFeatureType outFeatureType = null;
		try {
			outFeatureType = DataUtilities.createType(outTable, "geometry:Polygon:srid="+srid);
		} catch (SchemaException e) {
			throw new IllegalStateException("unable to create feature type for output");
		}
		
		SimpleFeatureCollection result = SpatialUtils.geomCollectionToSimpleFeatureCollection(bufferedGeoms, outFeatureType);		
		return result;
	}
	
	private double calcVariableBufferDistance(SimpleFeature f, double minBufferDist, double maxBufferDist) {
		double slope1 = (Double)f.getAttribute("slope1");
		double slope2 = (Double)f.getAttribute("slope2");
		double maxSlopeOfRidgeStick = Math.max(slope1, slope2);
		
		//the maximum buffer will occur when 'maxSlopeOfRidgeStick' == 'minAllowedSlope'
		//the minimum buffer will occur when 'maxSlopeOfRidgeStick' == 'maxAllowedSlope'.  
		//an in-between buffer will occur when 'maxSlopeOfRidgeStick' is between 'minAllowedSlope' and 'maxAllowedSlope'
		double minAllowedSlope = -10;
		double maxAllowedSlope = -0.5;
		
		double fraction =  (maxSlopeOfRidgeStick - maxAllowedSlope) / (minAllowedSlope - maxAllowedSlope);
		if (fraction > 1) {
			fraction = 1;
		}
		if (fraction < 0) {
			fraction = 0;
		}
		double bufferDist = fraction * (maxBufferDist - minBufferDist) + minBufferDist;
		
		if (bufferDist > maxBufferDist || bufferDist < minBufferDist) {
			throw new IllegalStateException("post-condition failed.  buffer dist expected to be in range "+minBufferDist+" - "+maxBufferDist+". Found "+ bufferDist);
		}
		return bufferDist;
	}
	
}
