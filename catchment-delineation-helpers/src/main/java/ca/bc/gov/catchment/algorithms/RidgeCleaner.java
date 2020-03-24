package ca.bc.gov.catchment.algorithms;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.geotools.data.DataUtilities;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.factory.CommonFactoryFinder;
import org.geotools.feature.DefaultFeatureCollection;
import org.geotools.feature.SchemaException;
import org.geotools.geometry.jts.JTSFactoryFinder;
import org.geotools.referencing.CRS;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryCollection;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.operation.polygonize.Polygonizer;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.filter.Filter;
import org.opengis.filter.FilterFactory2;
import org.opengis.filter.identity.FeatureId;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

import ca.bc.gov.catchment.utils.SpatialUtils;
import ca.bc.gov.catchment.utils.VectorUtils;
import ca.bc.gov.catchment.water.Water;

/**
 * This algorithm is intended to be run after RidgeGrower
 * @author Brock
 *
 */
public class RidgeCleaner {
	
	private static int NEXT_FID = 0;
	
	private SimpleFeatureCollection inRidges;
	private FilterFactory2 filterFactory;
	private GeometryFactory geometryFactory;
	private SimpleFeatureType ridgeFeatureType;
	private String ridgeGeometryPropertyName;
	private Water water;
	private LineSegmenter lineSegmenter;
	private SimpleFeatureType catchmentPolysFeatureType;
	
	public RidgeCleaner(SimpleFeatureCollection ridges, Water water, String catchmentPolysTable) throws SchemaException {	
		this.inRidges = ridges;
		this.water = water;
		this.filterFactory = CommonFactoryFinder.getFilterFactory2();
		this.geometryFactory = JTSFactoryFinder.getGeometryFactory();
		this.ridgeFeatureType = ridges.getSchema();
		this.ridgeGeometryPropertyName = ridgeFeatureType.getGeometryDescriptor().getLocalName();
		this.lineSegmenter = new LineSegmenter();

		CoordinateReferenceSystem crs = inRidges.getSchema().getCoordinateReferenceSystem();
		int srid = -1;
		try {
			srid = CRS.lookupEpsgCode(crs, true);
		} catch (FactoryException e1) {
			System.out.println("Unable to lookup SRID for feature type "+inRidges.getSchema().getTypeName());
			System.exit(1);
		}
		
		catchmentPolysFeatureType = DataUtilities.createType(catchmentPolysTable, "geometry:Polygon:srid="+srid);
	}
	
	
	public SimpleFeatureCollection doAllCleaning() throws IOException {
		SimpleFeatureCollection result = inRidges;		
		
		result =  truncateLoopbackLines(result);
		
		//result = truncateSinuousLines(inRidges);
		
		//System.out.println("split and filter out duplicates");
		
		
		//filter out "loose ends"
		//this call may not be necessary because loose end removal seems to be a side effect
		//of 'filterOutFalseCatchments'
		/*
		while (true) {
			int initialSize = result.size();
			result = filterOutLooseEnds(result);
			int finalSize = result.size();
			if (initialSize == finalSize) {
				break;
			}
		}
		*/
		
		//result = filterOutFalseCatchments(result);

		int initialSize = -1;
		while (true) {
			result = splitAndFilterOutDuplicates(result);
			result = filterOutFalseCatchments(result);
			int finalSize = result.size();
			System.out.println(initialSize+" =? "+finalSize);
			if (initialSize == finalSize) {
				break;
			}
			initialSize = finalSize;
		}
	

		
		//filter out crossing lines
		//result = filterOutCrossingLines(result);
		return result;
	}
	
	public SimpleFeatureCollection getWorkingJunctions() throws SchemaException {
		SimpleFeatureType junctionFeatureType = DataUtilities.createType("working_junctions", "geometry:Point:srid=3005");
		SimpleFeatureIterator ridgeIt = inRidges.features();
		DefaultFeatureCollection allJunctions = new DefaultFeatureCollection(); 
		while (ridgeIt.hasNext()) {
			SimpleFeature ridge = ridgeIt.next();
			List<Coordinate> junctions = getJunctions(ridge, inRidges);
			
			for(Coordinate c: junctions) {
				Point p = geometryFactory.createPoint(c);
				SimpleFeature f = SpatialUtils.geomToFeature(p, junctionFeatureType, allJunctions.size()+"");
				allJunctions.add(f);
			}
		}
		ridgeIt.close();
		return allJunctions;
				
	}
	
