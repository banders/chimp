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

/**
 * This junction improver uses no randomness, so results are deterministic.
 * 
 * @author Brock
 *
 */
public class BestInRadiusJunctionImprover extends JunctionImprover {

	private static final int MIN_FREEDOM = 1;
	private static final int MAX_FREEDOM = 10;
	private static final int FREEDOM_STEP = 2;
	
	private CatchmentValidity catchmentValidityChecker;
	private CatchmentLines catchmentLines;
	private TinEdges tinEdges;
	private JunctionFitness fitnessFinder;
	private double radius;
	private WaterAwareCatchmentRouter router;
	private ImprovementCoverage improvementCoverage;
	
	public BestInRadiusJunctionImprover(
			CatchmentLines catchmentLines,
			TinEdges tinEdges,
			SimpleFeatureSource waterFeatures,			
			JunctionFitness fitnessFinder, 
			double radius) throws IOException {
		this.tinEdges = tinEdges;
		this.catchmentLines = catchmentLines;
		this.fitnessFinder = fitnessFinder;
		this.catchmentValidityChecker = new CatchmentValidity(waterFeatures);
		this.router = new WaterAwareCatchmentRouter(tinEdges, new WaterAnalyzer(waterFeatures));
		this.radius = radius;
		this.improvementCoverage = new ImprovementCoverage(tinEdges.getPointCloud());
	}
	
	@Override
	public JunctionModification improve(Junction originalJunction) throws IOException {
		
		ImprovementMetrics metrics = new ImprovementMetrics();
		metrics.incrementNumImprovementRequests();
		Date start = new Date();
		
		double originalFit = fitnessFinder.fitness(originalJunction);
		System.out.println("trying to improve junction: "+originalJunction.getID());
		System.out.println("originalFitness: "+originalFit);
		
		JunctionModification bestModification = new JunctionModification(originalJunction);
		bestModification.setImprovementMetrics(metrics);
		
		if (originalJunction.getDegree() == 0) {
			System.out.println(" no sections touch this junction");
			return bestModification;
		}
		if (originalJunction.getDegree() < 3) {
			System.out.println(" invalid junction.  degree must be >= 3.  found: "+originalJunction.getDegree());
			return bestModification;
		}
		
		//test all junctions in radius.  for each junction tested, also try several different
		//"freedom" values.
		List<Coordinate> coordsToTest = tinEdges.getCoordsInRadius(originalJunction.getCoordinate(), radius);
		int numFreedomValues = (MAX_FREEDOM - MIN_FREEDOM) / FREEDOM_STEP;
		System.out.println(" testing "+coordsToTest.size()+ " neighbours for "+numFreedomValues+" freedom values ("+(coordsToTest.size()*numFreedomValues)+" total tests)");
		
		double bestFit = originalFit;
		for (int freedom = MIN_FREEDOM; freedom < MAX_FREEDOM; freedom+=FREEDOM_STEP) {
			for (Coordinate neighbourCoord : coordsToTest) {			
				List<SimpleFeature> newSections = null;
				try {
					newSections = router.rerouteFeatures(originalJunction.getTouchingSections(), originalJunction.getCoordinate(), neighbourCoord, freedom);
				} catch (RouteException e) {
					System.out.println(" neighbour invalid.  skipping");
					continue;
				}
				
				Junction neighbourJunction = new Junction(neighbourCoord, newSections);
				double neighbourFit = fitnessFinder.fitness(neighbourJunction);
				System.out.println(" neighbour fitness: "+neighbourFit);
				if (neighbourFit < bestFit) {
					continue;
				}
				
				//validate the new sections.  if invalid, skip the route and try again
				//Performance Note: catchmentValidityChecker.areSectionsValidWrtWater is slowest operation
				//within the improve() function.  It takes >50% of the processing time.
				boolean isValidWrtWater = catchmentValidityChecker.areSectionsValidWrtWater(newSections);
				if (!isValidWrtWater) {
					System.out.println("  neighbour rejected.  invalid w.r.t water");
					continue;
				}
				
				boolean isValidWrtCatchments = 
						catchmentValidityChecker.areSectionsValidWrtCatchments(newSections, catchmentLines.getUpdatedFeatures());
				if (!isValidWrtCatchments) {
					System.out.println("  neighbour rejected.  invalid w.r.t catchments");
					continue;
				}
				
				bestModification = new JunctionModification(originalJunction);
				bestModification.setModifiedJunction(neighbourJunction);
				bestFit = neighbourFit;
				
			}
		}
		 
		
		if (!originalJunction.equals(bestModification.getModifiedJunction())) {
			metrics.incrementNumImproved();
			System.out.println(" improved junction from "+originalFit+" to "+bestFit);
		}
		else {
			System.out.println(" junction could not be improved");
		}
		
		Date end = new Date();
		long runtimeMs = end.getTime() - start.getTime();
		metrics.setRuntimeMs(runtimeMs);
		bestModification.setImprovementMetrics(metrics);
		
		return bestModification;
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


	
}
