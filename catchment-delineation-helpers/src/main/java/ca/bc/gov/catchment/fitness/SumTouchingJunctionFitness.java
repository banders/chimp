package ca.bc.gov.catchment.fitness;

import java.io.IOException;
import java.util.List;

import org.locationtech.jts.geom.Coordinate;
import org.opengis.feature.simple.SimpleFeature;

import ca.bc.gov.catchment.improvement.Junction;

public class SumTouchingJunctionFitness extends JunctionFitness {

	private SectionFitness sectionFitness;
	
	/**
	 * fitness of a junction is calculated as: the sum of fitnesses for each touching section
	 * @param sectionFitness defines how the fitness of a section is determined
	 */
	public SumTouchingJunctionFitness(SectionFitness sectionFitness) {
		this.sectionFitness = sectionFitness;
	}
	
	@Override
	public double fitness(Junction junction) throws IOException {
		return sectionFitness.fitnessSum(junction.getTouchingSections());		
	}

}
