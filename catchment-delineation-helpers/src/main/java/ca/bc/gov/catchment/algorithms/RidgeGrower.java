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

import ca.bc.gov.catchment.fitness.ElevationSectionFitness;
import ca.bc.gov.catchment.fitness.SectionFitness;
import ca.bc.gov.catchment.tin.TinEdges;
import ca.bc.gov.catchment.utils.SpatialUtils;
import ca.bc.gov.catchment.water.Water;

public class RidgeGrower {

	private static final String RIDGE_TABLE_NAME = "ridges";
	
	private Water water;
	private TinEdges tinEdges;
	private SectionFitness sectionFitness;
	private Comparator<SimpleFeature> fitnessComparator;
	private int nextFid;
	private SimpleFeatureType ridgeFeatureType;
	private GeometryFactory geometryFactory;
	
	public RidgeGrower(Water water,
			TinEdges tinEdges) {
		this.nextFid = 0;
		this.water = water;
		this.tinEdges = tinEdges;
		this.sectionFitness = new ElevationSectionFitness(tinEdges);
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
		this.fitnessComparator = new Comparator<SimpleFeature>() {

			public int compare(SimpleFeature f1, SimpleFeature f2) {
				double fitness1;
				double fitness2;
				try {
					fitness1 = sectionFitness.fitness((Geometry)f1.getDefaultGeometry());
					fitness2 = sectionFitness.fitness((Geometry)f2.getDefaultGeometry());
				} catch (IOException e) {
					throw new RuntimeException("unable to determine fitness");
				}
				return fitness1 > fitness2 ? -1 
						 : fitness1 < fitness2 ? 1 
					     : 0;
			}
			
		};
		
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
			List<SimpleFeature> nextEdgesToConsider = tinEdges.getEdgesTouchingCoordinate(leadingCoord);
			nextEdgesToConsider.sort(fitnessComparator);
			
			int initialLineLen = ridgeCoords.size();
			System.out.println("to consider: "+nextEdgesToConsider.size()+ " from "+leadingCoord);
			for (SimpleFeature next : nextEdgesToConsider) {
				LineString edgeToConsider = (LineString)next.getDefaultGeometry();
				
				//choose the next coordinate to use as the leading edge
				Coordinate nextCoord = edgeToConsider.getCoordinateN(0);
				if (nextCoord.equals(leadingCoord)) {
					nextCoord = edgeToConsider.getCoordinateN(1);
				}
				
				boolean isValid = isCoordValid(nextCoord, ridgeCoords, adjacentWater);
				if (!isValid) {
					continue;
				}
				
				ridgeCoords.add(nextCoord);
				leadingCoord = nextCoord;
				break;				
			}
			int newLineLen = ridgeCoords.size();
			if (newLineLen == initialLineLen) {
				throw new RuntimeException("Unable to form a complete ridge.");
			}
			
			boolean end = water.isTouchingWater(leadingCoord) || ridgeCoords.size() > 100;
			if (end) {
				break;
			}
		}
		
		LineString ridgeLineString = SpatialUtils.toLineString(ridgeCoords);
		SimpleFeature ridgeFeature = SpatialUtils.geomToFeature(ridgeLineString, ridgeFeatureType, (nextFid++)+"");
		return ridgeFeature;
	}
	
	/**
	 * determines if a coordinate is a valid member of a ridge line.
	 * @param coord
	 * @param ridgeCoords
	 * @param adjacentWater
	 * @return
	 */
	private boolean isCoordValid(Coordinate coord, List<Coordinate> ridgeCoords, List<SimpleFeature> adjacentWater) {

		//is the coordinate already part of the line?  if so, disallow it again.  (no loops permitted)
		if (ridgeCoords.contains(coord)) {
			return false;
		}
		
		//if the coordinate touches any adjacent water feature, disallow it.
		int touchesAdjacentCount = 0;
		Point p = geometryFactory.createPoint(coord);
		for (SimpleFeature adjacentWaterFeature : adjacentWater) {
			Geometry g = (Geometry)adjacentWaterFeature;
			if (g.contains(p)) {
				touchesAdjacentCount++;
			}
		}
		if (touchesAdjacentCount == 1) { //0 is ok.  more than 1 is ok (that means confluence)
			return false;
		}
		
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
		
		for (int i = 0; i < edgesToIterate.size(); i++) {
			SimpleFeature edge = edgesToIterate.get(i);
			boolean isWater = water.isOverlappingWater((Geometry)edge.getDefaultGeometry());
			
			System.out.println("  edge "+edge+", isWater: "+isWater);
			
			if (!isWater) {
				edgesBetweenWater.add(edge);
				
				//add any non-water edges that occur *before* the first water edge
				//to the end of the edgesToIterate.  These will get processed at the end
				//note: this will affect the end condition of the For loop.
				if (seedEdges.size() == 0) {
					//edgesToIterate.add(edge);
				}
			}
			else {
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
		List<SimpleFeature> adjacentWater = new ArrayList<SimpleFeature>();
		SimpleFeature waterBefore = null;
		SimpleFeature waterAfter = null;
		boolean nextWaterIsAdjacent = false;
		for (SimpleFeature edge : edgesTouchingConfluence) {
			SimpleFeature waterFeature = water.getOverlappingWater((Geometry)edge.getDefaultGeometry());
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
