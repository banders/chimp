package ca.bc.gov.catchment.ridgegrowth;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.LineString;
import org.opengis.feature.simple.SimpleFeature;

import ca.bc.gov.catchment.tin.TinEdges;
import ca.bc.gov.catchment.utils.SpatialUtils;
import ca.bc.gov.catchment.water.Water;

/**
 * Defines a ridge growth strategy which is composed of two other strategies (plan A and plan B).
 * At every step, attempts to extend the ridge by plan A.  If that is not possible, attempts plan B.  
 * If neither plan A nor plan B are possible, the ridge is terminated.   
 * 
 * @author Brock
 *
 */
public class PlanAPlanBStrategy implements RidgeGrowthStrategy {

	private RidgeGrowthStrategy planA;
	private RidgeGrowthStrategy planB;
	private Water water;
	private TinEdges tinEdges;
	
	public PlanAPlanBStrategy(RidgeGrowthStrategy planA, RidgeGrowthStrategy planB, Water water, TinEdges tinEdges) {
		this.planA = planA;
		this.planB = planB;
		this.water = water;
		this.tinEdges = tinEdges;
	}
	
	public LineString growRidge(RidgeGrowthTask task) throws IOException {
		
		Coordinate confluence = task.getSeedCoord();
		LineString stem = task.getStem();
		
		//make sure sure the first point of stem is the confluence.  if not, reverse the stem
		if (confluence.equals(stem.getCoordinateN(stem.getNumPoints()-1))) {
			stem = (LineString)stem.reverse();
		}
		
		LineString ridgeLine = growRidgeImpl(stem, task.getAdjacentWater());		
		return ridgeLine; 
	}

	
	private LineString growRidgeImpl(LineString stem, List<SimpleFeature> adjacentWater) throws IOException {
		if (stem.getNumPoints() < 2) {
			throw new IllegalArgumentException("'stemCoords' must contain at least two coordinates");
		}
		
		LineString ridge = stem;
		while(true) {
			
			Coordinate nextCoord = chooseNext(ridge, adjacentWater);
			
			boolean isTouchingWater = water.isTouchingWater(nextCoord);
			
			boolean endConditionMet = nextCoord == null ||
					isTouchingWater;
			
			if (endConditionMet) {
				break;
			}
			//System.out.println("chose:" +nextCoord.getZ()+" dist:"+water.getDistanceToNearestWater(nextCoord));
			
			//extend the ridge with the new coordinate
			List<Coordinate> existingCoords = SpatialUtils.toCoordinateList(ridge.getCoordinates());
			List<Coordinate> extendedCoords = new ArrayList<Coordinate>();
			extendedCoords.addAll(existingCoords);
			extendedCoords.add(nextCoord);
			ridge = SpatialUtils.toLineString(extendedCoords);
		}
		
		return ridge;
	}
	
	public boolean canChooseNext(LineString stem, List<SimpleFeature> adjacentWater) throws IOException {
		RidgeGrowthStrategy strategy = 
				planA.canChooseNext(stem, adjacentWater) ?
						planA :
						planB;
		return strategy.canChooseNext(stem, adjacentWater);
	}
	
	public Coordinate chooseNext(LineString stem, List<SimpleFeature> adjacentWater) throws IOException {
		
		Coordinate nextCoord = planA.chooseNext(stem, adjacentWater);
		if (nextCoord == null) {
			nextCoord = planB.chooseNext(stem, adjacentWater);
		}
		
		return nextCoord;
	}
}
