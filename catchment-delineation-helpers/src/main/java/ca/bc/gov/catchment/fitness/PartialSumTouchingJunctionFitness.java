package ca.bc.gov.catchment.fitness;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.LineString;
import org.opengis.feature.simple.SimpleFeature;

import ca.bc.gov.catchment.improvement.Junction;
import ca.bc.gov.catchment.utils.SpatialUtils;

public class PartialSumTouchingJunctionFitness extends JunctionFitness {

	private SectionFitness sectionFitness;
	private int nTouching;
	
	/**
	 * fitness of a junction is calculated as: 
	 *	- the average elevation of the n vertices from each touching section which
	 *	  are closest to the junction
	 * e.g. for n=2, the three vertices closest to the junction from each touching section (including
	 * the junction point itself).
	 */
	public PartialSumTouchingJunctionFitness(SectionFitness sectionFitness) {
		this(sectionFitness, 3);
	}

	public PartialSumTouchingJunctionFitness(SectionFitness sectionFitness, int nTouching) {
		this.sectionFitness = sectionFitness;
		this.nTouching = nTouching;
	}
	
	@Override
	public double fitness(Junction junction) throws IOException {
		double sum = 0;
		for(SimpleFeature section : junction.getTouchingSections()) {
			LineString partialRoute = getPartialRouteTouchingJunction(section, junction, nTouching);
			sum += sectionFitness.fitness(partialRoute);
		}
		return sum;
	}

	/**
	 * gets a route which is a subset of the given section, starting from the end touching
	 * the given junction.  if the given section has at least 'nTouching' coordinate, the first 'nTouching'
	 * coordinate are included in the result.  Otherwise, the full section is included in the result 
	 * @param section
	 * @param junction
	 * @return
	 */
	private LineString getPartialRouteTouchingJunction(SimpleFeature section, Junction junction, int nTouching) {
		LineString originalRoute = (LineString)section.getDefaultGeometry();
		if (originalRoute.getNumPoints() < nTouching) {
			return originalRoute;
		}
		
		List<Coordinate> coords = new ArrayList<Coordinate>();
		if (originalRoute.getCoordinateN(0).equals(junction.getCoordinate())) {
			for (int i = 0; i < originalRoute.getNumPoints(); i++) {
				coords.add(originalRoute.getCoordinateN(i));
			}
		}
		else if (originalRoute.getCoordinateN(originalRoute.getNumPoints()-1).equals(junction.getCoordinate())) {
			for (int i = 0; i < originalRoute.getNumPoints(); i++) {
				int s = originalRoute.getNumPoints() - 1 - i;
				coords.add(originalRoute.getCoordinateN(s));
			}
		}
		else {
			throw new IllegalArgumentException("Section does not end at junction");
		}
		LineString partialRoute = SpatialUtils.toLineString(coords);
		return partialRoute;
	}
	
}
