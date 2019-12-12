package ca.bc.gov.catchment.improvement;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.TreeMap;

import org.geotools.data.simple.SimpleFeatureSource;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.filter.identity.FeatureId;

import ca.bc.gov.catchment.CatchmentLines;
import ca.bc.gov.catchment.fitness.CatchmentValidity;
import ca.bc.gov.catchment.fitness.JunctionFitness;
import ca.bc.gov.catchment.fitness.SectionFitness;
import ca.bc.gov.catchment.routes.RouteException;
import ca.bc.gov.catchment.routes.WaterAwareCatchmentRouter;
import ca.bc.gov.catchment.tin.TinEdges;
import ca.bc.gov.catchment.water.WaterAnalyzer;

public class SimulatedAnnealingJunctionImprover extends JunctionImprover {

	private static final double MAX_TEMPERATURE = 100;
	
	private CatchmentValidity catchmentValidityChecker;
	private CatchmentLines catchmentLines;
	private TinEdges tinEdges;
	private JunctionFitness fitnessFinder;
	private int maxSteps;
	private double radius;
	private WaterAwareCatchmentRouter router;
	private boolean shortCircuitOnFirstImprovement;
	
	public SimulatedAnnealingJunctionImprover(
			CatchmentLines catchmentLines,
			TinEdges tinEdges,
			SimpleFeatureSource waterFeatures,			
			JunctionFitness fitnessFinder, 
			double radius,
			int maxSteps) {
		this.tinEdges = tinEdges;
		this.catchmentLines = catchmentLines;
		this.fitnessFinder = fitnessFinder;
		this.catchmentValidityChecker = new CatchmentValidity(waterFeatures);
		this.router = new WaterAwareCatchmentRouter(tinEdges, new WaterAnalyzer(waterFeatures));
		this.radius = radius;
		this.maxSteps = maxSteps;
		this.shortCircuitOnFirstImprovement = true;
	}
	
	@Override
	public JunctionModification improve(Coordinate originalJunction) throws IOException {
		
		//identify other sections touching this junction		
		List<SimpleFeature> originalTouching = catchmentLines.getSectionsTouchingJunction(originalJunction);

		
		double originalFit = fitnessFinder.fitness(originalJunction, originalTouching);
		System.out.println("trying to improve junction: "+originalJunction);
		
		JunctionModification favouredModification = new JunctionModification(originalJunction, originalTouching);
		
		if (originalTouching.size() == 0) {
			System.out.println(" no sections touch this junction");
			return favouredModification;
		}
		if (originalTouching.size() < 3) {
			System.out.println(" point is not a complete junction.  expected 3 or more sections to meet here.  found: "+originalTouching.size());
			return favouredModification;
		}
		
		List<Coordinate> neighboursTested = new ArrayList<Coordinate>();
		neighboursTested.add(originalJunction);
		
		double favouredFit = originalFit;
		TreeMap<Double, JunctionModification> modificationsToConsider = new TreeMap<Double, JunctionModification>();
		modificationsToConsider.put(favouredFit, favouredModification);
		
		for(int stepNum = 0; stepNum < maxSteps; stepNum++) {
			Coordinate neighbourCoord = null;
			List<SimpleFeature> newSections = null;
			
			try {
				List<Coordinate> exclude = new ArrayList<Coordinate>();
				exclude.add(favouredModification.getModifiedJunction());
				neighbourCoord = tinEdges.getRandomCoordInRadius(favouredModification.getModifiedJunction(), radius, exclude);
				if (neighboursTested.contains(neighbourCoord)) {
					continue;
				}
				System.out.print(" neighbour:"+neighbourCoord);
				neighboursTested.add(neighbourCoord);
				int freedom = 5; //(int)(Math.random() * 10) + 1; //10 is max freedom
				newSections = router.rerouteFeatures(favouredModification.getModifiedSections(), favouredModification.getModifiedJunction(), neighbourCoord, freedom);
			} catch(IOException e) {
				System.out.println("  invalid: "+e);
				continue;
			} catch(RouteException e) {
				System.out.println("  invalid: "+e);
				continue;
			}
			
			double neighbourFit = fitnessFinder.fitness(neighbourCoord, newSections);
			boolean fitnessImproved = neighbourFit > favouredFit;
			boolean acceptWorse = false;
			if (!fitnessImproved) {
				//if the fit is not an improvement, still consider setting it as the favoured route
				//based on a probability function
				double fractionOfTimeElapsed = (stepNum+1.0f)/maxSteps;
				double T = getTemperature(fractionOfTimeElapsed);
				double p = getProbabilityOfSwitching(favouredFit, neighbourFit, T);
				double r = Math.random();
				acceptWorse = p > r;
				if (!acceptWorse) {
					System.out.println("  rejected.  fit worsened from "+favouredFit+" to "+neighbourFit);
					continue;
				}
			} 
			
			//validate the new sections.  if invalid, skip the route and try again
			boolean isValidWrtWater = catchmentValidityChecker.areSectionsValidWrtWater(newSections);
			if (!isValidWrtWater) {
				System.out.println("  rejected.  invalid w.r.t water");
				continue;
			}
			boolean isValidWrtCatchments = 
					catchmentValidityChecker.areSectionsValidWrtCatchments(newSections, catchmentLines.getUpdatedFeatures());
			if (!isValidWrtCatchments) {
				System.out.println("  rejected.  invalid w.r.t catchments");
				continue;
			}
			
			JunctionModification modification = new JunctionModification(originalJunction, originalTouching);
			modification.setModifiedJunction(neighbourCoord);
			modification.setModifiedSections(newSections);
			
			//if the fit is an improvement, record the route
			if (fitnessImproved) {
				System.out.println("  accepted.  fit improved from "+favouredFit+" to "+neighbourFit);
				modificationsToConsider.put(neighbourFit, modification);
				favouredFit = neighbourFit;	
				favouredModification = modification;
				//System.out.println(" accepted neighbour automatically");
				if (shortCircuitOnFirstImprovement) {
					break;
				}
				continue;
			}
			
			if (acceptWorse) {
				System.out.println("  accepted. fit worsened from "+favouredFit+" to "+neighbourFit);
				favouredFit = neighbourFit;
				favouredModification = modification;
				//System.out.println(" accepted neighbour at random");
				continue;
			}
		}
		 
		//the chosenModification may be the same as the favouredModification, but not necessarily.
		//chosenRoute takes into account fitness *and* validity.  
		//chosenRoute may also be null of no suitable route could be found
		JunctionModification chosenModification = chooseJunctionModification(modificationsToConsider);
		if (!chosenModification.getOriginalJunction().equals(chosenModification.getModifiedJunction())) {
			System.out.println(" improved junction from "+originalFit+" to "+fitnessFinder.fitness(chosenModification.getModifiedJunction(), chosenModification.getModifiedSections()));
		}
		else {
			System.out.println(" junction could not be improved");
		}
		
		return chosenModification;
	}
	
