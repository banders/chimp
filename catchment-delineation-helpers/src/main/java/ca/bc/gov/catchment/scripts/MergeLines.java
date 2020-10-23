package ca.bc.gov.catchment.scripts;

import java.io.IOException;

import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureSource;

import ca.bc.gov.catchment.algorithms.MergeLinesAlg;

public class MergeLines extends CLItoAlgorithmBridge {

	public static void main(String[] argv) {
		CLItoAlgorithmBridge transformer = new MergeLines();
		transformer.start(argv);
	}

	@Override
	public SimpleFeatureCollection transformBatch(SimpleFeatureSource inFeatureSource) throws IOException {
		SimpleFeatureCollection inFeatures = applyDefaultFilter(inFeatureSource);
		MergeLinesAlg alg = new MergeLinesAlg(inFeatures, getOutTable(), getInSrid());
		return alg.getResult();
	}

	
}
