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
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.filter.Filter;
import org.opengis.filter.FilterFactory2;

import ca.bc.gov.catchments.utils.SpatialUtils;

public class Tin {

	protected SimpleFeatureSource featureSource;
	protected SimpleFeatureType featureType;
	protected String geometryPropertyName;
	protected FilterFactory2 filterFactory;
	protected GeometryFactory geometryFactory;

	public Tin(SimpleFeatureSource featureSource) {
		this.featureSource = featureSource;
		this.featureType = featureSource.getSchema();
		this.geometryPropertyName = featureType.getGeometryDescriptor().getLocalName();
		this.filterFactory = CommonFactoryFinder.getFilterFactory2();
		this.geometryFactory = JTSFactoryFinder.getGeometryFactory();
	}
	
	public SimpleFeatureSource getFeatureSource() {
		return this.featureSource;
	}
	
	public SimpleFeatureType getSchema() {
		return featureType;
	}
	
	public SimpleFeatureCollection getFeatures() throws IOException {
		return featureSource.getFeatures();
	}
	
	public SimpleFeatureCollection getFeatures(Filter f) throws IOException {
		return featureSource.getFeatures(f);
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
	
	public List<Coordinate> getCoordsInRadius(Coordinate c, double radius) throws IOException  {
		Point p = geometryFactory.createPoint(c);
		List<Coordinate> coords = new ArrayList<Coordinate>();
		
		//first find water features that touch the given coordinate (at any point)
		Filter radiusFilter = filterFactory.dwithin(
				filterFactory.property(geometryPropertyName), 
				filterFactory.literal(p),
				radius,
				"meter");
		SimpleFeatureCollection matches = featureSource.getFeatures(radiusFilter);
		
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
	
	public double getMaxElevation() throws IOException {
		double maxZ = 0;
		SimpleFeatureCollection all = featureSource.getFeatures();
		SimpleFeatureIterator allIt = all.features();
		while(allIt.hasNext()) {
			SimpleFeature f = allIt.next();
			Geometry g = (Geometry)f.getDefaultGeometry();
			Coordinate[] coords = g.getCoordinates();
			for(Coordinate c : coords) {
				if (c.getZ() > maxZ) {
					maxZ = c.getZ();
				}
			}
		}
		return maxZ;
	}

	public double getMaxEdgeLength() throws IOException {
		double maxLen = 0;
		SimpleFeatureCollection all = featureSource.getFeatures();
		SimpleFeatureIterator allIt = all.features();
		while(allIt.hasNext()) {
			SimpleFeature f = allIt.next();
			Geometry g = (Geometry)f.getDefaultGeometry();
			Coordinate[] coords = g.getCoordinates();
			Coordinate prev = null;
			for(Coordinate c : coords) {
				if (prev != null) {
					LineString segment = SpatialUtils.toLineString(prev, c);
					double len = segment.getLength();
					if (len > maxLen) {
						maxLen = len;
					}
				}
				prev = c;
			}
		}
		return maxLen;
	}
}
