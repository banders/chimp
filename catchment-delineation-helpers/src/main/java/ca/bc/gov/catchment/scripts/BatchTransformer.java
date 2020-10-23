package ca.bc.gov.catchment.scripts;

import java.io.IOException;

import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureSource;

public interface BatchTransformer {

	public abstract SimpleFeatureCollection transformBatch(SimpleFeatureSource inFeatureSource) throws IOException;
	
}
