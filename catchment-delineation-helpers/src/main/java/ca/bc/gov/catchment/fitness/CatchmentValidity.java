package ca.bc.gov.catchment.fitness;

import java.io.IOException;

import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.factory.CommonFactoryFinder;
import org.geotools.factory.Hints;
import org.geotools.geometry.jts.JTSFactoryFinder;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.filter.Filter;
import org.opengis.filter.FilterFactory2;

import ca.bc.gov.catchment.water.WaterAnalyzer;

public class CatchmentValidity {

	private FilterFactory2 filterFactory2D;
	private GeometryFactory geometryFactory;
	private SimpleFeatureSource waterFeatures;
	private SimpleFeatureType waterFeatureType;
	private String waterGeometryPropertyName;
	private WaterAnalyzer waterAnalyzer;
	
	public CatchmentValidity(SimpleFeatureSource waterFeatures) {
		Hints filterHints = new Hints( Hints.FEATURE_2D, true ); // force 2D queries
		this.filterFactory2D = CommonFactoryFinder.getFilterFactory2(filterHints);
		this.geometryFactory = JTSFactoryFinder.getGeometryFactory();
		this.waterFeatures = waterFeatures;
		this.waterFeatureType = waterFeatures.getSchema();
		this.waterGeometryPropertyName = waterFeatureType.getGeometryDescriptor().getLocalName();
		this.waterAnalyzer = new WaterAnalyzer(waterFeatures);
	}
	
	public boolean areRoutesValidWrtWater(SimpleFeatureCollection catchments) throws IOException {
		SimpleFeatureIterator catchmentIt = catchments.features();
		while(catchmentIt.hasNext()) {
			SimpleFeature catchment = catchmentIt.next();
			LineString route = (LineString)catchment.getDefaultGeometry();
			boolean isValid = isRouteValidWrtWater(route);
			if (!isValid) {
				return false;
			}
		}
		return true;
	}
	
	/**
	 * checks if a catchment route is valid with respect (w.r.t.) water features. 
	 * validity depends on:
	 * - segments of the route do not overlap segments of the water
	 * - vertices of the route do not touch vertices of the water (except at a confluence)
	 * @throws IOException 
	 */
	public boolean isRouteValidWrtWater(LineString route) throws IOException {
		
		//identify route line SEGMENTS that cover water line SEGMENTS
		//i.e a route segment with both endpoints the same as a water segment
		Filter crossesFilter = filterFactory2D.contains(
				filterFactory2D.property(waterGeometryPropertyName), 
				filterFactory2D.literal(route));
		SimpleFeatureCollection nonEndpointCrossings = waterFeatures.getFeatures(crossesFilter);
		if (nonEndpointCrossings.size() > 0) {
			return false;
		}
		
		//check that each non-confluence VERTEX of the route doesn't touch water
		for(Coordinate c : route.getCoordinates()) {
			if (!waterAnalyzer.isConfluence(c)) {
				Point p = geometryFactory.createPoint(c);
				Filter touchesFilter = filterFactory2D.touches(
						filterFactory2D.property(waterGeometryPropertyName), 
						filterFactory2D.literal(p));
				SimpleFeatureCollection touchingWaterFeatures = waterFeatures.getFeatures(touchesFilter);
				if (touchingWaterFeatures.size() > 0) {
					return false;
				}
			}			
		}
		
		return true;
	}
	

}
