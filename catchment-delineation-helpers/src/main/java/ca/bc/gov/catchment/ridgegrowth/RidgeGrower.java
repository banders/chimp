package ca.bc.gov.catchment.ridgegrowth;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;

import org.geotools.data.DataUtilities;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.feature.DefaultFeatureCollection;
import org.geotools.feature.SchemaException;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Polygon;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;

import ca.bc.gov.catchment.fitness.ElevationSectionFitness;
import ca.bc.gov.catchment.fitness.SectionFitness;
import ca.bc.gov.catchment.routes.LineStringRouter;
import ca.bc.gov.catchment.routes.RouteException;
import ca.bc.gov.catchment.routes.WaterAwareLineStringRouter;
import ca.bc.gov.catchment.tin.Edge;
import ca.bc.gov.catchment.tin.TinEdges;
import ca.bc.gov.catchment.utils.SpatialUtils;
import ca.bc.gov.catchment.utils.VectorUtils;
import ca.bc.gov.catchment.water.Water;

/**
 * Grows catchments as lines extending from confluences.  The rules that define the line growth are defined 
 * in a "strategy" object.
 * 
 * example usage:
 * <pre>
 *  		int numThreads = 1;
 *			RidgeGrowthStrategy strategy = new MedialAxisStrategy(water, tinEdges);
 *			
 *			ca.bc.gov.catchment.ridgegrowth.RidgeGrower ridgeGrower = 
 *					new ca.bc.gov.catchment.ridgegrowth.RidgeGrower(water, tinEdges, strategy, numThreads);
 *			SimpleFeatureCollection ridges = ridgeGrower.growAllRidges();
 * </pre>
 * 
 * @author Brock
 * 
 * 
 *
 */
public class RidgeGrower {

	protected static final int NUM_SEED_POINTS_FOR_ISOLATED_LAKE = 2;
	protected static final String RIDGE_TABLE_NAME = "ridges";
	protected Water water;
	protected TinEdges tinEdges;	
	private SimpleFeatureType ridgeFeatureType;
	private SectionFitness seedEdgeFitness;
	private RidgeGrowthStrategy strategy;
	private int numThreads;
	
	public RidgeGrower(Water water, TinEdges tinEdges, RidgeGrowthStrategy strategy) {
		this(water,  tinEdges,  strategy, 1);
	}
	
	public RidgeGrower(Water water, TinEdges tinEdges, RidgeGrowthStrategy strategy, int numThreads) {
		this.water = water;
		this.tinEdges = tinEdges;
		this.strategy = strategy;
		this.seedEdgeFitness = new ElevationSectionFitness(tinEdges);	
		this.numThreads = numThreads;
		
		//create a feature type for the ridge features that are created
		this.ridgeFeatureType = null;
		try {
			ridgeFeatureType = DataUtilities.createType(RIDGE_TABLE_NAME, "geometry:LineString");
		} catch (SchemaException e1) {
			System.out.println("Unable to create feature type "+RIDGE_TABLE_NAME);
			System.exit(1);
		}
	}
	
