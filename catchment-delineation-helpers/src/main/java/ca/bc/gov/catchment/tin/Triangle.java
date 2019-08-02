package ca.bc.gov.catchment.tin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.geotools.geometry.jts.JTSFactoryFinder;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;


public class Triangle {
	private static int NEXT_ID = 1;
	
	List<Edge> edges;
	private int id;
	
	public Triangle() {
		this.id = NEXT_ID++;
		edges = new ArrayList<Edge>();
	}
	
	public Triangle(Edge e) {
		this();
		addEdge(e);
	}
	
	public int getId() {
		return id;
	}
	
	public int getNumEdges() {
		return edges.size();
	}
	
	public boolean hasEdge(Edge e) {
		if (e == null) {
			return false;
		}
		for (Edge edge : edges) {
			if (e.equals(edge)) {
				return true;
			}
		}
		return false;		
	}
	
	public boolean isComplete() {
		return getNumEdges() == 3;
	}
		
	public Coordinate[] getCoordinates() {
		Set<Coordinate> coordSet = getCoordCount().keySet();
		Coordinate[] coords = new Coordinate[coordSet.size()];
		Iterator<Coordinate> it = coordSet.iterator();
		for(int i = 0; it.hasNext(); i++) {
			coords[i] = it.next();
		}
		return coords;
	}
	
	private Map<Coordinate, Integer> getCoordCount() {
		Map<Coordinate, Integer> coordCount = new HashMap<Coordinate, Integer>();
		for(Edge edge: edges) {
			Coordinate a = edge.getA();
			Coordinate b = edge.getB();
			if (!coordCount.containsKey(a)) {
				coordCount.put(a, 0);
			}
			if (!coordCount.containsKey(b)) {
				coordCount.put(b, 0);
			}
			coordCount.put(a, coordCount.get(a)+1);
			coordCount.put(b, coordCount.get(b)+1);
		}
		return coordCount;
	}
	
	public Edge getMissingEdge() {
		if (getNumEdges() != 2) {
			throw new IllegalStateException("can only determine the missing edge on a triangle with exactly two edges defined");
		}
		Map<Coordinate, Integer> coordCount = getCoordCount();
		
		if (coordCount.size() != 3) {
			throw new IllegalStateException("triangle is in an invalid state.  it has two edges, but not three points");
		}
		
		List<Coordinate> coordsOfMissingEdge = new ArrayList<Coordinate>();
		java.util.Set<Coordinate> keys = coordCount.keySet();
		Iterator<Coordinate> keyIt = keys.iterator();
		while(keyIt.hasNext()) {
			Coordinate key = keyIt.next();
			int count = coordCount.get(key);
			if (count == 1) {
				coordsOfMissingEdge.add(key);
			}
		}
		
		if (coordsOfMissingEdge.size() != 2) {
			throw new IllegalStateException("triangle is in an invalid state.  unable to find exactly two points that are each connected to one edge");
		}
		
		return new Edge(coordsOfMissingEdge.get(0), coordsOfMissingEdge.get(1));
	}
	
	private void validate() {
		Map<Coordinate, Integer> coordCount = new HashMap<Coordinate, Integer>();
		for(Edge edge: edges) {
			Coordinate a = edge.getA();
			Coordinate b = edge.getB();
			if (!coordCount.containsKey(a)) {
				coordCount.put(a, 0);
			}
			if (!coordCount.containsKey(b)) {
				coordCount.put(b, 0);
			}
			coordCount.put(a, coordCount.get(a)+1);
			coordCount.put(b, coordCount.get(b)+1);
		}
					
		boolean isValid = (getNumEdges() == 0 && coordCount.size() == 0) ||
				(getNumEdges() == 1 && coordCount.size() == 2) ||
				(getNumEdges() == 2 && coordCount.size() == 3) ||
				(getNumEdges() == 3 && coordCount.size() == 3);
		if (!isValid) {
			throw new IllegalStateException("triangle has inconsistent numbers of vertices ("+coordCount.size()+") and edges ("+getNumEdges()+")");
		}
		
	}
	