	private DefaultFeatureCollection truncateLoopbackLines(SimpleFeatureCollection fc) {
		System.out.println("Truncating lines at loop-backs");
		DefaultFeatureCollection result = new DefaultFeatureCollection();
		
		SimpleFeatureIterator it = fc.features();
		while(it.hasNext()) {
			SimpleFeature f = it.next();
			LineString original = (LineString)f.getDefaultGeometry();
			LineString truncated = truncateAtLoopback(original);
			SimpleFeature newF = SpatialUtils.geomToFeature(truncated, f.getFeatureType(), f.getID());
			result.add(newF);		
		}
		it.close();
		return result;
		
	}
	
	private DefaultFeatureCollection truncateSinuousLines(SimpleFeatureCollection fc) {
		DefaultFeatureCollection result = new DefaultFeatureCollection();
		SinuosityQuantifier sinuosityQuantifier = new SinuosityQuantifier();
		SimpleFeatureIterator it = fc.features();
		while(it.hasNext()) {
			SimpleFeature f = it.next();
			
			double MAX_SINUOSITY = 1.5;
			double sinuosity = sinuosityQuantifier.getSinuosity(f);	
			if (sinuosity > MAX_SINUOSITY) {
				System.out.println("truncating sinuous line");
				LineString g = (LineString)f.getDefaultGeometry();
				LineString truncatedGeom = truncateAtHighPoint(g);
				SimpleFeature truncatedFeature = SpatialUtils.geomToFeature(truncatedGeom, f.getFeatureType(), f.getID());
				result.add(truncatedFeature);
			}
			else {
				//keep original feature
				result.add(f);
			}
		}
		it.close();
		return result;
		
	}
	
	private LineString truncateAtHighPoint(LineString ls) {
		Coordinate[] coords = ls.getCoordinates();

		//find highest coordinate
		Coordinate highestCoord = null;
		for(Coordinate c: coords) {
			if (highestCoord == null || c.getZ() >= highestCoord.getZ()) {
				highestCoord = c;
			}
		}
		
		//build a list of coordinates up to and including the highest coord.  omit
		//everything else
		List<Coordinate> truncatedCoords = new ArrayList<Coordinate>();
		boolean highestFound= false;
		for (Coordinate c: coords) {
			truncatedCoords.add(c);
			if (c.equals(highestCoord)) {
				highestFound = true;
			}
			if (highestFound && truncatedCoords.size() >= 2) {
				break;
			}
		}
		
		//turn the new (shorter) list of coordinates into a linesstring.
		LineString result = SpatialUtils.toLineString(truncatedCoords);
		return result;
	}
	
	private DefaultFeatureCollection splitAndFilterOutDuplicates(SimpleFeatureCollection fc) {
		
		System.out.println("Splitting and filtering out dups");
		
		List<LineString> segments = new ArrayList<LineString>();
		SimpleFeatureIterator ridgeIt = fc.features();
		while (ridgeIt.hasNext()) {
			SimpleFeature ridge = ridgeIt.next();
			List<Coordinate> junctions = getJunctions(ridge, fc);
			List<LineString> ridgePieces = lineSegmenter.segment((LineString)ridge.getDefaultGeometry(), junctions);
			
			for (LineString piece : ridgePieces) {
				
				LineString reverse = (LineString)piece.reverse();
				boolean isDuplicate = segments.contains(piece) || segments.contains(reverse);
				if (!isDuplicate) {
					segments.add(piece);
				}	
			}
		}
		ridgeIt.close();
		
		DefaultFeatureCollection result = new DefaultFeatureCollection();
		for(LineString segment : segments) {
			String fid = (NEXT_FID++)+"";
			SimpleFeature segmentFeature = SpatialUtils.geomToFeature(segment, fc.getSchema(), fid);
			result.add(segmentFeature);
		}
		
		return result;
		
	}
	
