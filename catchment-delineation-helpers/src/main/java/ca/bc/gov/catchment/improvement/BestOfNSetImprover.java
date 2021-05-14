package ca.bc.gov.catchment.improvement;

import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Logger;

import org.geotools.data.collection.SpatialIndexFeatureCollection;
import org.geotools.data.collection.SpatialIndexFeatureSource;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.factory.CommonFactoryFinder;
import org.geotools.feature.SchemaException;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.locationtech.jts.geom.Coordinate;
import org.opengis.feature.simple.SimpleFeature;

import ca.bc.gov.catchment.CatchmentLines;
import ca.bc.gov.catchment.fitness.CatchmentValidity;
import ca.bc.gov.catchment.fitness.SectionFitness;
import ca.bc.gov.catchment.water.Water;

public class BestOfNSetImprover extends SetImprover {
	
	private static Logger LOG = Logger.getAnonymousLogger();

	private static int MIN_NO_IMPROVEMENT_COUNT_TO_SKIP_JUNCTION = 2;
	private static int MIN_NO_IMPROVEMENT_COUNT_TO_SKIP_SECTION = 2;
	private static int MAX_JUNCTION_ITERATIONS = 1;
	private static int NUM_TESTS_PER_JUNCTION = 30;
	private static int MAX_SECTION_ITERATIONS = 1;
	private static int MIN_TESTS_PER_SECTION = 5;
	private static int MAX_TESTS_PER_SECTION = 10;
	
	private Water waterAnalyzer;
	private JunctionImprover junctionImprover;
	private SectionImprover sectionImprover;
	private SectionFitness globalFitness;
	private int n;
	private CatchmentLines bestResult;
	private CatchmentLines randomResult;
	private double bestGlobalFitness;
	
	public BestOfNSetImprover(
			Water waterAnalyzer,
			SectionImprover sectionImprover,
			JunctionImprover junctionImprover,
			int n) throws IOException {
		this.waterAnalyzer = waterAnalyzer;
		this.sectionImprover = sectionImprover;
		this.junctionImprover = junctionImprover;
		this.globalFitness = sectionImprover.getSectionFitness();
		this.n = n;
		reset();
	}
	
	@Override
	protected CatchmentLines improveImpl(CatchmentLines catchmentLines) throws IOException {
		reset();
		double initialFitness = checkGlobalFitness(catchmentLines.getUpdatedFeatures());
		this.bestGlobalFitness = initialFitness;
		SimpleFeatureCollection bestSet = catchmentLines.getUpdatedFeatures();
		SimpleFeatureCollection randomSet = bestSet;
		
		LOG.info("best of "+n+", initial set fitness: "+initialFitness);
		
		long r = (long)Math.floor(Math.random() * n);
		
		Date overallStart = new Date();
		for (int i = 0; i < n; i++) {
			Date iterationStart = new Date();			
			LOG.info("set "+(i+1)+" of "+n);
			SimpleFeatureCollection iterationResult = improveIteration(catchmentLines);
			double iterationGlobalFitness = checkGlobalFitness(iterationResult);
			Date iterationEnd = new Date();
			long iterationTime = (iterationEnd.getTime() - iterationStart.getTime())/1000;
			LOG.info(" set fitness: "+iterationGlobalFitness +", run time: "+iterationTime+"s");
			if (iterationGlobalFitness > bestGlobalFitness) {
				bestSet = iterationResult;
				bestGlobalFitness = iterationGlobalFitness;
			}
			if (i == r) {
				randomSet = iterationResult;
			}
		}
		Date overallEnd = new Date();
		long overallTime = (overallEnd.getTime() - overallStart.getTime())/1000;
		LOG.info("best set fitness: "+bestGlobalFitness +", run time: "+overallTime+"s");
		
		this.bestResult = toCatchmentLines(bestSet);
		this.randomResult = toCatchmentLines(randomSet);
		
		return this.bestResult;
	}
	
	public CatchmentLines getRandomSet() {
		return this.randomResult;
	}
	
	public CatchmentLines getBestSet() {
		return this.bestResult;
	}
	
