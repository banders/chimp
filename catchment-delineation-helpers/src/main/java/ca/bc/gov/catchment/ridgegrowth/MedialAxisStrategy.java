package ca.bc.gov.catchment.ridgegrowth;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.LineString;
import org.opengis.feature.simple.SimpleFeature;

import ca.bc.gov.catchment.tin.TinEdges;
import ca.bc.gov.catchment.utils.SpatialUtils;
import ca.bc.gov.catchment.water.Water;

public class MedialAxisStrategy implements RidgeGrowthStrategy {

	private Water water;
	private TinEdges tinEdges;
	
	public MedialAxisStrategy(Water water, TinEdges tinEdges) {
		this.water = water;
		this.tinEdges = tinEdges;
	}
		
	public LineString growRidge(RidgeGrowthTask task) throws IOException {
		return growRidgeImpl(task.getStem(), task.getAdjacentWater());	
	}

	/**
	 * Implements Bob's look-ahead-by-two approach
	 * @param stem
	 * @param adjacentWater
	 * @return
	 * @throws IOException
	 */
	private LineString growRidgeImpl(LineString stem, List<SimpleFeature> adjacentWater) throws IOException {
		//System.out.println("testing stem length "+stem.getNumPoints());
		if (stem.getNumPoints() < 2) {
			throw new IllegalArgumentException("'stemCoords' must contain at least two coordinates");
		}
				
		LineString ridge = stem;		
		while(true) {
			
			//boolean loopedBackOnSelf = isLoopedBackOnSelf(ridge);
			//if (loopedBackOnSelf) {
			//	System.out.println("end on loopback. ridge len: "+ridge.getNumPoints());
			//}
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
	
	public boolean canChooseNext(LineString stem, List<SimpleFeature> adjacentWater) {
		return true;
	}
	
	public Coordinate chooseNext(LineString stem, List<SimpleFeature> adjacentWater) throws IOException {
		
		Coordinate nextCoord = null;
		Coordinate leadingCoord = stem.getCoordinateN(stem.getNumPoints()-1);
		
		List<Coordinate> growthPossibilities = getPossibleNextCoords(stem, true);
		if (growthPossibilities.size() == 0) {
			return null;
		}
		
		//first assume we're moving away from start point
		Comparator<Coordinate> uphillComparator = getUphillComparator(stem);
		growthPossibilities.sort(uphillComparator);
		nextCoord = growthPossibilities.get(0);
		
		boolean isGettingCloserToWater = water.getDistanceToNearestWater(nextCoord) < water.getDistanceToNearestWater(leadingCoord);
		if (isGettingCloserToWater) { 
			Comparator<Coordinate> downhillComparator = getDownhillComparator(stem);
			growthPossibilities.sort(downhillComparator);
			nextCoord = growthPossibilities.get(0);		
		}
		
		//check post conditions: the sorted list should prioritize path extensions that move away
		//from the stem
		
		boolean firstMovingAway = RidgeGrowthHelpers.isMovingAway(stem, nextCoord);
		boolean lastMovingAway = RidgeGrowthHelpers.isMovingAway(stem, growthPossibilities.get(growthPossibilities.size()-1));
		if (!firstMovingAway && lastMovingAway) {
			System.out.println("post condition failed");
			throw new IllegalStateException("problem sorting possibilities");
		}
		
		return nextCoord;
	}
	
	/**
	 * get all possible coords to extend the given stem with
	 * @param stem the stem which extensions will be found for
	 * @param validOnly only consider valid lines? 
	 * @return
	 * @throws IOException
	 */
	private List<Coordinate> getPossibleNextCoords(LineString stem, boolean validOnly) throws IOException {
				
		List<Coordinate> stemCoords = SpatialUtils.toCoordinateList(stem.getCoordinates());
		Coordinate leadingCoord = stem.getCoordinateN(stem.getNumPoints()-1);
		List<Coordinate> nextCoordsToConsider = tinEdges.getConnectedCoordinates(leadingCoord);
		Comparator<Coordinate> uphillComparator = getUphillComparator(stem);
		nextCoordsToConsider.sort(uphillComparator);
				
		List<Coordinate> coordsToConsider = new ArrayList<Coordinate>();

		for (Coordinate ext : nextCoordsToConsider) {
			boolean isValid = RidgeGrowthHelpers.isCoordValidInRidge(ext, stemCoords, water);
			//boolean isHigher = isHigherWithinUncertainty(ext, leadingCoord);
			if (validOnly && !isValid) {
				continue;
			}

			if (!coordsToConsider.contains(ext)) {
				coordsToConsider.add(ext);
			}			
			
		}		
		
		return coordsToConsider;

	}
	

	private Comparator<Coordinate> getUphillComparator(final LineString stem) {
		Comparator<Coordinate> comparator = new Comparator<Coordinate>() {
			public int compare(Coordinate c1, Coordinate c2) {

				//sort by isMovingAway, then secondarily by distToNearestWater
				boolean m1 = RidgeGrowthHelpers.isMovingAway(stem, c1);
				boolean m2 = RidgeGrowthHelpers.isMovingAway(stem, c2);
				if (m1 != m2) {
					return m1 ? -1 : 1;
				}
				else {
					double dist1 = water.getDistanceToNearestWater(c1);
					double dist2 = water.getDistanceToNearestWater(c2);
					int result = dist1 > dist2 ? -1 //higher rank when dist is large (ie when farther from water)
						 : dist1 < dist2 ? 1 
					     : 0;
					return result;	
				}
							
			}			
		};
		return comparator;		
	}
	
	private Comparator<Coordinate> getDownhillComparator(final LineString stem) {
		Comparator<Coordinate> comparator = new Comparator<Coordinate>() {
			public int compare(Coordinate c1, Coordinate c2) {
				
				//sort by isMovingAway, then secondarily by distDiffBetweenToNearestWater
				boolean m1 = RidgeGrowthHelpers.isMovingAway(stem, c1);
				boolean m2 = RidgeGrowthHelpers.isMovingAway(stem, c2);
				if (m1 != m2) {
					return m1 ? -1 : 1;
				}
				else {
					double distDiff1 = water.getDistDiffBetweenTwoNearestWater(c1);
					double distDiff2 = water.getDistDiffBetweenTwoNearestWater(c2);
					int result = distDiff1 > distDiff2 ? 1 //higher rank when distDiff is small (ie when closer to medial axis)
						 : distDiff1 < distDiff2 ? -1 
					     : 0;
					return result;
				}
				
				
			}
			
		};
		return comparator;		
	}
}
