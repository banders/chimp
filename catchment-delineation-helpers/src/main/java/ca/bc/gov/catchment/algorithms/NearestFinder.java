package ca.bc.gov.catchment.algorithms;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.factory.CommonFactoryFinder;
import org.locationtech.jts.geom.Geometry;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.filter.Filter;
import org.opengis.filter.FilterFactory2;

import ca.bc.gov.catchment.utils.SpatialUtils;

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
	
	public SimpleFeature findNearest(Geometry g) {
		List<SimpleFeature> results = findNearest(g, 1);
		if (results.size() > 0) {
			return results.get(0);
		}
		return null;
	}
	
	public List<SimpleFeature> findNearest(Geometry g, int num) {
		double distanceMeters = initialSearchDistanceMeters;
		for(int i = 0; i < MAX_ATTEMPTS; i++) {
			Filter withinFilter = filterFactory.dwithin(
					filterFactory.property(geomProperty),	
					filterFactory.literal(g), 									
					distanceMeters, 
					"meters");	
			SimpleFeatureCollection waterFeatures = features.subCollection(withinFilter);
			
			if (waterFeatures.size() < num) {
				distanceMeters *= multiple;
				continue;
			}
			
			List<SimpleFeature> waterFeaturesAsList = SpatialUtils.simpleFeatureCollectionToFeatList(waterFeatures);
			waterFeaturesAsList.sort(getComparator(g));			
			return waterFeaturesAsList.subList(0, num);
		}
		
		return new ArrayList<SimpleFeature>();
		
				
	}
	
	private Comparator<SimpleFeature> getComparator(final Geometry g) {
		Comparator<SimpleFeature> distanceToWaterComparator = new Comparator<SimpleFeature>() {

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
		return distanceToWaterComparator;
	}
}
