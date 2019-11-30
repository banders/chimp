package ca.bc.gov.catchment.scripts;

import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.feature.DefaultFeatureCollection;
import org.geotools.geometry.jts.JTS;
import org.locationtech.jts.geom.Geometry;
import org.opengis.feature.simple.SimpleFeature;

import ca.bc.gov.catchment.algorithms.MidpointSmoother;
import ca.bc.gov.catchments.utils.SpatialUtils;

public class SmoothGeometries extends CLItoAlgorithmBridge {

	private static final int NUM_SMOOTHING_ITERATIONS = 3;
	
	public static void main(String[] argv) {
		CLItoAlgorithmBridge transformer = new SmoothGeometries();
		transformer.start(argv);
	}

	@Override
	public SimpleFeatureCollection transformBatch(SimpleFeatureCollection inFeatures) {
		MidpointSmoother smoother = new MidpointSmoother();
		DefaultFeatureCollection outFeatures = new DefaultFeatureCollection();
		SimpleFeatureIterator it = inFeatures.features();
		while(it.hasNext()) {
			SimpleFeature inFeature = it.next();
			Geometry inGeometry = (Geometry)inFeature.getDefaultGeometry();
			SimpleFeature outFeature = SpatialUtils.copyFeature(inFeature, inFeature.getFeatureType());
			Geometry outGeometry = smoother.smooth(inGeometry, NUM_SMOOTHING_ITERATIONS);
			outFeature.setDefaultGeometry(outGeometry);
			outFeatures.add(outFeature);
		}
		it.close();
		return outFeatures;
	}
	
	
}
