package ca.bc.gov.catchment.improvement;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.geotools.data.collection.SpatialIndexFeatureCollection;
import org.geotools.data.collection.SpatialIndexFeatureSource;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.filter.identity.FeatureId;

import ca.bc.gov.catchment.CatchmentLines;
import ca.bc.gov.catchment.fitness.SectionFitness;

public abstract class SetImprover {

	private static Map<FeatureId, Integer> sectionNoImprovementCount;
	private static Map<String, Integer> junctionNoImprovementCount;
	
	public SetImprover() {
	}
	
	protected void resetShortCircuitStatistics() {
		sectionNoImprovementCount = new HashMap<FeatureId, Integer>();
		junctionNoImprovementCount = new HashMap<String, Integer>();
	}
	
	public CatchmentLines improve(CatchmentLines catchmentLines) throws IOException {
		resetShortCircuitStatistics();
		return improveImpl(catchmentLines);
	}
	
	protected abstract CatchmentLines improveImpl(CatchmentLines catchmentLines) throws IOException;
	
	
	public abstract SectionFitness getGlobalFitness();
	
	public double checkGlobalFitness(SimpleFeatureCollection fc) throws IOException {
		return getGlobalFitness().fitnessAvg(fc);
	}
	
	protected CatchmentLines toCatchmentLines(SimpleFeatureCollection fc) throws IOException {
		SpatialIndexFeatureCollection fastFc = new SpatialIndexFeatureCollection(fc);
		SpatialIndexFeatureSource fastFs = new SpatialIndexFeatureSource(fastFc);
		CatchmentLines result = new CatchmentLines(fastFs);
		return result;
	}
	
	// ------------------------------------------------------------------------
	// Functions to support testing of the end condition
	// ------------------------------------------------------------------------
		
	protected void incrementNoImprovementCount(Junction junction) {
		String key = junction.getID();
		if(junctionNoImprovementCount.containsKey(key)) {
			int count = junctionNoImprovementCount.get(key);
			count++;
			junctionNoImprovementCount.put(key, count);
		}
		else {
			junctionNoImprovementCount.put(key, 1);
		}
	}
	
	protected void resetNoImprovementCount(Junction junction) {
		String key = junction.getID();
		junctionNoImprovementCount.put(key, 0);
	}
	
	protected void incrementNoImprovementCount(SimpleFeature section) {
		FeatureId key = section.getIdentifier();
		if(sectionNoImprovementCount.containsKey(key)) {
			int count = sectionNoImprovementCount.get(key);
			count++;
			sectionNoImprovementCount.put(key, count);
		}
		else {
			sectionNoImprovementCount.put(key, 1);
		}
	}
	
	protected void resetNoImprovementCount(SimpleFeature section) {
		FeatureId key = section.getIdentifier();
		sectionNoImprovementCount.put(key, 0);
	}
	
	protected int getNoImprovementCount(Junction junction) {
		String key = junction.getID();
		if(junctionNoImprovementCount.containsKey(key)) {
			return junctionNoImprovementCount.get(key);
		}
		return 0;
	}
	
	protected int getNoImprovementCount(SimpleFeature section) {
		FeatureId key = section.getIdentifier();
		if(sectionNoImprovementCount.containsKey(key)) {
			return sectionNoImprovementCount.get(key);
		}
		return 0;
	}
}