	/**
	 * sorts the edges into clockwise direction (in 2D)
	 */
	private void normalizeEdgeDirection() {
		Coordinate[] allCoords = getCoordinates();
		
		//the rule: first vertex is the one with the smallest x value
		int indexOfFirstPoint = -1;
		double minX = Double.NaN;
		for(int i = 0; i < allCoords.length; i++) {
			Coordinate c = allCoords[i];
			if (Double.isNaN(minX) || c.x < minX) {
				indexOfFirstPoint = i;
				minX = c.x;
			}
		}
		
		//here, indexOfFirstPoint should now have a value in [0,1,2]
		
		Coordinate firstCoordinate = allCoords[indexOfFirstPoint];
		
		List<Coordinate> remainingCoords = new ArrayList<Coordinate>();
		for(int i = 0; i < allCoords.length; i++) {
			if (i != indexOfFirstPoint) {
				remainingCoords.add(allCoords[i]);
			}
		}
		
		//here, remainingCoords should have two values
		Coordinate secondCoordinate = null;
		Coordinate thirdCoordinate = null;
		
		//from the remaining coordinates, choose which should be listed next.
		//the rule: which ever has a larger y value should be listed second
		if (remainingCoords.get(0).y > remainingCoords.get(1).y) {
			secondCoordinate = remainingCoords.get(0);
			thirdCoordinate = remainingCoords.get(1);
		}
		else {
			secondCoordinate = remainingCoords.get(1);
			thirdCoordinate = remainingCoords.get(0);
		}
		
		//recreate the edges in clockwise order from the first point:
		Edge e1 = new Edge(firstCoordinate, secondCoordinate);
		Edge e2 = new Edge(secondCoordinate, thirdCoordinate);
		Edge e3 = new Edge(thirdCoordinate, firstCoordinate);
		
		List<Edge> normalizedEdges = new ArrayList<Edge>();
		normalizedEdges.add(e1);
		normalizedEdges.add(e2);
		normalizedEdges.add(e3);
		this.edges = normalizedEdges;
		
	}
	
	public void addEdge(Edge e) {
		if (canReceiveEdge(e)) {
			edges.add(e);
			validate();
			if (isComplete()) {
				normalizeEdgeDirection();
			}
		}
		else {
			throw new IllegalArgumentException("unable to add edge");
		}
	}
	
	public boolean canReceiveEdge(Edge e) {
		if (hasEdge(e)) {
			return false;
		}
		if (getNumEdges() >= 3) {
			return false;
		}
		if (getNumEdges() == 2) {
			return getMissingEdge().equals(e);
		}
		if (getNumEdges() == 1) {
			Edge existingEdge = edges.get(0);
			return e.hasExactlyOneCommonPoint(existingEdge);
		}
		if (getNumEdges() == 0) {
			return true;
		}
		return false;
	}

	/**
	 * 
	 * @param alwaysPointUp: there are two normal vectors for each surface.  to ensure we get the 
	 * one that points up, set this value to true, otherwise which normal is returned will be arbitrary
	 * @return
	 */
	public Edge getUpwardSurfaceNormal() {
		if (!isComplete()) {
			throw new IllegalStateException("cannot calculate slope of incomplete triangle");
		}
	
		Edge e1 = edges.get(0);
		Edge e2 = edges.get(1);
		
		if (!is3D()) {
			throw new IllegalStateException("unable to compute surface normal because the vertices aren't 3D");
		}
		
		Edge e1n = Edge.normalize(e1);
		Edge e2n = Edge.normalize(e2);
		Edge crossProduct = e1n.getCrossProduct(e2n);
				
		Edge surfaceNormal = Edge.normalize(crossProduct);
		
		if (id == 997466 || id == 1009723 || id == 1009745) {
			System.out.println("id: "+id);
			System.out.println(" e1:"+ e1);
			System.out.println(" e1n:"+ e1n);
			System.out.println(" e2n:"+ e2);
			System.out.println(" e2n:"+ e2n);
			System.out.println(" crossProduct:"+ crossProduct);
			System.out.println(" surfaceNormal:"+ surfaceNormal);
		}
		
		if (surfaceNormal.b.getZ() < 0) {
			surfaceNormal = surfaceNormal.oppositeDirection();
		}
		
		if (id == 997466 || id == 1009723 || id == 1009745) {
			System.out.println(" surfaceNormal (z is up):"+ surfaceNormal);
		}
		
		return surfaceNormal;
	}
	
