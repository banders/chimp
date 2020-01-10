package ca.bc.gov.catchment.improvement;

import java.util.ArrayList;
import java.util.List;

import org.locationtech.jts.geom.Coordinate;
import org.opengis.feature.simple.SimpleFeature;

public class JunctionModification {

	private Junction originalJunction;
	private Junction modifiedJunction;
	private ImprovementMetrics improvementMetrics;
	
	public JunctionModification(Junction originalJunction) {
		this.originalJunction = originalJunction;
		
		//modified junction and modified sections are initialized to the original values
		this.modifiedJunction = originalJunction;
	}
	
	public void setModifiedJunction(Junction modifiedJunction) {
		this.modifiedJunction = modifiedJunction;
	}
	
	public Junction getOriginalJunction() {
		return this.originalJunction;
	}
	
	public Junction getModifiedJunction() {
		return this.modifiedJunction;
	}
	
	public boolean isModified() {
		boolean isModified = !originalJunction.getCoordinate().equals(modifiedJunction.getCoordinate());
		return isModified;
	}
	
	public ImprovementMetrics getImprovementMetrics() {
		return improvementMetrics;
	}
	
	public void setImprovementMetrics(ImprovementMetrics p) {
		improvementMetrics = p;
	}
}
