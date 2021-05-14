package ca.bc.gov.catchment.algorithms;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.factory.CommonFactoryFinder;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.filter.Filter;
import org.opengis.filter.FilterFactory2;

import ca.bc.gov.catchment.utils.SpatialUtils;
import ca.bc.gov.catchment.utils.VectorUtils;

public class NearestFinder {

	private static final double DEFAULT_SEARCH_DISTANCE_METERS = 100;
	private static final double DEFUALT_MULTIPLE = 2;
	private static final int MAX_ATTEMPTS = 50;
	
	private SimpleFeatureCollection features;
	private double initialSearchDistanceMeters;
	private double multiple;
	private FilterFactory2 filterFactory;
	private String geomProperty;
	
	public NearestFinder(SimpleFeatureCollection features) {
		this(features, DEFAULT_SEARCH_DISTANCE_METERS, DEFUALT_MULTIPLE);
		
	}
	
	
	public NearestFinder(SimpleFeatureCollection features, double initialSearchDistanceMeters, double multiple) {
		this.features = features;
		this.initialSearchDistanceMeters = initialSearchDistanceMeters;
		this.multiple = multiple;
		this.filterFactory = CommonFactoryFinder.getFilterFactory2();
		this.geomProperty  = features.getSchema().getGeometryDescriptor().getLocalName();
	}
	
	public SimpleFeature findNearestFeatureInDirection(Coordinate fromCoord, double compassAngle, Geometry excludeTouches) {
		double dist = 0.1;
		Point fromPoint = SpatialUtils.toPoint(fromCoord);
		for(int i = 0; i < 10; i++) {
			LineString outwardLine = VectorUtils.createLineInDirection(fromCoord, compassAngle, dist);

			Filter filter = filterFactory.intersects(
					filterFactory.property(geomProperty),	
					filterFactory.literal(outwardLine));
			
			if (excludeTouches != null) {
				Filter touchesFilter = filterFactory.touches(
							filterFactory.property(geomProperty),	
							filterFactory.literal(excludeTouches) 	
							);						
				Filter notTouchesFilter = filterFactory.not(touchesFilter);
				filter = filterFactory.and(
						filter,
						notTouchesFilter
						);
			}
			
			SimpleFeatureCollection filteredFeatures = features.subCollection(filter);
			if (filteredFeatures.size() == 0) {
				dist *= 10;
				continue;
			}
						
			List<SimpleFeature> matchingFeaturesAsList = SpatialUtils.simpleFeatureCollectionToFeatList(filteredFeatures);
			matchingFeaturesAsList.sort(getSimpleFeatureComparator(fromPoint));			
			return matchingFeaturesAsList.get(0);
			
		}
		
		return null;
		
	}
	
	public Coordinate findNearestCoordinateInDirection(Coordinate fromCoord, double compassAngle, Geometry excludeTouches) {
		double dist = 0.1;
		Point fromPoint = SpatialUtils.toPoint(fromCoord);
		for(int i = 0; i < 10; i++) {
			LineString outwardLine = VectorUtils.createLineInDirection(fromCoord, compassAngle, dist);

			Filter filter = filterFactory.intersects(
					filterFactory.property(geomProperty),	
					filterFactory.literal(outwardLine));
			
			if (excludeTouches != null) {
				Filter touchesFilter = filterFactory.touches(
							filterFactory.property(geomProperty),	
							filterFactory.literal(excludeTouches) 	
							);						
				Filter notTouchesFilter = filterFactory.not(touchesFilter);
				filter = filterFactory.and(
						filter,
						notTouchesFilter
						);
			}
			
			SimpleFeatureCollection filteredFeatures = features.subCollection(filter);
			if (filteredFeatures.size() == 0) {
				dist *= 10;
				continue;
			}
			
			List<SimpleFeature> matchingFeaturesAsList = SpatialUtils.simpleFeatureCollectionToFeatList(filteredFeatures);
			matchingFeaturesAsList.sort(getSimpleFeatureComparator(fromPoint));			
			SimpleFeature closestFeature = matchingFeaturesAsList.get(0);
			Geometry closestGeom = (Geometry)closestFeature.getDefaultGeometry();
			Geometry intersection = closestGeom.intersection(outwardLine);
			
			//the intersection may have multiple coordinates.  choose the one closest to 'fromPoint'
			Coordinate closestCoordFromIntersection = findNearestCoordinateOf(fromCoord, intersection); 
			
			//the closest intersection coord may not be a coordinate of the 'closest geom'. (e.g. if the intersection is between
			//coords of the closest geom).  choose the closest coord which is a member of the closest geom
			
			Coordinate closestCoordFromGeom = findNearestCoordinateOf(closestCoordFromIntersection, closestGeom);
			return closestCoordFromGeom;
			
		}
		
		return null;
		
	}
	
