package ca.bc.gov.catchment.ridgegrowth;

import java.io.IOException;
import java.util.List;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.LineString;
import org.opengis.feature.simple.SimpleFeature;


public interface RidgeGrowthStrategy {

	public LineString growRidge(RidgeGrowthTask task) throws IOException;
	
	public boolean canChooseNext(LineString stem, List<SimpleFeature> adjacentWater) throws IOException;
	
	public Coordinate chooseNext(LineString stem, List<SimpleFeature> adjacentWater) throws IOException;
}
