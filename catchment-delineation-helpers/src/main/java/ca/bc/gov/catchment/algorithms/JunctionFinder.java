package ca.bc.gov.catchment.algorithms;

import java.util.ArrayList;
import java.util.List;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.LineString;
import org.opengis.feature.simple.SimpleFeature;

import ca.bc.gov.catchment.utils.SpatialUtils;

/**
 * Provides methods to find the junction between two lines.  Each pair of lines
 * will have at most one junction.
 * @author Brock
 *
 */
public class JunctionFinder {

	/**
	 * Find the one (at most) junction between f1 and f2.  If no junction is found, returns null.
	 * The junction is defined as the first coordinate
	 * of the first segment of f2 that overlaps any part of f1.  
	 * This rule doesn't count other intersections between f1 and f2
	 * @param f1
	 * @param f2
	 * @return
	 */
	public List<Coordinate> getJunctions(SimpleFeature f1, SimpleFeature f2) {
		LineString g1 = (LineString)f1.getDefaultGeometry();
		LineString g2 = (LineString)f2.getDefaultGeometry();
		List<Coordinate> junctions1 = getJunctionsOneWay(g1, g2);
		List<Coordinate> junctions2 = getJunctionsOneWay(g2, g1);
		
		List<Coordinate> allJunctions = new ArrayList<Coordinate>();
		allJunctions.addAll(junctions1);
		for (Coordinate c : junctions2) {
			if (!allJunctions.contains(c)) {
				allJunctions.add(c);
			}
		}
		return allJunctions;
	}
	
	public Coordinate getJunctionOld(LineString g1, LineString g2) {
		LineSegmenter segmenter = new LineSegmenter();
		List<LineString> s2 = segmenter.segment(g2);
		
		for(LineString segment : s2) {
			if (segment.coveredBy(g1)) {
				Coordinate firstCoordOfSegment = segment.getCoordinateN(0);
				return firstCoordOfSegment;
			}
		}
		return null;
	}
	
	public Coordinate getJunctionOld2(LineString g1, LineString g2) {
		
		for(Coordinate c2 : g2.getCoordinates()) {
			boolean contains = SpatialUtils.hasCoordinate(g1, c2);
			if (contains) {
				return c2;
			}
		}
		return null;
	}
	
	/**
	 * This function doesn't necessarily get all junctions between g1 and g2.
	 * To ensure all junctions are found, also call again with g2 and g1 switched  
	 * @param g1
	 * @param g2
	 * @return
	 */
	public List<Coordinate> getJunctionsOneWay(LineString g1, LineString g2) {
		
		List<Coordinate> junctions = new ArrayList<Coordinate>();
		
		for(int i = 0; i < g1.getNumPoints(); i++) {
			Coordinate prev2 = i >= 1 ? g1.getCoordinateN(i-1) : null;
			Coordinate coord2 = g1.getCoordinateN(i);
			Coordinate next2 = i < g1.getNumPoints()-1 ? g1.getCoordinateN(i+1) : null;
			boolean containsPrev = SpatialUtils.hasCoordinate(g2, prev2);
			boolean containsCoord = SpatialUtils.hasCoordinate(g2, coord2);
			boolean containsNext = SpatialUtils.hasCoordinate(g2, next2);
			boolean atLeastOneAdjacentUnconnected = !containsPrev || !containsNext;
			if (containsCoord && atLeastOneAdjacentUnconnected) {
				junctions.add(coord2);
			}
			
		}
		return junctions;
	}
}
