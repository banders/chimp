package ca.bc.gov.catchment.water;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.geotools.data.DataStore;
import org.geotools.data.DataStoreFinder;
import org.geotools.data.collection.SpatialIndexFeatureCollection;
import org.geotools.data.collection.SpatialIndexFeatureSource;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.factory.CommonFactoryFinder;
import org.geotools.feature.DefaultFeatureCollection;
import org.geotools.geometry.jts.JTSFactoryFinder;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.util.factory.Hints;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.operation.polygonize.Polygonizer;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.filter.Filter;
import org.opengis.filter.FilterFactory2;

import ca.bc.gov.catchment.algorithms.NearestFinder;
import ca.bc.gov.catchment.utils.SpatialUtils;
import ca.bc.gov.catchment.utils.VectorUtils;

public class Water {
	
	private static final int MAX_CONFLUENCE_CACHE_SIZE = 1000;
	
	private FilterFactory2 filterFactory;
	private GeometryFactory geometryFactory;
	private SimpleFeatureSource waterFeatures;
	private SimpleFeatureType waterFeatureType;
	private String waterGeometryPropertyName;
	private Map<Coordinate, Boolean> confluenceCache;
	private NearestFinder nearestWaterFinder;

	public Water(SimpleFeatureSource waterFeatures) {
		Hints filterHints = new Hints(Hints.FEATURE_2D, true);
		this.filterFactory = CommonFactoryFinder.getFilterFactory2(filterHints);
		this.geometryFactory = JTSFactoryFinder.getGeometryFactory();
		this.waterFeatures = waterFeatures;
		this.waterFeatureType = waterFeatures.getSchema();
		this.waterGeometryPropertyName = waterFeatureType.getGeometryDescriptor().getLocalName();
		try {
			this.nearestWaterFinder = new NearestFinder(waterFeatures.getFeatures());
		} catch (IOException e) {
			// TODO Auto-generated catch block
			throw new IllegalArgumentException("invalid water features.");
		}

		//define a map that removes the oldest entry when the 
		//cache removes its maximum size.  i.e. the least-recently-used entry is
		//removed
		confluenceCache = new LinkedHashMap<Coordinate, Boolean>(MAX_CONFLUENCE_CACHE_SIZE, .75F, true) {
		    public boolean removeEldestEntry(Map.Entry<Coordinate, Boolean> eldest) {
		        return size() > MAX_CONFLUENCE_CACHE_SIZE;
		    }
		};
	}
	
	/**
	 * gets the features source containing the water features
	 * @return
	 */
	public SimpleFeatureSource getFeatureSource() {
		return waterFeatures;
	}
	
	/**
	 * Implementation note: this function caches the results of each lookup so that subsequent
	 * can be made faster.  
	 * @param c
	 * @return
	 * @throws IOException
	 */
	public boolean isConfluence(Coordinate c) throws IOException {
		if (confluenceCache.containsKey(c)) {
			return confluenceCache.get(c);
		}
		
		Point p = geometryFactory.createPoint(c);
		Filter touchesFilter = filterFactory.touches(
				filterFactory.property(waterGeometryPropertyName), 
				filterFactory.literal(p));
		SimpleFeatureCollection touchingWaterFeatures = waterFeatures.getFeatures(touchesFilter);
		
		boolean isConfluence = touchingWaterFeatures.size() >= 3;
		confluenceCache.put(c, isConfluence);
				
		return isConfluence;
	}
	
	public List<Coordinate> getConfluences() throws IOException {
		List<Coordinate> confluences = new ArrayList<Coordinate>();
		
		SimpleFeatureIterator waterIt = waterFeatures.getFeatures().features();
		while(waterIt.hasNext()) {
			SimpleFeature waterFeature = waterIt.next();
			Geometry g = (Geometry)waterFeature.getDefaultGeometry();
			Coordinate[] coords = g.getCoordinates();
			if (coords.length > 0) {
				Coordinate firstCoord = coords[0];
				if (isConfluence(firstCoord) && !confluences.contains(firstCoord)) {
					confluences.add(firstCoord);
				}
			}
			if (coords.length > 1) {
				Coordinate lastCoord = coords[coords.length-1];
				if (isConfluence(lastCoord) && !confluences.contains(lastCoord)) {
					confluences.add(lastCoord);
				}
			}
		}
		waterIt.close();
		
		return confluences;
	}
	
