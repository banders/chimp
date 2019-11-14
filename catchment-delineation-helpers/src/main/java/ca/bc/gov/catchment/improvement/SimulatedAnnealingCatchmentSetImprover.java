package ca.bc.gov.catchment.improvement;

import java.io.IOException;
import java.util.Iterator;
import java.util.NavigableSet;
import java.util.TreeMap;

import org.geotools.data.simple.SimpleFeatureSource;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.LineString;
import org.opengis.feature.simple.SimpleFeature;

import ca.bc.gov.catchment.fitness.CatchmentValidity;
import ca.bc.gov.catchment.fitness.GeometryFitnessFinder;
import ca.bc.gov.catchment.routes.LineStringRouter;
import ca.bc.gov.catchment.routes.RouteException;
import ca.bc.gov.catchment.routes.WaterAwareCatchmentRouter;
import ca.bc.gov.catchment.tin.TinEdges;
import ca.bc.gov.catchment.water.WaterAnalyzer;
import ca.bc.gov.catchments.utils.SpatialUtils;

public class SimulatedAnnealingCatchmentSetImprover extends CatchmentSetImprover {

	private static final double MAX_TEMPERATURE = 100;
	private static final int MAX_STEPS = 50;
	
	private CatchmentValidity catchmentValidityChecker;
	private SimpleFeatureSource catchmentEdges;
	private TinEdges tinEdges;
	private GeometryFitnessFinder fitnessFinder;
	private int maxSteps;
	private double radius;
	private WaterAwareCatchmentRouter router;
	

	public SimulatedAnnealingCatchmentSetImprover(
			SimpleFeatureSource waterFeatures, 
			TinEdges tinEdges,
			SimpleFeatureSource catchmentEdges,
			GeometryFitnessFinder fitnessFinder, 
			double radius) {
		super(waterFeatures);
		this.tinEdges = tinEdges;
		this.catchmentEdges = catchmentEdges;
		this.fitnessFinder = fitnessFinder;
		this.catchmentValidityChecker = new CatchmentValidity(waterFeatures);
		this.router = new WaterAwareCatchmentRouter(tinEdges, new WaterAnalyzer(waterFeatures));
		this.maxSteps = MAX_STEPS;
		this.radius = radius;
	}

	@Override
	public SectionModification improve(SimpleFeature section) throws IOException {
		
		LineString originalRoute = (LineString)section.getDefaultGeometry();
		double originalFit = fitnessFinder.fitness(originalRoute);
		
		//favouredRoute is the simulated annealing algorithm's best suggestion, although
		//it only considers fitness of the route (not whether the route is valid).
		//the reason for this is: fitness is quick to calculate, but validity is 
		//computationally expensive.
		LineString favouredRoute = originalRoute;
		double favouredFit = originalFit;
		TreeMap<Double, LineString> routesToConsider = new TreeMap<Double, LineString>();
		routesToConsider.put(favouredFit, favouredRoute);
		
		for(int stepNum = 0; stepNum < maxSteps; stepNum++) {
			LineString neighbourRoute = null;
			try {
				neighbourRoute = getRandomNeighbour(favouredRoute);
			} catch(RouteException e) {
				continue;
			} catch(IOException e) {
				continue;
			}
			
			//validate the neighbour route.  if invalid, skip the route and try again
			boolean isValidWrtWater = catchmentValidityChecker.isRouteValidWrtWater(neighbourRoute);
			if (!isValidWrtWater) {
				System.out.println("neighbour rejected.  invalid w.r.t water");
				continue;
			}
			boolean isValidWrtCatchments = 
					catchmentValidityChecker.isRouteValidWrtCatchments(neighbourRoute, getProposedCatchmentSections(), section.getIdentifier());
			if (!isValidWrtCatchments) {
				System.out.println("neighbour rejected.  invalid w.r.t catchments");
				continue;
			}
			
			double neighbourFit = fitnessFinder.fitness(neighbourRoute);
			System.out.println("neighbourFit:"+neighbourFit+", favouredFit:"+favouredFit);
			//if the fit is an improvement, record the route
			if (neighbourFit > favouredFit) {
				routesToConsider.put(neighbourFit, neighbourRoute);
				favouredFit = neighbourFit;	
				favouredRoute = neighbourRoute;
				//System.out.println(" accepted neighbour automatically");
				continue;
			}
			
			//if the fit is not an improvement, still consider setting it as the favoured route
			//based on a probability function
			double fractionOfTimeElapsed = (stepNum+1.0f)/maxSteps;
			double T = getTemperature(fractionOfTimeElapsed);
			double p = getProbabilityOfSwitching(favouredFit, neighbourFit, T);
			double r = Math.random();
			
			//System.out.println(" T: "+T);
			//System.out.println(" p: "+p);
			//System.out.println(" r: "+r);
			
			if (p > r) {
				favouredFit = neighbourFit;
				favouredRoute = neighbourRoute;
				//System.out.println(" accepted neighbour at random");
				continue;
			}
			
			//the current neighbour route was not selected as the favoured route
			//System.out.println(" rejected neighbour");
		}


		//the chosenRoute may be the same as the favouredRoute, but not necessarily.
		//chosenRoute takes into account fitness *and* validity.  
		//chosenRoute may also be null of no suitable route could be found
		LineString chosenRoute = chooseRoute(routesToConsider);
		
		//prepare result object which includes the chosenRoute
		SectionModification result = new SectionModification(section);
		if (chosenRoute != null && !chosenRoute.equals(originalRoute)) {
			SimpleFeature modifiedSection = SpatialUtils.geomToFeature(chosenRoute, section.getFeatureType(), section.getID());
			result.setModifiedSection(modifiedSection);	
			System.out.println("improved section from "+originalFit+" to "+fitnessFinder.fitness(chosenRoute));
		}
		else {
			System.out.println("section could not be improved");
		}
		return result;
	}