	private DefaultFeatureCollection filterOutLooseEnds(SimpleFeatureCollection fc) throws IOException {
		System.out.println("Filtering out loose ends");
		int initialNumSections = fc.size();
		DefaultFeatureCollection result = new DefaultFeatureCollection();
		
		SimpleFeatureIterator it = fc.features();
		while(it.hasNext()) {
			SimpleFeature f = it.next();
			LineString g = (LineString)f.getDefaultGeometry();
			Coordinate first = g.getCoordinateN(0);
			Coordinate last = g.getCoordinateN(g.getNumPoints()-1);
			
			Coordinate[] coordsToTest = {first, last};
			int numStickyCoords = 0;
			boolean touchesConfluence = false;
			for (Coordinate c: coordsToTest) {
				touchesConfluence = touchesConfluence || water.isConfluence(c);
				Point p = geometryFactory.createPoint(c);
				Filter filter = filterFactory.and(
						//endpoint of this feature touch other feature
						filterFactory.touches(
								filterFactory.property(ridgeGeometryPropertyName), 
								filterFactory.literal(p)),
						
						//but otherwise no overlap between this feature and the other feature
						filterFactory.not(
								filterFactory.overlaps(
										filterFactory.property(ridgeGeometryPropertyName), 
										filterFactory.literal(g))
						)
				);
				
				SimpleFeatureCollection matches = fc.subCollection(filter);
				
				//of the touching features, consider only those which touch at their endpoints
				matches = touchesOnlyAtEndpoints(matches, coordsToTest);
				
				boolean isConnectedToAnotherEndpoint = matches.size() > 1; //touches self and at least one more
				//System.out.println("isConnectionCoord: "+isConnectionCoord+" ("+matches.size()+"), touchesConfluence: "+touchesConfluence);
				
				if (touchesConfluence || isConnectedToAnotherEndpoint) {
					numStickyCoords++;
				}
				
			}
			
			boolean hasLooseEnd = numStickyCoords != 2 && !touchesConfluence;
			if (!hasLooseEnd) {
				result.add(f);
			}
			else {
				//System.out.println("filtering out segment from "+first+" to "+last+", num sticky:"+numStickyCoords);
			}
		}
		it.close();
		int numSectionsRemoved = initialNumSections - result.size();
		System.out.println(" - "+numSectionsRemoved+" of "+initialNumSections +" removed.");
		
		return result;
	}
	
	private SimpleFeatureCollection touchesOnlyAtEndpoints(SimpleFeatureCollection touching, Coordinate[] endPoints) {
		DefaultFeatureCollection result = new DefaultFeatureCollection();
		SimpleFeatureIterator it = touching.features();
		while(it.hasNext()) {
			SimpleFeature f = it.next();
			LineString g = (LineString)f.getDefaultGeometry();
			Coordinate first = g.getCoordinateN(0);
			Coordinate last = g.getCoordinateN(g.getNumPoints()-1);
			for (Coordinate c : endPoints) {
				if (c.equals(first) || c.equals(last)) {
					result.add(f);
				}
			}
		}
		it.close();
		if (result.size() != touching.size()) {
			System.out.println("filtered out");
		}
		return result;
	}
	
	private DefaultFeatureCollection filterOutCrossingLines(SimpleFeatureCollection fc) throws IOException {
		SimpleFeatureIterator it = fc.features();

		//identify crossing features that should be excluded
		List<SimpleFeature> allExcluded = new ArrayList<SimpleFeature>();
		while(it.hasNext()) {
			SimpleFeature f = it.next();
			LineString g = (LineString)f.getDefaultGeometry();
			
			Filter filter = filterFactory.crosses(
					filterFactory.property(ridgeGeometryPropertyName), 
					filterFactory.literal(g));
			SimpleFeatureCollection matches = fc.subCollection(filter);
			SimpleFeatureIterator matchesIt = matches.features();
			while(matchesIt.hasNext()) {
				allExcluded.add(matchesIt.next());
			}
			matchesIt.close();		
		}
		it.close();
		
		//create a new set that excludes the crossing features
		DefaultFeatureCollection result = new DefaultFeatureCollection();
		it = fc.features();
		while(it.hasNext()) {
			SimpleFeature f = it.next();
			if (!allExcluded.contains(f)) {
				result.add(f);
			}
		}
		it.close();
		
		return result;
	}
	
	private List<SimpleFeature> getTouchingRidges(SimpleFeature ridge, SimpleFeatureCollection allRidges) {
		LineString section = (LineString)ridge.getDefaultGeometry();
		
		List<Filter> filters = new ArrayList<Filter>();

		filters.add(filterFactory.touches(
				filterFactory.property(ridgeGeometryPropertyName), 
				filterFactory.literal(section)));		
		//filters.add(filterFactory.overlaps(
		//		filterFactory.property(ridgeGeometryPropertyName), 
		//		filterFactory.literal(section)));
		filters.add(filterFactory.dwithin(
				filterFactory.property(ridgeGeometryPropertyName), 
				filterFactory.literal(section),
				0,
				"meters"));
		filters.add(filterFactory.intersects(
				filterFactory.property(ridgeGeometryPropertyName), 
				filterFactory.literal(section)));

		
		Filter overlapsFilter = filterFactory.or(filters);
		SimpleFeatureCollection overlappingRidges = allRidges.subCollection(overlapsFilter);
		
		return SpatialUtils.simpleFeatureCollectionToList(overlappingRidges);		
	}
	
