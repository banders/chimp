package algorithms;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.factory.CommonFactoryFinder;
import org.geotools.factory.Hints;
import org.geotools.geometry.jts.JTSFactoryFinder;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.filter.Filter;
import org.opengis.filter.FilterFactory2;

import ca.bc.gov.catchments.utils.SpatialUtils;

/**
 * This class provides an API for re-routing a linestring between two points
 * by providing one or more additional points that must be included in the route
 * @author Brock
 *
 */
public class LineStringRouter {

	private SimpleFeatureSource tinEdges;
	private SimpleFeatureType tinEdgesFeatureType;
	private String tinEdgesGeometryProperty;
	private GeometryFactory geometryFactory; 
	private FilterFactory2 filterFactory2D;
	private boolean allowSelfIntersection;
	private boolean allowRepeatedCoords;
	
	public LineStringRouter(SimpleFeatureSource tinEdges) {
		this.tinEdges = tinEdges;
		this.allowSelfIntersection = true;
		this.allowRepeatedCoords = false;
		this.tinEdgesFeatureType = tinEdges.getSchema();
		this.tinEdgesGeometryProperty = tinEdgesFeatureType.getGeometryDescriptor().getLocalName();
		this.geometryFactory = JTSFactoryFinder.getGeometryFactory();
		
		Hints filterHints = new Hints( Hints.FEATURE_2D, true ); // force 2D queries
		this.filterFactory2D = CommonFactoryFinder.getFilterFactory2(filterHints);
	}
	
	/**
	 * @param required all coordinates that must be included in the result.  at a minimum,
	 * should include a start point and an end point. may also include coordinates
	 * in between the start and end. all coordinate must exactly match a point in the TIN.
	 * @throws IOException 
	 */
	public LineString makeRoute(Coordinate[] required) throws IOException {
		LineString result = null;
		Coordinate prev = null;
		for(Coordinate coord: required) {
			if (prev != null) {
				List<Coordinate> routeSoFar = null;
				if (result != null) {
					routeSoFar = toCoordinateList(result.getCoordinates());
				}
				LineString partial = makeRoute(prev, coord, routeSoFar);
				result = joinRoutes(result, partial);
			}
			prev = coord;
		}
		
		return result;
	}
	
	public LineString makeRoute(Coordinate startCoord, Coordinate endCoord) throws IOException {
		return makeRoute(startCoord, endCoord, null);
	}
	
	/**
	 * 
	 * @param startCoord
	 * @param endCoord
	 * @param previous a list of coordinates which this route will be connected to.  may be null if this is
	 * a new route
	 * @return
	 * @throws IOException
	 */
	public LineString makeRoute(Coordinate startCoord, Coordinate endCoord, List<Coordinate> previous) throws IOException {
		List<Coordinate> resultCoords = new ArrayList<Coordinate>();
		resultCoords.add(startCoord);
		Coordinate currentCoord = startCoord;
		while(!currentCoord.equals(endCoord)) {
			
			
			List<Coordinate> routeSoFar = new ArrayList<Coordinate>();
			if (previous != null) {
				routeSoFar.addAll(previous);
			}
			routeSoFar.addAll(resultCoords);
			
			//Note: alternative algorithms could be used for routing, such as lowest angle to destination or 
			//closest to destination
			Coordinate nextCoord = findEndpointClosestTo(currentCoord, endCoord, routeSoFar);
			if (nextCoord == null || resultCoords.contains(nextCoord)) {
				throw new IllegalStateException("Unable to find route between the given coordinates");
			}
			resultCoords.add(nextCoord);
			currentCoord = nextCoord;
		}
		
		LineString result = toLineString(resultCoords);
		return result;
	}
	
