package ca.bc.gov.catchment.fitness;

import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collection;
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
import org.locationtech.jts.geom.LineString;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.filter.Filter;
import org.opengis.filter.FilterFactory2;

import ca.bc.gov.catchment.tin.Edge;
import ca.bc.gov.catchment.tin.Tin;
import ca.bc.gov.catchment.tin.TinPolys;
import ca.bc.gov.catchment.tin.Triangle;
import ca.bc.gov.catchment.utils.SpatialUtils;

/**
 * @author Brock
 *
 */
public class ElevationSectionFitness extends SectionFitness {

	private GeometryFactory geometryFactory;
	private Tin tin;
	private FilterFactory2 filterFactory;
	private SimpleFeatureType tinFeatureType;
	private String tinGeometryProperty;
	
	public ElevationSectionFitness(Tin tin) {
		this.tin = tin;
		this.filterFactory = CommonFactoryFinder.getFilterFactory2();
		this.tinFeatureType = tin.getSchema();
		this.tinGeometryProperty = tinFeatureType.getGeometryDescriptor().getLocalName();
		this.geometryFactory = JTSFactoryFinder.getGeometryFactory();
	}
	
	@Override
	public double fitness(Geometry geom) throws IOException {
		double sumZ = 0;
		for(Coordinate coord : geom.getCoordinates()) {
			sumZ += coord.getZ();
		}
		double fitness = sumZ;
		return fitness;
	}
	
	@Override
	public double fitness(Coordinate c1, Coordinate c2) throws IOException {		
		Coordinate[] coords = {c1, c2};
		LineString segment = geometryFactory.createLineString(coords);
		return fitness(segment);
	}

	
	
}