	/*
	private List<Coordinate> getJunctions(SimpleFeature ridge) {
		List<Coordinate> coordsOfIntersection = new ArrayList<Coordinate>();
		List<SimpleFeature> overlappingRidges = getIntersectingRidges(ridge);
		LineString section = (LineString)ridge.getDefaultGeometry();
		
		for(Coordinate ridgeCoord : section.getCoordinates()) {
			Point ridgePoint = geometryFactory.createPoint(ridgeCoord);
			for (SimpleFeature overlappingRidge : overlappingRidges) {
				LineString overlappingSection = (LineString)overlappingRidge.getDefaultGeometry();
				if (!overlappingSection.contains(ridgePoint)) {
					continue;
				}
				Coordinate prevCoordOfOverlappingSection = getPrevCoordOfSection(overlappingSection, ridgeCoord);
				if (prevCoordOfOverlappingSection == null) {
					//this occurs when:
					// a. the *first coord* of the "overlapping section" is contained in the "primary section", or
					// b. the ridge coordinate isn't in the overlapping section
					// Possibility b is ruled out above, so practically the cause is possibility a.
					throw new IllegalStateException("the possible junction point under consideration has no previous coordinate on the overlapping section.");
				}
				Point prevPointOfOveralappingCoord = geometryFactory.createPoint(prevCoordOfOverlappingSection);
				boolean isJunction = !section.contains(prevPointOfOveralappingCoord);
				if (isJunction) {
					coordsOfIntersection.add(ridgeCoord);
				}
			}
		}
		
		return coordsOfIntersection;
	}
	*/
	
	private List<Coordinate> getJunctions(SimpleFeature ridge, SimpleFeatureCollection allRidges) {
		/*
		 * This function attempts to handle the difficult case where:
		 * - a ridge line loops back on itself several times. 
		 * - a touching line touches at several points, including some
		 *   before and some after the true junction
		 * 
		 */
		JunctionFinder junctionFinder = new JunctionFinder();
		List<Coordinate> allJunctions = new ArrayList<Coordinate>();
		List<SimpleFeature> overlappingRidges = getTouchingRidges(ridge, allRidges);
		for (SimpleFeature overlappingRidge : overlappingRidges) {
			//System.out.println("find junction");
			List<Coordinate> junctions = junctionFinder.getJunctions(ridge, overlappingRidge);
			for(Coordinate junction : junctions) {
				if (junction != null && !allJunctions.contains(junction)) {
					allJunctions.add(junction);
				}				
			}
		}
				
		return allJunctions;
		
	}


	
	/**
	 * gets the coord one before the given coord
	 * @param section
	 * @param coord
	 * @return
	 */
	/*
	private Coordinate getPrevCoordOfSection(LineString section, Coordinate coord) {
		Coordinate prev = null;
		for (Coordinate c : section.getCoordinates()) {
			if (c.equals(coord)) {
				return prev;
			}
			prev = c;
		}
		return null;
	}
	*/
	
	/*
	private List<SimpleFeature> splitRidgeByJunctions(SimpleFeature ridge, List<Coordinate> junctions) {
		List<SimpleFeature> pieces = new ArrayList<SimpleFeature>();
		
		LineString section = (LineString)ridge.getDefaultGeometry();
		List<Coordinate> coordsOfCurrentPiece = new ArrayList<Coordinate>();
		coordsOfCurrentPiece.add(section.getCoordinateN(0));
		
		Coordinate lastCoord = section.getCoordinateN(section.getNumPoints()-1);
		
		for(Coordinate c : section.getCoordinates()) {
			if (!coordsOfCurrentPiece.contains(c)) {
				coordsOfCurrentPiece.add(c);
			}
			boolean endOfPiece = junctions.contains(c) || c.equals(lastCoord);
			if (endOfPiece) {				
				if (coordsOfCurrentPiece.size() >= 2) {
					String fid = (NEXT_FID++)+"";
					LineString pieceGeometry = SpatialUtils.toLineString(coordsOfCurrentPiece);
					SimpleFeature pieceFeature = SpatialUtils.geomToFeature(pieceGeometry, ridgeFeatureType, fid);
					pieces.add(pieceFeature);
					
					//reset for next piece
					coordsOfCurrentPiece.clear();
					coordsOfCurrentPiece.add(c);
				}
			}
		}
		
		return pieces;
	}
	*/
	
