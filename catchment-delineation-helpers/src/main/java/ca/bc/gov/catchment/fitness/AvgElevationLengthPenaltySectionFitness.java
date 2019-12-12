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
import ca.bc.gov.catchment.tin.TinPolys;
import ca.bc.gov.catchment.tin.Triangle;
import ca.bc.gov.catchments.utils.SpatialUtils;

/**
 * Fitness is equal to the average elevation. 
 * (i.e. the sum of elevation of each coordinate, divided by the number of coordinates)
 * @author Brock Anderson
 */
public class AvgElevationLengthPenaltySectionFitness extends SectionFitness {

	private GeometryFactory geometryFactory;
	private TinPolys tinPolys;
	private FilterFactory2 filterFactory;
	private SimpleFeatureType tinPolysFeatureType;
	private String tinPolysGeometryProperty;
	private double maxLen;
	
	public AvgElevationLengthPenaltySectionFitness(TinPolys tinPolys) throws IOException {
		this.tinPolys = tinPolys;
		this.filterFactory = CommonFactoryFinder.getFilterFactory2();
		this.tinPolysFeatureType = tinPolys.getSchema();
		this.tinPolysGeometryProperty = tinPolysFeatureType.getGeometryDescriptor().getLocalName();
		this.geometryFactory = JTSFactoryFinder.getGeometryFactory();
		this.maxLen = 1500; //estimate
	}
	
	@Override
	public double fitness(Geometry geom) throws IOException {
		double len = geom.getLength();
		double sumZ = 0;
		for(Coordinate coord : geom.getCoordinates()) {
			sumZ += coord.getZ();
		}
		double avgLen = sumZ / geom.getNumPoints();
		double lenFraction = len/maxLen; //[0-1]
		if (lenFraction > 1) {
			lenFraction = 1;
		}
		double alpha = Math.log(1+lenFraction); //[0,1]
		double lenPenalty = avgLen * alpha; //[0,avgLen]
		double fitness = avgLen - lenPenalty;
		/*
		if (fitness < 0) {
			System.out.println("avgLen:"+avgLen);
			System.out.println("lenFraction:"+lenFraction);
			System.out.println("alpha:"+alpha);
			System.out.println("lenPenalty:"+lenPenalty);
			System.out.println("fitness:"+fitness);
			System.exit(1);
		}		
		*/
		return fitness;
	}
	
	@Override
	public double fitness(Coordinate c1, Coordinate c2) throws IOException {		
		Coordinate[] coords = {c1, c2};
		LineString segment = geometryFactory.createLineString(coords);
		return fitness(segment);
	}

	
	
}
