package ca.bc.gov.catchment.ridgeclean;

import java.util.Comparator;
import org.locationtech.jts.geom.Polygon;
import org.opengis.feature.simple.SimpleFeature;

/**
 * features with larger areas are ranked before those with smaller areas.
 */
public class DescendingAreaComparator implements Comparator<SimpleFeature> {

	
	public int compare(SimpleFeature f1, SimpleFeature f2) {
		Polygon p1 = (Polygon)f1.getDefaultGeometry();
		Polygon p2 = (Polygon)f2.getDefaultGeometry();
		double area1 = p1.getArea();
		double area2 = p2.getArea();
		return  area1 > area2 ? -1 
				 : area1 < area2 ? 1 
			     : 0;
		
	}

	
}
