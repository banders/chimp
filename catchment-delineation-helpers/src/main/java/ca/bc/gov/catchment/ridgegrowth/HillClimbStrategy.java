package ca.bc.gov.catchment.ridgegrowth;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.LineString;
import org.opengis.feature.simple.SimpleFeature;

import ca.bc.gov.catchment.fitness.AvgElevationSectionFitness;
import ca.bc.gov.catchment.fitness.ElevationPointFitness;
import ca.bc.gov.catchment.tin.TinEdges;
import ca.bc.gov.catchment.uncertainty.PointUncertainty;
import ca.bc.gov.catchment.uncertainty.UncertaintyHelpers;
import ca.bc.gov.catchment.utils.SpatialUtils;
import ca.bc.gov.catchment.water.Water;

public class HillClimbStrategy implements RidgeGrowthStrategy {

	private static final int MAX_NUM_POINTS = 5000;
	private static final int DEFAULT_LOOK_AHEAD = 2;
	
	private Comparator<Coordinate> elevationComparator;
	private Water water;
	private TinEdges tinEdges;
	private int lookAhead;
	private PointUncertainty uncertainty;
	
	public HillClimbStrategy(Water water, TinEdges tinEdges) {
		this(water, tinEdges, new PointUncertainty(0,  0), DEFAULT_LOOK_AHEAD);
	}
	
	public HillClimbStrategy(Water water, TinEdges tinEdges, PointUncertainty uncertainty, int lookAhead) {
		this.water = water;
		this.tinEdges = tinEdges;
		this.lookAhead = lookAhead;
		this.uncertainty = uncertainty;
		this.elevationComparator = ElevationPointFitness.getCoordinateComparator();
	}
	
	public LineString growRidge(RidgeGrowthTask task) throws IOException {
		Coordinate confluence = task.getSeedCoord();
		LineString stem = task.getStem();
		
		//make sure sure the first point of stem is the confluence.  if not, reverse the stem
		if (task.getSeedCoord().equals(stem.getCoordinateN(stem.getNumPoints()-1))) {
			stem = (LineString)stem.reverse();
		}
				
		return growBestRidge(confluence, stem, task.getAdjacentWater());
	}

	
	private LineString growBestRidge(Coordinate fromConfluence, 
			LineString seedEdge, 
			List<SimpleFeature> adjacentWater) throws IOException {
		
		LineString ridgeLineString = growBestRidgeImpl(seedEdge, adjacentWater);	
		return ridgeLineString;
	}
	
