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
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.operation.linemerge.LineMerger;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;

import ca.bc.gov.catchment.tin.Edge;
import ca.bc.gov.catchment.tin.TinPolys;
import ca.bc.gov.catchment.tin.Triangle;
import ca.bc.gov.catchment.utils.SpatialUtils;

public class IdentifyAdjacentSlopesAlg {

	private TinPolys tinPolys;
	
	public IdentifyAdjacentSlopesAlg(TinPolys tinPolys) {
		this.tinPolys = tinPolys;
	}
	
	/**
	 * computes the slope of the triangle on either side of each edge.  the output contains the same 
	 * geometries from the input, but adds two new attributes: slope1 and slope 2. negative values mean 
	 * downward, positive numbers mean upward.
	 * @param tinEdges
	 * @param outTableName
	 * @return
	 */
	public SimpleFeatureCollection process(SimpleFeatureCollection tinEdges, String outTableName) throws IOException {
		return process(tinEdges, outTableName, Double.NaN);
	}
	
	/**
	 * computes the slope of the triangle on either side of each edge.  the output contains the same 
	 * geometries from the input, but adds two new attributes: slope1 and slope 2. negative values mean 
	 * downward, positive numbers mean upward.
	 * @param tinEdges
	 * @param outTableName
	 * @param maxSlopeToKeep (optional).  if NaN, the output set will include all edges from the input set.  
	 * if a value is specified, only output edges in which both slopes are less than the given value.  
	 * @return
	 * @throws IOException
	 */
	public SimpleFeatureCollection process(SimpleFeatureCollection tinEdges, String outTableName, double maxSlopeToKeep) throws IOException {
		SimpleFeatureType inFeatureType = tinEdges.getSchema();
		
		DefaultFeatureCollection outFeatureCollection = new DefaultFeatureCollection();
		
		//add new attributes "slope1" and "slope2" to the featureType (if they
		//don't already exist)
		SimpleFeatureType outFeatureType = inFeatureType;
		if (outFeatureType.getDescriptor("slope1") == null) {
			try {
				outFeatureType = SpatialUtils.extendFeatureType(outFeatureType, "slope1:float", outTableName);
			} catch (SchemaException e) {
				throw new IOException("Internal error.  Unable to create output feature type");
			}
		}
		if (outFeatureType.getDescriptor("slope2") == null) {
			try {
				outFeatureType = SpatialUtils.extendFeatureType(outFeatureType, "slope2:float", outTableName);
			} catch (SchemaException e) {
				throw new IOException("Internal error.  Unable to create output feature type");
			}
		}
		
		int numFeatures = tinEdges.size();
		int index = 0;
		SimpleFeatureIterator tinEdgeIt = tinEdges.features();
		while(tinEdgeIt.hasNext()) {
			index++;
			SimpleFeature inFeature = tinEdgeIt.next();
			LineString segment = (LineString)inFeature.getDefaultGeometry();			
			double[] adjacentSlopes = process(segment);
			
			SimpleFeature outFeature = SimpleFeatureBuilder.retype(inFeature, outFeatureType);
			outFeature.setAttribute("slope1", adjacentSlopes[0]);
			outFeature.setAttribute("slope2", adjacentSlopes[1]);
			double maxSlope = Math.max(adjacentSlopes[0], adjacentSlopes[1]);
			if (Double.isNaN(maxSlopeToKeep) || maxSlope < maxSlopeToKeep) {
				outFeatureCollection.add(outFeature);
			}
			
			if (index % 50000 == 0) {
				System.out.println(index + "/" + numFeatures + " processed");
				//break;
			}
			
		}
		tinEdgeIt.close();
		
		return outFeatureCollection;	
	}
	
	/**
	 * Returns the slopes of the two aajacent triangles that "hinge" on the given
	 * tinEdge segment.  Values are negative for slopes that are downward from the tinEdge, 
	 * and are positive for slopes that are updward from the tinEdge. 
	 * @param segment
	 * @return
	 * @throws IOException
	 */
	public double[] process(LineString segment) throws IOException {
		
		double[] result = {Double.NaN, Double.NaN};
		
		if (segment.getNumPoints() != 2) {
			throw new IllegalArgumentException("segment must have exactly two points");
		}
		
		Edge baseEdge = new Edge(segment.getCoordinateN(0), segment.getCoordinateN(1));
		
		List<Triangle> adjacentTriangles = tinPolys.getTrianglesOnEdge(segment);
		if (adjacentTriangles.size() != 2) {
			return result;
		}
		
		Triangle t1 = adjacentTriangles.get(0);
		Triangle t2 = adjacentTriangles.get(1);
		
		//get slopes of the triangles relative to the shared edge.
		//values are positive if downward, negative if upward
		if (t1.is3D()) {
			result[0] = t1.getSlopeRelativeToBaseEdge(baseEdge);
		}
		if (t2.is3D()) {
			result[1] = t2.getSlopeRelativeToBaseEdge(baseEdge);
		}
		
		return result;
	}
}
