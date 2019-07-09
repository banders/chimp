package ca.bc.gov.catchment.voronoi;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.measure.Unit;

import org.apache.commons.math3.util.Pair;
import org.geotools.data.DataUtilities;
import org.geotools.data.FeatureSource;
import org.geotools.data.collection.SpatialIndexFeatureCollection;
import org.geotools.data.collection.SpatialIndexFeatureSource;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.factory.CommonFactoryFinder;
import org.geotools.feature.DefaultFeatureCollection;
import org.geotools.feature.FeatureCollection;
import org.geotools.feature.FeatureIterator;
import org.geotools.feature.SchemaException;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.geometry.jts.JTSFactoryFinder;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.referencing.CRS;
import org.locationtech.jts.algorithm.Distance;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.FeatureType;
import org.opengis.filter.Filter;
import org.opengis.filter.FilterFactory2;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

public class VoronoiDoubleEdgeCleaner {
	
	private static final double TOUCHES_DISTANCE_TOLERANCE = 0.0001; 
	private static final double MAX_LENGTH_TO_KEEP_IN_VORONOI_UNITS = 20000;
	private static final double MIN_LENGTH_TO_KEEP_IN_VORONOI_UNITS = 0.01; //1 cm
	private static final int NUM_X_TILES = 10;
	private static final int NUM_Y_TILES = 10;
	
	private String keptTypeName;
	private String discardedTypeName;
	
	private SimpleFeatureSource voronoiEdgesFeatureSource;
	private SimpleFeatureSource waterFeatureSource;
	private CoordinateReferenceSystem voronoiEdgesCrs;
	
	SimpleFeatureType voronoiEdgesFeatureType;
	SimpleFeatureType waterFeaturesType;
	int numVoronoiEdges;
	int numWaterFeatures;
	
	Set<Point> pointsProcessed;
	int numConfluencePoints;
	int numNonConfluencePoints;
	int numProblematicConcluencePoints;
	
	private SimpleFeatureType keptFeatureType;
	private SimpleFeatureType discardedFeatureType;
	private SimpleFeatureBuilder keptFeatureBuilder;
	private SimpleFeatureBuilder discardedFeatureBuilder;
	
	private Unit<?> distanceUnit;
	
	public VoronoiDoubleEdgeCleaner(SimpleFeatureSource voronoiEdgesFeatureSource, 
			SimpleFeatureSource waterFeatureSource,
			String keptTypeName,
			String discardedTypeName) throws IOException, FactoryException {
		
		//add spatial index to the voronoi features
		SpatialIndexFeatureCollection vfc = new SpatialIndexFeatureCollection(voronoiEdgesFeatureSource.getFeatures());
		this.voronoiEdgesFeatureSource = new SpatialIndexFeatureSource(vfc);
		this.numVoronoiEdges = vfc.size();
		
		this.pointsProcessed = new HashSet<Point>();
		this.numConfluencePoints = 0;
		this.numNonConfluencePoints = 0;
		this.numProblematicConcluencePoints = 0;

		//add a spatial index to the water features
		SpatialIndexFeatureCollection wfc = new SpatialIndexFeatureCollection(waterFeatureSource.getFeatures());
		this.waterFeatureSource = new SpatialIndexFeatureSource(wfc);	
		this.numWaterFeatures = wfc.size();
		
		this.voronoiEdgesFeatureType = voronoiEdgesFeatureSource.getSchema();
		this.waterFeaturesType = waterFeatureSource.getSchema();
		
		this.keptTypeName = keptTypeName;
		this.discardedTypeName = discardedTypeName;
		
		voronoiEdgesCrs = voronoiEdgesFeatureType.getGeometryDescriptor().getCoordinateReferenceSystem();
		this.distanceUnit = voronoiEdgesCrs.getCoordinateSystem().getAxis(0).getUnit();
	
		int srid = CRS.lookupEpsgCode(voronoiEdgesCrs, true);
		
		keptFeatureType = null;
		try {
			keptFeatureType = DataUtilities.createType(keptTypeName, "geometry:LineString:srid="+srid);
		} catch (SchemaException e1) {
			System.out.println("Unable to create feature type "+keptTypeName);
			System.exit(1);
		}
		
		discardedFeatureType = null;
		try {
			discardedFeatureType = DataUtilities.createType(discardedTypeName, "geometry:LineString:srid="+srid+",num_touch:int");
		} catch (SchemaException e1) {
			System.out.println("Unable to create feature type "+discardedTypeName);
			System.exit(1);
		}
		
		
		keptFeatureBuilder = new SimpleFeatureBuilder(keptFeatureType);
		discardedFeatureBuilder = new SimpleFeatureBuilder(discardedFeatureType);
		
		
		
		//System.out.println("   - Distance tolerance for 'touching' lines is: "+TOUCHES_DISTANCE_TOLERANCE + " " +distanceUnit.toString());
		
	}
	
