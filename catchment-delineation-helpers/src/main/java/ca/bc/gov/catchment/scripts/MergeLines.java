package ca.bc.gov.catchment.scripts;

import org.geotools.data.simple.SimpleFeatureCollection;

import algorithms.MergeLinesAlg;

public class MergeLines extends CLItoAlgorithmBridge {

	public static void main(String[] argv) {
		CLItoAlgorithmBridge transformer = new MergeLines();
		transformer.start(argv);
	}

	@Override
	public SimpleFeatureCollection transformBatch(SimpleFeatureCollection inFeatures) {
		MergeLinesAlg alg = new MergeLinesAlg(inFeatures, getOutTable(), getInSrid());
		return alg.getResult();
	}
	
}