	@Override
	public JunctionModification improveJunction(Coordinate originalJunction) throws IOException {
		// TODO Auto-generated method stub
		throw new IllegalStateException("not implemented yet");
	}
	
	//-------------------------------------------------------------------------
	
	/**
	 * Chooses the route from the given map with the best fit.  
	 * Assumption: all routes in the given map are valid.  Therefore, the chosen route will be a valid route.
	 * @param routesToConsider
	 * @return
	 * @throws IOException
	 */
	private LineString chooseRoute(TreeMap<Double, LineString> routesToConsider) throws IOException {
		NavigableSet<Double> keySet = routesToConsider.descendingKeySet();
		Iterator<Double> it = keySet.iterator(); //ascending order (of the descending key set), which is big to small
		while(it.hasNext()) {
			double key = it.next();
			LineString route = routesToConsider.get(key);
			return route;
			/*
			boolean isValidWrtWater = catchmentValidityChecker.isRouteValidWrtWater(route);
			boolean isValidWrtCatchments = catchmentValidityChecker.isRouteValidWrtCatchments(route, catchmentEdges.getFeatures());
					;
			if (isValidWrtWater && isValidWrtCatchments) {
				System.out.println(" route with fit "+key+" is valid");
				return route;
			}
			else {
				System.out.println(" route with fit "+key+" is not valid (water?:"+isValidWrtWater+", catchments?:"+isValidWrtCatchments+")");
			}
			*/
		}
		return null;
	}
	
	/**
	 * Generates a random route which is a "neighbour" of the given route. 
	 * The neighbour route is a similar route to the initial route provided, but with some perturbation.
	 * neighbour route are not guaranteed to be valid (e.g. with respect to water or other catchment routes)
	 * @param route
	 * @return
	 * @throws RouteException
	 * @throws IOException
	 */
	private LineString getRandomNeighbour(LineString route) throws RouteException, IOException {

		int MAX_TRIES = 100;
		for(int attemptNum = 0; attemptNum < MAX_TRIES; attemptNum++) { 
			//pick a pivotIndex, which is the main coordinate that will be displaced.
			int pivotIndex = (int)(Math.random() * route.getNumPoints());
			Coordinate oldCoord = route.getCoordinateN(pivotIndex);
			Coordinate newCoord = tinEdges.getRandomCoordInRadius(oldCoord, radius, true);
			int freedom = (int)(Math.random() * route.getNumPoints());
			try {
				LineString neighbourRoute = router.reroute(route, oldCoord, newCoord, freedom);
				return neighbourRoute;
			} catch (Exception e) {
				//neighbour routes won't be possible for all pivotCoords.  
				//if we encounter such a pivotCoord, try again
				continue;
			}
		}
		throw new RouteException("Unable to find a neighbour route");		
	}
	
	
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