	public List<Polygon> getLakePolygons() throws IOException {
		List<Polygon> results = new ArrayList<Polygon>();
		
		Polygonizer polygonizer = new Polygonizer(false);
		SimpleFeatureIterator it = waterFeatures.getFeatures().features();
		while(it.hasNext()) {
			SimpleFeature f = it.next();
			LineString g = (LineString)f.getDefaultGeometry();
			if (g != null && g.isValid()) {
				polygonizer.add(g);
			}
		}
		it.close();
		Collection<Geometry> geoms = polygonizer.getPolygons();
		for(Geometry geom : geoms) {
			results.add((Polygon)geom);
		}
		
		return results;
	}
	
	public boolean containsWater(Polygon poly) throws IOException {
		
		if (!poly.isValid()) {
			throw new IllegalArgumentException("polygon must be valid");
		}
		
		Filter filter = filterFactory.within(
				filterFactory.property(waterGeometryPropertyName), 
				filterFactory.literal(poly));
		SimpleFeatureCollection matches = waterFeatures.getFeatures(filter);
		boolean contains = matches.size() > 0;
		return contains;
	}
	
	/**
	 * gets the water feature that overlaps the given geometry.  
	 * returns null if none found.  The edge must be a 2-point segment.
	 * @param f
	 * @return
	 * @throws IOException
	 */
	public List<SimpleFeature> getOverlappingByWater(Geometry g) throws IOException {
		
		List<SimpleFeature> touching = getTouchingWater(g, null);
		
		List<SimpleFeature> results = new ArrayList<SimpleFeature>();
		for(SimpleFeature f: touching) {
			Geometry t = (Geometry)f.getDefaultGeometry();
			if (t.coveredBy(g) || t.covers(g)) {
				results.add(f);
			}
		}
		
		return results;		
	}
	
	/**
	 * gets the water feature that overlaps the given geometry.  
	 * returns null if none found.  The edge must be a 2-point segment.
	 * @param f
	 * @return
	 * @throws IOException
	 */
	public List<SimpleFeature> getContainingingWater(Geometry edge) throws IOException {
		List<SimpleFeature> results = new ArrayList<SimpleFeature>();
		
		if (edge.getNumPoints() != 2) {
			throw new IllegalArgumentException("edge must have exactly two vertices");
		}
		Filter overlapsFilter = filterFactory.contains(
				filterFactory.property(waterGeometryPropertyName), 
				filterFactory.literal(edge));
		SimpleFeatureCollection overlappingWaterFeatures = waterFeatures.getFeatures(overlapsFilter);
		
		SimpleFeatureIterator it = overlappingWaterFeatures.features();
		if (it.hasNext()) {
			SimpleFeature f = it.next();
			results.add(f);
		}
		it.close();
		
		return results;		
	}
	
	public SimpleFeature getOneContainingWater(Geometry g) throws IOException {
		List<SimpleFeature> matches = this.getContainingingWater(g);
		if (matches.size() > 0) {
			return matches.get(0);
		}
		return null;
	}
	
	/**
	 * gets the water features that touch the given point. 
	 * @param point
	 * @return list of touching water features
	 * @throws IOException
	 */
	public List<SimpleFeature> getTouchingWater(Coordinate c) throws IOException {
		Point p = geometryFactory.createPoint(c);
		return getTouchingWater(p, null);
	}
	
	/**
	 * gets the water features that touch the given geometry in any way.
	 * @param point
	 * @return list of touching water features
	 * @throws IOException
	 */
	public List<SimpleFeature> getTouchingWater(Geometry g) throws IOException {
		return getTouchingWater(g, null);
	}
	
