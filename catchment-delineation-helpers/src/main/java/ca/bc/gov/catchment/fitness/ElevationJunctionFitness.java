package ca.bc.gov.catchment.fitness;

import java.io.IOException;
import java.util.List;

import org.locationtech.jts.geom.Coordinate;
import org.opengis.feature.simple.SimpleFeature;

public class ElevationJunctionFitness extends JunctionFitness {

	@Override
	public double fitness(Coordinate junction, List<SimpleFeature> touchingSections) throws IOException {
		return junction.getZ();
	}

}
