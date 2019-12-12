package ca.bc.gov.catchment.improvement;

import java.util.ArrayList;
import java.util.List;

import org.locationtech.jts.geom.Geometry;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;

import ca.bc.gov.catchments.utils.SpatialUtils;

public class SectionModification {

	private SimpleFeature originalSection;
	private SimpleFeature modifiedSection;
	private List<SimpleFeature> modifiedTouchingSections;
	
	public SectionModification(SimpleFeature originalSection) {
		this.originalSection = originalSection;
		this.modifiedTouchingSections = new ArrayList<SimpleFeature>();
		setModifiedSection(originalSection);
	}
	
	public void setModifiedSection(SimpleFeature modifiedSection) {
		if (!modifiedSection.getIdentifier().equalsFID(originalSection.getIdentifier())) {
			throw new IllegalArgumentException("A modified section must have the same FID as the original section");
		}
		this.modifiedSection = modifiedSection;
	}
	
	public void addModifiedTouchingSection(SimpleFeature touchingSection) {
		this.modifiedTouchingSections.add(touchingSection);
	}
	
	public void setModifiedTouchingSections(List<SimpleFeature> touchingSections) {
		this.modifiedTouchingSections.clear();
		for (SimpleFeature f : touchingSections) {
			this.modifiedTouchingSections.add(f);
		}
	}
	
	public SimpleFeature getOriginalSection() {
		return this.originalSection;
	}
	
	public SimpleFeature getModifiedSection() {
		return this.modifiedSection;
	}
	
	public List<SimpleFeature> getModifiedTouchingSections() {
		return this.modifiedTouchingSections;
	}
	
	public boolean isModified() {
		boolean isModified = !getOriginalSection().equals(getModifiedSection());
		return isModified;
	}
	
}
