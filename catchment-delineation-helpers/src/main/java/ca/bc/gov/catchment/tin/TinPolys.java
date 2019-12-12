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

public class TinPolys extends Tin {

	public TinPolys(SimpleFeatureSource tinPolys) {
		super(tinPolys);
	}

	public TinPolys(SimpleFeatureSource tinPolys, Filter filter) {
		super(tinPolys, filter);
	}
	
	/**
	 * Gets a list of (usually two) triangles that share the given edge in the TIN.
	 */
	public List<Triangle> getTrianglesOnEdge(LineString segment) throws IOException {
		
		List<Triangle> result = new ArrayList<Triangle>();
		
		Filter overlapsFilter = filterFactory.touches(
			filterFactory.property(geometryPropertyName),
			filterFactory.literal(segment)
		);
		SimpleFeatureCollection fc = featureSource.getFeatures(overlapsFilter);
		
		SimpleFeatureIterator it = fc.features();
		try {
			while(it.hasNext()) {
				SimpleFeature f = it.next();
				Geometry g = (Geometry)f.getDefaultGeometry();
				Triangle t = new Triangle(g);
				if (!t.hasEdge(new Edge(segment.getCoordinates()))) {
					//triangle touches, but doesn't share the specified edge
					continue;
				}
				if (!t.isComplete()) {
					throw new IllegalStateException("Invalid triangle found.");
				}
				if (!result.contains(t)) {
					result.add(t);
				}
			}
		}
		finally {
			it.close();
		}
		
		return result;
	}
}
