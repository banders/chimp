package ca.bc.gov.catchment;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.factory.CommonFactoryFinder;
import org.geotools.feature.DefaultFeatureCollection;
import org.geotools.geometry.jts.JTSFactoryFinder;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.filter.Filter;
import org.opengis.filter.FilterFactory2;

import ca.bc.gov.catchment.water.WaterAnalyzer;

public class CatchmentLines {

	private FilterFactory2 filterFactory;
	private GeometryFactory geometryFactory;
	private SimpleFeatureType featureType;
	private String geometryPropertyName;
	private SimpleFeatureSource originalCatchmentLines;
	private DefaultFeatureCollection updatedCatchmentLines;
	
	public CatchmentLines(SimpleFeatureSource catchmentLines) throws IOException {
		this.filterFactory = CommonFactoryFinder.getFilterFactory2();
		this.geometryFactory = JTSFactoryFinder.getGeometryFactory();
		this.featureType = catchmentLines.getSchema();
		this.geometryPropertyName = featureType.getGeometryDescriptor().getLocalName();
		
		this.originalCatchmentLines = catchmentLines;
		this.updatedCatchmentLines = new DefaultFeatureCollection();
		this.updatedCatchmentLines.addAll(catchmentLines.getFeatures());
	}
	
	public List<SimpleFeature> getSectionsTouchingJunction(Coordinate junction) throws IOException {
		Point p = geometryFactory.createPoint(junction);
		List<SimpleFeature> touchingSections = new ArrayList<SimpleFeature>();
		
		//find routes from the original catchment set
		Filter touchesFilter = filterFactory.touches(
				filterFactory.property(geometryPropertyName), 
				filterFactory.literal(p)
				);
		
		SimpleFeatureCollection originalTouchingCatchmentEdges = originalCatchmentLines.getFeatures(touchesFilter);
		SimpleFeatureIterator it = originalTouchingCatchmentEdges.features();
		try {
			while(it.hasNext()) {
				SimpleFeature f = it.next();
				f = getLatest(f);
				touchingSections.add(f);
			}
		} finally {
			it.close();	
		}
		
		return touchingSections;
	}
	
	public SimpleFeatureType getSchema() {
		return this.featureType;
	}
	
	public SimpleFeatureCollection getOriginalFeatures(Filter f) throws IOException {
		return originalCatchmentLines.getFeatures(f);
	} 
	
	public SimpleFeatureCollection getOriginalFeatures() throws IOException {
		return this.originalCatchmentLines.getFeatures();
	}
	
	public SimpleFeatureCollection getUpdatedFeatures(Filter f) throws IOException {
		return updatedCatchmentLines.subCollection(f);
	} 
	
	public SimpleFeatureCollection getUpdatedFeatures() throws IOException {
		return this.updatedCatchmentLines;
	}
	
	public List<Coordinate> getJunctions(WaterAnalyzer waterAnalyzer) throws IOException {
		List<Coordinate> junctions = new ArrayList<Coordinate>();
		SimpleFeatureCollection fc = originalCatchmentLines.getFeatures();
		SimpleFeatureIterator it = fc.features();
		try {
			while(it.hasNext()) {
				SimpleFeature f = it.next();
				f = getLatest(f);
				LineString route = (LineString)f.getDefaultGeometry();
				Coordinate end1 = route.getCoordinateN(0);
				Coordinate end2 = route.getCoordinateN(route.getNumPoints()-1);
				if (!waterAnalyzer.isConfluence(end1) && !junctions.contains(end1)) {
					junctions.add(end1);
				}
				if (!waterAnalyzer.isConfluence(end2) && !junctions.contains(end2)) {
					junctions.add(end2);
				}
			}
		} finally {
			it.close();	
		}
		return junctions;
	}
	
	/**
	 * Checks if a SimpleFeature with the same ID as "section" exists in "sectionSet".  If so, returns
	 * the version of the feature from the sectionSet.  If not, returns the section given as a parameter
	 * @param sectionSet
	 * @param section
	 * @return
	 */
	public SimpleFeature getLatest(SimpleFeature section) {
		Filter fidFilter = filterFactory.id(section.getIdentifier());
		SimpleFeatureCollection matches = updatedCatchmentLines.subCollection(fidFilter);
		SimpleFeatureIterator matchesIt = matches.features();
		try {
			if (matchesIt.hasNext()) {
				SimpleFeature match = matchesIt.next();
				return match;
			}
		} 
		finally {
			matchesIt.close();	
		}
		return section;
	}
	
	/**
	 * Updates the given feature collection.  If the given feature doesn't exist in the
	 * feature collection (as determined by a FID comparison), it is added.  If the 
	 * given feature already exists in the 
	 * feature collection, it is removed and the updated version is added
	 * @param fc
	 * @param f
	 */
	public void addOrUpdate(SimpleFeature f) {
		Filter fidFilter = filterFactory.id(f.getIdentifier());
		SimpleFeatureCollection matches = updatedCatchmentLines.subCollection(fidFilter);
		SimpleFeatureIterator matchesIt = matches.features();

		//if a feature with the same FID as the given feature already exists, 
		//remove the existing feature (it will be replaced below)
		SimpleFeature toRemove = null;
		try {
			while(matchesIt.hasNext()) {
				SimpleFeature match = matchesIt.next();
				toRemove = match;
			}
		}
		finally {
			matchesIt.close();
		}
		if (toRemove != null) {
			updatedCatchmentLines.remove(toRemove);
		}
		
		//add the new feature
		updatedCatchmentLines.add(f);
	}
}
