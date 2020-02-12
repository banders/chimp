package ca.bc.gov.catchment.tin;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.apache.commons.math3.util.Pair;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.factory.CommonFactoryFinder;
import org.geotools.geometry.jts.JTSFactoryFinder;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.filter.Filter;
import org.opengis.filter.FilterFactory2;

public class TinEdges extends Tin {


	public TinEdges(SimpleFeatureSource tinEdges)  {
		super(tinEdges);
	}

	public TinEdges(SimpleFeatureSource tinEdges, Filter filter)  {
		super(tinEdges, filter);
	}
	
	public SimpleFeature getEdge(Coordinate c1, Coordinate c2) throws IOException  {
		Coordinate coords[] = {c1, c2};
		LineString edgeToFind = geometryFactory.createLineString(coords);
		
		//find the tin edge that overlaps the input edge
		Filter overlapsFilter = filterFactory.overlaps(
				filterFactory.property(geometryPropertyName), 
				filterFactory.literal(edgeToFind)
				);
		SimpleFeatureCollection matches = getFeatures(overlapsFilter);		
		SimpleFeatureIterator matchesIt = matches.features();
		SimpleFeature result = null;
		try {
			if(matchesIt.hasNext()) {
				result = matchesIt.next();
				if (matchesIt.hasNext()) {
					throw new IllegalStateException("found more than one overlapping edge");
				}
			}
		} 
		finally {
			matchesIt.close();
		}
		return result;
	}
	
	/**
	 * The resulting edges are sorted in clockwise order starting with east
	 * @param c
	 * @return
	 * @throws IOException
	 */
	public List<SimpleFeature> getEdgesTouchingCoordinate(final Coordinate c) throws IOException {
		Point p = geometryFactory.createPoint(c);
		List<SimpleFeature> result = new ArrayList<SimpleFeature>();
		
		//find the tin edges that touch the point
		Filter touchesFilter = filterFactory.touches(
				filterFactory.property(geometryPropertyName), 
				filterFactory.literal(p)
				);
		SimpleFeatureCollection matches = getFeatures(touchesFilter);		
		SimpleFeatureIterator matchesIt = matches.features();

		try {
			while(matchesIt.hasNext()) {
				SimpleFeature f = matchesIt.next();
				result.add(f);
			}
		} 
		finally {
			matchesIt.close();
		}
		
		//sort results by angle, clockwise from east
		result.sort(new Comparator<SimpleFeature>() {
			public int compare(SimpleFeature s1, SimpleFeature s2) {
				double angle1 = angle2D(c, (LineString)s1.getDefaultGeometry());
				double angle2 = angle2D(c, (LineString)s2.getDefaultGeometry());
				return angle1 < angle2 ? -1 
					 : angle1 > angle2 ? 1 
				     : 0;
			}			
		});
		
		return result;
	}
	
	/**
	 * gets the angle (in degrees) of the edge starting from 'fromCoord'.  Result is in
	 * [0-360] where 0 is east, 90 is north.
	 * @param fromCoord
	 * @param f
	 * @return
	 */
	private double angle2D(Coordinate fromCoord, LineString edge) {
		Coordinate[] coords = edge.getCoordinates();
		if (coords.length != 2) {
			throw new IllegalArgumentException("edge must have exactly two vertices");
		}
		Coordinate firstCoord = coords[0];
		Coordinate secondCoord = coords[1];
		Coordinate toCoord = null;
		if (firstCoord.equals(fromCoord)) {
			toCoord = secondCoord;
		}
		else if (secondCoord.equals(fromCoord)) {
			toCoord = firstCoord;
		}
		else {
			throw new IllegalArgumentException("'fromCoord' must be in the edge");
		}
		
		double opposite = toCoord.getY() - fromCoord.getY();
		double adjacent = toCoord.getX() - fromCoord.getX();
		double angleDegrees = Math.toDegrees(Math.atan2(opposite, adjacent));
		if (angleDegrees < 0) {
			angleDegrees +=360;
		}
		return angleDegrees;
	}
	
}
