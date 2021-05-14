package ca.bc.gov.catchment.algorithms;

import java.io.IOException;
import java.util.List;

import org.geotools.data.DataUtilities;
import org.geotools.data.collection.SpatialIndexFeatureCollection;
import org.geotools.data.collection.SpatialIndexFeatureSource;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.feature.DefaultFeatureCollection;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.geometry.jts.JTSFactoryFinder;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.opengis.feature.simple.SimpleFeature;

import ca.bc.gov.catchment.utils.SpatialUtils;

public class NearestNeighbour3DMaker {

	private static final double DEFAULT_SEARCH_RADIUS = 100;
	
	private int kNeighbours;
	private NearestNeighbour nearestNeighbour;
	private GeometryFactory geometryFactory;
	private ElevationEstimator elevationEstimator;
	
	public NearestNeighbour3DMaker(SimpleFeatureSource features3D, int kNeighbours) {
		this(features3D, kNeighbours, DEFAULT_SEARCH_RADIUS);
	}
	
	public NearestNeighbour3DMaker(SimpleFeatureSource features3D, int kNeighbours, double searchRadius) {
		this.kNeighbours = kNeighbours;
		this.nearestNeighbour = new NearestNeighbour(features3D, searchRadius);		
		this.geometryFactory = JTSFactoryFinder.getGeometryFactory();
		this.elevationEstimator = new ElevationEstimator();
	}
	
	public SimpleFeatureSource make3dCopy(SimpleFeatureSource features) throws IOException {
		SimpleFeatureCollection outFc = make3dCopy(features.getFeatures());
		SpatialIndexFeatureCollection indexedFc = new SpatialIndexFeatureCollection(outFc);
		SpatialIndexFeatureSource result = new SpatialIndexFeatureSource(indexedFc);
		return result;	
	}
	
	public SimpleFeatureCollection make3dCopy(SimpleFeatureCollection inFeatures) throws IOException {
		DefaultFeatureCollection outFeatures = new DefaultFeatureCollection();
		SimpleFeatureIterator inIt = inFeatures.features();
		while(inIt.hasNext()) {
			SimpleFeature original = inIt.next();
			SimpleFeature copy3d = null;
			try {
				copy3d = make3dCopy(original);
			} catch (IllegalArgumentException e) { //thrown if not enough nearby coords to make 3d copy
				//skip
				continue;
			}
			outFeatures.add(copy3d);
		}
		inIt.close();
		return outFeatures;
	}
	
	public SimpleFeature make3dCopy(SimpleFeature f) throws IOException {
		Geometry updatedGeometry = make3dCopy((Geometry)f.getDefaultGeometry());
		SimpleFeature copy = SimpleFeatureBuilder.copy(f);
		copy.setDefaultGeometry(updatedGeometry);
		return copy;
	}
	
	public Geometry make3dCopy(Geometry g) throws IOException {
		if (g == null) {
			throw new NullPointerException("geometry must not be null");
		}
		Coordinate[] originalCoords = g.getCoordinates();
		Coordinate[] coords3d = new Coordinate[originalCoords.length];
		
		int index = 0;
		for(Coordinate originalCoord : originalCoords) {
			Coordinate coord3d = null;
			if (Double.isNaN(originalCoord.getZ())) {
				List<Coordinate> nearbyCoords = nearestNeighbour.getKNearestCoords(originalCoord, kNeighbours);
				double z = elevationEstimator.estimateElevationFromNearbyPoints(originalCoord, nearbyCoords);
				coord3d = new Coordinate(originalCoord.getX(), originalCoord.getY(), z);
			}
			else {
				coord3d = new Coordinate(originalCoord.getX(), originalCoord.getY(), originalCoord.getZ());
			}
			coords3d[index] = coord3d;
			index++;
		}
		if (g.getGeometryType() == "LineString") {
			return geometryFactory.createLineString(coords3d);
		}
		if (g.getGeometryType() == "Polygon") {
			return geometryFactory.createPolygon(coords3d);
		}
		if (g.getGeometryType() == "Point") {
			return geometryFactory.createPoint(coords3d[0]);
		}
		throw new IllegalArgumentException(g.getGeometryType() + " geometries are not currently supported.");
	}
	
	
}