	/**
	 * gets the water features that touch the given geometry in any way.  if 'exclude' is specified, 
	 * ignore water features that touch the 'exclude' geometry.
	 * @param point
	 * @param exclude
	 * @return list of touching water features
	 * @throws IOException
	 */
	public List<SimpleFeature> getTouchingWater(Geometry g, Geometry exclude) throws IOException {
		List<SimpleFeature> result = new ArrayList<SimpleFeature>();

		Filter filter = filterFactory.or(
				
				//"touches" for lines means the endpoint of one line touches the other line
				//but it doesn't include the case where the lines cross (whether at an explicitly shared
				//vertex or another point)
				filterFactory.touches(
						filterFactory.property(waterGeometryPropertyName), 
						filterFactory.literal(g)),
				
				//to ensure lines that crossing lines are considered touching, we also include the
				//intersects clause
				filterFactory.intersects(
						filterFactory.property(waterGeometryPropertyName), 
						filterFactory.literal(g))
				);
		
		if (exclude != null) {			
			Filter touchesExcludeFilter = filterFactory.touches(
						filterFactory.property(waterGeometryPropertyName),	
						filterFactory.literal(exclude) 	
						);						
			Filter notTouchesExcludeFilter = filterFactory.not(touchesExcludeFilter);
			filter = filterFactory.and(
					filter,
					notTouchesExcludeFilter
					);
		
		}
		
		SimpleFeatureCollection matches = waterFeatures.getFeatures(filter);
		
		SimpleFeatureIterator it = matches.features();
		while (it.hasNext()) {
			SimpleFeature f = it.next();
			result.add(f);
		}
		it.close();
				
		return result;
	}
	
	/**
	 * checks whether the geometry crosses water 
	 */
	public boolean isCrossingWater(Geometry g) throws IOException {
		List<SimpleFeature> matches = getCrossingWater(g);		 
		return matches.size() > 0;
	}
	
	/**
	 * This function identifies whether the given coordinate touches a water feature in any way.
	 * It returns true if touching water at an endpoint, confluence, or mid-line.
	 * @param f
	 * @return
	 * @throws IOException
	 */
	public List<SimpleFeature> getCrossingWater(Geometry g) throws IOException {
		List<SimpleFeature> results = new ArrayList<SimpleFeature>();
		
		Filter filter = 
				filterFactory.crosses(
						filterFactory.property(waterGeometryPropertyName), 
						filterFactory.literal(g));
		SimpleFeatureCollection filteredFeatures = waterFeatures.getFeatures(filter);
		
		SimpleFeatureIterator it = filteredFeatures.features();
		if (it.hasNext()) {
			SimpleFeature f = it.next();
			results.add(f);
		}
		it.close();
		
		return results;	
	}
	
	/**
	 * checks whether the geometry intersects water 
	 */
	public boolean isIntersectingWater(Geometry g) throws IOException {
		List<SimpleFeature> matches = getIntersectingWater(g);		 
		return matches.size() > 0;
	}
	
	/**
	 * This function identifies whether the given coordinate touches a water feature in any way.
	 * It returns true if touching water at an endpoint, confluence, or mid-line.
	 * @param f
	 * @return
	 * @throws IOException
	 */
	public List<SimpleFeature> getIntersectingWater(Geometry g) throws IOException {
		List<SimpleFeature> results = new ArrayList<SimpleFeature>();
		
		Filter filter = 
				filterFactory.intersects(
						filterFactory.property(waterGeometryPropertyName), 
						filterFactory.literal(g));
		SimpleFeatureCollection filteredFeatures = waterFeatures.getFeatures(filter);
		
		SimpleFeatureIterator it = filteredFeatures.features();
		if (it.hasNext()) {
			SimpleFeature f = it.next();
			results.add(f);
		}
		it.close();
		
		return results;	
	}
	
	/**
	 * This function identifies whether the given geometry has any one or more coordinates that 
	 * touch a water feature in any way.
	 * It returns true if touching water at an endpoint, confluence, or mid-line, and also if an edge
	 * in the input geometry overlaps an edge in a water feature.
	 * @param f
	 * @return
	 * @throws IOException
	 */
	public boolean isTouchingWater(Geometry g) throws IOException {
		List<SimpleFeature> matches = getTouchingWater(g, null);		 
		return matches.size() > 0;
	}
	
	/**
	 * This function identifies whether the given geometry has any one or more coordinates that 
	 * touch a water feature in any way.
	 * It returns true if touching water at an endpoint, confluence, or mid-line, and also if an edge
	 * in the input geometry overlaps an edge in a water feature.
	 * @param g geometry to test
	 * @param exclude ignore water features that touch the given geometry.
	 * @return
	 * @throws IOException
	 */
	public boolean isTouchingWater(Geometry g, Geometry exclude) throws IOException {
		List<SimpleFeature> matches = getTouchingWater(g, exclude);		 
		return matches.size() > 0;
	}
	