	/**
	 * Finds the coordinate of g which is closest to c
	 * @param c
	 * @param g
	 * @return
	 */
	public Coordinate findNearestCoordinateOf(Coordinate searchFromCoord, Geometry g) {
		double closestDist = Double.NaN;
		Coordinate closest = null;
		for (Coordinate c: g.getCoordinates()) {
			double dist = c.distance(searchFromCoord);
			if (closest == null || dist < closestDist) {
				closestDist = dist;
				closest = c;
			}
		}
		return closest;
	}
	
	/**
	 * Finds the nearest feature to g
	 * @param g
	 * @return
	 */
	public SimpleFeature findNearest(Geometry g) {
		List<SimpleFeature> results = findNearest(g, 1, null);
		if (results.size() > 0) {
			return results.get(0);
		}
		return null;
	}
	
	/**
	 * Finds the feature nearest to g.  Does not consider features that overlaps
	 * "excludeOverlaps"
	 * @param g
	 * @param excludeTouches
	 * @return
	 */
	public SimpleFeature findNearest(Geometry g, Geometry excludeTouches) {
		List<SimpleFeature> results = findNearest(g, 1, excludeTouches);
		if (results.size() > 0) {
			return results.get(0);
		}
		return null;
	}
	
	/**
	 * Finds the 'num' features which are nearest to g.
	 * @param g
	 * @param num
	 * @param excludeOverlaps
	 * @return
	 */
	public List<SimpleFeature> findNearest(Geometry g, int num) {
		return findNearest(g, num, null);
	}
	
	/**
	 * Finds the 'num' features which are nearest to g, excluding any features which overlaps
	 * "excludeTouches"
	 * @param g
	 * @param num
	 * @param excludeTouches
	 * @return
	 */
	public List<SimpleFeature> findNearest(Geometry g, int num, Geometry excludeTouches) {
		double distanceMeters = initialSearchDistanceMeters;
		for(int i = 0; i < MAX_ATTEMPTS; i++) {
			Filter filter = filterFactory.dwithin(
					filterFactory.property(geomProperty),	
					filterFactory.literal(g), 									
					distanceMeters, 
					"meters");
			
			if (excludeTouches != null) {
					Filter touchesFilter = filterFactory.touches(
								filterFactory.property(geomProperty),	
								filterFactory.literal(excludeTouches) 	
								);						
				Filter notOverlapsFilter = filterFactory.not(touchesFilter);
				filter = filterFactory.and(
						filter,
						notOverlapsFilter
						);
			}
			
			SimpleFeatureCollection filteredFeatures = features.subCollection(filter);
			
			if (filteredFeatures.size() < num) {
				distanceMeters *= multiple;
				continue;
			}
			
			List<SimpleFeature> matchingFeaturesAsList = SpatialUtils.simpleFeatureCollectionToFeatList(filteredFeatures);
			matchingFeaturesAsList.sort(getSimpleFeatureComparator(g));			
			return matchingFeaturesAsList.subList(0, num);
		}
		
		return new ArrayList<SimpleFeature>();
		
				
	}
	
	private Comparator<Geometry> getGeometryComparator(final Geometry g) {
		Comparator<Geometry> comparator = new Comparator<Geometry>() {

			public int compare(Geometry g1, Geometry g2) {
				double dist1 = g.distance(g1);
				double dist2 = g.distance(g2);
				return dist1 > dist2 ? 1 :
					dist1 < dist2 ? -1 :
					0;
			}			
		};
		return comparator;
	}
	
	private Comparator<SimpleFeature> getSimpleFeatureComparator(final Geometry g) {
		Comparator<SimpleFeature> comparator = new Comparator<SimpleFeature>() {

			public int compare(SimpleFeature f1, SimpleFeature f2) {
				Geometry g1 = (Geometry)f1.getDefaultGeometry();
				Geometry g2 = (Geometry)f2.getDefaultGeometry();
				double dist1 = g.distance(g1);
				double dist2 = g.distance(g2);
				return dist1 > dist2 ? 1 :
					dist1 < dist2 ? -1 :
					0;
			}			
		};
		return comparator;
	}
}