	public SimpleFeatureCollection growAllRidges() throws IOException {
		
		final CountDownLatch latch = new CountDownLatch(1);
		final List<LineString> ridges = Collections.synchronizedList(new ArrayList<LineString>());
		
		//spin up the worker threads
		final List<RidgeGrowthWorker> workers = new ArrayList<RidgeGrowthWorker>();
		BlockingQueue<RidgeGrowthTask> taskQueue = new ArrayBlockingQueue<RidgeGrowthTask>(this.numThreads);
		for(int i = 0; i < this.numThreads; i++) {
			RidgeGrowthWorker worker = new RidgeGrowthWorker(taskQueue, strategy);			
			worker.addListener(new RidgeGrowthAdapter() {
				public void onRidgeSuccess(RidgeGrowthTask task, LineString ridge) {
					ridges.add(ridge);				
					System.out.println("ridge complete. count "+ridges.size());
				}
				public void onRidgeError(RidgeGrowthTask task) {
					System.out.println("ridge failed........................................");
				}
				public void onFinished() {
					System.out.println("worker finished");
					if (areAllWorkersFinished()) {
						latch.countDown();
					}
				}			
				private boolean areAllWorkersFinished() {
					
					for(RidgeGrowthWorker worker: workers) {
						if (!worker.isFinished()) {
							return false;
						}
					}
					return true;
				}
				
			});
			workers.add(worker);
			worker.start();
		}	
		
		List<Coordinate> confluences = water.getConfluences();
		System.out.println(confluences.size() + " confluences to process...");
		
		//Create "tasks" for each line to grow, and add them to the queue.  there are
		//two groups of lines to grow: a) those from confluences, b) those around isolated lakes

		//create the tasks for lines to be grown from confluences.		
		for (Coordinate confluence : confluences) {
			List<SimpleFeature> edgesTouchingConfluence = tinEdges.getEdgesTouchingCoordinate(confluence);
			List<SimpleFeature> seedEdges = getSeedEdgesAtConfluence(edgesTouchingConfluence);
			for (SimpleFeature seedEdge : seedEdges) {
				List<SimpleFeature> adjacentWater = getAdjacentWater(seedEdge, edgesTouchingConfluence);				
				RidgeGrowthTask task = new RidgeGrowthTask(confluence, (LineString)seedEdge.getDefaultGeometry(), adjacentWater);
				try {
					taskQueue.put(task);
					System.out.println("confluence task added");
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}		
			}
		}
		
		
		
		//Create the tasks for lines to grow around isolated lakes
		//Isolated lakes aren't connected to rivers, so there is no confluence coordinate to
		//start growing lines from. Instead we identify one coordinate that should be part of the catchment 
		//around the isolated lake (call this the seed coord).  Then we create two stem lines in opposite
		//directions
		List<Polygon> lakePolys = water.getLakePolygons();
		System.out.println("found "+lakePolys.size()+" lakes");
		for (Polygon lakePoly : lakePolys) {
			List<SimpleFeature> touching = water.getTouchingWater(lakePoly);			
			List<SimpleFeature> overlapping = water.getOverlappingByWater(lakePoly);			
			touching.removeAll(overlapping);
			boolean isIsolatedWater = touching.size() == 0;
			if (isIsolatedWater) {
				
				//Create tasks for growing two seed edges starting outside the lake
				//and pointing in opposite directions around the lake
				List<RidgeGrowthTask> tasks = getSeedEdgesForIsolatedLake(lakePoly, NUM_SEED_POINTS_FOR_ISOLATED_LAKE);
								
				//add the tasks to the queue
				for (RidgeGrowthTask task : tasks) {
					//stems.add(task.getStem());
					try {
						taskQueue.put(task);
						System.out.println("lake task added");
					} catch (InterruptedException e) {
						e.printStackTrace();
					}		
				}
			}			
		}
				
		
		System.out.println("all tasks have been submitted for processing");
		
		//tell the workers to finish 
		for(RidgeGrowthWorker worker : workers) {
			worker.requestFinishWhenQueueEmpty();
		}
		
		try {			
			latch.await();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		System.out.println("all workers finished");
		System.out.println("grew "+ ridges.size() +" ridges");
				
		SimpleFeatureCollection result = SpatialUtils.linestringCollectionToSimpleFeatureCollection(ridges, ridgeFeatureType);
		//SimpleFeatureCollection result = SpatialUtils.linestringCollectionToSimpleFeatureCollection(stems, ridgeFeatureType);
		return result;
	}
	
	
	/**
	 * gets a collection of seed edges for a given a set of all edges touching a confluence.  The seed edges
	 * are a subset of the set of edges that touch the confluence.
	 * @param edgesTouchingConfluence
	 * @return
	 * @throws IOException
	 */
	private List<SimpleFeature> getSeedEdgesAtConfluence(List<SimpleFeature> edgesTouchingConfluence) throws IOException {
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
	 * @param nonWaterEdge an edge in the TIN which touches a confluence at one end
	 * @param edgesTouchingConfluence a list of other edges in the TIN which also touch the same confluence
	 * @return a subset of edgesTouchingConfluence which contains exactly two edges: those corresponding to the water feature 
	 * immediately clockwise and immediately counterclockwise 
	 * @throws IOException
	 */
	private List<SimpleFeature> getAdjacentWater(SimpleFeature nonWaterEdge, List<SimpleFeature> edgesTouchingConfluence) throws IOException {
		List<SimpleFeature> edgesToIterate = new ArrayList<SimpleFeature>();
		edgesToIterate.addAll(edgesTouchingConfluence);
		if (edgesTouchingConfluence.size() > 0) {
			SimpleFeature first = edgesTouchingConfluence.get(0);
			boolean isWater = water.getContainingingWater((Geometry)first.getDefaultGeometry()) != null;
			if (isWater) {
				edgesToIterate.add(first);
			}
		}
		
		List<SimpleFeature> adjacentWater = new ArrayList<SimpleFeature>();
		SimpleFeature waterBefore = null;
		SimpleFeature waterAfter = null;
		boolean nextWaterIsAdjacent = false;
		for (SimpleFeature edge : edgesToIterate) {
			SimpleFeature waterFeature = water.getOneContainingWater((Geometry)edge.getDefaultGeometry());
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
	 * Creates N*2 ridges growth tasks for an isolated lake.
	 * If N==1 then the seed coordinate for the two stem lines is between the lake and the nearest adjacent water.
	 * If N>1 then the seed coordinates each pair of stem lines are approximately equally distributed around the lake.
	 * @param lakePoly
	 * @param N the number of pairs of growth tasks
	 * @return
	 */
	private List<RidgeGrowthTask> getSeedEdgesForIsolatedLake(Polygon lakePoly, int N) throws IOException {
		List<RidgeGrowthTask> tasks = new ArrayList<RidgeGrowthTask>();
		
		List<Coordinate> preSeedCoords = new ArrayList<Coordinate>();
		
		//Algorithm outline
		//1. iterate over each coordinate of lakePoly to find the coordinate which is closest to 
		//   another water feature (call this the first pre-seed coordinate)
		Coordinate firstPreSeedCoord = identifyCoordClosestToOtherWater(lakePoly);
		preSeedCoords.add(firstPreSeedCoord);
		
		//2. if N>1, add additional pre-seed coordinates, approximately evenly districuted around the lake
		int firstIndex = SpatialUtils.getIndexOfCoordinate(firstPreSeedCoord, lakePoly);		
		Coordinate[] lakeCoords = lakePoly.getCoordinates();
		for (int n = 1; n < N; n++) {
			int coordIndex = (firstIndex + lakeCoords.length/N) % lakeCoords.length;
			Coordinate otherPreSeedCoord = lakeCoords[coordIndex];
			preSeedCoords.add(otherPreSeedCoord);
		}
		
		//3. iterate over all pre-seed coordinates. 		
		System.out.println("seeds");
		for(Coordinate preSeedCoord : preSeedCoords) {
			preSeedCoord = tinEdges.getCoordinateAt(preSeedCoord); //get the 3d version of this coord
			if (preSeedCoord == null) {
				continue;
			}
			
			double outwardNormalCompassAngle = SpatialUtils.getOutwardNormalCompassAngle(preSeedCoord, lakePoly);
			
			//3.1. Identify the coord of the water feature nearest to the pre-seed point in the given direction
			Coordinate oppositeCoord = water.getNearestWaterCoordinateInDirection(preSeedCoord, outwardNormalCompassAngle, lakePoly);
			
			oppositeCoord = tinEdges.getCoordinateAt(oppositeCoord); //get the 3d version of this coord
			if (oppositeCoord == null) {
				continue;
			}
			
			//3.3. route a temporary line through the TIN which starts at the pre-seed coordinate and 
			//ends at the opposite coordinate (call this the span line)
			WaterAwareLineStringRouter router = new WaterAwareLineStringRouter(tinEdges, water);
			LineString spanLine = null;
			try {
				System.out.println("routing");
				spanLine = router.makeRoute(preSeedCoord, oppositeCoord);
			} catch (RouteException e) {
				continue;
			}
			
			boolean doesSpanLineCrossLake = spanLine.crosses(lakePoly);
			
			//3.4. find a seed coordinate on the scan line
			//   the seed coordinate must be a coordinate which doesn't touch water.
			if (spanLine.getNumPoints() < 3 || doesSpanLineCrossLake) {
				//the span line doesn't have any points that are in between the pre-seed coord and the opposite coord.
				continue;
			}
			
			Coordinate seedCoord = null;
			if (spanLine.getNumPoints() == 3) {
				//there's only one valid seed coord: the second coord. (the first and third coords touch water)
				seedCoord = spanLine.getCoordinateN(1);
			}
			else { //spanLine.getNumPoints() > 3
				//trim the first and last coordinates off the line (because they touch water so they aren't valid seed coordinates)
				//spanLine = SpatialUtils.slice(spanLine, 1, spanLine.getNumPoints()-2);		
				seedCoord = SpatialUtils.getHighestCoord(spanLine);
			}		
			
			
			//3.5. if elevation of seed point is within Z uncertainty of the lowest point on the line, then
			//  find the mid-point of the pre-seed line.  call this the seed coordinate.
					
			//3.6. choose two of the touching edges as stem edges.  the choices should be:
			// - approximately perpendicular to the span line
			// - each directed out a different side of the span line
			List<SimpleFeature> stemEdges = identifyStemEdgesForIsolatedLake(seedCoord, spanLine);
			
			//**for testing only**
			//tasks.add(new RidgeGrowthTask(seedCoord, spanLine));
			
			//3.7. create a ridge growth task for each stem edge
			for (SimpleFeature stemEdgeFeat : stemEdges) {
				LineString stemLine = (LineString)stemEdgeFeat.getDefaultGeometry();				
				tasks.add(new RidgeGrowthTask(seedCoord, stemLine));
			}
		}
		
		return tasks;		
	}
	
	/**
	 * This function identifies two stem lines for catchment line growing which extend
	 * in opposite directions from the given seedCoord and which are approximately perpendicular to 
	 * the given perpendicularLine
	 * @param seedCoord
	 * @param spanLine
	 * @return
	 * @throws IOException 
	 */
	private List<SimpleFeature> identifyStemEdgesForIsolatedLake(Coordinate seedCoord, LineString perpendicularLine) throws IOException {

		List<SimpleFeature> stems = new ArrayList<SimpleFeature>();
		
		//Get all the edges that touch the seed coord.  the edges are sorted clockwise		
		List<SimpleFeature> edgesTouchingSeedCoord = tinEdges.getEdgesTouchingCoordinate(seedCoord);
			
		//Get the compass angle of a touching edges which is also part of the perpendicular line.
		//This will be used as a reference when analyzing the other edges as candidate stems.
		double perpEdgeCompassAngle = Double.NaN;
		
		//identify the angle of the perpendicular line, and 
		//single out the edges which are not part of the perpendicular line
		List<SimpleFeature> nonPerpEdges = new ArrayList<SimpleFeature>();
		for (SimpleFeature edgeFeature : edgesTouchingSeedCoord) {
			LineString edgeLine = (LineString)edgeFeature.getDefaultGeometry();			
			
			//save the angle of a perpendicular edge (there should be two perpendicular edges, 
			//and it doesn't matter which one we save)
			if (edgeLine.covers(perpendicularLine) || edgeLine.coveredBy(perpendicularLine)) {
				double edgeCompassAngle = VectorUtils.angle2D(seedCoord, edgeLine);
				perpEdgeCompassAngle = edgeCompassAngle;
				continue;
			}
			else if (water.isTouchingWater(edgeLine)) {
				continue; //the chosen stem edges must not touch water
			}
			else {
				nonPerpEdges.add(edgeFeature);
			}
		}
		
		//choose two candidate edges: one clockwise from the perpendicular edge, and the other
		//counterclockwise.  ideally, candidate edge will be as close to 90 degrees off the
		//perpendicular edge.  (call this 90 ideal degree offset from the perpendicular line the
		//"ideal parallel")
		SimpleFeature bestClockwiseEdge = null;
		double minClockwiseAngleDiffFromIdealParallel = Double.NaN;
		SimpleFeature bestCounterClockwiseEdge = null;
		double minCounterClockwiseAngleDiffFromIdealParallel = Double.NaN;
		for (SimpleFeature edgeFeature : nonPerpEdges) {
			LineString edgeLine = (LineString)edgeFeature.getDefaultGeometry();			
			double edgeCompassAngle = VectorUtils.angle2D(seedCoord, edgeLine);
			
			double angleDiffFromPerpendicular = VectorUtils.getTrajectoryDiff(edgeCompassAngle, perpEdgeCompassAngle);
			double angleDiffFromIdealParallel = Math.abs(90 - angleDiffFromPerpendicular);
			boolean isClockwise = angleDiffFromPerpendicular < 0;
			
			if (isClockwise) {
				if (Double.isNaN(minClockwiseAngleDiffFromIdealParallel) || angleDiffFromIdealParallel < minClockwiseAngleDiffFromIdealParallel) {
					minClockwiseAngleDiffFromIdealParallel = angleDiffFromIdealParallel;
					bestClockwiseEdge = edgeFeature;					
				}
			}
			else {
				if (Double.isNaN(minCounterClockwiseAngleDiffFromIdealParallel) || angleDiffFromIdealParallel < minCounterClockwiseAngleDiffFromIdealParallel) {
					minCounterClockwiseAngleDiffFromIdealParallel = angleDiffFromIdealParallel;
					bestCounterClockwiseEdge = edgeFeature;
				}
			}			
		}
		
		if (bestClockwiseEdge != null) {
			stems.add(bestClockwiseEdge);
		}
		if (bestCounterClockwiseEdge != null) {
			stems.add(bestCounterClockwiseEdge);
		}
		
		return stems;
		
	}
	
	/**
	 * Identifies one coordinate of the lake poly which is closest to another water feature (besides
	 * features covered by the lake poly itself).
	 * @param lakePoly
	 * @return coordinate
	 */
	private Coordinate identifyCoordClosestToOtherWater(Polygon lakePoly) {
		Coordinate preSeedCoord = null;
		double minDistToNearestWater = Double.NaN;
		for(Coordinate coord : lakePoly.getCoordinates()) {
			
			//Note: this statement probably doesn't work as intended.  it will always return 
			//a distance to the lake poly itself, which is 0.  What we really want is the 
			//"second nearest" water feature
			Geometry excludeTouches = lakePoly;
			double distToNearestWater = water.getDistanceToNearestWater(coord, excludeTouches);
			
			if (Double.isNaN(minDistToNearestWater) || distToNearestWater < minDistToNearestWater) {				
				preSeedCoord = coord;
				minDistToNearestWater = distToNearestWater;
				
			}
		}
		return preSeedCoord;
	}
}