	public static LineString truncateAtLoopback(LineString ridge) {
		int windowSize = 6;
		int halfWindowSize = windowSize / 2;
		int oldStartIndex = 0;
		int oldEndIndex = halfWindowSize - 1;
		int newStartIndex = oldEndIndex+1;
		int newEndIndex = windowSize-1;		
		//TODO: validate the ranges don't intersect
		
		List<Coordinate> keptCoords = new ArrayList<Coordinate>();
		List<LineString> recentSegments = new ArrayList<LineString>();
		Coordinate prev = null;
		if (ridge.getNumPoints() > windowSize) {
			//scan the last N segments of the ridge (where N = windowSize)
			for (Coordinate c: ridge.getCoordinates()) {
				keptCoords.add(c);
				if (prev != null) {
					LineString segment = SpatialUtils.toLineString(prev, c);
					recentSegments.add(segment);
					if (recentSegments.size() > windowSize) {
						recentSegments.remove(oldStartIndex); 
					}
					if (recentSegments.size() == windowSize) {
						double angle1 = avgAngle(recentSegments, oldStartIndex, oldEndIndex);
						double angle2 = avgAngle(recentSegments, newStartIndex, newEndIndex);
												
						double diff = Math.abs(VectorUtils.getTrajectoryDiff(angle1, angle2));
						
						
						//System.out.println(angle1+","+angle2+", => "+diff);
						if (diff > 135) { //something in the range of 90-180
							//System.out.println("angle1:"+angle1+", angle2:"+angle2+", diff:"+diff);
							break;
						}
					}
				}
				prev = c;
			}
			
			//remove the last "halfWindowSize" points, which truncates back to the point of the "kink"
			for(int i = 0; i < halfWindowSize-1; i++) {
				keptCoords.remove(keptCoords.size()-1); //pop last
			}
			
			
			return SpatialUtils.toLineString(keptCoords);
		}
		else {
			return ridge;
		}
		
	}
	
	/**
	 * note: a prerequisite for this function is "splitAndFilterOutDuplicates".
	 * @param fc
	 * @return
	 * @throws IOException
	 */
	private SimpleFeatureCollection filterOutFalseCatchments(SimpleFeatureCollection fc) throws IOException {
		System.out.println("Filtering out false catchments");
		Collection<Geometry> polys = toPolygons(fc);
		SimpleFeatureCollection polyFeatures = SpatialUtils.geomCollectionToSimpleFeatureCollection(polys, catchmentPolysFeatureType);
			
		Map<FeatureId, Polygon> oldToNew = new HashMap<FeatureId, Polygon>();
		
		SimpleFeatureIterator polyIt = polyFeatures.features();
		while (polyIt.hasNext()) {
			SimpleFeature f = polyIt.next();
			Polygon poly = (Polygon)f.getDefaultGeometry();
			if (!oldToNew.containsKey(f.getIdentifier())) {
				oldToNew.put(f.getIdentifier(), poly);
			}
			poly = oldToNew.get(f.getIdentifier());
			boolean containsWater = water.containsWater(poly);
			if (!containsWater) {
				//false catchment.  merge into adjacent catchment
				//note: the adjacent catchment may also be a false catchment.
				//as such, this function should be run multiple times until no further merging
				//is done.
				
				SimpleFeature adjacentFeat = chooseAdjacent(f, polyFeatures);
				if (adjacentFeat != null && !oldToNew.containsKey(adjacentFeat.getIdentifier())) {
					//the chosen adjacent feature has been removed (it was
					//merged into another feature)
					adjacentFeat = null;
				}
				
				if (adjacentFeat == null) {
					System.out.println("false catchment with no adjacent poly");
					continue;
				}				
				
				//get the latest Polygon associated with the adjacent feature
				Polygon adjacentPoly = oldToNew.get(adjacentFeat.getIdentifier());
								
				//the union should always be a polygon if the 'poly' and 'adjacentPoly'
				//touch only at one shared edge
				Polygon union = (Polygon)poly.union(adjacentPoly);				
				oldToNew.put(adjacentFeat.getIdentifier(), union);
				oldToNew.remove(f.getIdentifier());
				
			}
		}
		polyIt.close();
		
		List<Polygon> keptPolys = new ArrayList<Polygon>();
		for (FeatureId key : oldToNew.keySet()) {
			Polygon p = oldToNew.get(key);
			keptPolys.add(p);
		}
		
		int numRemoved = polys.size() - keptPolys.size();
		System.out.println(" - found "+polys.size()+ " catchments.");
		System.out.println(" - filtered out "+numRemoved+" false catchments");
		
		//convert polys to lines
		List<LineString> lines = toLineStrings(keptPolys);
		Collection<Geometry> geoms = new ArrayList<Geometry>();
		geoms.addAll(lines);
		
		//convert lines to features
		SimpleFeatureCollection results = SpatialUtils.geomCollectionToSimpleFeatureCollection(geoms, inRidges.getSchema());
		
		return results;		
	}
	
