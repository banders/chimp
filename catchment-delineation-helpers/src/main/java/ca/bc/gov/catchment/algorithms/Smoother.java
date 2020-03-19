package ca.bc.gov.catchment.algorithms;

import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;

public abstract class Smoother {

	public Smoother() {
	
	}

	public LineString smooth(Geometry g, int iterations) {
		if (g.getGeometryType() == "LineString") {
			return smooth((LineString)g, iterations);
		}
		throw new IllegalStateException("Unsupported geometry type: "+g.getGeometryType());
	}
	
	
	public abstract LineString smooth(LineString g, int iterations);
}