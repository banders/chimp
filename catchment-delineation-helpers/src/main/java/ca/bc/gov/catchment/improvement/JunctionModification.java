package ca.bc.gov.catchment.improvement;

import java.util.ArrayList;
import java.util.List;

import org.locationtech.jts.geom.Coordinate;
import org.opengis.feature.simple.SimpleFeature;

public class JunctionModification {

	private Coordinate originalJunction;
	private Coordinate modifiedJunction;
	private List<SimpleFeature> modifiedSections;
	private List<SimpleFeature> originalSections;
	
	public JunctionModification(Coordinate originalJunction, List<SimpleFeature> originalSections) {
		this.originalJunction = originalJunction;
		this.originalSections = originalSections;
		
		//modified junction and modified sections are initialized to the original values
		this.modifiedJunction = originalJunction;
		this.modifiedSections = new ArrayList<SimpleFeature>();
		this.modifiedSections.addAll(originalSections);
		
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
	
	public List<SimpleFeature> getOriginalSections() {
		return this.originalSections;
	}
	
	public boolean isModified() {
		boolean isModified = originalJunction.equals(modifiedJunction);
		return isModified;
	}
	
}
