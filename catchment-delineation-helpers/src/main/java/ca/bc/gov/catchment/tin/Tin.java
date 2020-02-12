package ca.bc.gov.catchment.tin;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.geotools.data.DataUtilities;
import org.geotools.data.collection.SpatialIndexFeatureCollection;
import org.geotools.data.collection.SpatialIndexFeatureSource;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.factory.CommonFactoryFinder;
import org.geotools.feature.DefaultFeatureCollection;
import org.geotools.feature.SchemaException;
import org.geotools.geometry.jts.JTSFactoryFinder;
import org.geotools.referencing.CRS;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.filter.Filter;
import org.opengis.filter.FilterFactory2;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

import ca.bc.gov.catchment.utils.SpatialUtils;

public abstract class Tin {

	protected SimpleFeatureSource featureSource;
	protected SimpleFeatureType featureType;
	protected String geometryPropertyName;
	protected FilterFactory2 filterFactory;
	protected GeometryFactory geometryFactory;
	private SimpleFeatureSource pointCloud;
	private Filter defaultFilter;

	public Tin(SimpleFeatureSource featureSource) {
		this(featureSource, null);
	}
	
	public Tin(SimpleFeatureSource featureSource, Filter defaultFilter) {
		this.featureSource = featureSource;
		this.featureType = featureSource.getSchema();
		this.geometryPropertyName = featureType.getGeometryDescriptor().getLocalName();
		this.filterFactory = CommonFactoryFinder.getFilterFactory2();
		this.geometryFactory = JTSFactoryFinder.getGeometryFactory();
		this.pointCloud = null;
		this.defaultFilter = defaultFilter;
	}
	
	public SimpleFeatureSource getFeatureSource() {
		return this.featureSource;
	}
	
	public SimpleFeatureType getSchema() {
		return featureType;
	}
	
	public Filter getDefaultFilter() {
		return this.defaultFilter;
	}
	
	public SimpleFeatureCollection getFeatures() throws IOException {
		if (defaultFilter != null) {
			return featureSource.getFeatures(defaultFilter);
		}
		return featureSource.getFeatures();
	}
	
	public SimpleFeatureCollection getFeatures(Filter f) throws IOException {
		if (defaultFilter != null) {
			Filter allFilters = filterFactory.and(defaultFilter, f);
			return featureSource.getFeatures(allFilters);
		}
		return featureSource.getFeatures(f);
	} 
	
	public Coordinate getRandomCoordInRadius(Coordinate c, double radius, List<Coordinate> exclude) throws IOException {
		int MAX_TRIES = 100;
		List<Coordinate> coords = getCoordsInRadius(c, radius);
		for(int i = 0; i < MAX_TRIES; i++) {
			int pickedIndex = (int)(Math.random() * coords.size());
			Coordinate picked = coords.get(pickedIndex);
			if (exclude == null || !exclude.contains(picked)) {
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
		SimpleFeatureCollection matches = getFeatures(radiusFilter);		
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

	public abstract SimpleFeature getEdge(Coordinate c1, Coordinate c2) throws IOException;
	
	public double getMaxElevation() throws IOException {
		double maxZ = 0;
		SimpleFeatureCollection all = getFeatures();
		SimpleFeatureIterator allIt = all.features();
		try {
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
		}
		finally {
			allIt.close();
		}
		return maxZ;
	}

	public double getMaxEdgeLength() throws IOException {
		double maxLen = 0;
		SimpleFeatureCollection all = getFeatures();
		SimpleFeatureIterator allIt = all.features();
		try {
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
		}
		finally {
			allIt.close();
		}
		return maxLen;
	}
	
	public SimpleFeatureSource getPointCloud() throws IOException {
		if (this.pointCloud == null) {
					
			DefaultFeatureCollection dfc = new DefaultFeatureCollection();
			
			//lookup the SRID of the source data
			SimpleFeatureType tinFeatureType = getSchema(); 
			CoordinateReferenceSystem crs = tinFeatureType.getCoordinateReferenceSystem();
			int srid = -1;
			try {
				srid = CRS.lookupEpsgCode(crs, true);
			} catch (FactoryException e1) {
				System.out.println("Unable to lookup SRID of TIN");
				System.exit(1);
			}
			
			//feature type for the point cloud
			String outTable = "point_cloud";
			SimpleFeatureType pointCloudFeatureType = null;
			try {
				pointCloudFeatureType = DataUtilities.createType(outTable, "geometry:Point:srid="+srid);
			} catch (SchemaException e1) {
				System.out.println("Unable to create feature type "+outTable);
				System.exit(1);
			}
			
			//iterate over each feature in the Tin, and add all its coordinates to
			//a point cloud
			SimpleFeatureCollection tinFeatures = getFeatures();
			SimpleFeatureIterator it = tinFeatures.features();
			int outFid = 1;
			while(it.hasNext()) {
				SimpleFeature tinFeature = it.next();
				Geometry tinGeometry = (Geometry)tinFeature.getDefaultGeometry();
				for (Coordinate coordinate : tinGeometry.getCoordinates()) {
					Point point = geometryFactory.createPoint(coordinate);
					SimpleFeature pointFeature = SpatialUtils.geomToFeature(point, pointCloudFeatureType, ""+outFid++);
					dfc.add(pointFeature);
				}
			}
			it.close();
			
			//add a spatial index to the point cloud
			SpatialIndexFeatureCollection fastFeatureCollection = new SpatialIndexFeatureCollection(dfc);
			SpatialIndexFeatureSource fastFeatureSource = new SpatialIndexFeatureSource(fastFeatureCollection);
			this.pointCloud = fastFeatureSource;
		}
			
		return this.pointCloud;
		
	}
}
