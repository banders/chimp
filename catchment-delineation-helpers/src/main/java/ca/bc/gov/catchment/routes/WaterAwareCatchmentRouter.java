package ca.bc.gov.catchment.routes;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.geotools.filter.visitor.IsStaticExpressionVisitor;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.LineString;
import org.opengis.feature.simple.SimpleFeature;

import ca.bc.gov.catchment.tin.TinEdges;
import ca.bc.gov.catchment.water.WaterAnalyzer;
import ca.bc.gov.catchments.utils.SpatialUtils;

/**
 * Provides objects for routing lines through a TIN, but restricts solutions to those which don't conflict 
 * with water features.
 * @author Brock
 *
 */
public class WaterAwareCatchmentRouter {

	private WaterAnalyzer water;
	private TinEdges tinEdges;
	private LineStringRouter lineStringRouter;
	
	public WaterAwareCatchmentRouter(TinEdges tinEdges, WaterAnalyzer water) {
		this.tinEdges = tinEdges;
		this.water = water;
		this.lineStringRouter = new LineStringRouter(tinEdges);
	}
	
	/**
	 * 
	 * @param route
	 * @param oldCoord
	 * @param newCoord
	 * @param freedom the number of coords on either size of 'oldCoord' that are allowed to move
	 * @return
	 * @throws IOException 
	 * @throws RouteException 
	 */
	public LineString reroute(LineString route, Coordinate oldCoord, Coordinate newCoord, int freedom, boolean isEndpointMovable) throws IOException, RouteException {
		int pivotIndex = LineStringRouter.getIndex(route, oldCoord);
		if (pivotIndex == -1) {
			throw new IllegalArgumentException("'oldCoord' is not in 'route'");
		}
		if (!isCoordinateMovable(pivotIndex, route, isEndpointMovable)) {
			throw new IllegalArgumentException("'oldCoord' is not movable");
		}
		int pivotCoordMoved = 0;
		List<Coordinate> coordsToIncludeInNewRoute = new ArrayList<Coordinate>();
		for (int i = 0; i < route.getNumPoints(); i++) {
			boolean isEndpoint = i == 0 || i == route.getNumPoints()-1;
			int distance = Math.abs(pivotIndex - i);
			
			if (distance == 0) {
				//Note: we can move the current point if it is both an endpoint and a pivot.
				pivotCoordMoved++;
				coordsToIncludeInNewRoute.add(newCoord);
			}
			else if (distance <= freedom && isCoordinateMovable(i, route, isEndpointMovable) && !isEndpoint) {
				//don't include this coordinate in the new route
				//Note: we cannot move the current point if it is an endpoint but not a pivot
			}
			else {
				Coordinate c = route.getCoordinateN(i);
				if (!coordsToIncludeInNewRoute.contains(c)) {
					coordsToIncludeInNewRoute.add(c);
				}
			}
		}
		
		LineString newRoute = null;
		try {
			newRoute = lineStringRouter.makeRoute(SpatialUtils.toCoordinateArray(coordsToIncludeInNewRoute));
		} catch(RouteException e) {
			/*
			System.out.println("Unable to change route. Diagnosis below:");
			System.out.println("pivotIndex: "+pivotIndex);
			System.out.println("pivotCoordMoved #:"+pivotCoordMoved);
			System.out.println("original route: "+route);
			System.out.println("oldCoord: "+oldCoord);
			System.out.println("newCoord: "+newCoord);
			System.out.println("freedom: "+freedom);
			String s = "";
			for(Coordinate c: coordsToIncludeInNewRoute) {
				s = s + ", "+c;
			}
			System.out.println("new route: "+s);
			*/
			throw e;
		}

		//System.out.println(" old route:"+route);
		//System.out.println(" new route:"+newRoute);
		return newRoute;
	}
	
	/**
	 * reroutes the geometry within the given feature.  Returns a new feature with the new route, but the same
	 * feature id.
	 * @param feature
	 * @param oldCoord
	 * @param newCoord
	 * @param freedom
	 * @return
	 * @throws IOException
	 * @throws RouteException
	 */
	public SimpleFeature reroute(SimpleFeature feature, Coordinate oldCoord, Coordinate newCoord, int freedom, boolean isEndpointMovable) throws IOException, RouteException {
		LineString route = (LineString)feature.getDefaultGeometry();
		LineString newRoute = reroute(route, oldCoord, newCoord, freedom, isEndpointMovable);
		SimpleFeature newFeature = SpatialUtils.geomToFeature(newRoute, feature.getFeatureType(), feature.getID());
		return newFeature;
	}
	
	/**
	 * Reroutes all lines in the list to touch the new junction instead of the old junction
	 * @param routes
	 * @param oldJunction
	 * @param newJunction
	 * @param freedom
	 * @return
	 * @throws IOException
	 * @throws RouteException
	 */
	public List<LineString> reroute(List<LineString> routes, Coordinate oldJunction, Coordinate newJunction, int freedom, boolean isEndpointMovable) throws IOException, RouteException {
		List<LineString> results = new ArrayList<LineString>();
		for (LineString route : routes) {
			LineString newRoute = reroute(route, oldJunction, newJunction, freedom, isEndpointMovable);
			results.add(newRoute);
		}
		return results;
	}
	
	public List<SimpleFeature> rerouteFeatures(List<SimpleFeature> routes, Coordinate oldJunction, Coordinate newJunction, int freedom) throws IOException, RouteException {
		boolean isEndpointMovable = true;
		List<SimpleFeature> results = new ArrayList<SimpleFeature>();
		for (SimpleFeature route : routes) {
			SimpleFeature newRoute = reroute(route, oldJunction, newJunction, freedom, isEndpointMovable);
			results.add(newRoute);
		}
		return results;
	}
	
	public boolean isCoordinateMovable(int index, LineString route, boolean isEndpointMovable) throws IOException {
		Coordinate prev = null;
		Coordinate next = null;
		Coordinate c = route.getCoordinateN(index);
		if (index+1 < route.getNumPoints()) {
			next = route.getCoordinateN(index+1);
		}
		
		if (!isEndpointMovable) {
			boolean isEndpoint = index == 0 || index == route.getNumPoints()-1;
			if (isEndpoint) {
				return false;
			}
		}
		
		if (water.isConfluence(c)) {
			return false;
		}
		//check if adjacent coord is a confluence
		if (next != null && water.isConfluence(next)) {
			return false;
		}
		if (prev != null && water.isConfluence(prev)) {
			return false;
		}
		prev = c;
	
		return true;
	}
	
}