	public KeptAndDiscarded clean() throws IOException, FactoryException {
		KeptAndDiscarded finalResult = new KeptAndDiscarded(keptFeatureType, discardedFeatureType);

		GeometryFactory geometryFactory = JTSFactoryFinder.getGeometryFactory();
		SimpleFeatureCollection inFeatures = this.waterFeatureSource.getFeatures();
		SimpleFeatureIterator it = inFeatures.features();
		int waterFeatureIndex = 0;
		Date t1 = new Date();
		
		while(it.hasNext()) {
			waterFeatureIndex++;
			SimpleFeature inFeature = it.next();
			Geometry inGeometry = (Geometry)inFeature.getDefaultGeometry();
			Coordinate[] coords = inGeometry.getCoordinates();
			for(Coordinate coord : coords) {
				Point p = geometryFactory.createPoint(coord);
				if (!pointsProcessed.contains(p)) {
					KeptAndDiscarded keptAndDiscardedAtThisPoint = cleanEdgesTouchingPoint(this.waterFeatureSource, this.voronoiEdgesFeatureSource, p);
					finalResult.addDiscarded(keptAndDiscardedAtThisPoint.getDiscarded());
					pointsProcessed.add(p);
				}				
			}
			
			int progressInterval = 10000;
			if (waterFeatureIndex % progressInterval == 0) {
				waterFeatureIndex++;
				Date t2 = new Date();
				long elapsed = Math.round((t2.getTime()-t1.getTime())/1000.0);
				float rate = progressInterval / elapsed;
				System.out.println("Progress:");
				System.out.println(" - "+Math.round(1.0*waterFeatureIndex/this.numWaterFeatures*100)+"% complete");
				System.out.println(" - rate: "+rate+" water features per second");				
				System.out.println(" - "+numNonConfluencePoints+" non-confluence points ignored");
				System.out.println(" - "+numConfluencePoints+" confluence points inspected");
				System.out.println("   - "+this.numProblematicConcluencePoints+" with problems");
				System.out.println(" - "+finalResult.getNumDiscarded()+" of "+this.numVoronoiEdges +" voronoi edges discarded");
				t1 = t2;
			}
		}

		System.out.println("Processing complete.");
		System.out.println("Building result set...");
		
		//result set: 
		//	discarded values have already been set in the loop above
		//  kept values will be calculated as: kept = original - discarded
		SimpleFeatureCollection original = toKept(this.voronoiEdgesFeatureSource.getFeatures());
		SimpleFeatureIterator originalIt = original.features();
		SimpleFeatureCollection discarded = finalResult.getDiscarded();
		while(originalIt.hasNext()) {
			SimpleFeature f = originalIt.next();
			if (!discarded.contains(f)) {
				finalResult.addKept(f);
			}
		}
		
		return finalResult;		
	}
	
