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

public class HybridRidgeGrowerOld extends RidgeGrower {
	
	private static final int MAX_NUM_POINTS = 1000;
	
	private Comparator<Coordinate> elevationComparator;
	private int nextFid;
	private SimpleFeatureType ridgeFeatureType;
	private int lookAhead;
	
	public HybridRidgeGrowerOld(Water water,
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
		
		LineString ridge = stem;
		while(true) {
			
			Coordinate nextCoord = chooseNext(ridge, adjacentWater);
			
			if (nextCoord == null ||ridge.getNumPoints() > MAX_NUM_POINTS) {
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
		List<LineString> growthPossibilities = growthPossibilities(stem, adjacentWater, true, lookAhead);
		return growthPossibilities.size() > 0;
	}
	
	public Coordinate chooseNext(LineString stem, List<SimpleFeature> adjacentWater) throws IOException {
		
		Coordinate nextCoord = null;
		
		List<LineString> growthPossibilities = growthPossibilities(stem, adjacentWater, true, lookAhead);
		LineString bestGrowthPossibility = pickBestGrowthPossibility(stem, growthPossibilities);
		
		int nextCoordIndex = stem.getNumPoints();
		if (bestGrowthPossibility != null && nextCoordIndex < bestGrowthPossibility.getNumPoints()) {
			nextCoord = bestGrowthPossibility.getCoordinateN(nextCoordIndex);
		} 
		
		return nextCoord;
	}
	
	/**
	 * get all possible lines which start with the given stem and are extended by up to 'maxExtensionLen'
	 * @param stem the stem which extensions will be found for
	 * @param adjacentWater
	 * @param validOnly only consider valid lines? 
	 * @param maxExtensionLen a value >= 1
	 * @return
	 * @throws IOException
	 */
	private List<LineString> growthPossibilities(LineString stem, List<SimpleFeature> adjacentWater, boolean validOnly, int maxExtensionLen) throws IOException {
		
		if (maxExtensionLen < 1) {
			throw new IllegalArgumentException("'maxExtensionLen' must be >= 1");
		}
		
		List<Coordinate> stemCoords = SpatialUtils.toCoordinateList(stem.getCoordinates());
		Coordinate leadingCoord = stem.getCoordinateN(stem.getNumPoints()-1);
		List<Coordinate> nextCoordsToConsider = tinEdges.getConnectedCoordinates(leadingCoord);
		nextCoordsToConsider.sort(elevationComparator);
				
		List<LineString> allExtensions = new ArrayList<LineString>();

		for (Coordinate ext : nextCoordsToConsider) {
			boolean isValid = isCoordValid(ext, stemCoords, adjacentWater);
			//boolean isHigher = isHigherWithinUncertainty(ext, leadingCoord);
			if (!isValid) {
				continue;
			}

			LineString extendedStem = extend(stem, ext); 
			if (maxExtensionLen > 1) {
				List<LineString> extensions = growthPossibilities(extendedStem, adjacentWater, validOnly, maxExtensionLen-1);
				allExtensions.addAll(extensions);
			}
			if (!allExtensions.contains(extendedStem)) {
				allExtensions.add(extendedStem);
			}
			
			
		}
		
		
		return allExtensions;

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
	
	
	private LineString pickBestGrowthPossibility(final LineString stem, List<LineString> lookAheads) {
		final Coordinate leadingCoord = stem.getCoordinateN(stem.getNumPoints()-1);
		final int indexOfExtensionFirstCoord = stem.getNumPoints();
		if (lookAheads == null || lookAheads.size() == 0) {
			return null;
		}
		
		//sort by:
		// "is dead end" (non-dead end gets higher rank)
		// elevation of first coordinate in extension
		// distance to medial axis of first coord in extension
		Comparator<LineString> lookAheadComparator = new Comparator<LineString>() {
			public int compare(LineString s1, LineString s2) {
				try {
					boolean isDeadEnd1 = s1.getNumPoints() - stem.getNumPoints() <= 1;
					boolean isDeadEnd2 = s2.getNumPoints() - stem.getNumPoints() <= 1;
					//one line is dead, the other isn't
					if (lookAhead > 1 && isDeadEnd1 != isDeadEnd2) {
						int result = isDeadEnd1 ? 1 : -1; //higher rank to the non-dead end
						return result;
					}
					else { //both dead, or both not dead
						Coordinate c1 = s1.getCoordinateN(indexOfExtensionFirstCoord);
						Coordinate c2 = s2.getCoordinateN(indexOfExtensionFirstCoord);
						double z1 = c1.getZ();
						double z2 = c2.getZ();
						boolean isHigherThanLeading1 = definatelyHigherWithinUncertainty(c1, leadingCoord);
						boolean isHigherThanLeading2 = definatelyHigherWithinUncertainty(c2, leadingCoord);;
						if (isHigherThanLeading1 != isHigherThanLeading2) { //only one of the points is higher than the leading point
							int result = isHigherThanLeading1 ? -1 : 1; //higher rank to the point that is higher than the leading coord
							return result;
						}
						else { //both higher than leading coord, or both not higher than leading coord
							if (isHigherThanLeading1) { //if both higher than leading coord..
								int result = z1 > z2 ? -1 : 1;
								return result;
							}
							else { //if both lower than or same height as leading coord...
								double distDiff1 = water.getDistDiffBetweenTwoNearestWater(c1);
								double distDiff2 = water.getDistDiffBetweenTwoNearestWater(c2);
								int result = distDiff1 > distDiff2 ? 1 //higher rank when distDiff is small (ie when closer to medial axis)
									 : distDiff1 < distDiff2 ? -1 
								     : 0;
								return result;
							}
						}
					}	
				}
				catch(Exception e) {
					return 0;
				}
			}
			
		};
		lookAheads.sort(lookAheadComparator);
		
		/*
		System.out.println("next choices (sorted):");
		for (LineString s :lookAheads) {
			Coordinate c = s.getCoordinateN(indexOfExtensionFirstCoord);
			System.out.println(" "+c.getZ()+" dist:"+water.getDistDiffBetweenTwoNearestWater(c));
		}
		*/
		
		return lookAheads.get(0);
	}

}
