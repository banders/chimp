package ca.bc.gov.catchment.scripts;

import org.geotools.data.simple.SimpleFeatureCollection;

public interface BatchTransformer {

	public abstract SimpleFeatureCollection transformBatch(SimpleFeatureCollection inFeatures); 
}
