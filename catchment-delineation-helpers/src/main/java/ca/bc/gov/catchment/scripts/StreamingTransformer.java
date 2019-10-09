package ca.bc.gov.catchment.scripts;

import org.geotools.data.simple.SimpleFeatureCollection;
import org.opengis.feature.simple.SimpleFeature;

public interface StreamingTransformer {

	public abstract void streamingTransform(SimpleFeatureCollection inFeatures);
	
	public abstract void onFeatureReady(SimpleFeature feature);
}
