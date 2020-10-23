package ca.bc.gov.catchment.scripts;

import org.geotools.data.simple.SimpleFeatureSource;
import org.opengis.feature.simple.SimpleFeature;

public interface StreamingTransformer {

	public abstract void streamingTransform(SimpleFeatureSource inFeatureSource);
	
	public abstract void onFeatureReady(SimpleFeature feature);
}
