package ca.bc.gov.catchment.utils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import org.geotools.data.DataUtilities;
import org.geotools.data.collection.SpatialIndexFeatureCollection;
import org.geotools.data.collection.SpatialIndexFeatureSource;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.feature.DefaultFeatureCollection;
import org.geotools.feature.SchemaException;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.geotools.geometry.jts.JTSFactoryFinder;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.AttributeDescriptor;
import org.tinfour.common.IQuadEdge;
import org.tinfour.common.Vertex;
import org.tinfour.standard.IncrementalTin;

import ca.bc.gov.catchment.algorithms.TrianglesFromEdgesAlg;

public class SpatialUtils {

	public static SimpleFeature copyFeature(SimpleFeature feature) {
		return copyFeature(feature, feature.getFeatureType());
	}
	
	public static SimpleFeature copyFeature(SimpleFeature feature, SimpleFeatureType newFeatureType) {
		SimpleFeatureBuilder fb = new SimpleFeatureBuilder(newFeatureType);
		Geometry geometry = (Geometry)feature.getDefaultGeometry();
		Object[] attributeValues = new Object[] { geometry };
		SimpleFeature copiedFeature = fb.buildFeature(feature.getID(), attributeValues);
		return copiedFeature;
	}

	/**
	 * Extracts just the geometry from each features, then returns a collection of those geometries
	 * @param fc
	 * @return
	 */
	public static List<SimpleFeature> simpleFeatureCollectionToFeatList(SimpleFeatureCollection fc) {
		List<SimpleFeature> features = new ArrayList<SimpleFeature>();
		SimpleFeatureIterator it = fc.features();
		while(it.hasNext()) {
			SimpleFeature feature = it.next();
			features.add(feature);
		}
		it.close();
		return features;
	}
	
	public static SimpleFeatureCollection featListToSimpleFeatureCollection(List<SimpleFeature> features) {
		DefaultFeatureCollection df = new DefaultFeatureCollection();
		for (SimpleFeature feature : features) {
			df.add(feature);
		}
		return df;
	}
	
	/**
	 * Extracts just the geometry from each features, then returns a collection of those geometries
	 * @param fc
	 * @return
	 */
	public static Collection<Geometry> simpleFeatureCollectionToGeomCollection(SimpleFeatureCollection fc) {
		Collection<Geometry> geometries = new ArrayList<Geometry>();
		SimpleFeatureIterator it = fc.features();
		while(it.hasNext()) {
			SimpleFeature feature = it.next();
			Geometry geometry = (Geometry)feature.getDefaultGeometry();
			geometries.add(geometry);
		}
		it.close();
		return geometries;
	}
	
	/**
	 * Converts SimpleFeatureCollection to List<SimpleFeature>
	 * @param fc
	 * @return
	 */
	public static List<SimpleFeature> simpleFeatureCollectionToList(SimpleFeatureCollection fc) {
		List<SimpleFeature> result = new ArrayList<SimpleFeature>();
		SimpleFeatureIterator it = fc.features();
		while(it.hasNext()) {
			SimpleFeature feature = it.next();
			result.add(feature);
		}
		it.close();
		return result;
	}

	public static SimpleFeature geomToFeature(Geometry geom, SimpleFeatureType type, String fid) {
		if (geom == null) {
			throw new NullPointerException("Geometry must not be null");
		}
		if (type == null) {
			throw new NullPointerException("'type' must not be null");
		}
		SimpleFeatureBuilder featureBuilder = new SimpleFeatureBuilder(type);
		Object[] attrValues = {geom};
		SimpleFeature feature = featureBuilder.buildFeature(fid, attrValues);
		return feature;
	}

	public static SimpleFeatureCollection polygonCollectionToSimpleFeatureCollection(Collection<Polygon> geometries, SimpleFeatureType featureType) {
		
		DefaultFeatureCollection featureCollection = new DefaultFeatureCollection(null, featureType);		
		
		int nextFid = 0;
		for(Geometry geometry : geometries) {
			SimpleFeature feature = geomToFeature(geometry, featureType, nextFid+"");			
			featureCollection.add(feature);
			nextFid++;
		}
		
		return featureCollection;
	}
	
	public static SimpleFeatureCollection linestringCollectionToSimpleFeatureCollection(Collection<LineString> geometries, SimpleFeatureType featureType) {
		
		DefaultFeatureCollection featureCollection = new DefaultFeatureCollection(null, featureType);		
		
		int nextFid = 0;
		for(Geometry geometry : geometries) {
			SimpleFeature feature = geomToFeature(geometry, featureType, nextFid+"");			
			featureCollection.add(feature);
			nextFid++;
		}
		
		return featureCollection;
	}
	
	
	public static SimpleFeatureCollection geomCollectionToSimpleFeatureCollection(Collection<Geometry> geometries, SimpleFeatureType featureType) {
		
		DefaultFeatureCollection featureCollection = new DefaultFeatureCollection(null, featureType);		
		
		int nextFid = 0;
		for(Geometry geometry : geometries) {
			SimpleFeature feature = geomToFeature(geometry, featureType, nextFid+"");			
			featureCollection.add(feature);
			nextFid++;
		}
		
		return featureCollection;
	}
	

