package ca.bc.gov.catchment.routes;

import org.locationtech.jts.geom.LineString;

public interface RouteFinder {
	
	/**
	 * must return higher numbers for more fit segments and lower numbers for less fit segments
	 * @param segment
	 * @return
	 */
	public double getFitness(LineString segment);
	
	
}
