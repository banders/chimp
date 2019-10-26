package ca.bc.gov.catchment.improvement;

import java.util.ArrayList;
import java.util.List;

import org.locationtech.jts.geom.Coordinate;
import org.opengis.feature.simple.SimpleFeature;

public class JunctionModification {

	private Coordinate originalJunction;
	private Coordinate modifiedJunction;
	private List<SimpleFeature> modifiedSections;
	
	public JunctionModification(Coordinate originalJunction) {
		this.originalJunction = originalJunction;
		this.modifiedJunction = originalJunction;
		this.modifiedSections = new ArrayList<SimpleFeature>();
	}
	
	public void setModifiedJunction(Coordinate modifiedJunction) {
		this.modifiedJunction = modifiedJunction;
	}
	
	public void setModifiedSections(List<SimpleFeature> modifiedSections) {
		this.modifiedSections = modifiedSections;
	}
	
	public Coordinate getOriginalJunction() {
		return this.originalJunction;
	}
	
	public Coordinate getModifiedJunction() {
		return this.modifiedJunction;
	}
	
	public List<SimpleFeature> getModifiedSections() {
		return this.modifiedSections;
	}
}
