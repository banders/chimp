package ca.bc.gov.catchment.tin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.geotools.geometry.jts.JTSFactoryFinder;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
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
	
	public Triangle(Geometry g) {
		this();
		Coordinate[] coords = g.getCoordinates();
		Coordinate prev = null;
		for(Coordinate coord : coords) {
			if (prev != null) {
				Edge e = new Edge(prev, coord);
				addEdge(e);
			}
			prev = coord;
		}
	}
	
	public Triangle(Coordinate c1, Coordinate c2, Coordinate c3) {
		this();
		Edge e1 = new Edge(c1, c2);
		Edge e2 = new Edge(c2, c3);
		Edge e3 = new Edge(c3, c1);
		addEdge(e1);
		addEdge(e2);
		addEdge(e3);
	}
	

	/**
	 * Computes the 2D orthocenter coordinate (z coordinate is ignored)
	 * @return
	 */
	public Coordinate getOrthoCenter2D() {
		//see: https://byjus.com/orthocenter-formula/
		
		Coordinate[] coords = getCoordinates();
		Coordinate A = coords[0];
		Coordinate B = coords[1];
		Coordinate C = coords[2];
		
		//System.out.println("A:"+A);
		//System.out.println("B:"+B);
		//System.out.println("C:"+C);
		
		//here Coordinate is just used as a convenient way to store <slope, intercept> pairs
		List<Coordinate> slopeInterceptPairs = new ArrayList<Coordinate>();
		
		//slope of AB
		double slopeAB = (B.getY() - A.getY()) / (B.getX() - A.getX());
		if (slopeAB != 0) {
			
			//slope of line perpendicular to AB
			double m = -1 / slopeAB;
			
			//y-intercept of the line perpendicular to AB
			double b = C.getY() - m * C.getX();
			slopeInterceptPairs.add(new Coordinate(m, b));
		}
		
		//slope of BC
		double slopeBC = (C.getY() - B.getY()) / (C.getX() - B.getX());
		if (slopeBC != 0) {
			
			//slope of line perpendicular to BC
			double m = -1 / slopeBC;
			
			//y-intercept of the line perpendicular to BC
			double b = A.getY() - m * A.getX();
			slopeInterceptPairs.add(new Coordinate(m, b));
		}
		
		//slope of CA
		double slopeCA = (A.getY() - C.getY()) / (A.getX() - C.getX());
		if (slopeCA != 0) {
			
			//slope of line perpendicular to BC
			double m = -1 / slopeCA;
			
			//y-intercept of the line perpendicular to BC
			double b = B.getY() - m * B.getX();
			slopeInterceptPairs.add(new Coordinate(m, b));
		}
		
		if (slopeInterceptPairs.size() < 2) {
			throw new IllegalArgumentException("Unable to find two suitable altitide lines for calculating orthocenter");
		}
		
		//System.out.println("slopeAB:"+slopeAB);
		//System.out.println("slopeBC:"+slopeBC);
		//System.out.println("slopeCA:"+slopeCA);

		
		Coordinate line1 = slopeInterceptPairs.get(0);
		Coordinate line2 = slopeInterceptPairs.get(1);
		double m1 = line1.getX();
		double b1 = line1.getY();
		double m2 = line2.getX();
		double b2 = line2.getY();
		//System.out.println("m1:"+m1);
		//System.out.println("b1:"+b1);
		//System.out.println("m2:"+m2);
		//System.out.println("b2:"+b2);
		Coordinate orthoCenter = Edge.intersectionOfLines(m1, b1, m2, b2);
		
		orthoCenter.setZ(0);
	    return orthoCenter;
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
	
	public boolean hasCoordinate(Coordinate coordToFind) {
		for(Coordinate c : this.getCoordinates()) {
			if (c.equals(coordToFind)) {
				return true;
			}
		}
		return false;
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
				
		if (surfaceNormal.b.getZ() < 0) {
			surfaceNormal = surfaceNormal.oppositeDirection();
		}
		/*
		System.out.println(" e1:"+e1);
		System.out.println(" e2:"+e2);
		System.out.println(" e1n:"+e1n);
		System.out.println(" e2n:"+e2n);
		System.out.println(" cross: "+crossProduct);
		System.out.println(" surfaceNormal: "+surfaceNormal);
		*/
		
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
		//Z must be positive for this calculation to work 
		double z = normal.magnitudeZ();
		//System.out.println("z: "+z);
		double slope = Math.toDegrees(Math.acos(z)); //Z must be positive for this calculation to work  
		//if (slope > 90) {
		//	//convert to a first quadrant number
		//	slope = 180 - slope;
		//}
		//System.out.println("slope:"+slope);
		
		//if aspect cannot be determined (e.g. when the triangle is flat on the horizontal plane)
		//then assume aspect is 0.
		double aspect = 0;
		if (normal.magnitudeWithSignX() != 0) {
			//tangent function always returns a value in quadrant 1 or 4 (i.e. -90 to + 90)
			Math.toDegrees(Math.atan(normal.magnitudeWithSignY()/normal.magnitudeWithSignX()));
		}

		//System.out.println(magthis);
		//System.out.println(" slope:"+slope);
		//System.out.println(" aspect:"+aspect);
		
		//if the actual compass direction suggested by the x and y coordinates 
		//is in quadrant 2 or 3, adjust the aspect accordingly (see note on tangent function above)
		if (normal.magnitudeWithSignX() < 0) {
			aspect += 180;
		}
		//System.out.println("aspect:"+aspect);
		double result[] = {slope, aspect};
				
		return result;
		
	}
	
	private double containsCoordinateHelper (Coordinate c1, Coordinate c2, Coordinate c3)
	{
	    return (c1.x - c3.x) * (c2.y - c3.y) - (c2.x - c3.x) * (c1.y - c3.y);
	}
	
	/**
	 * checks whether the given coordinate lies within the bounds of this triangle
	 * (Z is ignored in this check)
	 * @param c
	 * @return
	 */
	public boolean containsCoordinate2D(Coordinate c) {

		Coordinate[] coords = getCoordinates();
		Coordinate A = coords[0];
		Coordinate B = coords[1];
		Coordinate C = coords[2];
		
	    double d1 = containsCoordinateHelper(c, A, B);
	    double d2 = containsCoordinateHelper(c, B, C);
	    double d3 = containsCoordinateHelper(c, C, A);

	    boolean has_neg = (d1 < 0) || (d2 < 0) || (d3 < 0);
	    boolean has_pos = (d1 > 0) || (d2 > 0) || (d3 > 0);

	    return !(has_neg && has_pos);
	}
		
	public double getSlope() {
		return getSlopeAndAspect()[0];
	}
	
	/**
	 * the compass direction of downhill
	 * @return
	 */
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
	
	/**
	 * The spine edge is a line perpendicular to the base edge.  It is a vector, with direction pointing away
	 * from the base edge towards the point opposite the base edge.  The point where the base line (an 
	 * infinite extension of the base edge) and the spine line intersect is not necessarily inside the triangle.   
	 */
	public Edge getSpineEdge(Edge baseEdge) {
		Coordinate spinePeakCoord = this.getOtherCoord(baseEdge.getA(), baseEdge.getB());
		Coordinate spineBaseCoord = null;
		
		//equation of base line (baseEdge)
		double baseSlope = (baseEdge.getA().getY() - baseEdge.getB().getY()) / (baseEdge.getA().getX() - baseEdge.getB().getX());
 	    
		//special base: baseline is vertical
		if (baseSlope == Double.POSITIVE_INFINITY || baseSlope == Double.NEGATIVE_INFINITY) { 
			spineBaseCoord = new Coordinate(baseEdge.getA().getX(), spinePeakCoord.getY());
		}
		
		//special case: baseline is horizontal
 	    else if (baseSlope == 0) { 
 	    	spineBaseCoord = new Coordinate(spinePeakCoord.getX(), baseEdge.getA().getY());
 	    }
		
		//normal case: baseline is neither horizontal nor vertical
 	    else {
			double baseYIntercept = baseEdge.getA().getY() - baseSlope * baseEdge.getA().getX();
			
			//equation of spine
			double spineSlope = -1 / baseSlope;
			double spineYIntercept = spinePeakCoord.getY() - spineSlope * spinePeakCoord.getX();
			
			//create an edge representing the spine.  It bpoints from the base to the peak of the 
			//triangle
			spineBaseCoord = Edge.intersectionOfLines(baseSlope, baseYIntercept, spineSlope, spineYIntercept);
 	    }
		
		//always provide a z-value for spine coordinates
		if (Double.isNaN(spineBaseCoord.getZ())) {
			spineBaseCoord.setZ(0);
		}
		
		Edge spine = new Edge(spineBaseCoord, spinePeakCoord);
		
		return spine;
	}
	
	public Point toCentroidPoint() {
		GeometryFactory geometryFactory = JTSFactoryFinder.getGeometryFactory();
		Coordinate centroid = getCentroid();
		Point centroidPoint = geometryFactory.createPoint(centroid);
		return centroidPoint;
	}
	
	public Coordinate getOtherCoord(Coordinate c1, Coordinate c2) {
		if (!isComplete()) {
			throw new IllegalStateException("triangle is incomplete");
		}
		Edge e = new Edge(c1, c2);
		if (!hasEdge(e)) {
			throw new IllegalArgumentException("one or both coordinates given aren't part of the triangle");
		}
		for(Coordinate c : this.getCoordinates()) {
			if (!c.equals(c1) && !c.equals(c2)) {
				return c;
			}
		}
		throw new IllegalStateException("failed to find the other coordinate");
		
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
	
	public boolean equals(Object other) {
		Triangle otherTriangle = (Triangle)other;
		for(Coordinate c: this.getCoordinates()) {
			if (!otherTriangle.hasCoordinate(c)) {
				return false;
			}
		}
		
		return this.getCoordinates().length == otherTriangle.getCoordinates().length;
	}

}