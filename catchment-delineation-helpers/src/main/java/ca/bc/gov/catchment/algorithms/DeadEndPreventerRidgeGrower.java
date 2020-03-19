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
import ca.bc.gov.catchment.water.Water;

public class DeadEndPreventerRidgeGrower extends RidgeGrower {
	
	private Comparator<Coordinate> elevationComparator;
	private int nextFid;
	private SimpleFeatureType ridgeFeatureType;
	private int lookAhead;
	
	public DeadEndPreventerRidgeGrower(Water water,
			TinEdges tinEdges,
			int lookAhead) {
		super(water, tinEdges);
		this.nextFid = 0;
		this.lookAhead = lookAhead;
		
		//create a feature type for the ridge features that are created
		this.ridgeFeatureType = null;
		try {
			ridgeFeatureType = DataUtilities.createType(RIDGE_TABLE_NAME, "geometry:LineString");
		} catch (SchemaException e1) {
			System.out.println("Unable to create feature type "+RIDGE_TABLE_NAME);
			System.exit(1);
		}
				
		//a comparator based on the section fitness object
		this.elevationComparator = ElevationPointFitness.getCoordinateComparator();
		
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
	 * a recursive function which chooses the best ridge after testing multiple possibilities
	 * @param stemCoords a list of coordinates which represent the start of the ridge.  the first
	 * coordinate is the confluence, and the last coordinate is the one that will be extended if 
	 * any valid extension is possible
	 * @param adjacentWater two adjacent water features
	 * @return
	 * @throws IOException 
	 */
	/*
	private LineString growBestRidge1(LineString stem, List<SimpleFeature> adjacentWater) throws IOException {
		//System.out.println("testing stem length "+stem.getNumPoints());
		if (stem.getNumPoints() < 2) {
			throw new IllegalArgumentException("'stemCoords' must contain at least two coordinates");
		}
		if (adjacentWater.size() != 2) {
			throw new IllegalArgumentException("'adjancentWater' must contain exactly two coordinates");
		}
		Coordinate leadingCoord = stem.getCoordinateN(stem.getNumPoints()-1);
		List<Coordinate> stemCoords = SpatialUtils.toCoordinateList(stem.getCoordinates());
		List<Coordinate> nextCoordsToConsider = tinEdges.getConnectedCoordinates(leadingCoord);
		nextCoordsToConsider.sort(elevationComparator);
		
		double bestRidgeFit = stem.getLength(); //avgElevationFitness.fitness(stem);
		LineString bestRidge = stem;
		
		for (Coordinate nextCoord : nextCoordsToConsider) {
			boolean isHigher = isHigherOrSameWithinUncertainty(nextCoord, leadingCoord);
			boolean isValid = isCoordValid(nextCoord, stemCoords, adjacentWater);
			if (!isHigher || !isValid) {
				continue;
			}
			
			//extend the stem with the next coordinate
			List<Coordinate> stemCoordsToTest = new ArrayList<Coordinate>();
			stemCoordsToTest.addAll(stemCoords);
			stemCoordsToTest.add(nextCoord);
			LineString nextStemToTest = SpatialUtils.toLineString(stemCoordsToTest);
			LineString ridge = growBestRidge1(nextStemToTest, adjacentWater);
			
			double fit = ridge.getLength(); //avgElevationFitness.fitness(ridge);
			if (fit > bestRidgeFit) {
				bestRidge = ridge;
				bestRidgeFit = fit;
				break;
			}
		}
		
		return bestRidge;
	}
	*/
	
	/**
	 * a recursive function which chooses the best ridge after testing multiple possibilities
	 * @param stemCoords a list of coordinates which represent the start of the ridge.  the first
	 * coordinate is the confluence, and the last coordinate is the one that will be extended if 
	 * any valid extension is possible
	 * @param adjacentWater two adjacent water features
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
		
		LineString ridge = stem;
		while(true) {
			Coordinate leadingCoord = ridge.getCoordinateN(ridge.getNumPoints()-1);
			
			List<Coordinate> nextCoordsToConsider = tinEdges.getConnectedCoordinates(leadingCoord);
			nextCoordsToConsider.sort(elevationComparator);
			
			//look ahead, check if we can extend ridge along each of the nextCoordsToConsider
			List<LineString> lookAheadRidges = new ArrayList<LineString>();
			for(Coordinate nextCoord : nextCoordsToConsider) {
				LineString lookAheadRidge = growByN(ridge, nextCoord, adjacentWater, lookAhead);
				lookAheadRidges.add(lookAheadRidge);				
			}

			//choose the best look-ahead ridge
			LineString bestLookAhead = pickBestLookAhead(lookAheadRidges, ridge.getNumPoints());
			
			//clip the chosen look-ahead ridge back to length: ridge + 1 
			LineString nextRidge = clipToLength(bestLookAhead, ridge.getNumPoints()+1);

			//System.out.println("ridge len:"+ridge.getNumPoints());
			boolean improvementFound = nextRidge.getNumPoints() > ridge.getNumPoints();
			if (!improvementFound) {
				break;
			}
			ridge = nextRidge;
		}
		

		
		return ridge;
	}
		
	/**
	 * grows the given ridge by the given 'firstCoord' coordinate if possible.  then attempts to grow by N-1 additional coords.
	 * if not possible to grow by N, returns the original.
	 * @param stem
	 * @param firstCoord
	 * @param adjacentWater
	 * @return
	 * @throws IOException
	 */
	private LineString growByN(LineString stem, Coordinate firstCoord, List<SimpleFeature> adjacentWater, int N) throws IOException {
		List<Coordinate> nextCoordsToConsider = new ArrayList<Coordinate>();
		nextCoordsToConsider.add(firstCoord);
		
		int maxLen = stem.getNumPoints() + N;
		LineString result = stem;
		Coordinate leadingCoord = result.getCoordinateN(result.getNumPoints()-1);
		while(true) {
			
			int initialLen = result.getNumPoints();
			List<Coordinate> existingCoords = SpatialUtils.toCoordinateList(result.getCoordinates());
			for(Coordinate nextCoord : nextCoordsToConsider) {
				
				boolean isHigher = couldBeHigherOrSameWithinUncertainty(nextCoord, leadingCoord);
				boolean isValid = isCoordValid(nextCoord, existingCoords, adjacentWater);
				if (!isHigher || !isValid) {
					continue;
				}
				
				//extend the stem with the next coordinate
				List<Coordinate> extendedCoords = new ArrayList<Coordinate>();
				extendedCoords.addAll(existingCoords);
				extendedCoords.add(nextCoord);
				result = SpatialUtils.toLineString(extendedCoords);
				break;
			
			}

			boolean improvement = result.getNumPoints() > initialLen;
			boolean maxLenReached = result.getNumPoints() >= maxLen;
			if (maxLenReached || !improvement) {
				break;
			}
			
			//identify points connected to the leading coord.  these are the next
			//set of points to evaluate
			leadingCoord = result.getCoordinateN(result.getNumPoints()-1);
			nextCoordsToConsider = tinEdges.getConnectedCoordinates(leadingCoord);
			nextCoordsToConsider.sort(elevationComparator);
			
		}
		
		if (result.getNumPoints() > stem.getNumPoints()) {
			Coordinate c = result.getCoordinateN(stem.getNumPoints());
			if (!c.equals(firstCoord)) {
				throw new IllegalStateException("post condition failed.  first new coord expected to be "+firstCoord);
			}
		}
		
		return result;
		
	}
	
	private LineString pickBestLookAhead(List<LineString> lookAheads, final int targetLen) {
		//sort by line length first, then secondary sort by elevation of last coordinate
		Comparator<LineString> lookAheadComparator = new Comparator<LineString>() {
			public int compare(LineString s1, LineString s2) {
				if (s1.getNumPoints() == s2.getNumPoints()) {
					int leadingIndex = Math.min(targetLen, s1.getNumPoints()-1);
					Coordinate leading1 = s1.getCoordinateN(leadingIndex);
					Coordinate leading2 = s2.getCoordinateN(leadingIndex);
					return leading1.getZ() > leading2.getZ() ? -1 
							 : leading1.getZ() < leading2.getZ() ? 1 
						     : 0;
				}
				else {
					return s1.getNumPoints() > s2.getNumPoints() ? -1 
							 : s1.getNumPoints() < s2.getNumPoints() ? 1 
						     : 0; 
				}
			}
		};
		lookAheads.sort(lookAheadComparator);
		return lookAheads.get(0);
	}
	

}
