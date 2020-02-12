package ca.bc.gov.catchment.improvement;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import ca.bc.gov.catchment.CatchmentLines;
import ca.bc.gov.catchment.fitness.SectionFitness;
import ca.bc.gov.catchment.water.Water;

public class EvolutionSetImprover extends SetImprover {

	private static Logger LOG = Logger.getAnonymousLogger();
	
	private SectionFitness globalFitness;
	private BestOfNSetImprover childGenerator1;
	private BestOfNSetImprover childGenerator2;
	private int maxGenerations;
	private boolean stopEarlyIfNoImprovement;
	
	public EvolutionSetImprover(
			Water waterAnalyzer,
			SectionImprover sectionImprover,
			JunctionImprover junctionImprover,
			int childrenPerGeneration,
			int maxGenerations, 
			boolean stopEarlyIfNoImprovement) throws IOException {
		
		if (childrenPerGeneration < 2) {
			throw new IllegalArgumentException("must be at least 2 children per generation");
		}
		if (childrenPerGeneration % 2 == 1) {
			throw new IllegalArgumentException("children per generation must be an even number");
		}
		
		this.globalFitness = sectionImprover.getSectionFitness();
		int halfChildren = Math.round(childrenPerGeneration/2);
		this.childGenerator1 = new BestOfNSetImprover(waterAnalyzer, sectionImprover, junctionImprover, halfChildren);
		this.childGenerator2 = new BestOfNSetImprover(waterAnalyzer, sectionImprover, junctionImprover, halfChildren);
		this.maxGenerations = maxGenerations;
		this.stopEarlyIfNoImprovement = stopEarlyIfNoImprovement;
	}
	
	@Override
	protected CatchmentLines improveImpl(CatchmentLines initialCatchmentLines) throws IOException {
		CatchmentLines bestOfGeneration = initialCatchmentLines;
		CatchmentLines otherFromGeneration = bestOfGeneration;
		double initialFitness = getGlobalFitness().fitnessAvg(initialCatchmentLines.getUpdatedFeatures());
		double bestFitnessMostRecentGeneration = initialFitness;
		
		for(int i = 0; i < maxGenerations; i++) {
			CatchmentLines parent1 = bestOfGeneration;
			CatchmentLines parent2 = otherFromGeneration;
			LOG.info("starting generation "+(i+1)+ " of "+maxGenerations);
			List<CatchmentLines> nextParents = spawnChildrenAndChooseNextParents(parent1, parent2);
			
			boolean foundImprovement =nextParents != null && nextParents.size() >= 2;
			if (stopEarlyIfNoImprovement && !foundImprovement) {
				LOG.info("no improvement this generation.  stopping early.");
				break;
			}
			
			bestOfGeneration = nextParents.get(0);
			otherFromGeneration = nextParents.get(1);
			
			bestFitnessMostRecentGeneration = getGlobalFitness().fitnessAvg(bestOfGeneration.getUpdatedFeatures());
						
		}
		LOG.info("best set fitness from generation: "+bestFitnessMostRecentGeneration);
		CatchmentLines bestResult = toCatchmentLines(bestOfGeneration.getUpdatedFeatures());
		return bestResult;
	}
	
	/**
	 * 
	 * @param parent1
	 * @param parent2
	 * @return a list of size 2.  The first is the best fitting child from the generation, and the second is a random child from
	 * that generation.  These are suggestions for the next set of parents.  If no suggestions are found, returns null.
	 * @throws IOException
	 */
	private List<CatchmentLines> spawnChildrenAndChooseNextParents(CatchmentLines parent1, CatchmentLines parent2) throws IOException {
		List<CatchmentLines> nextParents = new ArrayList<CatchmentLines>();
		
		double parent1Fitness = getGlobalFitness().fitnessAvg(parent1.getUpdatedFeatures());
		double parent2Fitness = getGlobalFitness().fitnessAvg(parent2.getUpdatedFeatures());
		
		//spawn half children from parent1
		childGenerator1.improve(parent1);
		
		//span the other half of the children from parent 2
		childGenerator2.improve(parent2);
		
		CatchmentLines bestChildFromParent1 = childGenerator1.getBestSet();
		CatchmentLines randomChildFromParent1 = childGenerator1.getRandomSet();
		double fitnessBest1 = getGlobalFitness().fitnessAvg(bestChildFromParent1.getUpdatedFeatures());
		
		CatchmentLines bestChildFromParent2 = childGenerator2.getBestSet();
		CatchmentLines randomChildFromParent2 = childGenerator2.getRandomSet();
		double fitnessBest2 = getGlobalFitness().fitnessAvg(bestChildFromParent2.getUpdatedFeatures());
		
		if (fitnessBest1 <= parent1Fitness && fitnessBest2 <= parent2Fitness) {
			return null;
		}
		
		CatchmentLines bestChild = bestChildFromParent1; //best overall
		CatchmentLines randomChild = randomChildFromParent2;
		
		if (fitnessBest2 > fitnessBest1) {
			bestChild = bestChildFromParent2;
			randomChild = randomChildFromParent1;
		}
		nextParents.add(bestChild);
		nextParents.add(randomChild);
		
		return nextParents;
	}

	@Override
	public SectionFitness getGlobalFitness() {
		return this.globalFitness;
	}
	
}
