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

public class RidgeGrower {

	private static final String RIDGE_TABLE_NAME = "ridges";
	private static final int UNCERTAINTY_Z = 5; //metres
	
	private Water water;
	private TinEdges tinEdges;
	private SectionFitness sectionFitness;
	private SectionFitness avgElevationFitness;
	private Comparator<Coordinate> elevationComparator;
	private int nextFid;
	private SimpleFeatureType ridgeFeatureType;
	private GeometryFactory geometryFactory;
	
	public RidgeGrower(Water water,
			TinEdges tinEdges) {
		this.nextFid = 0;
		this.water = water;
		this.tinEdges = tinEdges;
		this.sectionFitness = new ElevationSectionFitness(tinEdges);
		this.avgElevationFitness = new AvgElevationSectionFitness(tinEdges);
		this.geometryFactory = JTSFactoryFinder.getGeometryFactory();
		
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
	
	public SimpleFeatureCollection growRidges() throws IOException {
		DefaultFeatureCollection result = new DefaultFeatureCollection();
		List<Coordinate> confluences = water.getConfluences();
		System.out.println(confluences.size() + " confluences to process...");
		for (Coordinate confluence : confluences) {
			System.out.println(" processing confluence");
			List<SimpleFeature> edgesTouchingConfluence = tinEdges.getEdgesTouchingCoordinate(confluence);
			System.out.println("  found "+edgesTouchingConfluence.size()+ " touching edges");
			List<SimpleFeature> seedEdges = getSeedRidgeEdges(edgesTouchingConfluence);
			System.out.println("  found "+seedEdges.size()+ " seed edges");
			for (SimpleFeature seedEdge : seedEdges) {
				System.out.println("   growing ridge");
				List<SimpleFeature> adjacentWater = getAdjacentWater(seedEdge, edgesTouchingConfluence);
				SimpleFeature ridge = growRidge(confluence, (LineString)seedEdge.getDefaultGeometry(), adjacentWater);
				System.out.println("    done.  fid="+ridge.getID());
				result.add(ridge);
			}
		}
		return result;
	}
	
	private SimpleFeature growRidge(Coordinate fromConfluence, 
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
		
		LineString ridgeLineString = growBestRidge(seedEdge, adjacentWater);	
		
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
	private LineString growBestRidge(LineString stem, List<SimpleFeature> adjacentWater) throws IOException {
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
			LineString ridge = growBestRidge(nextStemToTest, adjacentWater);
			
			double fit = ridge.getLength(); //avgElevationFitness.fitness(ridge);
			if (fit > bestRidgeFit) {
				bestRidge = ridge;
				bestRidgeFit = fit;
				break;
			}
		}
		
		if (bestRidge.getNumPoints() == 2) {
			System.out.println("no improvement found");
		}
		return bestRidge;
	}
	
	private SimpleFeature growQuickRidge(Coordinate fromConfluence, 
			LineString seedEdge, 
			List<SimpleFeature> adjacentWater) throws IOException {
	
		if (adjacentWater.size() != 2) {
			throw new IllegalArgumentException("precondition failed: 'adjacentWater' must contain two features");
		}
		
		//identify the coordinate at the leading end of the ridge (i.e. opposite end 
		//of the ridge line from the confluence)
		Coordinate leadingCoord = seedEdge.getCoordinateN(0);
		if (leadingCoord.equals(fromConfluence)) {
			leadingCoord = seedEdge.getCoordinateN(seedEdge.getNumPoints()-1);
		}
		
		//start the ridge line
		List<Coordinate> ridgeCoords = new ArrayList<Coordinate>();
		ridgeCoords.add(fromConfluence);
		ridgeCoords.add(leadingCoord);
		
		while(true) {
			
			//next edges to consider, sorted by best candidate first
			List<Coordinate> nextCoordsToConsider = tinEdges.getConnectedCoordinates(leadingCoord);
			
			//choose the next coordinate as the one with the highest elevation >= the current coordinate
			Coordinate nextCoord = pickNextCoordByElevation(nextCoordsToConsider, ridgeCoords, adjacentWater);
			if (nextCoord == null) {
				
				//no suitable next coordinate found by the elevation rule above.  try an alternative rule:
				//choose the coordinate which is most equadistant between the two adjacent water features
				//Note: the equidistance option doesn't work very well.
				//nextCoord = pickNextCoordByEquidistance(nextCoordsToConsider, ridgeCoords, adjacentWater);
				if (nextCoord == null) {
					System.out.println(" cannot find suitable next coordinate. stopping at length: "+ridgeCoords.size());
					break;
				}
			}
			
			/*
			boolean touchesWater = water.isTouchingWater(nextCoord);
			if (touchesWater) {
				System.out.println(" terminating at water. stopping at length: "+ridgeCoords.size());
				System.exit(1);
				break;				
			}	
			*/
			
			ridgeCoords.add(nextCoord);
			leadingCoord = ridgeCoords.get(ridgeCoords.size()-1);
			
			if (ridgeCoords.size() > 5000) {
				System.out.println(" terminated because line is too long");
				break;
			}
			
		}
		
		LineString ridgeLineString = SpatialUtils.toLineString(ridgeCoords);
		SimpleFeature ridgeFeature = SpatialUtils.geomToFeature(ridgeLineString, ridgeFeatureType, (nextFid)+"");
		nextFid += 1;
		return ridgeFeature;
	}
	
	/**
	 * returns true if a is higher than b (within uncertainty)
	 * @param a
	 * @param b
	 * @return
	 */
	private boolean isHigherOrSameWithinUncertainty(Coordinate a, Coordinate b) {
		return a.getZ() + UNCERTAINTY_Z >= b.getZ() - UNCERTAINTY_Z;
	}
	
	private Coordinate pickNextCoordByElevation(List<Coordinate> coordsToConsider, List<Coordinate> ridgeCoords, List<SimpleFeature> adjacentWater) throws IOException {
		coordsToConsider.sort(elevationComparator);
		Coordinate leadingCoord = ridgeCoords.get(ridgeCoords.size()-1);
		
		//System.out.println("to consider: "+nextEdgesToConsider.size()+ " from "+leadingCoord);
		for (Coordinate nextCoord : coordsToConsider) {
			
			boolean isValid = isCoordValid(nextCoord, ridgeCoords, adjacentWater);
			if (!isValid) {
				continue;
			}
			boolean isGettingHigher = isHigherOrSameWithinUncertainty(nextCoord, leadingCoord);
			if (!isGettingHigher) {
				continue;
			}
			
			return nextCoord;
		}
		
		return null;
	}
	
	private Coordinate pickNextCoordByEquidistance(List<Coordinate> coordsToConsider, List<Coordinate> ridgeCoords, List<SimpleFeature> adjacentWater) throws IOException {
		
		//sort edges by relative equidistance to the adjacent water features
		SimpleFeature adjacentWater1 = adjacentWater.get(0);
		SimpleFeature adjacentWater2 = adjacentWater.get(1);
		Comparator<Coordinate> comparator = EquidistantPointFitness.getCoordinateComparator(adjacentWater1, adjacentWater2);
		coordsToConsider.sort(comparator);
		
		for (Coordinate nextCoord : coordsToConsider) {
		
			boolean isValid = isCoordValid(nextCoord, ridgeCoords, adjacentWater);
			if (!isValid) {
				continue;
			}	
			return nextCoord;
		}
		
		return null;
		
	}
	
	/**
	 * determines if a coordinate is a valid member of a ridge line.
	 * @param coord
	 * @param ridgeCoords
	 * @param adjacentWater
	 * @return
	 * @throws IOException 
	 */
	private boolean isCoordValid(Coordinate coord, List<Coordinate> ridgeCoords, List<SimpleFeature> adjacentWater) throws IOException {

		//is the coordinate already part of the line?  if so, disallow it again.  (no loops permitted)
		//compare only on X and Y (not on Z)
		for(Coordinate rc : ridgeCoords) {
			if (rc.getX() == coord.getX() && rc.getY() == coord.getY()) {
				return false;
			}
		}
		
		boolean isTouchingWater = water.isTouchingWater(coord);
		if (isTouchingWater) {
			return false;
		}
		
		/*
		//if the coordinate touches any adjacent water feature, disallow it.
		int touchesAdjacentCount = 0;
		for (SimpleFeature adjacentWaterFeature : adjacentWater) {
			Geometry g = (Geometry)adjacentWaterFeature.getDefaultGeometry();
			
			//check whether the given coord is in the geometry's coordinates (2d comparison only)
			water.isTouchingWater(coord);
		}
		if (touchesAdjacentCount == 1) { //0 is ok.  more than 1 is ok (that means confluence)
			return false;
		}
		*/
		
		return true;
	}
	
	/**
	 * gets a collection of seed edges for a given a set of all edges touching a confluence.  The seed edges
	 * are a subset of the set of edges that touch the confluence.
	 * @param edgesTouchingConfluence
	 * @return
	 * @throws IOException
	 */
	private List<SimpleFeature> getSeedRidgeEdges(List<SimpleFeature> edgesTouchingConfluence) throws IOException {
		List<SimpleFeature> edgesToIterate = new ArrayList<SimpleFeature>();
		edgesToIterate.addAll(edgesTouchingConfluence);
		
		List<SimpleFeature> seedEdges = new ArrayList<SimpleFeature>();		
		List<SimpleFeature> edgesBetweenWater = new ArrayList<SimpleFeature>();
		
		//if the first edge is a water features, copy it to the end of the list (so it
		//appears at the start and the end)
		if (edgesToIterate.size() > 0) {
			SimpleFeature firstEdge = edgesToIterate.get(0);
			boolean isFirstEdgeWater = water.isOverlappingWater((Geometry)firstEdge.getDefaultGeometry());
			if(isFirstEdgeWater) {
				edgesToIterate.add(firstEdge);
			}
		}
		
		for (int i = 0; i < edgesToIterate.size(); i++) {
			SimpleFeature edge = edgesToIterate.get(i);
			boolean isWater = water.isOverlappingWater((Geometry)edge.getDefaultGeometry());
			
			//System.out.println("  edge "+edge+", isWater: "+isWater);
			
			if (!isWater) {
				edgesBetweenWater.add(edge);
			}
			else { //is water
				//choose best fitting edge from 'edgesBetweenWater' to be a seed edge
				SimpleFeature seedEdge = getBestFittingEdge(edgesBetweenWater);
				if (seedEdge != null) {
					seedEdges.add(seedEdge);
				}
				edgesBetweenWater.clear();
			}
			
		}
		
		return seedEdges;
	}
	
	private List<SimpleFeature> getAdjacentWater(SimpleFeature nonWaterEdge, List<SimpleFeature> edgesTouchingConfluence) throws IOException {
		List<SimpleFeature> edgesToIterate = new ArrayList<SimpleFeature>();
		edgesToIterate.addAll(edgesTouchingConfluence);
		if (edgesTouchingConfluence.size() > 0) {
			SimpleFeature first = edgesTouchingConfluence.get(0);
			boolean isWater = water.getTouchingWater((Geometry)first.getDefaultGeometry()) != null;
			if (isWater) {
				edgesToIterate.add(first);
			}
		}
		
		List<SimpleFeature> adjacentWater = new ArrayList<SimpleFeature>();
		SimpleFeature waterBefore = null;
		SimpleFeature waterAfter = null;
		boolean nextWaterIsAdjacent = false;
		for (SimpleFeature edge : edgesToIterate) {
			SimpleFeature waterFeature = water.getTouchingWater((Geometry)edge.getDefaultGeometry());
			if (edge.equals(nonWaterEdge)) {
				nextWaterIsAdjacent = true;
			}			
			else if (waterFeature != null && nextWaterIsAdjacent) {
				waterAfter = waterFeature;
				nextWaterIsAdjacent = false;
			}
			else if (waterFeature != null) {
				if (waterAfter == null) {
					waterAfter = waterFeature;
				}
				waterBefore = waterFeature;
			}
		}
		
		if (waterBefore == null) {
			throw new IllegalStateException("post condition failed.  'waterBefore' should not be null");
		}
		if (waterAfter == null) {
			throw new IllegalStateException("post condition failed.  'waterAfter' should not be null");
		}
		if (waterBefore == waterAfter) {
			throw new IllegalStateException("post condition failed.  'waterAfter' should not equal 'waterBefore'");
		}
		
		adjacentWater.add(waterBefore);
		adjacentWater.add(waterAfter);
		
		return adjacentWater;

		
	}
	
	/**
	 * Finds the best fitting edge from a list of edges
	 * @param edgesBetweenWater
	 * @return
	 * @throws IOException
	 */
	private SimpleFeature getBestFittingEdge(List<SimpleFeature> edgesBetweenWater) throws IOException {
		SimpleFeature bestEdge = null;
		double bestFit = Double.NaN;
		for(SimpleFeature edge : edgesBetweenWater) {
			double fit = sectionFitness.fitness((Geometry)edge.getDefaultGeometry());
			if (Double.isNaN(bestFit) || fit > bestFit) {
				bestFit = fit;
				bestEdge = edge;
			}
		}
		return bestEdge;
	}
	

}
