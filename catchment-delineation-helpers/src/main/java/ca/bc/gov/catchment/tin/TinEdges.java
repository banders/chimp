package ca.bc.gov.catchment.tin;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.factory.CommonFactoryFinder;
import org.geotools.geometry.jts.JTSFactoryFinder;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.filter.Filter;
import org.opengis.filter.FilterFactory2;

public class TinEdges {

	private FilterFactory2 filterFactory;
	private GeometryFactory geometryFactory;
	private SimpleFeatureType tinEdgesFeatureType;
	private String tinEdgesGeometryPropertyName;
	private SimpleFeatureSource tinEdges;
	
	public TinEdges(SimpleFeatureSource tinEdges) {
		this.tinEdges = tinEdges;
		this.tinEdgesFeatureType = tinEdges.getSchema();
		this.tinEdgesGeometryPropertyName = tinEdgesFeatureType.getGeometryDescriptor().getLocalName();
		this.filterFactory = CommonFactoryFinder.getFilterFactory2();
		this.geometryFactory = JTSFactoryFinder.getGeometryFactory();
	}

	public SimpleFeatureType getSchema() {
		return tinEdgesFeatureType;
	}
	
	public SimpleFeatureCollection getFeatures() throws IOException {
		return tinEdges.getFeatures();
	}
	
	public SimpleFeatureCollection getFeatures(Filter f) throws IOException {
		return tinEdges.getFeatures(f);
	} 
	
	public Coordinate getRandomCoordInRadius(Coordinate c, double radius, boolean excludeOriginal) throws IOException {
		int MAX_TRIES = 100;
		List<Coordinate> coords = getCoordsInRadius(c, radius);
		for(int i = 0; i < MAX_TRIES; i++) {
			int pickedIndex = (int)(Math.random() * coords.size());
			Coordinate picked = coords.get(pickedIndex);
			if (!excludeOriginal || !c.equals(picked)) {
				return picked;
			}
		}
		return null;
	}
	
	public SimpleFeatureSource getFeatureSource() {
		return this.tinEdges;
	}
	
	public List<Coordinate> getCoordsInRadius(Coordinate c, double radius) throws IOException  {
		Point p = geometryFactory.createPoint(c);
		List<Coordinate> coords = new ArrayList<Coordinate>();
		
		//first find water features that touch the given coordinate (at any point)
		Filter radiusFilter = filterFactory.dwithin(
				filterFactory.property(tinEdgesGeometryPropertyName), 
				filterFactory.literal(p),
				radius,
				"meter");
		SimpleFeatureCollection matches = tinEdges.getFeatures(radiusFilter);
		
		SimpleFeatureIterator matchesIt = matches.features();
		try {
			while(matchesIt.hasNext()) {
				SimpleFeature match = matchesIt.next();
				Geometry g = (Geometry)match.getDefaultGeometry();
				for(Coordinate coord : g.getCoordinates()) {
					if (c.distance(coord) <= radius && !coords.contains(coord)) { //2d distance
						coords.add(coord);
					}
				}
			}
		} 
		finally {
			matchesIt.close();
		}
		return coords;
	}
}