	public static SimpleFeatureCollection coordListToSimpleFeatureCollection(List<Coordinate> coordinates, SimpleFeatureType featureType) {
		GeometryFactory geometryFactory = JTSFactoryFinder.getGeometryFactory();
		DefaultFeatureCollection featureCollection = new DefaultFeatureCollection(null, featureType);
		
		int nextFid = 0;
		for(Coordinate c : coordinates) {
			Point p = geometryFactory.createPoint(c);
			SimpleFeature feature = geomToFeature(p, featureType, nextFid+"");			
			featureCollection.add(feature);
			nextFid++;
		}
		
		return featureCollection;
	}
	
	public static Coordinate[] removeDuplicateCoordinates(Coordinate[] coordinates) {
		List<Coordinate> coordList = new ArrayList<Coordinate>();
		Coordinate prev = null;
		for(int i =0; i < coordinates.length; i++) {
			Coordinate c = coordinates[i];
			if (prev == null || (prev.getX() != c.getX() || prev.getY() != c.getY())) {
				coordList.add(c);
			}
			prev = c;
		}
		Coordinate[] result = coordList.toArray(new Coordinate[coordList.size()]);
		return result;
	}
	
	

	public static LineString toLineString(Coordinate c1, Coordinate c2) {
		List<Coordinate> coords = new ArrayList<Coordinate>();
		coords.add(c1);
		coords.add(c2);
		return toLineString(coords);
	}
	
	public static Point toPoint(Coordinate coord) {
		GeometryFactory geometryFactory = JTSFactoryFinder.getGeometryFactory();
		return geometryFactory.createPoint(coord);
	}
	
	public static LineString toLineString(Coordinate[] in) {
		GeometryFactory geometryFactory = JTSFactoryFinder.getGeometryFactory();
		return geometryFactory.createLineString(in);
	}
	
	public static LineString toLineString(List<Coordinate> in) {
		GeometryFactory geometryFactory = JTSFactoryFinder.getGeometryFactory();
		Coordinate[] coords = toCoordinateArray(in);
		return geometryFactory.createLineString(coords);
	}
	
	public static List<LineString> toSegments(LineString s) {
		List<LineString> segments = new ArrayList<LineString>();
		Coordinate prev = null;
		for(Coordinate c : s.getCoordinates()) {
			if (prev != null) {
				LineString segment = toLineString(prev, c);
				segments.add(segment);
			}
			prev = c;
		}
		return segments;
	}

	public static LineString[] toLineStringArray(List<LineString> in) {
		if (in == null) {
			return new LineString[0];
		}
		
		LineString[] result = new LineString[in.size()];		
		int index = 0;
		for(LineString ls : in) {
			result[index] = ls;
			index++;
		}
		return result;
	}
		
	public static Coordinate[] toCoordinateArray(List<Coordinate> in) {
		if (in == null) {
			return new Coordinate[0];
		}
		
		Coordinate[] coords = new Coordinate[in.size()];		
		int index = 0;
		for(Coordinate c : in) {
			coords[index] = c;
			index++;
		}
		return coords;
	}
	
	public static List<Coordinate> toCoordinateList(Coordinate[] in) {
		List<Coordinate> coords = new ArrayList<Coordinate>();
		if (in == null) {
			return coords;
		}
		
		for(Coordinate c : in) {
			coords.add(c);
		}
		return coords;
	}
	
	public static SimpleFeatureCollection renameFeatureType(SimpleFeatureCollection fc, String tableName) throws SchemaException {
		SimpleFeatureType originalFeatureType = fc.getSchema();
		if (originalFeatureType == null) {
			throw new NullPointerException("feature collection must have a non-null schema");
		}
		String spec = DataUtilities.encodeType(originalFeatureType);
		SimpleFeatureType newFeatureType = DataUtilities.createType(tableName, spec);
				
		DefaultFeatureCollection outFc = new DefaultFeatureCollection();
		
		SimpleFeatureIterator inIt = fc.features();
		while(inIt.hasNext()) {
			SimpleFeature f = inIt.next();
			SimpleFeature modified = SimpleFeatureBuilder.retype(f, newFeatureType);
			outFc.add(modified);
		}
		inIt.close();
		
		
		return outFc;	
	}

	public static SimpleFeatureType extendFeatureType(SimpleFeatureType inFeatureType, String newProperties) throws SchemaException {
		return extendFeatureType(inFeatureType, newProperties, inFeatureType.getTypeName());
	}
	