	/**
	 * This function identifies whether the given coordinate touches a water feature in any way.
	 * It returns true if touching water at an endpoint, confluence, or mid-line.
	 * @param f
	 * @return
	 * @throws IOException
	 */
	public boolean isTouchingWater(Coordinate c) throws IOException {
		Point p = geometryFactory.createPoint(c);
		Filter filter = filterFactory.or(
				filterFactory.touches(
						filterFactory.property(waterGeometryPropertyName), 
						filterFactory.literal(p)),
				filterFactory.contains( 
						filterFactory.property(waterGeometryPropertyName), 
						filterFactory.literal(p))
				);
		SimpleFeatureCollection overlappingWaterFeatures = waterFeatures.getFeatures(filter);
		return overlappingWaterFeatures.size() > 0;
	}
	
	/**
	 * @param g
	 * @return
	 * @throws IOException
	 */
	public boolean isOverlappingWater(Geometry g) throws IOException {
		List<SimpleFeature> matches = getOverlappingByWater(g);
		return matches.size() > 0;
		
		/*	
		Filter filter = filterFactory.or(
				filterFactory.overlaps(
						filterFactory.property(waterGeometryPropertyName), 
						filterFactory.literal(g)),
				filterFactory.overlappedBy( 
						filterFactory.property(waterGeometryPropertyName), 
						filterFactory.literal(g))
				);
		SimpleFeatureCollection matches = waterFeatures.getFeatures(filter);
		
		return matches.size() > 0;
		*/
	}
	
	/**
	 * returns the water feature nearest to the given geometry
	 * @param g
	 * @return
	 */
	public SimpleFeature getNearestWater(Coordinate c) {
		Point g = geometryFactory.createPoint(c);
		return getNearestWater(g);
	}
	
	/**
	 * returns the water feature nearest to the given geometry
	 * @param g
	 * @return
	 */
	public SimpleFeature getNearestWater(Geometry g) {
		return nearestWaterFinder.findNearest(g);
	}
	
	/**
	 * returns the water feature nearest to g.  does not 
	 * consider any water feature which overlaps 'excludeTouches'
	 * @param g
	 * @return
	 */
	public SimpleFeature getNearestWater(Geometry g, Geometry excludeTouches) {
		return nearestWaterFinder.findNearest(g, excludeTouches);
	}
	
	public SimpleFeature getNearestWaterFeatureInDirection(Coordinate c, double compassAngle, Geometry excludeTouches) {
		return nearestWaterFinder.findNearestFeatureInDirection(c, compassAngle, excludeTouches);		
	}
	
	public Coordinate getNearestWaterCoordinateInDirection(Coordinate c, double compassAngle, Geometry excludeTouches) {
		return nearestWaterFinder.findNearestCoordinateInDirection(c, compassAngle, excludeTouches);		
	}
	
	/**
	 * gets the distance from g to the nearest water feature.  Excludes water features
	 * that overlap "excludesOverlaps"
	 * @param g
	 * @return
	 */
	public double getDistanceToNearestWater(Geometry g, Geometry excludeTouches) {
		SimpleFeature nearestWater = getNearestWater(g, excludeTouches);
		Geometry nearestGeom = (Geometry)nearestWater.getDefaultGeometry();
		return g.distance(nearestGeom);
	}
	
	/**
	 * gets the distance to the nearest water feature
	 * @param g
	 * @return
	 */
	public double getDistanceToNearestWater(Geometry g) {
		SimpleFeature nearestWater = getNearestWater(g);
		Geometry nearestGeom = (Geometry)nearestWater.getDefaultGeometry();
		return g.distance(nearestGeom);
	}
	
	/**
	 * gets the distance to the nearest water feature
	 * @param g
	 * @return
	 */
	public double getDistanceToNearestWater(Coordinate c) {
		Point g = geometryFactory.createPoint(c);
		return getDistanceToNearestWater(g);
	}
	
	/**
	 * gets the distance to the nearest water feature
	 * @param g
	 * @return
	 */
	public double getDistanceToNearestWater(Coordinate c, Geometry excludeTouches) {
		Point g = geometryFactory.createPoint(c);
		return getDistanceToNearestWater(g, excludeTouches);
	}
	
	/**
	 * returns the water feature nearest to the given geometry
	 * @param g
	 * @return
	 */
	public double getDistDiffBetweenTwoNearestWater(Coordinate c) {
		Point g = geometryFactory.createPoint(c);
		return getDistDiffBetweenTwoNearestWater(g);
	}
	