	/**
	 * Use a look-ahead-by-N algorithm to choose the best edge for each extension
	 * @param stem
	 * @param adjacentWater
	 * @return
	 * @throws IOException
	 */
	private LineString growBestRidgeImpl(LineString stem, List<SimpleFeature> adjacentWater) throws IOException {

		if (stem.getNumPoints() < 2) {
			throw new IllegalArgumentException("'stemCoords' must contain at least two coordinates");
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
	
	public boolean canChooseNext(LineString stem, List<SimpleFeature> adjacentWater) throws IOException {
		Coordinate nextCoord = chooseNext(stem, adjacentWater);
		return nextCoord != null;
	}
	
	public Coordinate chooseNext(LineString stem, List<SimpleFeature> adjacentWater) throws IOException {
		
		Coordinate nextCoord = null;
		List<LineString> growthPossibilities = getAllLineExtensions(stem, true, lookAhead);
		LineString bestGrowthPossibility = pickBestGrowthPossibility4(stem, growthPossibilities);
	
		if (bestGrowthPossibility != null && bestGrowthPossibility.getNumPoints() > 1) {
			//coordinate 0 is the last coordinate of the stem, 
			//so coordinate 1 is the extension coordinate
			nextCoord = bestGrowthPossibility.getCoordinateN(1);
		}
		
		//Note: this is an experimental restriction which is intended to avoid creating lines that approach a summit
		//by circling the summit and then getting stuck in the centre of a spiral with no more possible path 
		//extensions.  preliminary tests suggest the restriction helps when used in
		//conjunction with a PlanAPlanBStrategy[PlanA=HillClimbStrategy, PlanB=MedialAxisStrategy]
		//this restriction does increase run time by about 30% when used with a PlanAPlanBStrategy.
		//Update: a weakness has been found.  this approach doesn't allow the next best growth possibility to be selected.
		//instead, update the 'pickBestGrowthPossibility' algorithm to secondary sort by 'isMovingAway' from nextCoord
		//(it already primary sorts by isMovingAway from the end of the extension)
		if (nextCoord != null) {
			boolean isMovingAway = RidgeGrowthHelpers.isMovingAway(stem,  nextCoord);
			if (!isMovingAway) {
				nextCoord = null;
			}
		}
		
		if (bestGrowthPossibility != null) {
			boolean endsHigher = bestGrowthPossibility.getCoordinateN(bestGrowthPossibility.getNumPoints()-1).getZ() > bestGrowthPossibility.getCoordinateN(0).getZ();			
			double verticalChange = SpatialUtils.getHighestZ(bestGrowthPossibility) - SpatialUtils.getLowestZ(bestGrowthPossibility);
			boolean isTooFlat = verticalChange < uncertainty.getUncertaityVertical();
			if (isTooFlat || ! endsHigher) {
				nextCoord = null;
			}		

		}

		
		
		return nextCoord;
	}
	
	/**
	 * get all possible extensions to the given stem.  the resulting linestrings only exclude the extension, not the stem portion.
	 * 
	 * Implementation note: this function calls "getAllExtendedLines", which returns linestrings with stem+extension.
	 * Then this function trims off the stems.
	 * @param stem the stem which extensions will be found for
	 * @param adjacentWater
	 * @param validOnly only consider valid lines? 
	 * @param maxExtensionLen a value >= 1
	 * @return
	 * @throws IOException
	 */
	private List<LineString> getAllLineExtensions(LineString stem, boolean validOnly, int maxExtensionLen) throws IOException {
		List<LineString> stemsPlusExtensions = getAllExtendedLines(stem, validOnly, maxExtensionLen);
		List<LineString> onlyExtensions = new ArrayList<LineString>();
		for(LineString s : stemsPlusExtensions) {
			LineString ext = SpatialUtils.slice(s, stem.getNumPoints()-1, s.getNumPoints()-1);
			onlyExtensions.add(ext);
		}
		return onlyExtensions;
	}
	

	/**
	 * get all possible lines which start with the given stem and are extended by up to 'maxExtensionLen'.
	 * (the results are linestrings which include the stem and the extension).
	 * 
	 * Implementation note: although we're really more interested in linestrings representing extensions only (not stem+extension),
	 * it's necessary to include the stem during the processing so we can confirm there are no self-intersections.  The stems can be 
	 * trimmed off by a separate function if necessary.  see "getAllLineExtensions"
	 * 
	 * @param stem the stem which extensions will be found for
	 * @param adjacentWater
	 * @param validOnly only consider valid lines? 
	 * @param maxExtensionLen a value >= 1
	 * @return
	 * @throws IOException
	 */
	private List<LineString> getAllExtendedLines(LineString stem, boolean validOnly, int maxExtensionLen) throws IOException {
		
		if (maxExtensionLen < 1) {
			throw new IllegalArgumentException("'maxExtensionLen' must be >= 1");
		}
		
		List<Coordinate> stemCoords = SpatialUtils.toCoordinateList(stem.getCoordinates());
		Coordinate leadingCoord = stem.getCoordinateN(stem.getNumPoints()-1);
		List<Coordinate> nextCoordsToConsider = tinEdges.getConnectedCoordinates(leadingCoord);
		nextCoordsToConsider.sort(elevationComparator);
		
		List<LineString> allExtensions = new ArrayList<LineString>();

		for (Coordinate ext : nextCoordsToConsider) {
			boolean isValid = RidgeGrowthHelpers.isCoordValidInRidge(ext, stemCoords, water);
						
			if (!isValid) {
				continue;
			}

			LineString extendedStem = RidgeGrowthHelpers.extend(stem, ext); 
			if (maxExtensionLen > 1) {
				List<LineString> extensions = getAllExtendedLines(extendedStem, validOnly, maxExtensionLen-1);
				allExtensions.addAll(extensions);
			}
			if (!allExtensions.contains(extendedStem)) {
				allExtensions.add(extendedStem);
			}			
			
		}
		
		return allExtensions;

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
				
				boolean m1 = RidgeGrowthHelpers.isMovingAway(s1, s1.getCoordinateN(s1.getNumPoints()-1));
				boolean m2 = RidgeGrowthHelpers.isMovingAway(s2, s2.getCoordinateN(s2.getNumPoints()-1));
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
				
				boolean m1 = RidgeGrowthHelpers.isMovingAway(s1, s1.getCoordinateN(s1.getNumPoints()-1));
				boolean m2 = RidgeGrowthHelpers.isMovingAway(s2, s2.getCoordinateN(s2.getNumPoints()-1));
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
	 * The drawback is that sometimes pathing goes downhill in some non-intuitive ways.
	 * @param lookAheads
	 * @return
	 */
	private LineString pickBestGrowthPossibility3(final LineString stem, List<LineString> lookAheads) {		
		if (lookAheads == null || lookAheads.size() == 0) {
			return null;
		}
		
		//sort by:
		//1. is moving away?
		//2. number of coordinates in line
		//3. average elevation rise (above the lowest coord) divided by length of line
		// e..g if Z values of growth possibility are 618m, 625m, 634m, the average will be the average
		//Z above the lowest coord will be 7.6m.  that value will be divided by the line length
		final AvgElevationSectionFitness avgElevationFitness = new AvgElevationSectionFitness(tinEdges);
		Comparator<LineString> lookAheadComparator = new Comparator<LineString>() {
			public int compare(LineString s1, LineString s2) {
											
				//is end of extension moving away
				boolean m1 = RidgeGrowthHelpers.isMovingAway(stem, s1.getCoordinateN(s1.getNumPoints()-1));
				boolean m2 = RidgeGrowthHelpers.isMovingAway(stem, s2.getCoordinateN(s2.getNumPoints()-1));
				if (m1 != m2) {
					return m1 ? -1 : 1;
				}
				else {
					//is start of extension moving away
					boolean a1 = RidgeGrowthHelpers.isMovingAway(stem, s1.getCoordinateN(1));
					boolean a2 = RidgeGrowthHelpers.isMovingAway(stem, s2.getCoordinateN(1));
					if (a1 != a2) {
						return a1 ? -1 : 1;
					}
					else {
					
						if (s1.getNumPoints() == s2.getNumPoints()) {
							try {							
								double fit1 = (avgElevationFitness.fitness(s1) - s1.getCoordinateN(0).getZ()) / s1.getLength();
								double fit2 = (avgElevationFitness.fitness(s2) - s2.getCoordinateN(0).getZ()) / s2.getLength();
								
								if (fit1<0) {
									fit1 = 1/fit1;
								}
								if (fit2<0) {
									fit2 = 1/fit2;
								}
								
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
			}
		};
		lookAheads.sort(lookAheadComparator);
		
		return lookAheads.get(0);
	}
	
	/**
	 * this implementation attempts to work well with non-delaunay triangles (as in #3), but reduce the occasional, 
	 * non-intuitive downhill pathing. 
	 * 
	 * @param lookAheads
	 * @return
	 */
	public LineString pickBestGrowthPossibility4(final LineString stem, List<LineString> lookAheads) {		
		if (lookAheads == null || lookAheads.size() == 0) {
			return null;
		}
		
		//sort by:
		//1. is moving away?
		//2. number of coordinates in line
		//3. line ends higher than it starts? (prefer those that do)
		//4. average elevation rise (above the lowest coord) divided by length of line
		// e..g if Z values of growth possibility are 618m, 625m, 634m, the average will be the average
		//Z above the lowest coord will be 7.6m.  that value will be divided by the line length.  may be negative if line moves downward
		Comparator<LineString> lookAheadComparator = new Comparator<LineString>() {
			public int compare(LineString s1, LineString s2) {
											
				//is end of extension moving away
				boolean m1 = RidgeGrowthHelpers.isMovingAway(stem, s1.getCoordinateN(s1.getNumPoints()-1));
				boolean m2 = RidgeGrowthHelpers.isMovingAway(stem, s2.getCoordinateN(s2.getNumPoints()-1));
				if (m1 != m2) {
					return m1 ? -1 : 1;
				}
				else {
					//is start of extension moving away
					boolean a1 = RidgeGrowthHelpers.isMovingAway(stem, s1.getCoordinateN(1));
					boolean a2 = RidgeGrowthHelpers.isMovingAway(stem, s2.getCoordinateN(1));
					if (a1 != a2) {
						return a1 ? -1 : 1;
					}
					else {
					
						//prefer lines that end higher than they start
						boolean endsHigher1 = s1.getCoordinateN(0).getZ() <= s1.getCoordinateN(s1.getNumPoints()-1).getZ();
						boolean endsHigher2 = s2.getCoordinateN(0).getZ() <= s2.getCoordinateN(s2.getNumPoints()-1).getZ();
						if (endsHigher1 != endsHigher2) {
							return endsHigher1 ? -1 : 1;							
						}
						else {
						
							if (s1.getNumPoints() == s2.getNumPoints()) {
					
								double slope1 = (SpatialUtils.getAverageElevation(s1) - s1.getCoordinateN(0).getZ()) / s1.getLength();
								double slope2 = (SpatialUtils.getAverageElevation(s2) - s2.getCoordinateN(0).getZ()) / s2.getLength();
								
								//System.out.println("1. # pts: "+s1.getNumPoints()+", avg elev:"+SpatialUtils.getAverageElevation(s1)+", first:"+s1.getCoordinateN(0).getZ() + ", len:"+s1.getLength()+", slope: "+slope1);
								//System.out.println("2. # pts: "+s2.getNumPoints()+", avg elev:"+SpatialUtils.getAverageElevation(s2)+", first:"+s2.getCoordinateN(0).getZ() + ", len:"+s2.getLength()+", slope: "+slope2);
								
								/*
								if (slope1<0) {
									slope1 = 1/slope1;
								}
								if (slope2<0) {
									slope2 = 1/slope2;
								}
								*/
								
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
				}
			}
		};
		lookAheads.sort(lookAheadComparator);
		/*
		for (LineString ls : lookAheads) {
			double slope =  (SpatialUtils.getAverageElevation(ls) - ls.getCoordinateN(0).getZ()) / ls.getLength();
			System.out.println("# pts: "+ls.getNumPoints()+", avg elev:"+SpatialUtils.getAverageElevation(ls)+", first:"+ls.getCoordinateN(0).getZ() + ", len:"+ls.getLength()+", slope: "+slope);
		}
		System.exit(1);
		*/	
		
		return lookAheads.get(0);
	}
	
}
