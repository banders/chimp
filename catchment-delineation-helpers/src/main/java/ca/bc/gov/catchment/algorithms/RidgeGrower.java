package ca.bc.gov.catchment.algorithms;

import java.io.IOException;
import java.util.ArrayList;
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
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;

import ca.bc.gov.catchment.fitness.ElevationSectionFitness;
import ca.bc.gov.catchment.fitness.SectionFitness;
import ca.bc.gov.catchment.tin.TinEdges;
import ca.bc.gov.catchment.utils.SpatialUtils;
import ca.bc.gov.catchment.water.Water;

public abstract class RidgeGrower {

	private static final double UNCERTAINTY_Z = 2;
	
	protected static final String RIDGE_TABLE_NAME = "ridges";
	protected Water water;
	protected TinEdges tinEdges;
	private SectionFitness seedEdgeFitness;
	private SimpleFeatureType ridgeFeatureType;
	
	public RidgeGrower(Water water, TinEdges tinEdges) {
		this.water = water;
		this.tinEdges = tinEdges;
		this.seedEdgeFitness = new ElevationSectionFitness(tinEdges);
		
		//create a feature type for the ridge features that are created
		this.ridgeFeatureType = null;
		try {
			ridgeFeatureType = DataUtilities.createType(RIDGE_TABLE_NAME, "geometry:LineString");
		} catch (SchemaException e1) {
			System.out.println("Unable to create feature type "+RIDGE_TABLE_NAME);
			System.exit(1);
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
	
	public SimpleFeatureType getRidgeFeatureType() {
		return this.ridgeFeatureType;
	}
	
	public abstract SimpleFeature growRidge(Coordinate confluence, LineString seedEdge, List<SimpleFeature> adjacentWater) throws IOException;

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
				SimpleFeature seedEdge = getBestSeedEdge(edgesBetweenWater);
				if (seedEdge != null) {
					seedEdges.add(seedEdge);
				}
				edgesBetweenWater.clear();
			}
			
		}
		
		return seedEdges;
	}	
	
	public abstract boolean canChooseNext(LineString stem, List<SimpleFeature> adjacentWater) throws IOException;
	
	public abstract Coordinate chooseNext(LineString stem, List<SimpleFeature> adjacentWater) throws IOException;
	
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
	private SimpleFeature getBestSeedEdge(List<SimpleFeature> edgesBetweenWater) throws IOException {
		SimpleFeature bestEdge = null;
		double bestFit = Double.NaN;
		for(SimpleFeature edge : edgesBetweenWater) {
			double fit = seedEdgeFitness.fitness((Geometry)edge.getDefaultGeometry());
			if (Double.isNaN(bestFit) || fit > bestFit) {
				bestFit = fit;
				bestEdge = edge;
			}
		}
		return bestEdge;
	}
	
	
	/**
	 * determines if a coordinate is a valid member of a ridge line.
	 * @param coord
	 * @param ridgeCoords
	 * @param adjacentWater
	 * @return
	 * @throws IOException 
	 */
	protected boolean isCoordValid(Coordinate coord, List<Coordinate> ridgeCoords, List<SimpleFeature> adjacentWater) throws IOException {

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
	 * determines if a proposed next coordinate gets farther from or closer to the start of the line
	 * @param stem
	 * @param nextCoord
	 * @return
	 */
	protected boolean isMovingAway(LineString stem, Coordinate nextCoord) {
		Coordinate start = stem.getCoordinateN(0);
		Coordinate end = stem.getCoordinateN(stem.getNumPoints()-1);
				
		double dist1 = start.distance(end);
		double dist2 = start.distance(nextCoord);
		
		boolean isMovingAway = dist2 > dist1;
		return isMovingAway;
	}
	
	/**
	 * returns true if a could be higher than b, or the same as b (within uncertainty)
	 * @param a
	 * @param b
	 * @return
	 */
	protected boolean couldBeHigherOrSameWithinUncertainty(Coordinate a, Coordinate b) {
		return a.getZ() + UNCERTAINTY_Z >= b.getZ() - UNCERTAINTY_Z;
	}
	
	/**
	 * returns true if a could be higher than b (within uncertainty)
	 * @param a
	 * @param b
	 * @return
	 */
	protected boolean couldBeHigherWithinUncertainty(Coordinate a, Coordinate b) {
		return a.getZ() + UNCERTAINTY_Z > b.getZ() - UNCERTAINTY_Z;
	}
	
	/**
	 * returns true if a is definately higher than b (within uncertainty)
	 * @param a
	 * @param b
	 * @return
	 */
	protected boolean definatelyHigherWithinUncertainty(Coordinate a, Coordinate b) {
		return a.getZ() - UNCERTAINTY_Z > b.getZ() + UNCERTAINTY_Z;
	}
	
	/**
	 * returns true if a is definately higher than b (within uncertainty)
	 * @param a
	 * @param b
	 * @return
	 */
	protected boolean definatelyHigherOrSameWithinUncertainty(Coordinate a, Coordinate b) {
		return a.getZ() - UNCERTAINTY_Z >= b.getZ() + UNCERTAINTY_Z;
	}
	
	/**
	 * shorten the given linestring to the given number of coordinates.  If points are to be removed,
	 * they are removed from the end.
	 * @param s
	 * @param len
	 * @return
	 */
	protected LineString clipToLength(LineString s, int len) {
		if (s.getNumPoints() <= len) {
			return s;
		}
		
		List<Coordinate> clippedCoords = new ArrayList<Coordinate>();
		for(Coordinate coord : s.getCoordinates()) {
			clippedCoords.add(coord);
			if (clippedCoords.size() >= len) {
				break;
			}
		}
		return SpatialUtils.toLineString(clippedCoords);
	}
	

	
}