package ca.bc.gov.catchment.fitness;

import java.io.IOException;

import ca.bc.gov.catchment.improvement.Junction;

public abstract class JunctionFitness {

	public abstract double fitness(Junction junction) throws IOException;
	
}
