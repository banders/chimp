package ca.bc.gov.catchment.routes;

import org.locationtech.jts.geom.LineString;

public interface RouteValidator {

	public boolean isValid(LineString line);
}
