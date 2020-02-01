package ca.bc.gov.catchment.improvement;

import java.io.IOException;
import java.util.logging.Logger;

import org.geotools.data.collection.SpatialIndexFeatureCollection;
import org.geotools.data.collection.SpatialIndexFeatureSource;
import org.geotools.data.simple.SimpleFeatureCollection;

import ca.bc.gov.catchment.CatchmentLines;
import ca.bc.gov.catchment.fitness.SectionFitness;

public class EvolutionSetImprover extends SetImprover {

	private static Logger LOG = Logger.getAnonymousLogger();
	
	private SetImprover generationSetImprover;
	private int numGenerations;
	
	public EvolutionSetImprover(
			SetImprover generationSetImprover,
			int numGenerations) {
		this.generationSetImprover = generationSetImprover;
		this.numGenerations = numGenerations;
	}
	
	@Override
	public SimpleFeatureCollection improve(CatchmentLines initialCatchmentLines) throws IOException {
		CatchmentLines bestOverall = initialCatchmentLines;
		for(int i = 0; i < numGenerations; i++) {
			LOG.info("starting generation "+(i+1)+ " of "+numGenerations);
			SimpleFeatureCollection bestOfGeneration = simulateOneGeneration(bestOverall);
			
			//create the CatchmentLines that will be the starting point for the next generation
			SpatialIndexFeatureCollection fc = new SpatialIndexFeatureCollection(bestOfGeneration);
			SpatialIndexFeatureSource fs = new SpatialIndexFeatureSource(fc);
			
			//Note: because of the bbox filter, the following statement *could* create a set with fewer 
			//features than it started with if "simulateOneGeneration" pushes any feature outside the initial 
			//bbox
			
			bestOverall = new CatchmentLines(fs, initialCatchmentLines.getDefaultFilter());
			
			
		}
		double bestFitness = getGlobalFitness().fitnessAvg(bestOverall.getUpdatedFeatures());
		LOG.info("best set fitness from generation: "+bestFitness);
		return bestOverall.getOriginalFeatures();
	}
	
	private SimpleFeatureCollection simulateOneGeneration(CatchmentLines initialCatchmentLines) throws IOException {
		SimpleFeatureCollection bestOfGeneration = generationSetImprover.improve(initialCatchmentLines);
		return bestOfGeneration;
	}

	@Override
	public SectionFitness getGlobalFitness() {
		return this.generationSetImprover.getGlobalFitness();
	}
	
}