	/*
	 * 
	 * Doubled voronoi edges can occur only at confluence points in the water network.
	 * At a confluence point there are three (or possibly more?) touching water features.
	 * In a normal (valid) case there will be an equal number of voronoi edges as water features
	 * touching the confluence point.  (e.g. 3 voronoi edges, 3 water features).  
	 * In addition, there should be an alternating pattern of water features and voronoi edges
	 * if proceeding clockwise (or counterclockwise) around the confluence.
	 * This function fixes the situation where there is:
	 *  - an extra voronoi edge at the confluence (e.g. 4 voronoi edges, 3 water features)
	 *  - the pattern of alternating edge types clockwise is violated.
	 * The situation is fixed by removing one of the extra voronoi edges which violates
	 * the clockwise alternating pattern.
	 */
	private KeptAndDiscarded cleanEdgesTouchingPoint(
			SimpleFeatureSource inWaterFeatures, 
			SimpleFeatureSource inVoronoiFeatures, 
			Point p) throws IOException {
		
		KeptAndDiscarded result = new KeptAndDiscarded(keptFeatureType, discardedFeatureType);
		
		FilterFactory2 ff = CommonFactoryFinder.getFilterFactory2();
		
		//identify water features touching the given point
		SimpleFeatureType waterFeatureType = inWaterFeatures.getSchema();
		String waterGeomProperty = waterFeatureType.getGeometryDescriptor().getLocalName();
		Filter waterFeatureFilter = ff.dwithin(
				ff.property(waterGeomProperty), 
				ff.literal(p), 
				TOUCHES_DISTANCE_TOLERANCE, 
				distanceUnit.toString());
		//Filter waterFeatureFilter = ff.touches(ff.property(waterGeomProperty), ff.literal(p));
		SimpleFeatureCollection touchingWaterFeatures = waterFeatureSource.getFeatures(waterFeatureFilter);

		//identify voronoi edges touching the given point
		SimpleFeatureType voronoiFeatureType = inVoronoiFeatures.getSchema();
		String voronoiGeomProperty = voronoiFeatureType.getGeometryDescriptor().getLocalName();
		Filter voronoiFeatureFilter = ff.dwithin(
				ff.property(voronoiGeomProperty), 
				ff.literal(p), 
				TOUCHES_DISTANCE_TOLERANCE, 
				distanceUnit.toString());
		//Filter voronoiFeatureFilter = ff.touches(ff.property(voronoiGeomProperty), ff.literal(p));
		SimpleFeatureCollection touchingVoronoiFeatures = inVoronoiFeatures.getFeatures(voronoiFeatureFilter);
		
		//only edges touching confluence points need to be considered
		boolean isConfluence = touchingWaterFeatures.size() >= 3;
		if (!isConfluence) {
			//this is not a confluence point, so it won't have double edges.
			//all voronoi edges touching the point are kept.
			this.numNonConfluencePoints++;
			result.addKept(toKept(touchingVoronoiFeatures));
			return result;
		}
		else {
			this.numConfluencePoints++;
		}

		
		if (touchingWaterFeatures.size() > touchingVoronoiFeatures.size()) {
			throw new IllegalStateException("Voronoi diagram is invalid at confluence point: "+p.getX()+","+p.getY()+". "+ touchingVoronoiFeatures.size() +" touching voronoi edges < "+touchingWaterFeatures.size()+" touching water features.  This scenario cannot be fixed by cleaning");
		}
		
		//this is an invalid confluence point with too many voronoi edges.  Any doubled edges can be removed.
		
		//first step: compute the edge angles so we can inspect the pattern of edges around the confluence point
		List<Pair<Double, SimpleFeature>> allTouching = new ArrayList<Pair<Double, SimpleFeature>>();
		
		//compute angles for touching water features
		SimpleFeatureIterator touchingWaterFeatureIt = touchingWaterFeatures.features(); 
		while(touchingWaterFeatureIt.hasNext()) {
			SimpleFeature waterFeature = touchingWaterFeatureIt.next();
			double angleDegrees = getAngleDegrees(p, (Geometry)waterFeature.getDefaultGeometry());
			Pair<Double, SimpleFeature> pair = new Pair(angleDegrees, waterFeature);
			allTouching.add(pair);
		}
		touchingWaterFeatureIt.close();
		
		//compute angles for touching voronoi features
		SimpleFeatureIterator touchingVoronoiFeatureIt = touchingVoronoiFeatures.features(); 
		int tooShort = 0;
		while(touchingVoronoiFeatureIt.hasNext()) {
			SimpleFeature voronoiFeature = touchingVoronoiFeatureIt.next();
			Geometry geometry = (Geometry)voronoiFeature.getDefaultGeometry();
			Coordinate[] coords = geometry.getCoordinates();
			//String coordStr = coords[0].x+","+coords[0].y+" "+coords[1].x+","+coords[1].y;
			
			double angleDegrees = getAngleDegrees(p, geometry);
			//System.out.println(voronoiFeature.getID() + " " + coordStr + " " + angleDegrees);
			Pair<Double, SimpleFeature> pair = new Pair(angleDegrees, voronoiFeature);
			if (geometry.getLength() < TOUCHES_DISTANCE_TOLERANCE) {
				tooShort++;
				continue;				
			}
			allTouching.add(pair);
		}
		touchingVoronoiFeatureIt.close();
		int numTouchingVoronoi = touchingVoronoiFeatures.size() - tooShort;
				
		if (touchingWaterFeatures.size() == numTouchingVoronoi) {
			//assume this is a valid confluence point touched by alternating water features and voronoi edges 
			result.addKept(toKept(touchingVoronoiFeatures));
			return result;
		}
		
		numProblematicConcluencePoints++;
		
		//sort all touching features by angle
		allTouching.sort(new Comparator<Pair<Double, SimpleFeature>>() {
			public int compare(Pair<Double, SimpleFeature> o1, Pair<Double, SimpleFeature> o2) {
				return o1.getFirst() < o2.getFirst() ? -1 
					 : o1.getFirst() > o2.getFirst() ? 1 
				     : 0;
			}			
		});
		
		//System.out.println(touchingWaterFeatures.size() + " touching water");
		//System.out.println(numTouchingVoronoi + " touching voronoi");
		//System.out.println(allTouching.size() + " combined");
		
		//System.out.println("Problem detected at confluence: "+p.getX()+","+p.getY());
		//printPattern(allTouching);
		
		//copy the first item to the end of the list so the loop below can detect
		//doubled edgeson either side of the 0 degree/360 degree divide
		allTouching.add(allTouching.get(0)); 
		
		//second step: using the angles compute above, identify the voronoi edges that can be removed
		Pair<Double, SimpleFeature> prev = null;
		for (Pair<Double, SimpleFeature> pair : allTouching) {
			
			if (prev != null) {
				SimpleFeatureType thisFeatType = pair.getSecond().getFeatureType();
				SimpleFeatureType prevFeatType = prev.getSecond().getFeatureType();				
				boolean isVoronoiEdge = thisFeatType.equals(this.voronoiEdgesFeatureType);
				boolean isDoubledVoronoiEdge = prevFeatType.equals(thisFeatType) && isVoronoiEdge;
				if (isDoubledVoronoiEdge) {
					result.addDiscarded(toDiscarded(pair.getSecond()));
				}
				else if (isVoronoiEdge) {
					result.addKept(toKept(pair.getSecond()));
				}
			}
			prev = pair;
		}
		
		return result;
		
	}
	
