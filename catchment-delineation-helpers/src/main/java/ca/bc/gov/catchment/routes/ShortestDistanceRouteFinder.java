package ca.bc.gov.catchment.routes;

import org.geotools.geometry.jts.JTSFactoryFinder;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;

public class ShortestDistanceRouteFinder implements RouteFinder {

	private Point endPoint;
	private GeometryFactory geometryFactory; 
	
	public ShortestDistanceRouteFinder(Coordinate endCoord) {
		this.geometryFactory = JTSFactoryFinder.getGeometryFactory();
		this.endPoint = geometryFactory.createPoint(endCoord);
	}
	
	public double getFitness(LineString segment) {
		if (segment == null) {
			throw new NullPointerException("segment must not be null.");
		}
		double distance = segment.distance(endPoint);
		double fitness = distance * -1;
		return fitness;
	}
	
}