	private SimpleFeatureCollection improveIteration(CatchmentLines catchmentLinesOriginal) throws IOException {
		resetShortCircuitStatistics();
		Date start = new Date();
		SimpleFeatureCollection outFeatureCollection = null;
		
		CatchmentLines catchmentLines = catchmentLinesOriginal.copy();
		
		try {

			
			LOG.fine("improving junctions...");
			for(int iterationNum = 1; iterationNum <= MAX_JUNCTION_ITERATIONS; iterationNum++) {
				
				//double roundStartGlobalFitness = globalFitness.fitnessAvg(catchmentLines.getUpdatedFeatures());
				
				ImprovementMetrics metrics = improveJunctions(catchmentLines, NUM_TESTS_PER_JUNCTION);
				
				/*
				String outFilename1 = outputGeopackageFilename.replace(".gpkg", "-j"+iterationNum+".gpkg");
				SimpleFeatureCollection outFeatureCollection1 = catchmentLines.getUpdatedFeatures();
				outFeatureCollection1 = SpatialUtils.renameFeatureType(outFeatureCollection1, outTable);
				SaveUtils.saveToGeoPackage(outFilename1, outFeatureCollection1, true);
				*/
				
				//double roundEndGlobalFitness = globalFitness.fitnessAvg(catchmentLines.getUpdatedFeatures());
				//double percentImprovementThisRound = (roundEndGlobalFitness - roundStartGlobalFitness)/roundEndGlobalFitness * 100;
				/*
				System.out.println("junction round "+iterationNum+" complete");
				System.out.println(" improvements: ");
				System.out.println("  - # requested: "+metrics.getNumImprovementRequests());
				System.out.println("  - # succeeded: "+metrics.getNumImproved());
				System.out.println(" alternatives tested: ");
				System.out.println("  - # total: "+metrics.getNumAlternativesTested());
				System.out.println("  - # valid: "+metrics.getNumValidAlternativesTested());
				System.out.println(" runtime: ");
				System.out.println("  - total for all improvement requests: "+metrics.getRuntimeMs()+ " ms");
				System.out.println("  - average per improvement request: "+metrics.getAvgRuntimeMsPerTest()+ " ms");
				System.out.println("  - average per improvement request alternative: "+metrics.getAvgRuntimeMsPerTest()+ " ms");				
				System.out.println(" global fitness:");
				System.out.println("  - start: "+roundStartGlobalFitness);
				System.out.println("  - end: "+roundEndGlobalFitness);
				System.out.println("  - improved by: "+percentImprovementThisRound+"%");
				*/
				
				if(metrics.getNumImprovementRequests() == 0) {
					LOG.fine("Junction improvements no improvements were requested in the previous round");
					break;
				}
				/*
				if (percentImprovementThisRound < MIN_JUNCTION_IMPROVEMENT_PERCENT) {
					System.out.println("Junction improvements halted because latest improvement < "+MIN_JUNCTION_IMPROVEMENT_PERCENT);
					break;
				}
				*/
			}
				
			LOG.fine("improving sections...");
			
			for(int iterationNum = 1; iterationNum <= MAX_SECTION_ITERATIONS; iterationNum++) {
				int numSteps = MAX_TESTS_PER_SECTION;
				if (iterationNum < 5) {
					//ramp up number of tests for the first 5 rounds
					numSteps = Math.min(MIN_TESTS_PER_SECTION * iterationNum, MAX_TESTS_PER_SECTION);
				}
				LOG.fine("round "+iterationNum+". ("+ numSteps +" tests per section)");
				
				//double roundStartGlobalFitness = globalFitness.fitnessAvg(catchmentLines.getUpdatedFeatures());
				
				ImprovementMetrics metrics = improveSections(catchmentLines, numSteps);
				
				//double roundEndGlobalFitness = globalFitness.fitnessAvg(catchmentLines.getUpdatedFeatures());
				//double percentImprovementThisRound = (roundEndGlobalFitness - roundStartGlobalFitness)/roundEndGlobalFitness * 100;			
				/*
				System.out.println("section round "+iterationNum+" complete");
				System.out.println(" round settings: ");
				System.out.println("  - # steps per section: "+numSteps);
				System.out.println(" improvements: ");
				System.out.println("  - # requested: "+metrics.getNumImprovementRequests());
				System.out.println("  - # succeeded: "+metrics.getNumImproved());
				System.out.println(" alternatives tested: ");
				System.out.println("  - # total: "+metrics.getNumAlternativesTested());
				System.out.println("  - # valid: "+metrics.getNumValidAlternativesTested());				
				System.out.println(" runtime: ");
				System.out.println("  - total for all improvement requests: "+metrics.getRuntimeMs()+ " ms");
				System.out.println("  - average per improvement request: "+metrics.getAvgRuntimeMsPerRequest()+ " ms");
				System.out.println("  - average per improvement request alternative: "+metrics.getAvgRuntimeMsPerTest()+ " ms");				
				System.out.println(" global fitness:");
				System.out.println("  - start: "+roundStartGlobalFitness);
				System.out.println("  - end: "+roundEndGlobalFitness);
				System.out.println("  - improved by: "+percentImprovementThisRound+"%");
				*/
				
				if(metrics.getNumImprovementRequests() == 0) {
					LOG.fine("Section improvements - no improvements were requested in the previous round");
					break;
				}
				/*
				if (percentImprovementThisRound < MIN_SECTION_IMPROVEMENT_PERCENT) {
					System.out.println("section improvements halted because latest improvement < "+MIN_SECTION_IMPROVEMENT_PERCENT);
					break;
				}
				*/
			}
			
			Date end = new Date();
			
			//saving improved catchments
			//-----------------------------------------------------------------
			outFeatureCollection = catchmentLines.getUpdatedFeatures();
			//outFeatureCollection = SpatialUtils.renameFeatureType(outFeatureCollection, catchmentLines.getSchema().getTypeName());
			
			//System.out.println("initial avg catchment elevation:"+initialFitness);
			double finalFitness = globalFitness.fitnessAvg(catchmentLines.getUpdatedFeatures());
			//System.out.println("final avg catchment fitness:"+finalFitness);
			
			double runtimeMinutes = (end.getTime() - start.getTime())/(1000.0*60);
			LOG.fine("run time (minutes): "+runtimeMinutes); //minutes
			
		} catch(IOException e) {
			e.printStackTrace();
			System.exit(1);
		}
		
		LOG.fine("All done");
		return outFeatureCollection;
		
	}
	
