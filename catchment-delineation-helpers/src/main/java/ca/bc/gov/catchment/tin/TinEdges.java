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

public class TinEdges extends Tin {


	public TinEdges(SimpleFeatureSource tinEdges)  {
		super(tinEdges);
	}

	public TinEdges(SimpleFeatureSource tinEdges, Filter filter)  {
		super(tinEdges, filter);
	}
	
	public SimpleFeature getEdge(Coordinate c1, Coordinate c2) throws IOException  {
		Coordinate coords[] = {c1, c2};
		LineString edgeToFind = geometryFactory.createLineString(coords);
		
		//find the tin edge that overlaps the input edge
		Filter overlapsFilter = filterFactory.overlaps(
				filterFactory.property(geometryPropertyName), 
				filterFactory.literal(edgeToFind)
				);
		SimpleFeatureCollection matches = getFeatures(overlapsFilter);		
		SimpleFeatureIterator matchesIt = matches.features();
		SimpleFeature result = null;
		try {
			if(matchesIt.hasNext()) {
				result = matchesIt.next();
				if (matchesIt.hasNext()) {
					throw new IllegalStateException("found more than one overlapping edge");
				}
			}
		} 
		finally {
			matchesIt.close();
		}
		return result;
	}
	
}
