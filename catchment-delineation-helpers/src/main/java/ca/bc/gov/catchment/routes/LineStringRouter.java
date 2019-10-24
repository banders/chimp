package ca.bc.gov.catchment.routes;

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

	
	// Public
	// ------------------------------------------------------------------------
	
	/**
	 * makes a route through the specified coordinates
	 * @param included
	 * @return
	 * @throws IOException
	 * @throws RouteException 
	 */
	public LineString makeRoute(Coordinate[] included) throws IOException, RouteException {
		return makeRoute(included, null);		
	}
	
	/**
	 * @param required all coordinates that must be included in the result.  at a minimum,
	 * should include a start point and an end point. may also include coordinates
	 * in between the start and end. all coordinate must exactly match a point in the TIN.
	 * @throws IOException 
	 * @throws RouteException 
	 */
	public LineString makeRoute(Coordinate[] included, Coordinate[] excluded) throws IOException, RouteException {
		LineString result = null;
		Coordinate prev = null;
		
		//if only one coordinate is included, duplicate it so there can be a 0-length line
		if (included == null || included.length < 2) {
			throw new IllegalArgumentException("Must specifiy at least two coordinates.");
		}
		
		for(Coordinate coord: included) {
			if (prev != null) {
				List<Coordinate> routeSoFar = null;
				if (result != null) {
					routeSoFar = SpatialUtils.toCoordinateList(result.getCoordinates());
				}
				LineString partial = makeRoute(prev, coord, routeSoFar, SpatialUtils.toCoordinateList(excluded));
				result = joinRoutes(result, partial);
			}
			prev = coord;
		}
		
		return result;
	}
	
	/**
	 * makes a route between a given start and end coordinates
	 * @param startCoord
	 * @param endCoord
	 * @return
	 * @throws IOException
	 * @throws RouteException 
	 */
	public LineString makeRoute(Coordinate startCoord, Coordinate endCoord) throws IOException, RouteException {
		return makeRoute(startCoord, endCoord, null);
	}
	
	/**
	 * makes a route between the given start and end coordinates.  avoids all coordinates listed in 'excluded'
	 * @param startCoord
	 * @param endCoord
	 * @return
	 * @throws IOException
	 * @throws RouteException 
	 */
	public LineString makeRoute(Coordinate startCoord, Coordinate endCoord, Coordinate[] excluded) throws IOException, RouteException {
		List<Coordinate> blacklist = SpatialUtils.toCoordinateList(excluded);
		return makeRoute(startCoord, endCoord, null, blacklist);
	}
	
	/**
	 * finds alternative routes that don't pass through the given excluded point.  Keeps all other points the same.
	 * @param route
	 * @param exclude
	 * @return
	 * @throws IOException
	 * @throws RouteException 
	 */
	public List<LineString> alternativeRoutes(LineString route, Coordinate exclude) throws IOException, RouteException {
		if (exclude == null) {
			throw new NullPointerException("coordinate to exclude must not be null");
		}
		
		List<Coordinate> origCoordinates = SpatialUtils.toCoordinateList(route.getCoordinates());
		Coordinate firstCoord = origCoordinates.get(0);
		Coordinate lastCoord = origCoordinates.get(origCoordinates.size()-1);
		if (exclude.equals(firstCoord) || exclude.equals(lastCoord)) {
			throw new IllegalArgumentException("cannot route around an endpoint");
		}
		
		List<LineString> alternativeRoutes = new ArrayList<LineString>();
				
		List<Coordinate> connected = getConnectedCoords(exclude);
		for (Coordinate alternativeCoord : connected) {
			try {
				LineString newRoute = replaceRouteCoordinate(route, exclude, alternativeCoord);
				boolean duplicateRoute = route.equals(newRoute) || alternativeRoutes.contains(newRoute);
				if (!duplicateRoute) {	
					alternativeRoutes.add(newRoute);
				}
			}
			catch(IllegalArgumentException e) {
				//e.printStackTrace();
			}			
		}
		
		return alternativeRoutes;
		
	}
	
	// Public Helpers
	// ------------------------------------------------------------------------
	
	/**
	 * Given an list of linestrings which share one common endpoint, move the common endpoint to another point.  
	 * The result is a list of new linestrings which reflect the change.
	 * @param routes
	 * @param endpointToRemove
	 * @param newEndpoint
	 * @return
	 * @throws IOException
	 * @throws RouteException 
	 */
	public List<LineString> moveJunction(List<LineString> routes, Coordinate endpointToRemove, Coordinate newEndpoint) throws IOException, RouteException {
		
		//validate input
		int n = 0;
		for(LineString route: routes) {
			n++;
			if (route == null) {
				throw new NullPointerException("one of the routes provided is null.  null routes are not valid.");
			}
			if (!isEndPointOf(endpointToRemove, route)) {
				throw new IllegalArgumentException("the given coordinate to move is not a junction of route "+n);
			}
		}
		
		List<LineString> updatedRoutes = new ArrayList<LineString>();
		for(LineString route : routes) {
			LineString updatedRoute = replaceRouteCoordinate(route, endpointToRemove, newEndpoint);
			updatedRoutes.add(updatedRoute);
		}
		
		if (doRoutesOverlap(updatedRoutes)) {
			throw new RouteException("unable to find a an alternative junction position");
		}
		
		return updatedRoutes;
	}
	
	/**
	 * identifies and returns coordinates in the TIN that are connected
	 * to the given coordinate by a single edge
	 * @param c
	 * @return
	 * @throws IOException 
	 */
	public List<Coordinate> getConnectedCoords(Coordinate c) throws IOException {
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
	
	public boolean isEndPointOf(Coordinate c, LineString route) {
		Coordinate c1 = route.getCoordinateN(0);
		Coordinate cn = route.getCoordinateN(route.getNumPoints()-1);
		if (c.equals(c1) || c.equals(cn)) {
			return true;
		}
		return false;
	}
	
	public boolean isCoordinateOf(Coordinate c, LineString route) {
		List<Coordinate> coords = SpatialUtils.toCoordinateList(route.getCoordinates());
		if (coords.contains(c)) {
			return true;
		}
		return false;
	}
	
	public boolean doesRouteFollowTinEdges(LineString s) throws IOException {
		List<LineString> segments = SpatialUtils.toSegments(s);
		
		//check that each segment of the given linestring is an edge in the TIN
		for (LineString segment : segments) {
			Filter equalFilter1 = filterFactory2D.within(
					filterFactory2D.property(tinEdgesGeometryProperty),
					filterFactory2D.literal(s));
			Filter equalFilter2 = filterFactory2D.within(
					filterFactory2D.property(tinEdgesGeometryProperty),
					filterFactory2D.literal(s.reverse()));
			Filter filter = filterFactory2D.or(equalFilter1, equalFilter2);
			SimpleFeatureCollection matches = tinEdges.getFeatures(filter);
			if (matches.size() == 0) {
				System.out.println("segment not in TIN: "+segment);
				return false;
			}
		}
		return true;
	}
	
	// Private
	// ------------------------------------------------------------------------
	
	/**
	 * makes a route between the given start and end coordinates  assumes the route may be connect to the
	 * end of another route, the points of which are provided in the 'previous' parameter
	 * @param startCoord
	 * @param endCoord
	 * @param previous a list of coordinates which this route will be connected to.  may be null if this is
	 * a new route
	 * @param blacklist a list of coordinates which may not be included in the route.
	 * @return
	 * @throws IOException
	 * @throws RouteException 
	 */
	private LineString makeRoute(Coordinate startCoord, Coordinate endCoord, List<Coordinate> previous, List<Coordinate> blacklist) throws IOException, RouteException {
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
			Coordinate nextCoord = findEndpointClosestTo(currentCoord, endCoord, routeSoFar, blacklist);
			if (nextCoord == null || resultCoords.contains(nextCoord)) {
				throw new RouteException("Unable to find route between the given coordinates");
			}
			resultCoords.add(nextCoord);
			currentCoord = nextCoord;
		}
		
		LineString result = SpatialUtils.toLineString(resultCoords);
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
	private Coordinate findEndpointClosestTo(Coordinate fromCoord, Coordinate toCoord, List<Coordinate> routeSoFar, List<Coordinate> blacklist) throws IOException {
		RouteFinder routeFitter = new ShortestDistanceRouteFinder(toCoord);
		List<Coordinate> connectedCoords = getConnectedCoords(fromCoord);
		if (connectedCoords.size() == 0) {
			throw new IllegalArgumentException("Coordinate ["+fromCoord.getX()+","+fromCoord.getY()+"] is not a valid input.  It isn't a member of the TIN.");
		}
		
		if (blacklist == null) {
			blacklist = new ArrayList<Coordinate>();
		}
		
		Coordinate bestFitCoord = null;
		double bestFitness = -999999999;
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
				LineString proposed = SpatialUtils.toLineString(proposedCoords);
				
				if (selfIntersects(proposed)) {
					continue;
				}				
			}
			
			boolean violatesRepeatedConstraint = !allowRepeatedCoords && routeSoFar.contains(connectedCoord);
			boolean isBlacklisted = blacklist.contains(connectedCoord);
			
			//define the proposed segment created from 'fromCoord' to the
			//current 'connectedCoord'
			List<Coordinate> proposedSegmentCoords = new ArrayList<Coordinate>();
			proposedSegmentCoords.add(fromCoord);
			proposedSegmentCoords.add(connectedCoord);
			LineString proposedSegment = SpatialUtils.toLineString(proposedSegmentCoords);
			
			//quantify the fitness of the proposed segment.  higher values mean more fit.
			double fitness = routeFitter.getFitness(proposedSegment);
			if (fitness > bestFitness && !violatesRepeatedConstraint && !isBlacklisted) {
				bestFitness = fitness;
				bestFitCoord = connectedCoord;
			}
		}
		
		return bestFitCoord;
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
		LineString[] routes = {a, b};
		return joinRoutes(routes);
	}
	
	private LineString joinRoutes(LineString[] routes) {
		List<Coordinate> allCoords = new ArrayList<Coordinate>();
		for(LineString route: routes) {
			if (route == null ) {
				route = geometryFactory.createLineString();
			}
			for(Coordinate c : route.getCoordinates()) {
				allCoords.add(c);
			}
		}
		Coordinate[] resultCoords = SpatialUtils.removeDuplicateCoordinates(SpatialUtils.toCoordinateArray(allCoords));
		LineString result = geometryFactory.createLineString(resultCoords);		
		return result;
	}
	
	private LineString replaceRouteCoordinate(LineString route, Coordinate oldCoord, Coordinate newCoord) throws IOException, RouteException {
		Coordinate[] blacklist = {oldCoord};
		List<Coordinate> oldCoords = SpatialUtils.toCoordinateList(route.getCoordinates());
		LineString newRoute = null;
		
		if (oldCoords.contains(newCoord)) {
			throw new IllegalArgumentException("new coordinate "+newCoord+" is already part of route.");
		}
	
		int sliceIndex = oldCoords.indexOf(oldCoord);

		if (sliceIndex < 0) {
			throw new IllegalArgumentException("old coordinate must be part of route");
		}
		if (sliceIndex == 0) { //first point of string to be replaced
			List<Coordinate> endSlice = oldCoords.subList(sliceIndex+1, oldCoords.size());
			LineString routeA = makeRoute(newCoord, oldCoords.get(1), blacklist); //changed portion
			LineString routeB = SpatialUtils.toLineString(endSlice); //unchanged portion
			
			LineString[] routes = {routeA, routeB};
			newRoute = joinRoutes(routes);
		}
		else if (sliceIndex == oldCoords.size() - 1) { //last point of string to be replaced
			List<Coordinate> startSlice = oldCoords.subList(0, sliceIndex);
			LineString routeA = SpatialUtils.toLineString(startSlice); //unchanged portion
			LineString routeB = makeRoute(oldCoords.get(oldCoords.size()-2), newCoord, blacklist); //changed portion
			
			LineString[] routes = {routeA, routeB};
			newRoute = joinRoutes(routes);
		}
		else { //mid point in string to be replaced
			List<Coordinate> beforeSlice = oldCoords.subList(0, sliceIndex);
			List<Coordinate> afterSlice = oldCoords.subList(sliceIndex+1, oldCoords.size());
			Coordinate middleStart = beforeSlice.get(beforeSlice.size()-1);
			Coordinate middleEnd = afterSlice.get(0);
			
			Coordinate[] keyCoords = {middleStart, newCoord, middleEnd};
			LineString middleRoute = makeRoute(keyCoords, blacklist);
			
			LineString routeA = SpatialUtils.toLineString(beforeSlice);
			LineString routeB = middleRoute;
			LineString routeC = SpatialUtils.toLineString(afterSlice);
			
			LineString[] routes = {routeA, routeB, routeC};
			newRoute = joinRoutes(routes);
		}
		
		return newRoute;
	}
	
	public boolean doRoutesOverlap(List<LineString> routes) {
		
		for(LineString route1 : routes) {
			for(LineString route2 : routes) {
				if (route1 == route2 ) {
					continue;
				}
				//check whether route SEGMENTS overlap
				if (route1.contains(route2)) {
					return false;
				}
			}
		}
		return true;
	}
	
}
