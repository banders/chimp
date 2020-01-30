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
		if (e.getA().equals(a) || e.getA().equals(b)) {
			numCommonPoints++;
		}
		if(e.getB().equals(a) || e.getB().equals(b)) {
			numCommonPoints++;
		}
		return numCommonPoints == 1;
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
	
	/* 
	 * Angle of vector pointing from A to B, ignoring the z-coordinate.  Unit degrees.  0 means east.  90 means north.
	 */
	public double getCompassAngle() {
		double opp = this.magnitudeWithSignY();
		double adj = this.magnitudeWithSignX();
		double angle = Math.toDegrees(Math.atan(opp/adj));
		if (adj < 0) {
			angle += 180;
		}
		return angle;
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
	
	public Coordinate getMidPoint() {
		double x = (getA().getX() + getB().getX()) / 2;
		double y = (getA().getY() + getB().getY()) / 2;
		double z = (getA().getZ() + getB().getZ()) / 2;
		Coordinate m = new Coordinate(x, y, z);
		return m;
	}
	
	public Edge oppositeDirection() {
		if (!isNormalized()) {
			return new Edge(b, a);
		}
		else {
			Coordinate B = new Coordinate(-b.getX(), -b.getY(), -b.getZ());
			
			//these checks look non-sensical at a glance, but they are needed to prevent us converting
			//0 to -0.  For some reason a value of -0 can muck up some calculations.  Best to avoid it. 
			//always convert -0 to 0.
			if (B.getX() == 0) {
				B.setX(0);
			}
			if (B.getY() == 0) {
				B.setY(0);
			}
			if (B.getZ() == 0) {
				B.setZ(0);
			}
			
			Edge e = new Edge(a, B);
			return e;
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
	
	// Static
	
	/**
	 * Given the slope and y-intercept of two lines, calculate the point of intersection.
	 * 
	 * @param m1 slope of line 1
	 * @param b1 y-intercept of line 1
	 * @param m2 slope of line 2
	 * @param b2 y-intercept of line 2
	 * @return
	 */
	public static Coordinate intersectionOfLines(double m1, double b1, double m2, double b2) {

		//intersection of the two altitude lines
		// see: https://www.baeldung.com/java-intersection-of-two-lines
		
		if (m1 == m2) {
	        throw new IllegalStateException("Unable to calculate intersection of lines with equal slopes.");
	    }
	 
	    double x = (b2 - b1) / (m1 - m2);
	    double y = m1 * x + b1;
	 
	    Coordinate orthoCenter = new Coordinate(x, y);
	    return orthoCenter;
	}
	
	public double getZAt(double x, double y) {
		double x1 = getA().getX();
		double x2 = getB().getX();
		double z1 = getA().getZ();
		double z2 = getB().getZ();
		double z = (x - x1) / (x2 - x1) * (z2 - z1) + z1;
		return z;
	}
}