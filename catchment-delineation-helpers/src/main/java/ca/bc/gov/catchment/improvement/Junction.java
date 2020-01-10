package ca.bc.gov.catchment.improvement;

import java.util.Comparator;
import java.util.List;

import org.locationtech.jts.geom.Coordinate;
import org.opengis.feature.simple.SimpleFeature;

public class Junction {
	
	private Coordinate coord;
	private List<SimpleFeature> touchingSections;
	
	public Junction(Coordinate coord, List<SimpleFeature> touchingSections) {
		this.coord = coord;
		this.touchingSections = touchingSections;
	}
	
	public int getDegree() {
		return touchingSections.size();
	}
	
	public Coordinate getCoordinate() {
		return this.coord;
	}
	
	public List<SimpleFeature> getTouchingSections() {
		return this.touchingSections;
	}
	
	public String getID() {
		return Junction.getID(touchingSections);
	}
	
	public boolean equals(Object other) {
		Junction otherJunction = (Junction)other;
		if (!getID().equals(otherJunction.getID())) {
			return false;
		}
		if (!coord.equals(otherJunction.getCoordinate())) {
			return false;
		}
		return true;
	}
	
	public static String getID(List<SimpleFeature> touchingSections) {
		//the Junction ID is build from a concatenation of the IDs
		//of the touching sections.  The section IDs are sorted before 
		//concatenation.
		
		//sort the touching sections
		touchingSections.sort(new Comparator<SimpleFeature>() {
			public int compare(SimpleFeature o1, SimpleFeature o2) {
				return o1.getID().compareTo(o2.getID());
			}
		});
		
		String junctionId = "";
		for(SimpleFeature f : touchingSections) {
			if (!junctionId.equals("")) {
				junctionId += ".";
			}
			junctionId += f.getIdentifier().getID();
		}
		
		junctionId = "{"+junctionId+"}";
		
		return junctionId;
	}
}
