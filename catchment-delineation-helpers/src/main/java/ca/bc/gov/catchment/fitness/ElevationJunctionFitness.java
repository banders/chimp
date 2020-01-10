package ca.bc.gov.catchment.fitness;

import java.io.IOException;
import java.util.List;

import org.locationtech.jts.geom.Coordinate;
import org.opengis.feature.simple.SimpleFeature;

import ca.bc.gov.catchment.improvement.Junction;

public class ElevationJunctionFitness extends JunctionFitness {

	@Override
	public double fitness(Junction junction) throws IOException {
		return junction.getCoordinate().getZ();
	}

}
