package ca.bc.gov.catchment.voronoi;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;

import javax.measure.Unit;

import org.geotools.data.DataUtilities;
import org.geotools.data.FeatureSource;
import org.geotools.data.collection.SpatialIndexFeatureCollection;
import org.geotools.data.collection.SpatialIndexFeatureSource;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.factory.CommonFactoryFinder;
import org.geotools.feature.DefaultFeatureCollection;
import org.geotools.feature.FeatureCollection;
import org.geotools.feature.FeatureIterator;
import org.geotools.feature.SchemaException;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.geometry.jts.JTSFactoryFinder;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.referencing.CRS;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
import org.opengis.feature.Feature;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.filter.Filter;
import org.opengis.filter.FilterFactory;
import org.opengis.filter.FilterFactory2;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

import ca.bc.gov.catchment.algorithms.MergeLinesAlg;

public class VoronoiFalseCapCleaner {
	
	private static final int NUM_X_TILES = 10;
	private static final int NUM_Y_TILES = 10;
	
	private String voronoiEdgesTypeName;
	private String keptTypeName;
	private String discardedTypeName;
	
	private SimpleFeatureSource voronoiEdgesFeatureSource;
	private SimpleFeatureType voronoiEdgesFeatureType;
	private SimpleFeatureSource waterFeatureSource;
	
	private SimpleFeatureType keptFeatureType;
	private SimpleFeatureType discardedFeatureType;
	private SimpleFeatureBuilder keptFeatureBuilder;
	private SimpleFeatureBuilder discardedFeatureBuilder;
	
	private CoordinateReferenceSystem voronoiEdgesCrs;
	private Unit<?> distanceUnit;
	
	private double touchesDistanceTolerance;
	
	int srid;
	
	public VoronoiFalseCapCleaner(SimpleFeatureSource voronoiEdgesFeatureSource,
			SimpleFeatureSource waterFeatureSource,
			String keptTypeName,
			String discardedTypeName,
			double touchesDistanceTolerance) throws IOException, FactoryException {
		
		this.touchesDistanceTolerance = touchesDistanceTolerance; 
	
		this.voronoiEdgesFeatureSource = voronoiEdgesFeatureSource;
		voronoiEdgesFeatureType = voronoiEdgesFeatureSource.getSchema();
		this.waterFeatureSource = waterFeatureSource;
		
		this.voronoiEdgesTypeName = voronoiEdgesFeatureType.getTypeName();
		this.keptTypeName = keptTypeName;
		this.discardedTypeName = discardedTypeName;
		
		voronoiEdgesCrs = voronoiEdgesFeatureType.getGeometryDescriptor().getCoordinateReferenceSystem();
		this.distanceUnit = voronoiEdgesCrs.getCoordinateSystem().getAxis(0).getUnit();

		srid = CRS.lookupEpsgCode(voronoiEdgesCrs, true);
		
		keptFeatureType = null;
		try {
			keptFeatureType = DataUtilities.createType(keptTypeName, "geometry:LineString:srid="+srid+",num_end_points_touching:int");
		} catch (SchemaException e1) {
			System.out.println("Unable to create feature type "+keptTypeName);
			System.exit(1);
		}
		
		discardedFeatureType = null;
		try {
			discardedFeatureType = DataUtilities.createType(discardedTypeName, "geometry:LineString:srid="+srid+",num_end_points_touching:int");
		} catch (SchemaException e1) {
			System.out.println("Unable to create feature type "+discardedTypeName);
			System.exit(1);
		}		
		
		keptFeatureBuilder = new SimpleFeatureBuilder(keptFeatureType);
		discardedFeatureBuilder = new SimpleFeatureBuilder(discardedFeatureType);
		
		System.out.println("   - Distance tolerance for 'touching' lines is: "+touchesDistanceTolerance + " " +distanceUnit.toString());

	}
	
	public KeptAndDiscarded clean() throws IOException {
		KeptAndDiscarded result = new KeptAndDiscarded(keptFeatureType, discardedFeatureType);
		
		SimpleFeatureCollection waterFeatures = this.waterFeatureSource.getFeatures();
		SpatialIndexFeatureSource fastWaterFeatureSource = new SpatialIndexFeatureSource(new SpatialIndexFeatureCollection(waterFeatures));
		SimpleFeatureCollection voronoiFeatures = this.voronoiEdgesFeatureSource.getFeatures();
		SpatialIndexFeatureSource fastVoronoiFeatureSource = new SpatialIndexFeatureSource(new SpatialIndexFeatureCollection(voronoiFeatures));
		
		//initially add all voronoi edges to "kept"
		result.addKept(toKept(voronoiFeatures));
		
		//identify any voronoi edges that should be discarded
		SimpleFeatureIterator waterIt = waterFeatures.features();
		int numProcessed = 0;
		while(waterIt.hasNext()) {
			SimpleFeature waterFeature = waterIt.next();
			Collection<Point> looseEnds = getLooseEnds(waterFeature, fastWaterFeatureSource);
			
			for(Point looseEnd: looseEnds) {
				SimpleFeatureCollection touchingVoronoiFeatures = getFuzzyTouchingFeatures(looseEnd, touchesDistanceTolerance, fastVoronoiFeatureSource);
				if (touchingVoronoiFeatures.size() > 0) {
					result.addDiscarded(toDiscarded(touchingVoronoiFeatures));
				}
			}
			numProcessed++;
			
			if (numProcessed % 50000 == 0) {
				System.out.println("Processed "+numProcessed+" water features.  Found "+ result.getNumDiscarded()+" voronoi edges to discard");
			}
		}
		waterIt.close();
		
		//ensure any discarded features don't exist in the kept set.
		result.clean();
		
		System.out.println("Finished. "+result.getNumKept() + " voronoi edges to keep. "+ result.getNumDiscarded()+" voronoi edges to discard");
		
		return result;
	}
	
