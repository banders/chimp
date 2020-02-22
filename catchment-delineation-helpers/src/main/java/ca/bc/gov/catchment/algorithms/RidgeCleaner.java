package ca.bc.gov.catchment.algorithms;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.factory.CommonFactoryFinder;
import org.geotools.feature.DefaultFeatureCollection;
import org.geotools.geometry.jts.JTSFactoryFinder;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.filter.Filter;
import org.opengis.filter.FilterFactory2;

import ca.bc.gov.catchment.utils.SpatialUtils;
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
	
	public RidgeCleaner(SimpleFeatureCollection ridges, Water water) {	
		this.inRidges = ridges;
		this.water = water;
		this.filterFactory = CommonFactoryFinder.getFilterFactory2();
		this.geometryFactory = JTSFactoryFinder.getGeometryFactory();
		this.ridgeFeatureType = ridges.getSchema();
		this.ridgeGeometryPropertyName = ridgeFeatureType.getGeometryDescriptor().getLocalName();
	}
	
	public SimpleFeatureCollection cleanRidges() throws IOException {
		DefaultFeatureCollection result = new DefaultFeatureCollection();
		
		result = filterOutDuplicates(inRidges);
		
		//filter out "loose ends"
		result = filterOutLooseEnds(result);
		
		return result;
	}
	
	private DefaultFeatureCollection filterOutDuplicates(SimpleFeatureCollection fc) {
		DefaultFeatureCollection result = new DefaultFeatureCollection();
		SimpleFeatureIterator ridgeIt = inRidges.features();
		while (ridgeIt.hasNext()) {
			SimpleFeature ridge = ridgeIt.next();
			List<Coordinate> junctions = getJunctions(ridge);
			List<SimpleFeature> ridgePieces = splitRidgeByJunctions(ridge, junctions);
			for (SimpleFeature piece : ridgePieces) {
				
				//check if another feature with same geometry already exists in the collection.
				//only add if it doesn't yet exist.
				Filter filter = filterFactory.contains(
						filterFactory.property(ridgeGeometryPropertyName), 
						filterFactory.literal(piece.getDefaultGeometry()));
				SimpleFeatureCollection matches = result.subCollection(filter);
				boolean isDuplicate = matches.size() > 0; 
				
				if (!isDuplicate) {
					result.add(piece);
				}				
			}
		}
		ridgeIt.close();
		return result;
		
	}
	
	private DefaultFeatureCollection filterOutLooseEnds(SimpleFeatureCollection fc) throws IOException {
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
				Filter filter = filterFactory.touches(
						filterFactory.property(ridgeGeometryPropertyName), 
						filterFactory.literal(p));
				SimpleFeatureCollection matches = fc.subCollection(filter);
				boolean isJunction = matches.size() >= 3;
				System.out.println("isJunction: "+isJunction+" ("+matches.size()+"), touchesConfluence: "+touchesConfluence);
				
				if (touchesConfluence || isJunction) {
					numStickyCoords++;
				}
			}
			
			boolean hasLooseEnd = numStickyCoords != 2 && !touchesConfluence;
			if (!hasLooseEnd) {
				result.add(f);
			}
		}
		it.close();
		System.out.println(" result.size: "+result.size());
		
		return result;
	}
	
	private List<SimpleFeature> getOverlappingRidges(SimpleFeature ridge) {
		LineString section = (LineString)ridge.getDefaultGeometry();
		
		Filter overlapsFilter = filterFactory.overlaps(
				filterFactory.property(ridgeGeometryPropertyName), 
				filterFactory.literal(section));
		SimpleFeatureCollection overlappingWaterFeatures = inRidges.subCollection(overlapsFilter);
		
		return SpatialUtils.simpleFeatureCollectionToList(overlappingWaterFeatures);		
	}
	
	private List<Coordinate> getJunctions(SimpleFeature ridge) {
		List<Coordinate> junctions = new ArrayList<Coordinate>();
		List<SimpleFeature> overlappingRidges = getOverlappingRidges(ridge);
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
					junctions.add(ridgeCoord);
				}
			}
		}
		
		return junctions;
	}

	/**
	 * gets the coord one before the given coord
	 * @param section
	 * @param coord
	 * @return
	 */
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
	
}
