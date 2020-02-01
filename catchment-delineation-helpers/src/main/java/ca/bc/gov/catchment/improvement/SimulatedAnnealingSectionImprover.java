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

import org.geotools.data.DataUtilities;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.feature.SchemaException;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.referencing.CRS;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.LineString;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.filter.identity.FeatureId;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

import ca.bc.gov.catchment.CatchmentLines;
import ca.bc.gov.catchment.fitness.CatchmentValidity;
import ca.bc.gov.catchment.fitness.SectionFitness;
import ca.bc.gov.catchment.routes.RouteException;
import ca.bc.gov.catchment.routes.WaterAwareCatchmentRouter;
import ca.bc.gov.catchment.tin.TinEdges;
import ca.bc.gov.catchment.water.WaterAnalyzer;
import ca.bc.gov.catchments.utils.SaveUtils;
import ca.bc.gov.catchments.utils.SpatialUtils;

public class SimulatedAnnealingSectionImprover extends SectionImprover {

	private static final double MAX_TEMPERATURE = 100;
	private static Logger LOG = Logger.getAnonymousLogger();
	
	private CatchmentValidity catchmentValidityChecker;
	private CatchmentLines catchmentLines;
	private TinEdges tinEdges;
	private int maxSteps;
	private double radius;
	private WaterAwareCatchmentRouter router;
	private ImprovementCoverage improvementCoverage;
	
	public SimulatedAnnealingSectionImprover(
			CatchmentLines catchmentLines,
			TinEdges tinEdges,
			SimpleFeatureSource waterFeatures,
			SectionFitness fitnessFinder, 
			double radius,
			int maxSteps) throws IOException {
		this.tinEdges = tinEdges;
		this.catchmentLines = catchmentLines;
		this.setSectionFitness(fitnessFinder);
		this.catchmentValidityChecker = new CatchmentValidity(waterFeatures);
		this.router = new WaterAwareCatchmentRouter(tinEdges, new WaterAnalyzer(waterFeatures));
		this.maxSteps = maxSteps;
		this.radius = radius;
		this.improvementCoverage = new ImprovementCoverage(tinEdges.getPointCloud());
	}
	
	@Override
	public SectionModification improve(SimpleFeature section) throws IOException {
		
		ImprovementMetrics metrics = new ImprovementMetrics();
		metrics.incrementNumImprovementRequests();
		Date start = new Date();
		
		SectionModification result = new SectionModification(section);
		result.setImprovementMetrics(metrics);
		
		//this list is kept just so we can output all neighbours, then visualize it later for debugging.
		List<SimpleFeature> neighboursTested = new ArrayList<SimpleFeature>();
		
		LineString originalRoute = (LineString)section.getDefaultGeometry();
		double originalFit = getSectionFitness().fitness(originalRoute);
		
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
				metrics.incrementNumAlternativesTested();
				neighbourRoute = getRandomNeighbour(favouredRoute);
			} catch(RouteException e) {
				continue;
			} catch(IOException e) {
				continue;
			}
			
			//document the neighbour route
			improvementCoverage.incrementCountTotal(neighbourRoute, section);	
			
