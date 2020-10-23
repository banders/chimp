package ca.bc.gov.catchment.algorithms;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

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

public class NearestNeighbour {

	private SimpleFeatureSource features;
	private double initialSearchRadiusMetres;

	private FilterFactory2 filterFactory2D;
	private GeometryFactory geometryFactory;
	private SimpleFeatureType featureType;
	private String geometryPropertyName;
	
	public NearestNeighbour(SimpleFeatureSource features) {
		this(features, 200);
	}
	
	public NearestNeighbour(SimpleFeatureSource features, double initialSearchRadius) {
		this.features = features;
		this.initialSearchRadiusMetres = initialSearchRadius;
		
		Hints filterHints = new Hints( Hints.FEATURE_2D, true ); // force 2D queries
		this.filterFactory2D = CommonFactoryFinder.getFilterFactory2(filterHints);
		this.geometryFactory = JTSFactoryFinder.getGeometryFactory();
		this.featureType = features.getSchema();
		this.geometryPropertyName = featureType.getGeometryDescriptor().getLocalName();
	}
	
	public List<Coordinate> getKNearestCoords(Coordinate c, int k) throws IOException {
		double radius = initialSearchRadiusMetres;
		
		//search for points within a radius.  increase the radius and try again if none are found.  
		//retry up to 3 times, before giving up.
		List<Coordinate> matches = null;
		for(int attempt = 0; attempt < 3; attempt++) {
			matches = getCoordsInRadius(c, radius);
			if (matches.size() > 0) {
				break;
			}
			radius *= 2;
		}
		//give up.  no neighbours found
		if (matches.size() == 0) {
			throw new IllegalArgumentException("unable to find "+k+" neighbours. ("+matches.size()+" were found)");
		}
		
		//sorts the matches by distance to the input coordinate
		final Point p = geometryFactory.createPoint(c);
		matches.sort(new Comparator<Coordinate>() {
			public int compare(Coordinate c1, Coordinate c2) {
				Point p1 = geometryFactory.createPoint(c1);
				Point p2 = geometryFactory.createPoint(c2);
				double dist1 = p1.distance(p);
				double dist2 = p2.distance(p);
				return dist1 > dist2 ? 1 : -1;
			}			
		});
		
		if (matches.size() < k) {
			throw new IllegalArgumentException("unable to find "+k+" neighbours. (only "+matches.size()+" were found)");
		}
		
		
		//copy the k nearest neighbours to the result set
		List<Coordinate> result = new ArrayList<Coordinate>();
		for(int i = 0;  i < k; i++) {
			result.add(matches.get(i));
		}
		return result;		
		
	}
	
	private List<Coordinate> getCoordsInRadius(Coordinate c, double radius) throws IOException  {
		Point p = geometryFactory.createPoint(c);
		List<Coordinate> coords = new ArrayList<Coordinate>();
		
		//first find water features that touch the given coordinate (at any point)
		Filter radiusFilter = filterFactory2D.dwithin(
				filterFactory2D.property(geometryPropertyName), 
				filterFactory2D.literal(p),
				radius,
				"meter");
		SimpleFeatureCollection matches = features.getFeatures(radiusFilter);
		
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
