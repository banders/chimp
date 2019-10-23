package ca.bc.gov.catchment.water;

import java.io.IOException;

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
	private FilterFactory2 filterFactory;
	private GeometryFactory geometryFactory;
	private SimpleFeatureSource waterFeatures;
	private SimpleFeatureType waterFeatureType;
	private String waterGeometryPropertyName;

	public WaterAnalyzer(SimpleFeatureSource waterFeatures) {
		this.filterFactory = CommonFactoryFinder.getFilterFactory2();
		this.geometryFactory = JTSFactoryFinder.getGeometryFactory();
		this.waterFeatures = waterFeatures;
		this.waterFeatureType = waterFeatures.getSchema();
		this.waterGeometryPropertyName = waterFeatureType.getGeometryDescriptor().getLocalName();
	}
	
	public boolean isConfluence(Coordinate c) throws IOException {
		Point p = geometryFactory.createPoint(c);
		Filter touchesFilter = filterFactory.touches(
				filterFactory.property(waterGeometryPropertyName), 
				filterFactory.literal(p));
		SimpleFeatureCollection touchingWaterFeatures = waterFeatures.getFeatures(touchesFilter);
		return touchingWaterFeatures.size() >= 3;
	}
}