	/**
	 * Identifies the "loose ends" of a water feature.  i.e. those ends which don't touch another
	 * water feature.  Returns a collection of them.
	 * @param waterFeature
	 * @param waterFeatureSource
	 * @return
	 * @throws IOException
	 */
	private Collection<Point> getLooseEnds(SimpleFeature waterFeature, SimpleFeatureSource waterFeatureSource) throws IOException {
		Collection<Point> result = new ArrayList<Point>();
		FilterFactory2 ff = CommonFactoryFinder.getFilterFactory2();
		GeometryFactory geometryFactory = JTSFactoryFinder.getGeometryFactory();
		
		String waterGeomProperty = waterFeatureSource.getSchema().getGeometryDescriptor().getLocalName();
		Geometry geometry = (Geometry)waterFeature.getDefaultGeometry();
		Coordinate[] coords = geometry.getCoordinates();
		Point firstPoint = geometryFactory.createPoint(coords[0]);
		Point lastPoint = geometryFactory.createPoint(coords[coords.length-1]);
		
		//check first point
		Filter firstCoordTouchesWaterFilter = ff.dwithin(
				ff.property(waterGeomProperty),
				ff.literal(firstPoint),
				0,
				"meter"
				);

		SimpleFeatureCollection touchingFirst = waterFeatureSource.getFeatures(firstCoordTouchesWaterFilter);
		if (touchingFirst.size() == 1) {
			result.add(firstPoint);
		}
		
		//check last point
		Filter lastCoordTouchesWaterFilter = ff.dwithin(
				ff.property(waterGeomProperty),
				ff.literal(lastPoint),
				0,
				"meter"
				);
		SimpleFeatureCollection touchingLast = waterFeatureSource.getFeatures(lastCoordTouchesWaterFilter);
		if (touchingLast.size() == 1) {
			result.add(lastPoint);
		}
		
		return result;
		


	}
	
	private SimpleFeatureCollection getFuzzyTouchingFeatures(Point pointToCheck, double pointRadius, SimpleFeatureSource featureSource) throws IOException {
		FilterFactory2 ff = CommonFactoryFinder.getFilterFactory2();
		String geomProperty = featureSource.getSchema().getGeometryDescriptor().getLocalName();
		
		Filter fuzzyTouchesFilter = ff.dwithin(
				ff.property(geomProperty),
				ff.literal(pointToCheck),				
				pointRadius,
				distanceUnit.toString()
				);
		SimpleFeatureCollection results = featureSource.getFeatures(fuzzyTouchesFilter);
		
		return results;
	}
	
	private SimpleFeatureCollection toKept(SimpleFeature f) {
		DefaultFeatureCollection fc = new DefaultFeatureCollection();
		fc.add(f);
		return toKept(fc);
	}
	
	private SimpleFeatureCollection toKept(SimpleFeatureCollection fc) {
		DefaultFeatureCollection result = new DefaultFeatureCollection();
		SimpleFeatureIterator it = fc.features();
		while (it.hasNext()) {
			SimpleFeature origFeature = it.next();
			Object[] attributeValues = new Object[] { origFeature.getDefaultGeometry() };
			SimpleFeature keptFeature = keptFeatureBuilder.buildFeature(origFeature.getID(), attributeValues);
			result.add(keptFeature);
		}
		it.close();
		return result;
	}
	
	private SimpleFeatureCollection toDiscarded(SimpleFeature f) {
		DefaultFeatureCollection fc = new DefaultFeatureCollection();
		fc.add(f);
		return toDiscarded(fc);
	}
	
	private SimpleFeatureCollection toDiscarded(SimpleFeatureCollection fc) {
		DefaultFeatureCollection result = new DefaultFeatureCollection();
		SimpleFeatureIterator it = fc.features();
		while (it.hasNext()) {
			SimpleFeature origFeature = it.next();
			Object[] attributeValues = new Object[] { origFeature.getDefaultGeometry() };
			SimpleFeature discardedFeature = discardedFeatureBuilder.buildFeature(origFeature.getID(), attributeValues);
			result.add(discardedFeature);
		}
		it.close();
		return result;
	}
	
}