	private void reset() {
		bestResult = null;
		randomResult = null;
		bestGlobalFitness = Double.NaN;
	}

	private ImprovementMetrics improveJunctions(CatchmentLines catchmentLines, int numSteps) throws IOException {
		ImprovementMetrics metricsTotal = new ImprovementMetrics();
			
		List<Coordinate> junctions = catchmentLines.getJunctions(waterAnalyzer);
				
		LOG.info("improving "+junctions.size()+" junctions");
		for(Coordinate junctionCoord : junctions) {
			
			List<SimpleFeature> touchingSections = catchmentLines.getSectionsTouchingJunction(junctionCoord);
			Junction junction = new Junction(junctionCoord, touchingSections);
			
			LOG.finer("---");
			if (getNoImprovementCount(junction) >= MIN_NO_IMPROVEMENT_COUNT_TO_SKIP_JUNCTION) {
				//System.out.println("skipping. this junction has "+getNoImprovementCount(junction)+" prior no-improvement events");
				continue;
			}
			JunctionModification modification = null;
			try {
				modification = junctionImprover.improve(junction, catchmentLines);
				metricsTotal.merge(modification.getImprovementMetrics());
			}
			catch(Exception e) {
				e.printStackTrace();
			}
			 
			if (modification != null && modification.isModified()) {
				resetNoImprovementCount(junction);
				LOG.finer("  moved junction "+modification.getOriginalJunction().getCoordinate()+" to "+modification.getModifiedJunction().getCoordinate());
				for (SimpleFeature touchingSection : modification.getModifiedJunction().getTouchingSections()) {
					//System.out.println("  updated section with fid="+touchingSection.getIdentifier());
					catchmentLines.addOrUpdate(touchingSection);
				}
			} else {
				incrementNoImprovementCount(junction);
			}
		}
		
		return metricsTotal;
	}
	
	private ImprovementMetrics improveSections(CatchmentLines catchmentLines, int numSteps) throws IOException {
		
		ImprovementMetrics metricsTotal = new ImprovementMetrics();
		
		//note: iterate over "original features" so to avoid concurrent modification exception.
		//(we lookup the latest version of each original feature below)
		SimpleFeatureCollection sections = catchmentLines.getOriginalFeatures(); 
		SimpleFeatureIterator sectionIt = sections.features();
		LOG.info("improving "+sections.size()+" sections");
		while(sectionIt.hasNext()) {
			LOG.finer("---");
			SimpleFeature section = sectionIt.next();	
			if (getNoImprovementCount(section) >= MIN_NO_IMPROVEMENT_COUNT_TO_SKIP_SECTION) {
				//System.out.println("skipping.  assume this section cannot be improved further.");
				continue;
			}
			section = catchmentLines.getLatest(section);
			SectionModification modification = null;
			try {
				modification = sectionImprover.improve(section, catchmentLines);
				metricsTotal.merge(modification.getImprovementMetrics());
			} 
			catch (Exception e) {
				//System.out.println("no improvements were possible. ");
				e.printStackTrace();
			}
			
			if (modification != null && modification.isModified()) {
				catchmentLines.addOrUpdate(modification.getModifiedSection());
				resetNoImprovementCount(section);
				for (SimpleFeature touchingSection : modification.getModifiedTouchingSections()) {
					//System.out.println(" updated section with fid="+touchingSection.getIdentifier());
					catchmentLines.addOrUpdate(touchingSection);
					throw new IllegalStateException("This should never occur -- touching sections don't get modified by SectionImprover");
				}	
			}
			else {
				incrementNoImprovementCount(section);
			}
			
		}
		sectionIt.close();		
		
		return metricsTotal;
	}

	@Override
	public SectionFitness getGlobalFitness() {
		return sectionImprover.getSectionFitness();
	}
	
}
