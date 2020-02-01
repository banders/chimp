package ca.bc.gov.catchment.fitness;

import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
import ca.bc.gov.catchment.tin.TinEdges;
import ca.bc.gov.catchment.tin.TinPolys;
import ca.bc.gov.catchment.tin.Triangle;
import ca.bc.gov.catchments.utils.SpatialUtils;

/**
 * Fitness function that treats elevation as the primary variable, and 
 * "boosts" the fitness value if additional conditions are true, such as:
 *  - alignment with ridges
 * it also 
 * 
 * @author Brock
 * 
 */
public class CedarSectionFitness extends SectionFitness {

	private static final int MAX_CACHE_SIZE = 1000;
	private static final int COLOR_UNDEFINED = -100;
	private static final int COLOR_GOOD = 2;
	private static final int COLOR_MEDIUM = 1;
	
	private GeometryFactory geometryFactory;
	private TinPolys tinPolys;
	private Map<LineString, Double> fitnessCache;
	private double maxElevation;
	
	public CedarSectionFitness(TinPolys tinPolys) throws IOException {
		this.tinPolys = tinPolys;
		this.geometryFactory = JTSFactoryFinder.getGeometryFactory();
		
		//define a map that removes the oldest entry when the 
		//cache reaches its maximum size.  i.e. the least-recently-used entry is
		//removed
		fitnessCache = new LinkedHashMap<LineString, Double>(MAX_CACHE_SIZE, .75F, true) {
		    public boolean removeEldestEntry(Map.Entry<LineString, Double> eldest) {
		        return size() > MAX_CACHE_SIZE;
		    }
		};
		maxElevation = tinPolys.getMaxElevation(); 
	}
	
	@Override
	public double fitness(Geometry geom) throws IOException {
		double originalFitness = super.fitness(geom);
		double len = geom.getLength();
		double fitnessAdjustedForLength = originalFitness / len;
		return fitnessAdjustedForLength;
	}
	
	@Override
	/**
	 * Fitness of a section is:
	 * 	 fitness = [section length] * [section color]
	 * where section color is a number which is high if the 
	 * section is on a ridge, and low if it isn't
	 */
	public double fitness(Coordinate c1, Coordinate c2) throws IOException {		
		Coordinate[] coords = {c1, c2};
		LineString segment = geometryFactory.createLineString(coords);
		
		//lookup fitness in cache
		Double cachedResult = fitnessCache.get(segment);
		if (cachedResult != null) {
			return cachedResult.doubleValue();
		}
		
		double length = segment.getLength();
		double avgElevation = (c1.getZ() + c2.getZ()) / 2;
		
		List<Triangle> triangles = tinPolys.getTrianglesOnEdge(segment);
		double color = COLOR_UNDEFINED;
		if (triangles.size() == 2) {
			Triangle t1 = triangles.get(0);
			Triangle t2 = triangles.get(1);
			Edge baseEdge = new Edge(c1, c2);			
			double alpha = t1.getSlopeRelativeToBaseEdge(baseEdge);
			double beta = t2.getSlopeRelativeToBaseEdge(baseEdge);
			double maxSlope = Math.max(alpha, beta);
			if (maxSlope < 0) {
				//ridge
				color =  COLOR_GOOD;
			}
			else if (maxSlope < 2.5) {
				//partial ridge
				color = COLOR_MEDIUM;
			}
			else {
				//no ridge 
				double elevationFraction = avgElevation / maxElevation; //[0-1] where higher numbers indicate higher elevation
				color = elevationFraction;
			}
		}
		double fitness = length * color;
		fitnessCache.put(segment, fitness);
		return fitness;
	}

	
	
}
