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

/**
 * @deprecated Instead use ca.bc.gov.catchment.ridgegrowth.HillClimbStrategy with ca.bc.gov.catchment.ridgegrowth.RidgeGrower
 * @author Brock
 *
 */
public class LookAheadRidgeGrower extends RidgeGrower {
	
	private static final int MAX_NUM_POINTS = 500;
	
	private Comparator<Coordinate> elevationComparator;
	private int nextFid;
	private SimpleFeatureType ridgeFeatureType;
	private int lookAhead;
	
	/**
	 * This line growing strategy chooses an up-hill path through a given TIN.  
	 * It chooses each path extension by looking ahead up to N connected edges and then
	 * choosing the most favourable next step according to some pre-defined rules.
	 * 
	 * - start the "growing line" as a 1-segment "seed line" from the confluence outward  
	 * - initialize the "leading coordinate" of the growing line to the second coordinate of the seed line (i.e. the coordinate
	 *   at the non-confluence end)
	 * - loop:
	 * 	- identify all possible paths of length N or less extending from the leading coordinate 
	 *    of the growing line
	 * 	- sort the possible path extensions by:
	 * 	  1. is path moving away from start of the growing line? (Yes or no).  Prefer Yes.
	 *    2. number of segments in the possible path extension (1 to N). Prefer larger.
	 *    3. average elevation of possible path extension.  Prefer higher average elevation.
	 * 	- select the path extension at the top of the sorted list 
	 * 	- select the first coordinate after the current coordinate from the selected path extension.
	 *    this selected coordinate will be the next "leading coordinate" of the grown line
	 * 	- repeat until the no valid path extensions can be found from the current leading coordinate (valid means doesn't intersect water) 
	 * 
	 * 
	 * @param water
	 * @param tinEdges
	 * @param lookAhead
	 */
	
	public LookAheadRidgeGrower(Water water,
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
			
			//extend the ridge with the new coordinate
			List<Coordinate> existingCoords = SpatialUtils.toCoordinateList(ridge.getCoordinates());
			List<Coordinate> extendedCoords = new ArrayList<Coordinate>();
			extendedCoords.addAll(existingCoords);
			extendedCoords.add(nextCoord);
			ridge = SpatialUtils.toLineString(extendedCoords);
		}
		