	public boolean is3D() {
		for(Edge e: edges) {
			if (!e.is3D()) {
				return false;
			}
		}
		return true;
	}
	
	/**
	 * slope is a first-quadrant number (angle 0-90 degrees).  0 means horizontal.  90 means vertical.
	 * aspect is a number 0-360 indicating the downhill direction.  0 means east, 90 means north, 180 means west, 270 means south
	 * @return
	 */
	public double[] getSlopeAndAspect() {
		boolean surfaceNormalPointsUp = true;
		Edge normal = getUpwardSurfaceNormal();
		//System.out.println("normal edge: "+normal);
		double slope = Math.toDegrees(Math.acos(normal.magnitudeZ())); //Z must be positive for this calculation to work 
		//if (slope > 90) {
		//	//convert to a first quadrant number
		//	slope = 180 - slope;
		//}
		//System.out.println("slope:"+slope);
		
		//tangent function always returns a value in quadrant 1 or 4 (i.e. -90 to + 90)
		double aspect = Math.toDegrees(Math.atan(normal.magnitudeWithSignY()/normal.magnitudeWithSignX()));

		if (id == 997466 || id == 1009723 || id == 1009745) {
			System.out.println(" slope:"+slope);
			System.out.println(" aspect:"+aspect);
		}
		
		//if the actual compass direction suggested by the x and y coordinates 
		//is in quadrant 2 or 3, adjust the aspect accordingly (see note on tangent function above)
		if (normal.magnitudeWithSignX() < 0) {
			aspect += 180;
			if (id == 997466 || id == 1009723 || id == 1009745) {
				
				System.out.println(" aspect adjusted: "+aspect);
			}
		}
		//System.out.println("aspect:"+aspect);
		double result[] = {slope, aspect};
		

		
		
		return result;
		
	}
		
	public double getSlope() {
		return getSlopeAndAspect()[0];
	}
	
	public double getAspect() {
		return getSlopeAndAspect()[1];
	}
	
	public Coordinate getCentroid() {
		if (!isComplete()) {
			throw new IllegalStateException("cannot get a centroid on an incompoete triangle");
		}
		Coordinate[] coords = getCoordinates();
		Coordinate a = coords[0];
		Coordinate b = coords[1];
		Coordinate c = coords[2];
		double cx = (a.getX() + b.getX() + c.getX()) / 3; 
		double cy = (a.getY() + b.getY() + c.getY()) / 3;
		double cz = (a.getZ() + b.getZ() + c.getZ()) / 3;
		Coordinate centroid = new Coordinate(cx, cy, cz);
		return centroid;
	}
	
	public Point toCentroidPoint() {
		GeometryFactory geometryFactory = JTSFactoryFinder.getGeometryFactory();
		Coordinate centroid = getCentroid();
		Point centroidPoint = geometryFactory.createPoint(centroid);
		return centroidPoint;
	}
	
	public Polygon toPolygon() {
		GeometryFactory geometryFactory = JTSFactoryFinder.getGeometryFactory();
		Coordinate[] coords = getCoordinates();
		
		//copy the first vertex to the end of the array (because polygons
		//must be closed) (
		Coordinate[] closedCoords = new Coordinate[coords.length+1];
		for (int i = 0; i < coords.length; i++) {
			closedCoords[i] = coords[i];
		}
		closedCoords[closedCoords.length-1] = closedCoords[0];
		
		Polygon p = geometryFactory.createPolygon(closedCoords);
		return p;
	}
	
	public String toString() {
		String complete = isComplete() ? "complete" : "incomplete";
		String s = "Triangle with "+getNumEdges()+" edges ("+complete+"):\n";
		for(Edge e: edges) {
			s += " Edge: "+e.toString()+"\n";
		}
		return s;
	}

}