	/**
	 * finds the next point on a route that will lead from 'fromCoord' closest to 'toCoord'. 
	 * returns null if no suitable point is found.  
	 * @param coord
	 * @param blacklist
	 * @return
	 * @throws IOException 
	 */
	private Coordinate findEndpointClosestTo(Coordinate fromCoord, Coordinate toCoord, List<Coordinate> routeSoFar) throws IOException {
		//Point fromPoint = geometryFactory.createPoint(fromCoord);
		Point toPoint = geometryFactory.createPoint(toCoord);
		
		List<Coordinate> connectedCoords = getConnectedCoords(fromCoord);
		if (connectedCoords.size() == 0) {
			throw new IllegalArgumentException("Coordinate ["+fromCoord.getX()+","+fromCoord.getY()+"] is not a valid input.  It isn't a member of the TIN.");
		}
		
		Coordinate closestCoord = null;
		double closestDist = 999999999;
		for(Coordinate connectedCoord : connectedCoords) {
			Point connectedPoint = geometryFactory.createPoint(connectedCoord);

			//don't consider options that cause line to intersect self
			if (!allowSelfIntersection) {
				
				//proposed line
				List<Coordinate> proposedCoords = new ArrayList<Coordinate>();
				if (routeSoFar != null) {
					proposedCoords.addAll(routeSoFar);
				}
				proposedCoords.add(connectedCoord);
				LineString proposed = toLineString(proposedCoords);
				
				if (selfIntersects(proposed)) {
					System.out.println("rejected self insersecting segment");
					continue;
				}				
			}
			
			double dist = connectedPoint.distance(toPoint);
			
			boolean isCoordAProblem = !allowRepeatedCoords && routeSoFar.contains(connectedCoord);
			
			if (dist < closestDist && !isCoordAProblem) {
				closestDist = dist;
				closestCoord = connectedCoord;
			}
		}
		
		return closestCoord;
	}
	
	private boolean selfIntersects(LineString ls) {
		return !ls.isSimple();
	}
	
	/**
	 * combines linestrings a and b.  assumes that a ends with the same coordinate that
	 * b starts with. If not throws IllegalArgumentException
	 * @param a
	 * @param b
	 * @return
	 */
	private LineString joinRoutes(LineString a, LineString b) {
		
		if (a == null) {
			a = geometryFactory.createLineString();
		}
		if (b == null) {
			b = geometryFactory.createLineString();
		}
		
		Coordinate[] aCoords = a.getCoordinates();
		Coordinate[] bCoords = b.getCoordinates();

		//copy coordinates from A and B into the result Coordinate array
		Coordinate[] resultCoords = new Coordinate[aCoords.length + bCoords.length];
		int resultIndex = 0;
		for(Coordinate c : aCoords) {
			resultCoords[resultIndex] = c;
			resultIndex++;
		}
		for(Coordinate c : bCoords) {
			resultCoords[resultIndex] = c;
			resultIndex++;
		}
		resultCoords = SpatialUtils.removeDuplicateCoordinates(resultCoords);
		LineString result = geometryFactory.createLineString(resultCoords);		
		return result;
	}
	
	/**
	 * identifies and returns coordinates in the TIN that are connected
	 * to the given coordinate by a single edge
	 * @param c
	 * @return
	 * @throws IOException 
	 */
	private List<Coordinate> getConnectedCoords(Coordinate c) throws IOException {
		Point p = geometryFactory.createPoint(c);
		Filter firstCoordTouchesWaterFilter = filterFactory2D.touches(
				filterFactory2D.property(tinEdgesGeometryProperty),
				filterFactory2D.literal(p)
				);		
		SimpleFeatureCollection touching = tinEdges.getFeatures(firstCoordTouchesWaterFilter);
		
		SimpleFeatureIterator touchingIt = touching.features();
		List<Coordinate> results = new ArrayList<Coordinate>();
		while(touchingIt.hasNext()) {
			SimpleFeature f = touchingIt.next();
			LineString g = (LineString)f.getDefaultGeometry();
			Coordinate[] coords = g.getCoordinates();
			Coordinate connectedCoord = c.equals(coords[0]) ? coords[coords.length-1] : coords[0];
			results.add(connectedCoord);
		}
		return results;
	}
	
	private LineString toLineString(List<Coordinate> in) {
		Coordinate[] coords = toCoordinateArray(in);
		return geometryFactory.createLineString(coords);
	}
	
	private Coordinate[] toCoordinateArray(List<Coordinate> in) {
		Coordinate[] coords = new Coordinate[in.size()];
		int index = 0;
		for(Coordinate c : in) {
			coords[index] = c;
			index++;
		}
		return coords;
	}
	
	private List<Coordinate> toCoordinateList(Coordinate[] in) {
		List<Coordinate> coords = new ArrayList<Coordinate>();
		for(Coordinate c : in) {
			coords.add(c);
		}
		return coords;
	}
	
}