	/**
	 * 
	 * @param inFeatureType
	 * @param newProperties a csv list of properties in the same format used by DataUtilities.createType(...)
	 * @return
	 * @throws SchemaException
	 */
	public static SimpleFeatureType extendFeatureType(SimpleFeatureType inFeatureType, String newProperties, String typeName) throws SchemaException {
		
		if (typeName == null) {
			typeName = inFeatureType.getTypeName(); 
		}
		
		String typeSpec = DataUtilities.encodeType(inFeatureType);
		String newTypeSpec = typeSpec + "," + newProperties;
		
		SimpleFeatureType outFeatureType = DataUtilities.createType(typeName, newTypeSpec);
		return outFeatureType;
		
	}
	
	public static boolean hasCoordinate(Geometry g, Coordinate c) {
		if (c == null) {
			return false;
		}
		GeometryFactory geometryFactory = JTSFactoryFinder.getGeometryFactory();
		Point p = geometryFactory.createPoint(c);
		boolean hasCoordinate = g.distance(p) == 0;
		return hasCoordinate;		
	}
	
	public static boolean is3D(Geometry g) {
		Coordinate[] coords = g.getCoordinates();
		for(Coordinate coord : coords) {
			if (Double.isNaN(coord.getZ())) {
				return false;
			}
		}
		return true;
	}
	
	/**
	 * Gets the a coordinate from the given list at the given index.  Use the 'startCoord' parameter to indicate which end to
	 * count from.
	 * @param coords
	 * @param startCoord the coordinate at one end or the other of the array.  this will be treated as the starting
	 * end.
	 * @param index
	 * @return
	 */
	public static Coordinate getCoordAtIndex(Coordinate[] coords, Coordinate startCoord, int index) {
		Coordinate result = null;
		if (coords.length == 0) {
			throw new IndexOutOfBoundsException("Array is empty.  No such index "+index);
		}
		if (index > coords.length) {
			throw new IndexOutOfBoundsException("Index must be in range 0-"+(coords.length-1));
		}
		
		if (coords[0].equals2D(startCoord)) {
			result = coords[index];
		}
		else if (coords[coords.length-1].equals2D(startCoord)) {
			int reverseIndex = coords.length - 1 - index;
			result = coords[reverseIndex];
		}
		else {
			throw new IllegalArgumentException("'startCoord' must match the coordinate at one end of the array");
		}
		
		return result;
	}
	
	/**
	 * gets the index of the first occurange of the given coordinate within the given geometry, or returns -1 if not found
	 */
	public static int getIndexOfCoordinate(Coordinate coordToFind, Geometry g) {
		Coordinate[] coords = g.getCoordinates();
		for(int i = 0; i < coords.length; i++) {
			Coordinate c = coords[i];
			if (c.equals2D(coordToFind)) {
				return i;
			}
		}
		return -1;
	}
	
	/**
	 * computes the slope of a given linestring between its first coordinate and its last coordinate
	 * @param s
	 * @return
	 */
	public static double getSlope(LineString s) {
		Coordinate c1 = s.getCoordinateN(0);
		Coordinate c2 = s.getCoordinateN(s.getNumPoints()-1);
		
		double rise = c2.getZ() - c1.getZ();
		double run = s.getLength();
		double slope = rise / run;
		return slope;		
	}
	
	/**
	 * computes the average slope of a given geometry
	 * @param g
	 * @return
	 */
	public static double getAverageSlope(Geometry g) {
		Coordinate prevCoord = null;
		double sumSlope = 0;
		double numSections = 0;
		for (Coordinate coord : g.getCoordinates()) {
			if (prevCoord != null) {
				//absolute value because we don't care about the direction of the slope.
				//for example, we don want "downward" slopes to cancel out "upward" slopes				
				double rise = Math.abs(coord.getZ() - prevCoord.getZ());
				double run = coord.distance(prevCoord);
				double segmentSlope = rise / run;
				sumSlope += segmentSlope;
				numSections++;
			}
			prevCoord = coord;
		}
		if (numSections == 0) {
			return 0;			
		}
		double avgSlope = sumSlope / numSections;
		return avgSlope;
	}
	
	/**
	 * computes the slope of a given linestring between its first coordinate and its last coordinate
	 * @param s
	 * @return
	 */
	public static double getAverageElevation(LineString s) {
		double sumZ = 0;
		for(Coordinate c : s.getCoordinates()) {
			sumZ += c.getZ();
		}
		double avgZ = sumZ / s.getNumPoints();
		return avgZ;
	}
	