	private double getAngleDegrees(Point fromPoint, Geometry line) {
		
		Coordinate[] coords = line.getCoordinates();
		if (coords.length < 2) {
			throw new IllegalArgumentException("geometry expected to be a line with at least two points.  actual geometry has "+coords.length+" points.");
		}
		
		Coordinate fromCoord = fromPoint.getCoordinate();
		
		Coordinate firstCoord = coords[0];
		Coordinate lastCoord = coords[coords.length-1];
		
		if (fromCoord.distance(firstCoord) > TOUCHES_DISTANCE_TOLERANCE && fromCoord.distance(lastCoord) > TOUCHES_DISTANCE_TOLERANCE) {
			throw new IllegalArgumentException("one of the line endpoints must be the same at the 'fromPoint' (within the distance tolerance)");
		}
		
		//determine which end of the line is closer to 'fromCoord'. assume
		//"from" point is the same as the first point on the close end and 
		//"to" point is the next point along from that end.
		Coordinate toCoord = null;
		if (fromCoord.distance(firstCoord) < fromCoord.distance(lastCoord)) {
			toCoord = coords[1];
		}
		else {
			toCoord = coords[coords.length-2];
		}
						
		double diffX = fromCoord.getX() - toCoord.getX();
		double diffY = fromCoord.getY() - toCoord.getY();
		double angleRadians = Math.atan2(diffY, diffX);
		double angleDegrees = Math.toDegrees(angleRadians);
		return angleDegrees;
	}
	
	private void printPattern(List<Pair<Double, SimpleFeature>> touching) {
		String result = "";
		for (Pair<Double, SimpleFeature> pair : touching) {
			String typeCode = "?";
			if (pair.getSecond().getFeatureType() == this.voronoiEdgesFeatureType) {
				typeCode = "Voronoi";
			}
			else if (pair.getSecond().getFeatureType() == this.waterFeaturesType) {
				typeCode = "Water";
			}
			else {
				throw new IllegalArgumentException("simplefeature with unknown feature type. expected voronoi edge feature type or water feature type.");
			}
			result += typeCode+"["+Math.round(pair.getFirst())+" deg] "; 
		}
		System.out.println(" "+result);
	}
	
	private SimpleFeatureCollection toKept(SimpleFeature f) {
		DefaultFeatureCollection fc = new DefaultFeatureCollection();
		fc.add(f);
		return toKept(fc);
	}
	
	private SimpleFeatureCollection toKept(SimpleFeatureCollection fc) {
		DefaultFeatureCollection result = new DefaultFeatureCollection();
		SimpleFeatureIterator it = fc.features();
		while (it.hasNext()) {
			SimpleFeature origFeature = it.next();
			Object[] attributeValues = new Object[] { origFeature.getDefaultGeometry() };
			SimpleFeature keptFeature = keptFeatureBuilder.buildFeature(origFeature.getID(), attributeValues);
			result.add(keptFeature);
		}
		it.close();
		return result;
	}
	
	private SimpleFeatureCollection toDiscarded(SimpleFeature f) {
		DefaultFeatureCollection fc = new DefaultFeatureCollection();
		fc.add(f);
		return toDiscarded(fc);
	}
	
	private SimpleFeatureCollection toDiscarded(SimpleFeatureCollection fc) {
		DefaultFeatureCollection result = new DefaultFeatureCollection();
		SimpleFeatureIterator it = fc.features();
		while (it.hasNext()) {
			SimpleFeature origFeature = it.next();
			Object[] attributeValues = new Object[] { origFeature.getDefaultGeometry() };
			SimpleFeature discardedFeature = discardedFeatureBuilder.buildFeature(origFeature.getID(), attributeValues);
			result.add(discardedFeature);
		}
		return result;
	}
	
}