		return ridge;
	}
	
	@Override
	public boolean canChooseNext(LineString stem, List<SimpleFeature> adjacentWater) throws IOException {
		List<LineString> growthPossibilities = growthPossibilities(stem, adjacentWater, true, lookAhead);
		return growthPossibilities.size() > 0;
	}
	
	@Override
	public Coordinate chooseNext(LineString stem, List<SimpleFeature> adjacentWater) throws IOException {
		
		Coordinate nextCoord = null;
		
		List<LineString> growthPossibilities = growthPossibilities(stem, adjacentWater, true, lookAhead);
		LineString bestGrowthPossibility = pickBestGrowthPossibility3(growthPossibilities);
		
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
		
		String s = "";
		for(Coordinate c : nextCoordsToConsider) {
			s += " "+c.getZ();
		}
		
		List<LineString> allExtensions = new ArrayList<LineString>();

		for (Coordinate ext : nextCoordsToConsider) {
			boolean isValid = isCoordValid(ext, stemCoords, adjacentWater);
			//boolean isHigher = couldBeHigherOrSameWithinUncertainty(ext, leadingCoord);
			boolean isHigher = definatelyHigherOrSameWithinUncertainty(ext, leadingCoord);
			if (!isValid || !isHigher) {
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
	
	/**
	 * this implementation is fairly good, but it performs awkardly when it encounters sliver triangles.  
	 * if the TIN is delanuay conforming then this is a good choice, otherwise, "pickBestGrowthPossibility2"
	 * may be preferable.
	 * @param lookAheads
	 * @return
	 */
	private LineString pickBestGrowthPossibility1(List<LineString> lookAheads) {
		if (lookAheads == null || lookAheads.size() == 0) {
			return null;
		}
		
		//sort by:
		//1. is moving away?
		//2. number of coordinates in line
		//3. average elevation of line
		final AvgElevationSectionFitness sectionFitness = new AvgElevationSectionFitness(tinEdges);
		Comparator<LineString> lookAheadComparator = new Comparator<LineString>() {
			public int compare(LineString s1, LineString s2) {
				
				boolean m1 = isMovingAway(s1, s1.getCoordinateN(s1.getNumPoints()-1));
				boolean m2 = isMovingAway(s2, s2.getCoordinateN(s2.getNumPoints()-1));
				if (m1 != m2) {
					return m1 ? -1 : 1;
				}
				else {
					if (s1.getNumPoints() == s2.getNumPoints()) {
						try {
							double fit1 = sectionFitness.fitness(s1);
							double fit2 = sectionFitness.fitness(s2);
							return fit1 > fit2 ? -1 
									 : fit1 < fit2 ? 1 
								     : 0;
						} 
						catch(IOException e) {
							return 0;
						}
					}
					else {
						return s1.getNumPoints() > s2.getNumPoints() ? -1 
								 : s1.getNumPoints() < s2.getNumPoints() ? 1 
							     : 0; 
					}
				}
			}
		};
		lookAheads.sort(lookAheadComparator);
		return lookAheads.get(0);
	}

	/**
	 * this implementation is less susceptible to getting channeled along long edges of sliver triangles,
	 * (as compared to pickBestGrowthPossibility1)
	 * which means this is a reasonable choice when triangles aren't delaunay confirming
	 * @param lookAheads
	 * @return
	 */
	private LineString pickBestGrowthPossibility2(List<LineString> lookAheads) {		
		if (lookAheads == null || lookAheads.size() == 0) {
			return null;
		}
		
		//sort by:
		//1. is moving away?
		//2. number of coordinates in line
		//3. slope
		Comparator<LineString> lookAheadComparator = new Comparator<LineString>() {
			public int compare(LineString s1, LineString s2) {
				
				boolean m1 = isMovingAway(s1, s1.getCoordinateN(s1.getNumPoints()-1));
				boolean m2 = isMovingAway(s2, s2.getCoordinateN(s2.getNumPoints()-1));
				if (m1 != m2) {
					return m1 ? -1 : 1;
				}
				else { //both moving away, or neither moving away.  look to second criteria
					
					if (s1.getNumPoints() == s2.getNumPoints()) {

						double slope1 = SpatialUtils.getSlope(s1);
						double slope2 = SpatialUtils.getSlope(s2);
						return slope1 > slope2 ? -1 
								 : slope1 < slope2 ? 1 
							     : 0; 
					}
					else {
						return s1.getNumPoints() > s2.getNumPoints() ? -1 
								 : s1.getNumPoints() < s2.getNumPoints() ? 1 
							     : 0; 
					}
					
				}
			}
		};
		lookAheads.sort(lookAheadComparator);
		return lookAheads.get(0);
	}
	
	/**
	 * this implementation is less susceptible to getting channeled along long edges of sliver triangles,
	 * (as compared to pickBestGrowthPossibility1)
	 * which means this is a reasonable choice when triangles aren't delaunay confirming
	 * @param lookAheads
	 * @return
	 */
	private LineString pickBestGrowthPossibility3(List<LineString> lookAheads) {		
		if (lookAheads == null || lookAheads.size() == 0) {
			return null;
		}
		
		//sort by:
		//1. is moving away?
		//2. number of coordinates in line
		//3. average elevation (above the lowest coord) of line divided by length of line
		// e..g if Z values of growth possibility are 618m, 625m, 634m, the average will be the average
		//Z above the lowest coord will be 7.6m.  that value will be divided by the line length
		final AvgElevationSectionFitness sectionFitness = new AvgElevationSectionFitness(tinEdges);
		Comparator<LineString> lookAheadComparator = new Comparator<LineString>() {
			public int compare(LineString s1, LineString s2) {
				
				boolean m1 = isMovingAway(s1, s1.getCoordinateN(s1.getNumPoints()-1));
				boolean m2 = isMovingAway(s2, s2.getCoordinateN(s2.getNumPoints()-1));
				if (m1 != m2) {
					return m1 ? -1 : 1;
				}
				else {
					if (s1.getNumPoints() == s2.getNumPoints()) {
						try {							
							double fit1 = sectionFitness.fitness(s1) - SpatialUtils.getLowestZ(s1) / s1.getLength();
							double fit2 = sectionFitness.fitness(s2) - SpatialUtils.getLowestZ(s2) / s2.getLength();
							return fit1 > fit2 ? -1 
									 : fit1 < fit2 ? 1 
								     : 0;
						} 
						catch(IOException e) {
							return 0;
						}
					}
					else {
						return s1.getNumPoints() > s2.getNumPoints() ? -1 
								 : s1.getNumPoints() < s2.getNumPoints() ? 1 
							     : 0; 
					}
				}
			}
		};
		lookAheads.sort(lookAheadComparator);
		return lookAheads.get(0);
	}
	
}