	private SimpleFeature chooseAdjacent(SimpleFeature polyFeature, SimpleFeatureCollection allPolyFeatures) {
		SimpleFeature result = null;
		Geometry g = (Geometry)polyFeature.getDefaultGeometry();
		
		List<SimpleFeature> touching = getTouching(polyFeature, allPolyFeatures);

		for (SimpleFeature adjacentFeat : touching) {
			Geometry adjacentGeom = (Geometry)adjacentFeat.getDefaultGeometry();
			Geometry intersection = g.intersection(adjacentGeom);
			
			//only consider touching features that share an edge
			//  (exclude touching features that share only one point
			//   and also those that share an area)
			
			if (intersection.getGeometryType().equals("LineString") 
					|| intersection.getGeometryType().equals("MultiLineString")
					|| intersection.getGeometryType().equals("GeometryCollection")) {
				result = adjacentFeat;
				break;
			}
			else {
				//System.out.println("intersection type:"+intersection.getGeometryType());
			}
		}
		
		//check post conditions
		if (result != null && result.equals(polyFeature)) {
			throw new IllegalStateException("postcondition failed.  should not return self.");
		}
		
		return result;
	}
	
	
	private List<SimpleFeature> getTouching(SimpleFeature polyFeature, SimpleFeatureCollection allPolyFeatures) {
		String geomPropertyName = allPolyFeatures.getSchema().getGeometryDescriptor().getLocalName();
		Geometry g = (Geometry)polyFeature.getDefaultGeometry();
		
		Filter filter = filterFactory.and(
				//touches the input feature
				filterFactory.intersects(
						filterFactory.property(geomPropertyName), 
						filterFactory.literal(g)),
				//... but not the same as the input feature
				filterFactory.not(filterFactory.id(polyFeature.getIdentifier()))
				);
		SimpleFeatureCollection matches = allPolyFeatures.subCollection(filter);

		List<SimpleFeature> result = SpatialUtils.simpleFeatureCollectionToFeatList(matches);
		return result;
	}
	
	private List<LineString> toLineStrings(Collection<Polygon> polys) {
		List<LineString> result = new ArrayList<LineString>();
		for(Polygon p : polys) {
			LineString line = SpatialUtils.toLineString(p.getCoordinates());
			result.add(line);
		}
		return result;
	}
	
	/**
	 * converts features with linestring geometries into a collection of polygons
	 * @param fc
	 * @return
	 */
	private Collection<Geometry> toPolygons(SimpleFeatureCollection fc) {
		Polygonizer polygonizer = new Polygonizer();
		SimpleFeatureIterator it = fc.features();
		while(it.hasNext()) {
			SimpleFeature f = it.next();
			LineString g = (LineString)f.getDefaultGeometry();
			if (g != null) {
				polygonizer.add(g);
			}
		}
		it.close();
		Collection<Geometry> polys = polygonizer.getPolygons();
		return polys;
		
	}
	
	private static double avgAngle(List<LineString> segments, int startIndex, int endIndex) {
		double prevAngle = Double.NaN;
		double sum = 0;
		int num = 0;
		double firstAngle = Double.NaN;
		for(int i = startIndex; i <= endIndex; i++) {
			boolean isFirst = i == startIndex;
			
			LineString s = segments.get(i);
			if (s.getNumPoints() != 2) {
				throw new IllegalArgumentException("LineStrings must have exactly 2 coordinates");
			}
			double angle = VectorUtils.getTrajectory2D(s.getCoordinateN(0), s.getCoordinateN(1));
			if (isFirst) {
				firstAngle = angle;
			}
			else {
				double diff = angle - firstAngle;
				if (diff > 180) {					
					angle -= 360;
				}
				else if (diff < -180) {
					angle += 360;
				}
			}
			
			sum += angle;
			num++;
		}
		double avg = sum / num;		
		avg = avg % 360;
		
		return avg;
	}
}
