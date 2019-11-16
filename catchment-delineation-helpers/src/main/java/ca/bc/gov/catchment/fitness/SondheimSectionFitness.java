package ca.bc.gov.catchment.fitness;

import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.factory.CommonFactoryFinder;
import org.geotools.factory.Hints;
import org.geotools.geometry.jts.JTSFactoryFinder;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.filter.Filter;
import org.opengis.filter.FilterFactory2;

import ca.bc.gov.catchment.tin.Edge;
import ca.bc.gov.catchment.tin.TinPolys;
import ca.bc.gov.catchment.tin.Triangle;
import ca.bc.gov.catchments.utils.SpatialUtils;

/**
 * @author Brock
 *
 */
public class SondheimSectionFitness extends SectionFitness {

	private TinPolys tinPolys;

	private double r; //maximum elevation in data set
	private double L; //maximum TIN edge length
	
	public SondheimSectionFitness(TinPolys tinPolys) throws IOException {
		this.tinPolys = tinPolys;
		r = tinPolys.getMaxElevation();
		L = tinPolys.getMaxEdgeLength();
		
	}
	
	@Override
	public double fitness(Coordinate coord1, Coordinate coord2) throws IOException {
		if (Double.isNaN(coord1.getZ()) ||Double.isNaN(coord2.getZ())) {
			throw new IllegalArgumentException("unable to determine fitness for segment without elevation.  z1: "+coord1.getZ()+", z2: "+coord2.getZ());
		}
		
		LineString segment = SpatialUtils.toLineString(coord1, coord2);
		Edge baseEdge = new Edge(coord1, coord2);
		
		double c1 = 0;
		double c2 = 1;
		
		double v = (coord1.getZ() + coord2.getZ()) / 2; //average elevation of the segment
		
		List<Triangle> adjacentTriangles = tinPolys.getTrianglesOnEdge(segment);
		if (adjacentTriangles.size() != 2) {
			return 0;
		}
		
		Triangle t1 = adjacentTriangles.get(0);
		Triangle t2 = adjacentTriangles.get(1);
		
		//alpha and beta are slopes of the triangles relative to the shared edge.
		//values are positive if downward, negative if upward
		double alpha = -t1.getSlopeRelativeToBaseEdge(baseEdge);
		double beta = -t2.getSlopeRelativeToBaseEdge(baseEdge);
		
		//ensure -0 is converted to 0
		if (beta == 0) {
			beta = 0;
		}
		if (alpha == 0) {
			alpha = 0;
		}
		
		//radio of the segment's length to the length of the longest segment in the TIN
		double d = segment.getLength() / L; //d is 0-1
		
		double minAngleRadians = Math.min(alpha, beta)*Math.PI/180;
		double numerator = (c1+Math.log(r/v));
		double denominator = (1+Math.sin(minAngleRadians));
		double m = Math.pow((numerator/denominator), c2);
		
		//fitness
		double g = m * d;
		double fitness = 0;
		if (g != 0) {
			fitness = 1 / g;	
		}			
		
		//System.out.println("g: "+g+", num:"+numerator+", denom:"+ denominator+", minAngle:"+minAngleRadians+", v:"+v);
		return fitness;
	}

	
	
}
