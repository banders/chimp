package ca.bc.gov.catchment.fitness;

import java.io.IOException;
import java.util.Comparator;

import org.geotools.geometry.jts.JTSFactoryFinder;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;

public abstract class PointFitness {

	public double fitness(Coordinate c) throws IOException {
		GeometryFactory geometryFactory = JTSFactoryFinder.getGeometryFactory();
		Point p = geometryFactory.createPoint(c);
		return fitness(p);
	}
	
	public abstract double fitness(Point c) throws IOException;
	
	public static Comparator<Point> getPointComparator(final PointFitness pf) {
		Comparator<Point> c = new Comparator<Point>() {
		
			public int compare(Point p1, Point p2) {
				double fitness1;
				double fitness2;
				try {
					fitness1 = pf.fitness(p1);
					fitness2 = pf.fitness(p2);
				} catch (IOException e) {
					throw new RuntimeException("unable to determine fitness");
				}
				return fitness1 > fitness2 ? -1 
						 : fitness1 < fitness2 ? 1 
					     : 0;
			}
			
		};
		return c;
	}
	
	public static Comparator<Coordinate> getCoordinateComparator(final PointFitness pf) {
		Comparator<Coordinate> c = new Comparator<Coordinate>() {
		GeometryFactory geometryFactory = JTSFactoryFinder.getGeometryFactory();
		
			public int compare(Coordinate c1, Coordinate c2) {
				double fitness1;
				double fitness2;
				Point p1 = geometryFactory.createPoint(c1);
				Point p2 = geometryFactory.createPoint(c2);
				try {
					fitness1 = pf.fitness(p1);
					fitness2 = pf.fitness(p2);
				} catch (IOException e) {
					throw new RuntimeException("unable to determine fitness");
				}
				return fitness1 > fitness2 ? -1 
						 : fitness1 < fitness2 ? 1 
					     : 0;
			}
			
		};
		return c;
	}
}
