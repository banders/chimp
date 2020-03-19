package ca.bc.gov.catchment.algorithms;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.geotools.data.DataUtilities;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.feature.DefaultFeatureCollection;
import org.geotools.feature.SchemaException;
import org.geotools.geometry.jts.JTSFactoryFinder;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.operation.valid.IsValidOp;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;

import ca.bc.gov.catchment.fitness.AvgElevationSectionFitness;
import ca.bc.gov.catchment.fitness.ElevationPointFitness;
import ca.bc.gov.catchment.fitness.ElevationSectionFitness;
import ca.bc.gov.catchment.fitness.EquidistantPointFitness;
import ca.bc.gov.catchment.fitness.SectionFitness;
import ca.bc.gov.catchment.tin.TinEdges;
import ca.bc.gov.catchment.utils.SpatialUtils;
import ca.bc.gov.catchment.utils.VectorUtils;
import ca.bc.gov.catchment.water.Water;

public class MedialAxisRidgeGrower extends RidgeGrower {
	
	private static final int MAX_NUM_POINTS = 1000;
	
	private int nextFid;
	private SimpleFeatureType ridgeFeatureType;
	
	public MedialAxisRidgeGrower(Water water,
			TinEdges tinEdges, 
			int lookAhead) {
		super(water, tinEdges);
		this.nextFid = 0;
		
		//create a feature type for the ridge features that are created
		this.ridgeFeatureType = null;
		try {
			ridgeFeatureType = DataUtilities.createType(RIDGE_TABLE_NAME, "geometry:LineString");
		} catch (SchemaException e1) {
			System.out.println("Unable to create feature type "+RIDGE_TABLE_NAME);
			System.exit(1);
		}
		
		SimpleFeatureCollection fc;
		try {
			fc = water.getFeatureSource().getFeatures();
			System.out.println(fc.size()+" water features");
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}
	
	public SimpleFeature growRidge(Coordinate fromConfluence, 
			LineString seedEdge, 
			List<SimpleFeature> adjacentWater) throws IOException {
		
		//make sure sure the first point is the confluence
		if (fromConfluence.equals(seedEdge.getCoordinateN(seedEdge.getNumPoints()-1))) {
			seedEdge = (LineString)seedEdge.reverse();
		}
		//return growQuickRidge(fromConfluence, seedEdge, adjacentWater);
		return growBestRidge(fromConfluence, seedEdge, adjacentWater);
	}
	
	private SimpleFeature growBestRidge(Coordinate fromConfluence, 
			LineString seedEdge, 
			List<SimpleFeature> adjacentWater) throws IOException {
		
		LineString ridgeLineString = growBestRidgeImpl(seedEdge, adjacentWater);	
		
		SimpleFeature ridgeFeature = SpatialUtils.geomToFeature(ridgeLineString, ridgeFeatureType, (nextFid)+"");
		nextFid += 1;
		return ridgeFeature;
	}
	
	/**
	 * Implements Bob's look-ahead-by-two approach
	 * @param stem
	 * @param adjacentWater
	 * @return
	 * @throws IOException
	 */
	private LineString growBestRidgeImpl(LineString stem, List<SimpleFeature> adjacentWater) throws IOException {
		//System.out.println("testing stem length "+stem.getNumPoints());
		if (stem.getNumPoints() < 2) {
			throw new IllegalArgumentException("'stemCoords' must contain at least two coordinates");
		}
		if (adjacentWater.size() != 2) {
			throw new IllegalArgumentException("'adjancentWater' must contain exactly two coordinates");
		}
		
		List<Coordinate> consideredCoords = new ArrayList<Coordinate>();
		
		LineString ridge = stem;		
		while(true) {
			
			//boolean loopedBackOnSelf = isLoopedBackOnSelf(ridge);
			//if (loopedBackOnSelf) {
			//	System.out.println("end on loopback. ridge len: "+ridge.getNumPoints());
			//}
			Coordinate nextCoord = chooseNext(ridge, adjacentWater, consideredCoords);
			
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
	
	private Coordinate chooseNext(LineString stem, List<SimpleFeature> adjacentWater, List<Coordinate> consideredCoords) throws IOException {
		
		Coordinate nextCoord = null;
		
		List<Coordinate> growthPossibilities = getPossibleNextCoords(stem, adjacentWater, true);
		if (growthPossibilities.size() == 0) {
			return null;
		}
		
		//first assume we're moving away from start point
		Comparator<Coordinate> uphillComparator = getUphillComparator(stem);
		growthPossibilities.sort(uphillComparator);
		nextCoord = growthPossibilities.get(0);
		/*
		for(Coordinate c: growthPossibilities) {
			boolean alreadyConsidered = consideredCoords.contains(c);
			if (!alreadyConsidered) {
				nextCoord = c;
				break;
			}
		}
		*/
		
		//consideredCoords.addAll(growthPossibilities);

		if (nextCoord == null) { // || !isMovingAway(stem, nextCoord)
			Comparator<Coordinate> downhillComparator = getDownhillComparator(stem);
			//nextCoord = null;
			growthPossibilities.sort(downhillComparator);
			nextCoord = growthPossibilities.get(0);
			//System.out.println("possibilities:");
			//for(Coordinate c : growthPossibilities) {
			//	System.out.println(c+", dist:"+water.getDistanceToNearestWater(c));
			//}
			
		}
		
		return nextCoord;
	}
	
	/**
	 * get all possible coords to extend the given stem with
	 * @param stem the stem which extensions will be found for
	 * @param adjacentWater
	 * @param validOnly only consider valid lines? 
	 * @return
	 * @throws IOException
	 */
	private List<Coordinate> getPossibleNextCoords(LineString stem, List<SimpleFeature> adjacentWater, boolean validOnly) throws IOException {
				
		List<Coordinate> stemCoords = SpatialUtils.toCoordinateList(stem.getCoordinates());
		Coordinate leadingCoord = stem.getCoordinateN(stem.getNumPoints()-1);
		List<Coordinate> nextCoordsToConsider = tinEdges.getConnectedCoordinates(leadingCoord);
		Comparator<Coordinate> uphillComparator = getUphillComparator(stem);
		nextCoordsToConsider.sort(uphillComparator);
				
		List<Coordinate> coordsToConsider = new ArrayList<Coordinate>();

		for (Coordinate ext : nextCoordsToConsider) {
			boolean isValid = isCoordValid(ext, stemCoords, adjacentWater);
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
	
	/**
	 * create a new line which starts with 'stem', but is extended by 'extension'  
	 * @param stem
	 * @param extension
	 * @return
	 */
	private LineString extend(LineString stem, Coordinate extension) {
		List<Coordinate> allCoords = SpatialUtils.toCoordinateList(stem.getCoordinates());
		if (!allCoords.contains(extension)) {
			allCoords.add(extension);
		}
		return SpatialUtils.toLineString(allCoords);
	}
	
	/**
	 * determines if a proposed next coordinate gets farther from or closer to the start of the line
	 * @param stem
	 * @param nextCoord
	 * @return
	 */
	private boolean isMovingAway(LineString stem, Coordinate nextCoord) {
		Coordinate start = stem.getCoordinateN(0);
		Coordinate end = stem.getCoordinateN(stem.getNumPoints()-1);
				
		double dist1 = start.distance(end);
		double dist2 = start.distance(nextCoord);
		
		boolean isMovingAway = dist2 > dist1;
		return isMovingAway;
	}
	
	private Comparator<Coordinate> getUphillComparator(final LineString stem) {
		Comparator<Coordinate> comparator = new Comparator<Coordinate>() {
			public int compare(Coordinate c1, Coordinate c2) {

				//sort by isMovingAway, then secondarily by distToNearestWater
				boolean m1 = isMovingAway(stem, c1);
				boolean m2 = isMovingAway(stem, c2);
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
				boolean m1 = isMovingAway(stem, c1);
				boolean m2 = isMovingAway(stem, c2);
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
