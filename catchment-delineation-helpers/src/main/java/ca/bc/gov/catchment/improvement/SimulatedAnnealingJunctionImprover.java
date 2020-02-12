package ca.bc.gov.catchment.improvement;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.TreeMap;
import java.util.logging.Logger;

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
import ca.bc.gov.catchment.water.Water;

public class SimulatedAnnealingJunctionImprover extends JunctionImprover {

	private static final double MAX_TEMPERATURE = 100;
	private static Logger LOG = Logger.getAnonymousLogger();
	
	private CatchmentValidity catchmentValidityChecker;
	private TinEdges tinEdges;
	private JunctionFitness fitnessFinder;
	private int maxSteps;
	private double radius;
	private WaterAwareCatchmentRouter router;
	private boolean shortCircuitOnFirstImprovement;
	private ImprovementCoverage improvementCoverage;
	
	public SimulatedAnnealingJunctionImprover(
			TinEdges tinEdges,
			SimpleFeatureSource waterFeatures,			
			JunctionFitness fitnessFinder, 
			double radius,
			int maxSteps, 
			boolean shortCircuitOnFirstImprovement) throws IOException {
		this.tinEdges = tinEdges;
		this.fitnessFinder = fitnessFinder;
		this.catchmentValidityChecker = new CatchmentValidity(waterFeatures);
		this.router = new WaterAwareCatchmentRouter(tinEdges, new Water(waterFeatures));
		this.radius = radius;
		this.maxSteps = maxSteps;
		this.shortCircuitOnFirstImprovement = shortCircuitOnFirstImprovement;
		this.improvementCoverage = new ImprovementCoverage(tinEdges.getPointCloud());
	}
	
	@Override
	public JunctionModification improve(Junction originalJunction, CatchmentLines catchmentLines) throws IOException {
		
		ImprovementMetrics metrics = new ImprovementMetrics();
		metrics.incrementNumImprovementRequests();
		Date start = new Date();
		
		double originalFit = fitnessFinder.fitness(originalJunction);
		LOG.finer("trying to improve junction: "+originalJunction.getID());
		
		JunctionModification favouredModification = new JunctionModification(originalJunction);
		favouredModification.setImprovementMetrics(metrics);
		
		if (originalJunction.getDegree() == 0) {
			LOG.finer(" no sections touch this junction");
			return favouredModification;
		}
		if (originalJunction.getDegree() < 3) {
			LOG.finer(" invalid junction.  degree must be >= 3.  found: "+originalJunction.getDegree());
			return favouredModification;
		}
		
		List<Coordinate> neighboursTested = new ArrayList<Coordinate>();
		neighboursTested.add(originalJunction.getCoordinate());
		
		double favouredFit = originalFit;
		TreeMap<Double, JunctionModification> modificationsToConsider = new TreeMap<Double, JunctionModification>();
		modificationsToConsider.put(favouredFit, favouredModification);
		
		for(int stepNum = 0; stepNum < maxSteps; stepNum++) {
			Coordinate neighbourCoord = null;
			List<SimpleFeature> newSections = null;
			try {
				
				metrics.incrementNumAlternativesTested();
				List<Coordinate> exclude = new ArrayList<Coordinate>();
				exclude.add(favouredModification.getModifiedJunction().getCoordinate());
				neighbourCoord = tinEdges.getRandomCoordInRadius(favouredModification.getModifiedJunction().getCoordinate(), radius, exclude);
				
				if (neighboursTested.contains(neighbourCoord)) {
					continue;
				}
				
				LOG.finer(" neighbour:"+neighbourCoord);
				neighboursTested.add(neighbourCoord);
				improvementCoverage.incrementCountTotal(neighbourCoord);	
				
				//int freedom = 5; 
				//int freedom = (int)(Math.random() * 10) + 1; //random 1-10
				int freedom = (int)(Math.random() * 5) + 5; //random 5-10
				newSections = router.rerouteFeatures(favouredModification.getModifiedJunction().getTouchingSections(), favouredModification.getModifiedJunction().getCoordinate(), neighbourCoord, freedom);
			} catch(IOException e) {
				LOG.finer("  invalid: "+e);
				continue;
			} catch(RouteException e) {
				LOG.finer("  invalid: "+e);
				continue;
			}
			
			improvementCoverage.incrementCountValid(neighbourCoord, null);
			
			Junction neighbourJunction = new Junction(neighbourCoord, newSections);
			double neighbourFit = fitnessFinder.fitness(neighbourJunction);
			boolean fitnessImprovedVsFavoured = neighbourFit > favouredFit;
			boolean fitnessImprovedVsOriginal = neighbourFit > originalFit;
			boolean acceptWorse = false;
			if (!fitnessImprovedVsFavoured) {
				//if the fit is not an improvement, still consider setting it as the favoured route
				//based on a probability function
				double fractionOfTimeElapsed = (stepNum+1.0f)/maxSteps;
				double T = getTemperature(fractionOfTimeElapsed);
				double p = getProbabilityOfSwitching(favouredFit, neighbourFit, T);
				double r = Math.random();
				acceptWorse = p > r;
				if (!acceptWorse) {
					LOG.finer("  rejected.  fit worsened from "+favouredFit+" to "+neighbourFit);
					continue;
				}
			} 
			
			//validate the new sections.  if invalid, skip the route and try again
			//Performance Note: catchmentValidityChecker.areSectionsValidWrtWater is slowest operation
			//within the improve() function.  It takes >50% of the processing time.
			boolean isValidWrtWater = catchmentValidityChecker.areSectionsValidWrtWater(newSections);
			if (!isValidWrtWater) {
				LOG.finer("  rejected.  invalid w.r.t water");
				continue;
			}
			
			boolean isValidWrtCatchments = 
					catchmentValidityChecker.areSectionsValidWrtCatchments(newSections, catchmentLines.getUpdatedFeatures());
			if (!isValidWrtCatchments) {
				LOG.finer("  rejected.  invalid w.r.t catchments");
				continue;
			}
			
			metrics.incrementNumValidAlternativesTested();
			
			JunctionModification modification = new JunctionModification(originalJunction);
			modification.setModifiedJunction(neighbourJunction);
			
			//if the fit is an improvement, record the route
			if (fitnessImprovedVsFavoured) {
				LOG.finer("  accepted.  fit improved from "+favouredFit+" to "+neighbourFit);
				modificationsToConsider.put(neighbourFit, modification);
				favouredFit = neighbourFit;	
				favouredModification = modification;
				//System.out.println(" accepted neighbour automatically");
				if (fitnessImprovedVsOriginal && shortCircuitOnFirstImprovement) {
					break;
				}
				continue;
			}
			
			if (acceptWorse) {
				LOG.finer("  accepted. fit worsened from "+favouredFit+" to "+neighbourFit);
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
			metrics.incrementNumImproved();
			LOG.finer(" improved junction from "+originalFit+" to "+fitnessFinder.fitness(chosenModification.getModifiedJunction()));
		}
		else {
			LOG.finer(" junction could not be improved");
		}
		
		Date end = new Date();
		long runtimeMs = end.getTime() - start.getTime();
		metrics.setRuntimeMs(runtimeMs);
		chosenModification.setImprovementMetrics(metrics);
		
		return chosenModification;
	}
	
	/**
	 * Initialize the ImprovementCoverage with an existing object
	 * @param improvementCoverage
	 */
	public void setImprovementCoverage(ImprovementCoverage improvementCoverage) {
		this.improvementCoverage = improvementCoverage;
	}
	
	public ImprovementCoverage getImprovementCoverage() {
		return improvementCoverage;
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