	/**
	 * gets the coordinate with the lowest Z
	 * @param s
	 * @return
	 */
	public static Coordinate getLowestCoord(Geometry g) {
		Coordinate lowestCoord = null;
		for(Coordinate c : g.getCoordinates()) {
			if (!Double.isNaN(c.getZ()) && (lowestCoord == null || c.getZ() < lowestCoord.getZ())) {
				lowestCoord = c;
			}
		}
		return lowestCoord;
	}
	
	/**
	 * gets the Z value of the coordinate with the Lowest Z in the whole linestring
	 * @param s
	 * @return
	 */
	public static double getLowestZ(Geometry g) {
		Coordinate lowest = getLowestCoord(g);
		if (lowest != null) {
			return lowest.getZ();
		}
		return Double.NaN;
	}
	
	/**
	 * gets the coordinate with the lowest Z
	 * @param s
	 * @return
	 */
	public static Coordinate getHighestCoord(Geometry g) {
		Coordinate highestCoord = null;
		for(Coordinate c : g.getCoordinates()) {
			if (!Double.isNaN(c.getZ()) && (highestCoord == null || c.getZ() > highestCoord.getZ())) {
				highestCoord = c;
			}
		}
		return highestCoord;
	}
	
	/**
	 * gets the Z value of the coordinate with the Lowest Z in the whole linestring
	 * @param s
	 * @return
	 */
	public static double getHighestZ(Geometry g) {
		Coordinate lowest = getHighestCoord(g);
		if (lowest != null) {
			return lowest.getZ();
		}
		return Double.NaN;
	}
	
	/**
	 * Returns a subset of the given linestring
	 * @param s
	 * @param fromIndex
	 * @param toIndex
	 * @return
	 */
	public static LineString slice(LineString s, int fromIndex, int toIndex) {
		if (fromIndex < 0 || fromIndex >= toIndex || toIndex > s.getNumPoints() - 1) {
			throw new IllegalArgumentException("invalid range.  must be within [0, "+(s.getNumPoints()-1)+"] and new length must be >= 2");
		}

		
		Coordinate[] origCoords = s.getCoordinates();
		Coordinate[] newCoords = new Coordinate[toIndex-fromIndex+1];
		int newIndex = 0;
		for (int oldIndex = fromIndex; oldIndex <= toIndex; oldIndex++) {
			newCoords[newIndex] = origCoords[oldIndex];
			newIndex++;
		}
		
		return toLineString(newCoords);
	}
	
	/**
	 * Searches all coordinates in 'geomToChooseCoordFrom', and finds the one that is nearest
	 * to the 'other' geometry.
	 * @param geomToChooseCoordFrom
	 * @param other
	 * @return
	 */
	public static Coordinate getCoordNearestTo(Geometry geomToChooseCoordFrom, Geometry other) {
		double minDist = Double.NaN;
		Coordinate nearestCoord = null;
		for (Coordinate coord : geomToChooseCoordFrom.getCoordinates()) {
			Point point = SpatialUtils.toPoint(coord);
			double dist = point.distance(other);
			if (Double.isNaN(minDist) || dist < minDist) {
				nearestCoord = coord;
				minDist = dist;
			}
		}
		return nearestCoord;
	}
	
	/**
	 * gets the angle of a line perpendicular to the polygon at the given coordinate.  There will be two perpendicular lines: one 
	 * towards the interior of the polygon, and the other outside the polygon.  this method returns angle of the outside line.
	 * If no suitable line is found, returns NaN
	 * @param coordOfPoly
	 * @param poly
	 * @return the compas angle of the perpendicular line
	 */
	public static double getOutwardNormalCompassAngle(Coordinate coordOfPoly, Polygon poly) {
		poly = (Polygon)poly.copy();
		
		//normalizing ensures clockwise? ordering of coordinates
		poly.normalize();
		
		Coordinate[] polyCoords = poly.getCoordinates(); 
		for(int i = 0; i < polyCoords.length; i++) {			
			Coordinate prevCoord = null;
			Coordinate nextCoord = null;
			Coordinate c = polyCoords[i];
			if (!c.equals2D(coordOfPoly)) {
				continue;
			}			
			if (i == 0) {
				prevCoord = polyCoords[polyCoords.length-2]; //-2 may be necessary if -1 is the same as index=0
			}
			else {
				prevCoord = polyCoords[i-1];
			}
			if (i == polyCoords.length-1) { //last
				nextCoord = polyCoords[1];
			}
			else {
				nextCoord = polyCoords[i+1];
			}
			//find a line between the next coord and the prev coord.  this
			//line has the same angle as the tangent line at the current coord (c)
			LineString line = toLineString(prevCoord, nextCoord);
			
			double lineAngle = VectorUtils.angle2D(prevCoord, line);
			double perpAngle = (lineAngle + 90) % 360; //+270 is the perpendicular direction facing inward
			return perpAngle;
		
		}
		
		return Double.NaN;
	}
}
