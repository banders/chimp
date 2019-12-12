package ca.bc.gov.catchment.fitness;

import java.io.IOException;
import java.util.List;

import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.opengis.feature.simple.SimpleFeature;

/**
 * An abstract notion of "fitness".  "Fitness" refers to the goodness of fit of a geometry to some not-yet-specified 
 * criteria.  Subclasses define the specific criteria used to evaluate fitness.
 * Fitness is defined on multiple scales:
 *   - the fitness of a two-point line segment is the basic unit of fitness
 *   - the scale of a n-point line segment is a sum of the fitnesses of the component two-point line segments
 *   - the scale of a collection of line segments is a sum of the fitnesses of the component n-point line segments
 * Larger values mean "more fit" than smaller values.  The meaning of sign and magnitude of the fitness values 
 * are not prescribed here (i.e. subclasses are free to define fitness such that sign and magnitude are 
 * somehow meaningful, or they may treat these as meaningless)
 * @author Brock
 *
 */
public abstract class SectionFitness {

	public double fitnessSum(List<SimpleFeature> features) throws IOException {
		double totalFitness = 0;
		for(SimpleFeature f : features) {
			Geometry g = (Geometry)f.getDefaultGeometry();
			double fitness = fitness(g);
			totalFitness += fitness;
		}
		return totalFitness;
	}
	
	public double fitnessSum(SimpleFeatureCollection features) throws IOException {
		double totalFitness = 0;
		SimpleFeatureIterator it = features.features();
		try {
			while(it.hasNext()) {
				SimpleFeature f = it.next();
				Geometry g = (Geometry)f.getDefaultGeometry();
				double fitness = fitness(g);
				totalFitness += fitness;
			}
		}
		finally {
			it.close();
		}
		return totalFitness;
	}
	
	public double fitnessAvg(List<SimpleFeature> features) throws IOException {
		double avg = fitnessSum(features) / features.size();
		return avg;
	}
	
	public double fitnessAvg(SimpleFeatureCollection features) throws IOException {
		double avg = fitnessSum(features) / features.size();
		return avg;
	}

	public double fitness(Geometry geom) throws IOException {
		double totalFitness = 0;
		Coordinate prev = null;
		for(Coordinate coord : geom.getCoordinates()) {
			if (prev != null) {
				double fitness = fitness(prev, coord);
				totalFitness += fitness;
			}
			prev = coord;
		}
		return totalFitness;
	}
	
	public void describe(Geometry geom) throws IOException {
		double totalFitness = 0;
		Coordinate prev = null;
		for(Coordinate coord : geom.getCoordinates()) {
			if (prev != null) {
				double fitness = fitness(prev, coord);
				System.out.println("fitness of "+prev +" to "+ coord+": "+fitness);
				totalFitness += fitness;
			}
			prev = coord;
		}		
		System.out.println("total fitness: "+totalFitness);
	}
	
	public abstract double fitness(Coordinate c1, Coordinate c2) throws IOException;
	
}
