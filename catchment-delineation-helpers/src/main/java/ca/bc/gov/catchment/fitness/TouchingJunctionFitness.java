package ca.bc.gov.catchment.fitness;

import java.io.IOException;
import java.util.List;

import org.locationtech.jts.geom.Coordinate;
import org.opengis.feature.simple.SimpleFeature;

public class TouchingJunctionFitness extends JunctionFitness {

	private SectionFitness sectionFitness;
	
	/**
	 * fitness of a junction is calculated as: the sum of fitnesses for each touching section
	 * @param sectionFitness defines how the fitness of a section is determined
	 */
	public TouchingJunctionFitness(SectionFitness sectionFitness) {
		this.sectionFitness = sectionFitness;
	}
	
	@Override
	public double fitness(Coordinate junction, List<SimpleFeature> touchingSections) throws IOException {
		return sectionFitness.fitnessSum(touchingSections);		
	}

}
