package ca.bc.gov.catchment.improvement;

import java.io.IOException;

import org.opengis.feature.simple.SimpleFeature;

import ca.bc.gov.catchment.fitness.SectionFitness;

public abstract class SectionImprover {

	private SectionFitness sectionFitness;
	
	public abstract SectionModification improve(SimpleFeature section) throws IOException;
	
	public SectionFitness getSectionFitness() {
		return this.sectionFitness;
	}
	
	public void setSectionFitness(SectionFitness sectionFitness) {
		this.sectionFitness = sectionFitness;
	}
	
}
