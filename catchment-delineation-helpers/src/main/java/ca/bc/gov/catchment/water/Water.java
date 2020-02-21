package ca.bc.gov.catchment.water;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.factory.CommonFactoryFinder;
import org.geotools.factory.Hints;
import org.geotools.geometry.jts.JTSFactoryFinder;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.filter.Filter;
import org.opengis.filter.FilterFactory2;

import ca.bc.gov.catchment.utils.SpatialUtils;

public class Water {
	
	private static final int MAX_CONFLUENCE_CACHE_SIZE = 1000;
	
	private FilterFactory2 filterFactory;
	private GeometryFactory geometryFactory;
	private SimpleFeatureSource waterFeatures;
	private SimpleFeatureType waterFeatureType;
	private String waterGeometryPropertyName;
	private Map<Coordinate, Boolean> confluenceCache;

	public Water(SimpleFeatureSource waterFeatures) {
		Hints filterHints = new Hints(Hints.FEATURE_2D, true);
		this.filterFactory = CommonFactoryFinder.getFilterFactory2(filterHints);
		this.geometryFactory = JTSFactoryFinder.getGeometryFactory();
		this.waterFeatures = waterFeatures;
		this.waterFeatureType = waterFeatures.getSchema();
		this.waterGeometryPropertyName = waterFeatureType.getGeometryDescriptor().getLocalName();

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
	
	/**
	 * gets the water feature that overlaps the given edge.  returns null if none found.  The edge must be a 2-point segment.
	 * @param f
	 * @return
	 * @throws IOException
	 */
	public SimpleFeature getTouchingWater(Geometry edge) throws IOException {
		if (edge.getNumPoints() != 2) {
			throw new IllegalArgumentException("edge must have exactly two vertices");
		}
		Filter overlapsFilter = filterFactory.contains(
				filterFactory.property(waterGeometryPropertyName), 
				filterFactory.literal(edge));
		SimpleFeatureCollection overlappingWaterFeatures = waterFeatures.getFeatures(overlapsFilter);
		if (overlappingWaterFeatures.size() == 1) {
			SimpleFeatureIterator it = overlappingWaterFeatures.features();
			if (it.hasNext()) {
				return it.next();
			}
			it.close();
		}
		else if (overlappingWaterFeatures.size() == 0) {
			return null;
		}
		throw new IllegalStateException("edge overlaps two water features.  this indicates an invalid stream network.");
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
		Filter filter = filterFactory.or(
				filterFactory.touches(
						filterFactory.property(waterGeometryPropertyName), 
						filterFactory.literal(g)),
				filterFactory.contains(
						filterFactory.property(waterGeometryPropertyName), 
						filterFactory.literal(g))
				);
		SimpleFeatureCollection overlappingWaterFeatures = waterFeatures.getFeatures(filter);
		return overlappingWaterFeatures.size() > 0;
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
	 * This function is a less selective version of 'isTouchingWater'.  This function returns true in
	 * all cases as 'isTouchingWater' **except** it doesn't return true if the only common coordinate
	 * between the input geometry and a water feature is an endpoint of a water feature.
	 * @param f
	 * @return
	 * @throws IOException
	 */
	public boolean isOverlappingWater(Geometry g) throws IOException {
		Filter overlapsFilter = filterFactory.contains(
				filterFactory.property(waterGeometryPropertyName), 
				filterFactory.literal(g));
		SimpleFeatureCollection overlappingWaterFeatures = waterFeatures.getFeatures(overlapsFilter);
		return overlappingWaterFeatures.size() > 0;
	}

}
