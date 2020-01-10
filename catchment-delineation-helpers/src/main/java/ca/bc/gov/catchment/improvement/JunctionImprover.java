package ca.bc.gov.catchment.improvement;

import java.io.IOException;

import org.locationtech.jts.geom.Coordinate;
import org.opengis.feature.simple.SimpleFeature;

public abstract class JunctionImprover {

	public abstract JunctionModification improve(Junction junction) throws IOException;
	
}
