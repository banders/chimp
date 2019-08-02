package ca.bc.gov.catchment.tin;

import org.locationtech.jts.geom.Coordinate;

public class Edge {
	Coordinate a;
	Coordinate b;
	
	public Edge(Coordinate a, Coordinate b) {
		if (a == null || b == null) {
			throw new IllegalArgumentException("edge must have two non-null coordinates");
		}
		this.a = a;
		this.b = b;
	}
	
	public Edge(Coordinate[] coords) {
		if (coords == null || coords.length != 2) {
			throw new IllegalArgumentException("coordinates must be an array of size 2");
		}
		this.a = coords[0];
		this.b = coords[1];
	}
	
	public Coordinate getA() {
		return a;
	}
	
	public Coordinate getB() {
		return b;
	}
	
	public boolean is3D() {
		return !Double.isNaN(a.getZ()) && !Double.isNaN(b.getZ());
	}
	
	public boolean equals(Edge other) {
		if (other == null) {
			return false;
		}
		if (other.getA().equals2D(a) && other.getB().equals2D(b) 
				|| other.getA().equals2D(b) && other.getB().equals2D(a)) {
			return true;
		}
		return false;
	}
	
	public boolean hasExactlyOneCommonPoint(Edge e) {
		if (e == null) {
			return false;
		}
		int numCommonPoints = 0;
		if (e.getA() == a || e.getA() == b) {
			numCommonPoints++;
		}
		if(e.getB() == a || e.getB() == b) {
			numCommonPoints++;
		}
		return numCommonPoints == 0;
	}
	
	public double magnitudeX() {
		return Math.abs(a.getX() - b.getX());
	}
	
	public double magnitudeY() {
		return Math.abs(a.getY() - b.getY());
	}
	
	public double magnitudeZ() {
		return Math.abs(a.getZ() - b.getZ());
	}
	
	public double magnitudeWithSignX() {
		return b.getX() - a.getX();
	}
	
	public double magnitudeWithSignY() {
		return b.getY() - a.getY();
	}
	
	public double magnitudeWithSignZ() {
		return b.getZ() - a.getZ();
	}
	
	public Edge getCrossProduct(Edge other) {
		Edge a = this;
		Edge b = other;
		//double cx = aybz − azby
		double cx = a.magnitudeWithSignY() * b.magnitudeWithSignZ() - a.magnitudeWithSignZ() * b.magnitudeWithSignY();	
		//cy = azbx − axbz
		double cy = a.magnitudeWithSignZ() * b.magnitudeWithSignX() - a.magnitudeWithSignX() * b.magnitudeWithSignZ();
		//cz = axby − aybx
		double cz = a.magnitudeWithSignX() * b.magnitudeWithSignY() - a.magnitudeWithSignY() * b.magnitudeWithSignX();
		Coordinate c1 = new Coordinate(0, 0, 0);
		Coordinate c2 = new Coordinate(cx, cy, cz);
		Edge crossProduct = new Edge(c1, c2);		
		return crossProduct;
	}
	
	public double getMagnitude() {
		return Math.sqrt(
				Math.pow(magnitudeX(), 2) +
				Math.pow(magnitudeY(), 2) +
				Math.pow(magnitudeZ(), 2)); 
	}
	
	public double getLengthOfLongestDimension() {
		double h = Math.max(magnitudeX(), magnitudeY());
		double result = Math.max(h, magnitudeZ());
		return result;
	}
	
	public Edge oppositeDirection() {
		if (!isNormalized()) {
			return new Edge(b, a);
		}
		else {
			return new Edge(a, new Coordinate(-b.getX(), -b.getY(), -b.getZ()));
		}
	}
	
	public boolean isNormalized() {
		return a.getX() == 0 && a.getY() == 0 && a.getZ() == 0;
	}
	
	public static Edge makeZUp(Edge e) {
		if(e.magnitudeWithSignZ() < 0) {
			return e.oppositeDirection();
		}
		return e;
	}
	
	/**
	 * scales to length of 1
	 * @param e
	 * @return
	 */
	public static Edge normalize(Edge e) {
		double magnitude = e.getMagnitude();
		Coordinate c1 = new Coordinate(0, 0, 0);
		Coordinate c2 = new Coordinate(
				e.magnitudeWithSignX()/magnitude, 
				e.magnitudeWithSignY()/magnitude, 
				e.magnitudeWithSignZ()/magnitude);
		Edge normalized = new Edge(c1, c2);
		
		//System.out.println(e.toString()+" -> " +normalized.toString());
		
		return normalized;
	}
	
	public String toString() {
		return a.getX()+","+a.getY()+","+a.getZ()+" "+b.getX()+","+b.getY()+","+b.getZ();
	}
}