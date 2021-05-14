package ca.bc.gov.catchment.ridgegrowth;

import java.io.IOException;
import java.util.List;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.LineString;
import org.opengis.feature.simple.SimpleFeature;

import ca.bc.gov.catchment.uncertainty.PointUncertainty;
import ca.bc.gov.catchment.utils.SpatialUtils;
import ca.bc.gov.catchment.water.Water;

public class RidgeGrowthHelpers {

	/**
	 * determines if a proposed next coordinate gets farther from or closer to 
	 * a reference coordinate.  The reference coordinate is computed as:
	 *  - is vertex index x 0.75 (rounded down)
	 * if getting farther, returns true, otherwise returns false
	 * 
	 * Implementation note: this function is heavily used, so it must be fast.  The reference coordinate
	 * should be determined in a way that doesn't require looping over the stem coordinates.  Ideally
	 * it will also be independent of line resolution.  The Approach used here is mostly (though 
	 * not completely) independent of line resolution.  This compromise was chosen to keep the algorithm fast  
	 * 
	 * @param stem
	 * @param nextCoord
	 * @return
	 */
	public static boolean isMovingAway(LineString stem, Coordinate nextCoord) {
		int refIndex = (int)Math.floor(stem.getNumPoints() * 0.75);
		Coordinate referenceCoord = stem.getCoordinateN(refIndex);
		Coordinate end = stem.getCoordinateN(stem.getNumPoints()-1);
				
		double dist1 = referenceCoord.distance(end);
		double dist2 = referenceCoord.distance(nextCoord);
		
		boolean isMovingAway = dist2 > dist1;
		return isMovingAway;
	}
	
	/**
	 * determines if a coordinate is a valid candidate as a member of a ridge line.
	 * @param coord
	 * @param ridgeCoords
	 * @param adjacentWater
	 * @return
	 * @throws IOException 
	 */
	public static boolean isCoordValidInRidge(Coordinate coord, List<Coordinate> ridgeCoords, Water water) throws IOException {

		//is the coordinate already part of the line?  if so, disallow it again.  (no loops permitted)
		//compare only on X and Y (not on Z)
		for(Coordinate rc : ridgeCoords) {
			if (rc.getX() == coord.getX() && rc.getY() == coord.getY()) {
				return false;
			}
		}
		
		//it's okay to touch water at a confluence, but nowhere else
		if (water.isTouchingWater(coord) && !water.isConfluence(coord)) {
			return false;
		}
						
		return true;
	}

	/**
	 * create a new line which starts with 'stem', but is extended by 'extension'  
	 * @param stem
	 * @param extension
	 * @return
	 */
	public static LineString extend(LineString stem, Coordinate extension) {
		List<Coordinate> allCoords = SpatialUtils.toCoordinateList(stem.getCoordinates());
		if (!allCoords.contains(extension)) {
			allCoords.add(extension);
		}
		return SpatialUtils.toLineString(allCoords);
	}
}