	public ImprovementCoverage getImprovementCoverage() {
		return null;
	}
	
	private String getJunctionId(List<SimpleFeature> touchingSections) {
		String id = "";
		for(SimpleFeature f : touchingSections) {
			id += f.getIdentifier().getID();
		}
		if (id.equals("")) {
			return null;
		}
		return id;
	}
	
	/**
	 * Chooses the JunctionModification from the given map with the best fit.  
	 * Assumption: all JunctionModification in the given map are valid.  Therefore, the chosen JunctionModification will be a valid JunctionModification.
	 * @param modificationsToConsider
	 * @return
	 * @throws IOException
	 */
	private JunctionModification chooseJunctionModification(TreeMap<Double, JunctionModification> modificationsToConsider) throws IOException {
		NavigableSet<Double> keySet = modificationsToConsider.descendingKeySet();
		Iterator<Double> it = keySet.iterator(); //ascending order (of the descending key set), which is big to small
		while(it.hasNext()) {
			double key = it.next();
			JunctionModification modification = modificationsToConsider.get(key);
			return modification;
		}
		return null;
	}
	
	/**
	 * A function which gives temperature (0-MAX_TEMPERATURE) as a function of the fraction of elapsed time (0-1).
	 * @param fractionOfTimeElapsed
	 * @return
	 */
	private double getTemperature(double fractionOfTimeElapsed) {
		if (fractionOfTimeElapsed > 1 || fractionOfTimeElapsed < 0) {
			throw new IllegalArgumentException("fractionOfTimeElapsed must be in range [0,1]");
		}
		
		//implemented as a straight line (i.e. linear function of order 1)
		//with a given initial temperature (i.e. y intercept)
		//and a decay rate that is a function of the fractionOfTimeElapsed
		double yIntercept = MAX_TEMPERATURE;
		double slope = -MAX_TEMPERATURE;
		double T = slope*fractionOfTimeElapsed + yIntercept;
		return T;
	}
	
	/**
	 * returns a probability in range [0,1] that the option with proposedFit should be 
	 * selected over the option with currentFit 
	 * @param currentFit
	 * @param proposedFit
	 * @param T
	 * @return
	 */
	private double getProbabilityOfSwitching(double currentFit, double proposedFit, double T) {
		//implementation from: https://fenix.tecnico.ulisboa.pt/downloadFile/3779572125309/1415%20OD_Metaheuristics_BC-2008.pdf
		//if we aim to maximize, use (currentFit - proposedFit).  to minimize use (proposedFit - currentFit)
		double x = (proposedFit - currentFit) / T;
		double p = Math.pow(Math.E, x);
		return p;
	}
	
}
