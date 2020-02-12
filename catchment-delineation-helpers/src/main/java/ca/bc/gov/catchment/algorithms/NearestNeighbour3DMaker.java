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

	private int kNeighbours;
	private NearestNeighbour nearestNeighbour;
	private GeometryFactory geometryFactory;
	
	public NearestNeighbour3DMaker(SimpleFeatureSource features3D, int kNeighbours) {
		this.kNeighbours = kNeighbours;
		this.nearestNeighbour = new NearestNeighbour(features3D);		
		this.geometryFactory = JTSFactoryFinder.getGeometryFactory();
	}
	
	public NearestNeighbour3DMaker(SimpleFeatureSource features3D, int kNeighbours, int searchRadius) {
		this.kNeighbours = kNeighbours;
		this.nearestNeighbour = new NearestNeighbour(features3D, searchRadius);		
		this.geometryFactory = JTSFactoryFinder.getGeometryFactory();
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
			SimpleFeature copy3d = make3dCopy(original);
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
		Coordinate[] originalCoords = g.getCoordinates();
		Coordinate[] coords3d = new Coordinate[originalCoords.length];
		
		int index = 0;
		for(Coordinate originalCoord : originalCoords) {
			Coordinate coord3d = null;
			if (Double.isNaN(originalCoord.getZ())) {
				List<Coordinate> nearbyCoords = nearestNeighbour.getKNearestCoords(originalCoord, kNeighbours);
				double z = estimateElevationFromNearbyPoints(originalCoord, nearbyCoords);
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
	
	private double estimateElevationFromNearbyPoints(Coordinate c, List<Coordinate> nearbyCoords) {
		Point p = geometryFactory.createPoint(c);
		
		//get distance sum of nearest points
		double denom = 0;
		for(Coordinate nearbyCoord : nearbyCoords) {
			Point nearbyPoint = geometryFactory.createPoint(nearbyCoord);
			denom += 1/nearbyPoint.distance(p);
		}
		
		//calc weighted average elevation
		double weightedAverageElevation = 0;
		for(Coordinate nearbyCoord : nearbyCoords) {
			Point nearbyPoint = geometryFactory.createPoint(nearbyCoord);
			double z = nearbyCoord.getZ();
			double dist = nearbyPoint.distance(p);
			
			//if a nearbyPoint is exactly on top of the given point, don't attempt to find a 
			//weighted average of multiple nearby points.  instead just use the value from the single 
			//point at the same location
			if (dist == 0) {
				return z;
			}
			double numerator = z/dist;
			double thisVal = numerator/denom;
			//System.out.println(" elevation:"+z+", dist:"+dist);
			weightedAverageElevation += thisVal;
		}
		
		return weightedAverageElevation;
	}
	
}
