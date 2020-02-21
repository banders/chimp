package ca.bc.gov.catchment.fitness;

import java.io.IOException;
import java.util.Comparator;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Point;

public class ElevationPointFitness extends PointFitness {

	@Override
	public double fitness(Point p) throws IOException {
		Coordinate c = p.getCoordinate();
		return c.getZ();
	}

	public static Comparator<Coordinate> getCoordinateComparator() {
		PointFitness pf = new ElevationPointFitness();
		return getCoordinateComparator(pf);
	}
}
