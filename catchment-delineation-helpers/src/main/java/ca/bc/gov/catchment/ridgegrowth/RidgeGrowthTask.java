package ca.bc.gov.catchment.ridgegrowth;

import java.util.List;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.LineString;
import org.opengis.feature.simple.SimpleFeature;

public class RidgeGrowthTask {

	private Coordinate seedCoord;
	private LineString stem;
	private List<SimpleFeature> adjacentWater;

	
	public RidgeGrowthTask(Coordinate seedCoord, LineString stem) {
		this(seedCoord, stem, null);
	}
	
	public RidgeGrowthTask(Coordinate seedCoord, LineString stem, List<SimpleFeature> adjacentWater) {
		this.seedCoord = seedCoord;
		this.stem = stem;		
		this.adjacentWater = adjacentWater;
	}	
	
	
	public Coordinate getSeedCoord() {
		return this.seedCoord;
	}
	
	public LineString getStem() {
		return this.stem;		
	}
	
	public List<SimpleFeature> getAdjacentWater() {
		return this.adjacentWater;
	}
}
