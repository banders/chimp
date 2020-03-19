package ca.bc.gov.catchment.scripts;

import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.feature.DefaultFeatureCollection;
import org.geotools.geometry.jts.JTS;
import org.locationtech.jts.geom.Geometry;
import org.opengis.feature.simple.SimpleFeature;

import ca.bc.gov.catchment.algorithms.EliminationSmoother;
import ca.bc.gov.catchment.algorithms.MidpointSmoother;
import ca.bc.gov.catchment.algorithms.Smoother;
import ca.bc.gov.catchment.utils.SpatialUtils;

public class SmoothGeometries extends CLItoAlgorithmBridge {

	private static final String DEFAULT_ALG = "midpoint";
	
	public static void main(String[] argv) {
		CLItoAlgorithmBridge transformer = new SmoothGeometries();
		
		Options customOptions = new Options();
		customOptions.addOption(new Option("alg", true, "algorithm.  one of [elimination,midpoint]"));
		
		transformer.start(argv, customOptions);
		
	}

	@Override
	public SimpleFeatureCollection transformBatch(SimpleFeatureCollection inFeatures) {
		String alg = this.getOptionValue("alg", DEFAULT_ALG);
		Smoother smoother = null;
		int numIterations = 1;
		if (alg.equals("midpoint")) {
			numIterations = 3;
			smoother = new MidpointSmoother();
		}
		else if (alg.equals("elimination")) {
			numIterations = 1;
			smoother = new EliminationSmoother();
		}
		else {
			throw new IllegalArgumentException("unknown smoothing algorithm:");
		}
		System.out.println("smoothing algorithm: "+ alg);
		
		DefaultFeatureCollection outFeatures = new DefaultFeatureCollection();
		SimpleFeatureIterator it = inFeatures.features();
		int numSmoothed = 0;
		while(it.hasNext()) {
			SimpleFeature inFeature = it.next();
			Geometry inGeometry = (Geometry)inFeature.getDefaultGeometry();
			SimpleFeature outFeature = SpatialUtils.copyFeature(inFeature, inFeature.getFeatureType());
			Geometry outGeometry = smoother.smooth(inGeometry, numIterations);
			
			if (!inGeometry.equals(outGeometry)) {
				numSmoothed++;
			}
			
			outFeature.setDefaultGeometry(outGeometry);
			outFeatures.add(outFeature);
		}
		it.close();
		
		System.out.println("finished.  smoothed "+numSmoothed+" of "+inFeatures.size());
		return outFeatures;
	}
	
	
}
