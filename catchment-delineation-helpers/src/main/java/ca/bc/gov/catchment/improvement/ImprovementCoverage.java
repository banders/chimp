package ca.bc.gov.catchment.improvement;

import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.geotools.data.DataUtilities;
import org.geotools.data.collection.SpatialIndexFeatureCollection;
import org.geotools.data.collection.SpatialIndexFeatureSource;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.feature.DefaultFeatureCollection;
import org.geotools.feature.SchemaException;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.geometry.jts.JTSFactoryFinder;
import org.geotools.referencing.CRS;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

import ca.bc.gov.catchments.utils.SpatialUtils;

public class ImprovementCoverage {

	private Map<Coordinate, ImprovementCoverageItem> coordinateMap; 
	private int pointCloudSrid;
		
	public ImprovementCoverage(SimpleFeatureSource pointCloud) throws IOException {
		coordinateMap = new TreeMap<Coordinate, ImprovementCoverageItem>();
		if(pointCloud != null) {
			initializeFromPointCloud(pointCloud);
		}
	}
	
	/**
	 * Increments the "valid" counter for all coordinates in the specified LineString
	 * @param route
	 * @param featureBeingImproved
	 */
	public void incrementCountValid(LineString route, SimpleFeature featureBeingImproved) {
		for (Coordinate c: route.getCoordinates()) {
			incrementCountValid(c, featureBeingImproved);
		}
	}
	
	/**
	 * Increments the "total" counter for the specified coordinate
	 * @param coordTested the coordinate that was tested and which the count will be incremented for
	 * @param featureBeingImproved an optional parameter (set to null if not used).  If specified, identifies the 
	 * section for which the coordinate was tested
	 */
	public void incrementCountValid(Coordinate coordTested, SimpleFeature featureBeingImproved) {
		ImprovementCoverageItem item = coordinateMap.get(coordTested);
		if (item == null) {
			item = new ImprovementCoverageItem();
		}
		String fid = null;
		if (featureBeingImproved != null) {
			fid = featureBeingImproved.getIdentifier().toString();
		}
		item.incrementCountValid(fid);
		coordinateMap.put(coordTested, item);
	}
	
	/**
	 * Increments the "total" counter for all coordinates in the specified LineString
	 * @param route
	 * @param featureBeingImproved
	 */
	public void incrementCountTotal(LineString route, SimpleFeature featureBeingImproved) {
		for (Coordinate c: route.getCoordinates()) {
			incrementCountTotal(c, featureBeingImproved);
		}
	}
	
	/**
	 * Increments the "total" counter for the specified coordinate
	 * @param coordTested
	 */
	public void incrementCountTotal(Coordinate coordTested) {
		this.incrementCountTotal(coordTested, null);
	}
	
	/**
	 * Increments the "total" counter for the specified coordinate
	 * @param coordTested the coordinate that was tested and which the count will be incremented for
	 * @param featureBeingImproved an optional parameter (set to null if not used).  If specified, identifies the 
	 * section for which the coordinate was tested
	 */
	public void incrementCountTotal(Coordinate coordTested, SimpleFeature featureBeingImproved) {
		ImprovementCoverageItem item = coordinateMap.get(coordTested);
		if (item == null) {
			item = new ImprovementCoverageItem();
		}
		String fid = null;
		if (featureBeingImproved != null) {
			fid = featureBeingImproved.getIdentifier().toString();
		}
		item.incrementCountTotal(fid);
		coordinateMap.put(coordTested, item);
	}
	