	/**
	 * gets the difference in distance between:
	 * g and the nearest water feature
	 * g and the second nearest water feature
	 * @param g
	 * @return the distance difference
	 */
	public double getDistDiffBetweenTwoNearestWater(Geometry g) {
		List<SimpleFeature> matches = nearestWaterFinder.findNearest(g, 2);
		if (matches.size() != 2) {
			throw new IllegalStateException("Expected to find 2 nearby water features. Found "+matches.size());
		}
		SimpleFeature water1 = matches.get(0);
		SimpleFeature water2 = matches.get(1);
		
		Geometry g1 = (Geometry)water1.getDefaultGeometry();
		Geometry g2 = (Geometry)water2.getDefaultGeometry();
		
		double dist1 = g.distance(g1);
		double dist2 = g.distance(g2);
		
		//System.out.println(water1.getID() +"("+dist1+")"+", "+water2.getID()+" ("+dist2+")");
		
		double diff = Math.abs(dist1 - dist2);
		return diff;
	}

	/**
	 * Returns a subcollection which includes only those features from the input that don't touch water
	 * @param features an input set of features
	 * @throws IOException
	 */
	public SimpleFeatureCollection filterNotTouchingWater(SimpleFeatureCollection features) throws IOException {
		DefaultFeatureCollection matches = new DefaultFeatureCollection();
		
		SimpleFeatureIterator it = features.features();
		while (it.hasNext()) {
			SimpleFeature f = it.next();
			Geometry g = (Geometry)f.getDefaultGeometry();
			if (!isTouchingWater(g)) {
				matches.add(f);
			}			
		}
		it.close();		
		
		return matches;	
	}
	
	/**
	 * creates a Water object from a given geopackage filename and table name
	 * @param inWaterFilename the full path and filename of a geopackage containing water features
	 * @param waterTable the table name within the geopackage which contains water
	 * @return
	 */
	public static Water fromGeopackage(String inWaterFilename, String waterTable) {
		return fromGeopackage(inWaterFilename, waterTable, null);
	}
	
	public static Water fromGeopackage(String inWaterFilename, String waterTable, ReferencedEnvelope boundsToProcess) {

		Map<String, String> waterInputDatastoreParams = new HashMap<String, String>();
		waterInputDatastoreParams.put("dbtype", "geopkg");
		waterInputDatastoreParams.put("database", inWaterFilename);
		
		DataStore waterDatastore = null;
		try {
			waterDatastore = DataStoreFinder.getDataStore(waterInputDatastoreParams);
		} catch (IOException e) {
			System.out.println("Unable to open input file: "+inWaterFilename);
			e.printStackTrace();
			System.exit(1);
		}
		
		if (waterDatastore == null) {
			System.out.println("Unable to open input water datastore");
			System.exit(1);
		}
		
		SimpleFeatureSource waterFeatureSource = null;
		try {
			waterFeatureSource = waterDatastore.getFeatureSource(waterTable);
		} catch (IOException e1) {
			System.out.println("Unable to get in feature source: "+waterTable);
			e1.printStackTrace();
			System.exit(1);
		}
		
		String waterGeometryPropertyName = waterFeatureSource.getSchema().getGeometryDescriptor().getLocalName();
	
		//create a bbox filter
		Hints filterHints = new Hints( Hints.FEATURE_2D, true ); // force 2D queries
		FilterFactory2 filterFactory = CommonFactoryFinder.getFilterFactory2(filterHints);
		
		Filter bboxFilter = null;
		Filter bufferedBboxFilter = null;
		if (boundsToProcess != null) {
			System.out.println("Defining bbox limit...");
			
			bboxFilter = filterFactory.bbox(
					filterFactory.property(waterGeometryPropertyName), 
					boundsToProcess);								
		}	
		
		//add spatial index and apply bbox filter
		SpatialIndexFeatureSource fastWaterFeatureSource = null;
		try {
			SpatialIndexFeatureCollection waterFeatureCollection = null;
			if (bboxFilter != null) {
				waterFeatureCollection = new SpatialIndexFeatureCollection(waterFeatureSource.getFeatures(bboxFilter));
			}
			else {
				waterFeatureCollection = new SpatialIndexFeatureCollection(waterFeatureSource.getFeatures());
			}
			fastWaterFeatureSource = new SpatialIndexFeatureSource(waterFeatureCollection);
		} catch (IOException e) {
			System.out.println("Unable to add spatial index");
			System.exit(1);
		}
		
		Water water = new Water(fastWaterFeatureSource);
		return water;
	}
}
