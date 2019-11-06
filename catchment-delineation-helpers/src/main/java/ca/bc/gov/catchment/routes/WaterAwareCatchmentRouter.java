package ca.bc.gov.catchment.routes;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.LineString;

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
	public LineString reroute(LineString route, Coordinate oldCoord, Coordinate newCoord, int freedom) throws IOException, RouteException {
		int pivotIndex = LineStringRouter.getIndex(route, oldCoord);
		if (pivotIndex == -1) {
			throw new IllegalArgumentException("'oldCoord' is not in 'route'");
		}
		if (!isCoordinateMovable(pivotIndex, route)) {
			throw new IllegalArgumentException("'oldCoord' is not movable");
		}
		
		List<Coordinate> coordsToIncludeInNewRoute = new ArrayList<Coordinate>();
		for (int i = 0; i < route.getNumPoints(); i++) {
			if (i == pivotIndex) {
				coordsToIncludeInNewRoute.add(newCoord);
				continue;
			}
			int distance = Math.abs(pivotIndex - i);
			if (distance > 0 && distance <= freedom && isCoordinateMovable(i, route)) {
				//don't include this coordinate
			}
			else {
				Coordinate c = route.getCoordinateN(i);
				coordsToIncludeInNewRoute.add(c);
			}
		}
		
		LineString newRoute = lineStringRouter.makeRoute(SpatialUtils.toCoordinateArray(coordsToIncludeInNewRoute));
		//System.out.println(" old route:"+route);
		//System.out.println(" new route:"+newRoute);
		return newRoute;
	}
	
	public boolean isCoordinateMovable(int index, LineString route) throws IOException {
		Coordinate prev = null;
		Coordinate next = null;
		Coordinate c = route.getCoordinateN(index);
		if (index+1 < route.getNumPoints()) {
			next = route.getCoordinateN(index+1);
		}
		boolean isEndpoint = index == 0 || index == route.getNumPoints()-1;
		
		if (isEndpoint) {
			//TODO: in future, allow junction points to be moved (but not confluences)
			return false;
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
