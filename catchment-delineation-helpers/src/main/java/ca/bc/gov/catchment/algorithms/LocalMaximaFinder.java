package ca.bc.gov.catchment.algorithms;

import java.io.IOException;
import java.util.List;

import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.factory.CommonFactoryFinder;
import org.geotools.feature.DefaultFeatureCollection;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.locationtech.jts.geom.Geometry;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.filter.Filter;
import org.opengis.filter.FilterFactory2;

import ca.bc.gov.catchment.utils.SpatialUtils;

/**
 * Finds points which are higher than the all neighbour points within a given radius
 * @author Brock Anderson
 */

public class LocalMaximaFinder {

	private SimpleFeatureSource elevationPoints;
	private FilterFactory2 filterFactory;
	private double radius;
	
	public LocalMaximaFinder(SimpleFeatureSource elevationPoints, double radius) {
		this.filterFactory = CommonFactoryFinder.getFilterFactory2();
		this.elevationPoints = elevationPoints;
		this.radius = radius;
	}
	
	/**
	 * get a collection of features, each of which is a local maxima
	 * @return
	 * @throws IOException
	 */
	public SimpleFeatureCollection getMaximaPoints() throws IOException {		
		DefaultFeatureCollection results = new DefaultFeatureCollection();
		
		String geometryPropertyName = elevationPoints.getSchema().getGeometryDescriptor().getLocalName();
		
		SimpleFeatureCollection fc = elevationPoints.getFeatures();
		SimpleFeatureIterator it = fc.features();
		while(it.hasNext()){
			SimpleFeature f = it.next();
			Geometry g = (Geometry)f.getDefaultGeometry();
			Filter radiusFilter = filterFactory.dwithin(
					filterFactory.property(geometryPropertyName),	
					filterFactory.literal(g),
					radius,
					"meter"
					);
			SimpleFeatureCollection featuresInRadius = elevationPoints.getFeatures(radiusFilter);
			
			boolean isHighest = !isAnyFeatureHigherThan(featuresInRadius, SpatialUtils.getHighestZ(g));
			if (isHighest) {
				results.add(f);
			}
		}
		it.close();
		
		return results;
	}
	
	/**
	 * Checks if any feature in the given collection has an elevation higher than the given 'zToLookFor'
	 * @param fc
	 * @return
	 */
	private boolean isAnyFeatureHigherThan(SimpleFeatureCollection fc, double zToLookFor) {
		boolean isHigher = false;
		SimpleFeatureIterator it = fc.features();
		while(it.hasNext()) {
			SimpleFeature f = it.next();
			Geometry g = (Geometry)f.getDefaultGeometry();
			double z = SpatialUtils.getHighestZ(g);
			if (z > zToLookFor) {
				isHigher = true;
				break;
			}
		}
		it.close();
		return isHigher;
	}
}
