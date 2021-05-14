package ca.bc.gov.catchment.algorithms;

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
 * @deprecated Instead use ca.bc.gov.catchment.ridgegrowth.PlanAPlanBStrategy with ca.bc.gov.catchment.ridgegrowth.RidgeGrower
 * @author Brock
 *
 */
public class HybridRidgeGrower extends RidgeGrower {

	private int nextFid;
	private RidgeGrower primaryGrower;
	private RidgeGrower secondaryGrower;
	
	private static final int LOOKAHEAD = 2;
	
	public HybridRidgeGrower(Water water, TinEdges tinEdges) {
		super(water, tinEdges);
				
		this.nextFid = 0;
		this.primaryGrower = new LookAheadRidgeGrower(water, tinEdges, LOOKAHEAD);
		this.secondaryGrower = new MedialAxisRidgeGrower(water, tinEdges);
	}

	@Override
	public SimpleFeature growRidge(Coordinate confluence, LineString seedEdge, List<SimpleFeature> adjacentWater)
			throws IOException {
		//make sure sure the first point is the confluence
		if (confluence.equals(seedEdge.getCoordinateN(seedEdge.getNumPoints()-1))) {
			seedEdge = (LineString)seedEdge.reverse();
		}
		
		LineString ridgeLine = growRidgeImpl(seedEdge, adjacentWater);
		
		SimpleFeature ridgeFeature = SpatialUtils.geomToFeature(ridgeLine, getRidgeFeatureType(), (nextFid)+"");
		nextFid += 1;
		
		return ridgeFeature; 
	}
	
	private LineString growRidgeImpl(LineString stem, List<SimpleFeature> adjacentWater) throws IOException {
		if (stem.getNumPoints() < 2) {
			throw new IllegalArgumentException("'stemCoords' must contain at least two coordinates");
		}
		if (adjacentWater.size() != 2) {
			throw new IllegalArgumentException("'adjancentWater' must contain exactly two coordinates");
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
	
	public boolean canChooseNext(LineString stem, List<SimpleFeature> adjacentWater) throws IOException {
		RidgeGrower grower = 
				primaryGrower.canChooseNext(stem, adjacentWater) ?
						primaryGrower :
						secondaryGrower;
		return grower.canChooseNext(stem, adjacentWater);
	}
	
	public Coordinate chooseNext(LineString stem, List<SimpleFeature> adjacentWater) throws IOException {
		
		RidgeGrower grower = 
				primaryGrower.canChooseNext(stem, adjacentWater) ?
						primaryGrower :
						secondaryGrower;
		Coordinate nextCoord = grower.chooseNext(stem, adjacentWater);
		//String s = primaryGrower.canChooseNext(stem, adjacentWater) ? "primary" : "secondary";
		//System.out.println(nextCoord+", "+s);
		
		return nextCoord;
	}



}