			double neighbourFit = getSectionFitness().fitness(neighbourRoute);
			SimpleFeature neighbourFeature = toTestedFeature(neighbourRoute, neighbourFit, ""+stepNum);
			neighboursTested.add(neighbourFeature);
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
					LOG.finer("neighbour rejected.  fit worsened from "+favouredFit+" to "+neighbourFit);
					continue;
				}
			}
			
			//validate the neighbour route.  if invalid, skip the route and try again
			boolean isValidWrtWater = catchmentValidityChecker.isRouteValidWrtWater(neighbourRoute);
			if (!isValidWrtWater) {
				LOG.finer("neighbour rejected.  invalid w.r.t water");
				continue;
			}
			boolean isValidWrtCatchments = 
					catchmentValidityChecker.isRouteValidWrtCatchments(neighbourRoute, catchmentLines.getUpdatedFeatures(), section.getIdentifier());
			if (!isValidWrtCatchments) {
				LOG.finer("neighbour rejected.  invalid w.r.t catchments");
				continue;
			}
			
			//document the neighbour route
			improvementCoverage.incrementCountValid(neighbourRoute, section);
			metrics.incrementNumValidAlternativesTested();
			
			//System.out.println("neighbourFit:"+neighbourFit+", favouredFit:"+favouredFit);
			
			//if the fit is an improvement, record the route
			if (fitnessImproved) {
				routesToConsider.put(neighbourFit, neighbourRoute);
				favouredFit = neighbourFit;	
				favouredRoute = neighbourRoute;
				LOG.finer("neighbour accepted.  fit improved from "+favouredFit+" to "+neighbourFit);
				continue;
			}
						
			if (acceptWorse) {
				favouredFit = neighbourFit;
				favouredRoute = neighbourRoute;
				LOG.finer("neighbour accepted.  fit worsened from "+favouredFit+" to "+neighbourFit);
				continue;
			}
			
			//if the execution reaches here, then the neighbour wasn't accepted as the favoured route
		}


		//the chosenRoute may be the same as the favouredRoute, but not necessarily.
		//chosenRoute takes into account fitness *and* validity.  
		//chosenRoute may also be null of no suitable route could be found
		LineString chosenRoute = chooseRoute(routesToConsider);
		
		//prepare result object which includes the chosenRoute		
		if (chosenRoute != null && !chosenRoute.equals(originalRoute)) {
			SimpleFeature modifiedSection = SpatialUtils.geomToFeature(chosenRoute, section.getFeatureType(), section.getID());
			result.setModifiedSection(modifiedSection);
			metrics.incrementNumImproved();
			LOG.finer("improved section from "+originalFit+" to "+getSectionFitness().fitness(chosenRoute));
		}
		else {
			LOG.finer("section could not be improved from "+originalFit);
		}
		
		Date end = new Date();
		long runtimeMs = end.getTime() - start.getTime();
		metrics.setRuntimeMs(runtimeMs);
		
		/*
		SaveUtils.saveToGeoPackage("C:/Temp/catchment-section-"+section.getID()+".gpkg", 
				SpatialUtils.featListToSimpleFeatureCollection(neighboursTested), 
				false);
		*/
		
		return result;
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
		List<Coordinate> existingRouteCoords = SpatialUtils.toCoordinateList(route.getCoordinates());
		int MAX_TRIES = 10;
		for(int attemptNum = 0; attemptNum < MAX_TRIES; attemptNum++) { 
			//pick a pivotIndex, which is the main coordinate that will be displaced.
			int pivotIndex = (int)(Math.random() * route.getNumPoints());
			Coordinate oldCoord = route.getCoordinateN(pivotIndex);
			Coordinate newCoord = tinEdges.getRandomCoordInRadius(oldCoord, radius, existingRouteCoords);
			int freedom = (int)(Math.random() * route.getNumPoints()/2);
			try {
				LineString neighbourRoute = router.reroute(route, oldCoord, newCoord, freedom, false);
				return neighbourRoute;
			} catch (Exception e) {
				//neighbour routes won't be possible for all pivotCoords.  
				//if we encounter such a pivotCoord, try again
				continue;
			}
		}
		throw new RouteException("Unable to find a neighbour route");		
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
	
	private SimpleFeature toTestedFeature(LineString route, double fit, String fid) {

		//lookup the SRID of the point cloud
		CoordinateReferenceSystem crs = catchmentLines.getSchema().getCoordinateReferenceSystem();
		int srid = -1;
		try {
			srid = CRS.lookupEpsgCode(crs, true);
		} catch (FactoryException e1) {
			LOG.finer("Unable to lookup SRID");
			System.exit(1);
		}
		
		//feature type for the point cloud
		String outTable = "tested_routes";
		SimpleFeatureType testedFeatureType = null;
		try {
			testedFeatureType = DataUtilities.createType(outTable, "geometry:LineString:srid="+srid+",fit:float");
		} catch (SchemaException e1) {
			LOG.finer("Unable to create feature type "+outTable);
			System.exit(1);
		}
		SimpleFeatureBuilder featureBuilder = new SimpleFeatureBuilder(testedFeatureType);
		
		Object[] values = {route, fit};
		SimpleFeature feature = featureBuilder.buildFeature(fid, values);
		return feature;
	}
}
