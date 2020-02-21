package ca.bc.gov.catchment.fitness;

import java.io.IOException;
import java.util.Comparator;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Point;
import org.opengis.feature.simple.SimpleFeature;

/**
 * Defines a fitness function which is largest when the given point is
 * is equal distance between two other geometries  
 * 
 * @author Brock
 *
 */
public class EquidistantPointFitness extends PointFitness {

	private Geometry g1;
	private Geometry g2;
	
	public EquidistantPointFitness(SimpleFeature f1, SimpleFeature f2) {
		this((Geometry)f1.getDefaultGeometry(), (Geometry)f2.getDefaultGeometry());
	}
	
	public EquidistantPointFitness(Geometry g1, Geometry g2) {
		this.g1 = g1; 
		this.g2 = g2;
	}
	
	@Override
	public double fitness(Point p) throws IOException {
		double dist1 = p.distance(g1);
		double dist2 = p.distance(g2);
		double imbalance = Math.abs(dist1 - dist2);
		double fitness = 1 / imbalance;
		return fitness;
	}
	
	public static Comparator<Coordinate> getCoordinateComparator(final SimpleFeature f1, final SimpleFeature f2) {
		final PointFitness pf = new EquidistantPointFitness(f1, f2);
		return getCoordinateComparator(pf);
	}

}
