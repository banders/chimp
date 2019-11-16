package ca.bc.gov.catchment.improvement;

import java.io.IOException;

import org.opengis.feature.simple.SimpleFeature;

public abstract class SectionImprover {

	public abstract SectionModification improve(SimpleFeature section) throws IOException;
	
}