	public SimpleFeatureSource toFeatureSource() throws IOException {
		GeometryFactory geometryFactory = JTSFactoryFinder.getGeometryFactory();
				
		DefaultFeatureCollection dfc = new DefaultFeatureCollection();
		
		//feature type for the point cloud
		String outTable = "improvement_coverage";
		SimpleFeatureType improvementCoverageFeatureType = null;
		try {
			improvementCoverageFeatureType = DataUtilities.createType(outTable, "geometry:Point:srid="+pointCloudSrid+",num_tests:int,num_valid_tests:int,tested_sections:string");
		} catch (SchemaException e1) {
			System.out.println("Unable to create feature type "+outTable);
			System.exit(1);
		}
		SimpleFeatureBuilder featureBuilder = new SimpleFeatureBuilder(improvementCoverageFeatureType);
		
		//convert "coordinateMap" into a FeatureCollection of coverage information
		Set<Coordinate> keys = coordinateMap.keySet();
		Iterator<Coordinate> keyIt = keys.iterator();
		int nextFid = 0;
		while(keyIt.hasNext()) {
			Coordinate c = keyIt.next();
			ImprovementCoverageItem item = coordinateMap.get(c);
			Point point = geometryFactory.createPoint(c);
			Object[] attrValues = {
					point,
					item.getCountTotal(),
					item.getCountValid(),
					item.getFidsAsString()
					};
			SimpleFeature feature = featureBuilder.buildFeature(""+nextFid++, attrValues);
			dfc.add(feature);
		}
		
		//add a spatial index
		SpatialIndexFeatureCollection fastFc = new SpatialIndexFeatureCollection(dfc);
		SpatialIndexFeatureSource fastFs = new SpatialIndexFeatureSource(fastFc);
		
		return fastFs;
	}
	
	private void initializeFromPointCloud(SimpleFeatureSource pointCloud) throws IOException {
		SimpleFeatureType pointCloudFeatureType = pointCloud.getSchema();
		
		//lookup the SRID of the point cloud
		CoordinateReferenceSystem crs = pointCloudFeatureType.getCoordinateReferenceSystem();
		pointCloudSrid = -1;
		try {
			pointCloudSrid = CRS.lookupEpsgCode(crs, true);
		} catch (FactoryException e1) {
			System.out.println("Unable to lookup SRID of point cloud");
			System.exit(1);
		}
		
		SimpleFeatureCollection points = pointCloud.getFeatures();
		SimpleFeatureIterator pointIt = points.features();
		while(pointIt.hasNext()) {
			SimpleFeature pointFeature = pointIt.next();
			Point p = (Point)pointFeature.getDefaultGeometry();
			Coordinate c = p.getCoordinate();
			ImprovementCoverageItem item = coordinateMap.get(c);
			if (item == null) {
				coordinateMap.put(c, new ImprovementCoverageItem());
			}
		}
	}
	
	/**
	 * returns a number in [0,1] representing the fraction of the point cloud which 
	 * has been covered.
	 * @return
	 */
	public double getTotalCoverageFraction() {
		int countTouched = 0;
		int countUntouched = 0;
		Set<Coordinate> keys = coordinateMap.keySet();
		Iterator<Coordinate> keyIt = keys.iterator();
		while(keyIt.hasNext()) {
			Coordinate key = keyIt.next();
			ImprovementCoverageItem item = coordinateMap.get(key);
			if (item.getCountTotal() == 0) {
				countUntouched++;
			}
			else {
				countTouched++;
			}
		}
		int countTotal = countTouched + countUntouched;
		double coverageFraction = ((double)countTouched)/((double)countTotal);
		return coverageFraction;
	}
	
	/**
	 * returns a number in [0,1] representing the fraction of the point cloud which 
	 * has been covered.
	 * @return
	 */
	public double getValidCoverageFraction() {
		int countTouched = 0;
		int countUntouched = 0;
		Set<Coordinate> keys = coordinateMap.keySet();
		Iterator<Coordinate> keyIt = keys.iterator();
		while(keyIt.hasNext()) {
			Coordinate key = keyIt.next();
			ImprovementCoverageItem item = coordinateMap.get(key);
			if (item.getCountValid() == 0) {
				countUntouched++;
			}
			else {
				countTouched++;
			}
		}
		int countTotal = countTouched + countUntouched;
		double coverageFraction = ((double)countTouched)/((double)countTotal);
		return coverageFraction;
	}
}
