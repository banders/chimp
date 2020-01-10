package ca.bc.gov.catchment.water;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.factory.CommonFactoryFinder;
import org.geotools.geometry.jts.JTSFactoryFinder;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.filter.Filter;
import org.opengis.filter.FilterFactory2;

public class WaterAnalyzer {
	
	private static final int MAX_CONFLUENCE_CACHE_SIZE = 1000;
	
	private FilterFactory2 filterFactory;
	private GeometryFactory geometryFactory;
	private SimpleFeatureSource waterFeatures;
	private SimpleFeatureType waterFeatureType;
	private String waterGeometryPropertyName;
	private Map<Coordinate, Boolean> confluenceCache;

	public WaterAnalyzer(SimpleFeatureSource waterFeatures) {
		this.filterFactory = CommonFactoryFinder.getFilterFactory2();
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
}
