package ca.bc.gov.catchment.improvement;

import java.io.IOException;

import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.factory.CommonFactoryFinder;
import org.geotools.feature.DefaultFeatureCollection;
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

/**
 * Defines the interface for an algorithm that improves a catchment "section".
 * Definition: A catchment "section" is a SimpleFeature that represents an n-vertex LineString which is part
 * of a catchment boundary
 * 
 * @author Brock
 *
 */
public abstract class CatchmentSetImprover {

	private FilterFactory2 filterFactory;
	private GeometryFactory geometryFactory;
	private SimpleFeatureSource waterFeatures;
	private SimpleFeatureType waterFeatureType;
	private String waterGeometryPropertyName;
	
	public CatchmentSetImprover(SimpleFeatureSource waterFeatures) {
		this.waterFeatures = waterFeatures;
		this.waterFeatureType = waterFeatures.getSchema();
		this.waterGeometryPropertyName = waterFeatureType.getGeometryDescriptor().getLocalName();
		this.filterFactory = CommonFactoryFinder.getFilterFactory2();
		this.geometryFactory = JTSFactoryFinder.getGeometryFactory();
	}
	
	/**
	 * Iterates over all sections in the given set, and attempts to improve each.  What it means to be
	 * an "improvement" is defined by subclasses.  
	 * Returns a set of results representing the improved sections.
	 * @param sections
	 * @return
	 * @throws IOException 
	 */
	public SimpleFeatureCollection improve(SimpleFeatureSource sectionSet) throws IOException {
		DefaultFeatureCollection resultSet = new DefaultFeatureCollection();
		
		SimpleFeatureCollection inputSet = sectionSet.getFeatures();
		
		//first, process all sections that touch confluence points
		SimpleFeatureIterator inputSetIt = inputSet.features();
		while(inputSetIt.hasNext()) {
			SimpleFeature sectionToImprove = inputSetIt.next();
			LineString route = (LineString)sectionToImprove.getDefaultGeometry();
			boolean isConfluence = getNumEndsTouchingConfluence(route) > 0;
			if (!isConfluence) {
				continue; 
			}
			sectionToImprove = getLatest(resultSet, sectionToImprove);
			SectionModification modification = improve(sectionToImprove);
			
			addOrUpdate(resultSet, modification.getModifiedSection());
			for (SimpleFeature touchingSection : modification.getModifiedTouchingSections()) {
				addOrUpdate(resultSet, touchingSection);
			}
		}
		inputSetIt.close();
		
		//second, process all sections that don't touch confluence points
		inputSetIt = inputSet.features();
		while(inputSetIt.hasNext()) {
			SimpleFeature sectionToImprove = inputSetIt.next();
			LineString route = (LineString)sectionToImprove.getDefaultGeometry();
			boolean isConfluence = getNumEndsTouchingConfluence(route) > 0;
			if (isConfluence) {
				continue;
			}
			sectionToImprove = getLatest(resultSet, sectionToImprove);
			SectionModification modification = improve(sectionToImprove);
			
			addOrUpdate(resultSet, modification.getModifiedSection());
			for (SimpleFeature touchingSection : modification.getModifiedTouchingSections()) {
				addOrUpdate(resultSet, touchingSection);
			}
		}
		inputSetIt.close();
		
		return resultSet;
	}
	
	/**
	 * proposes a new section which is an improvement over the given section.  What it means to be an "improvement"
	 * is defined by subclasses.  If a section endpoint is moved, other sections touching that endpoint must also 
	 * be updated to match.  This is the responsibility of the subclass.
	 * @param section
	 * @return
	 */
	public abstract SectionModification improve(SimpleFeature section) throws IOException;
	
	/**
	 * Checks whether the given feature touches a confluence point
	 * @throws IOException 
	 */
	protected int getNumEndsTouchingConfluence(LineString route) throws IOException {
		Coordinate[] coords = route.getCoordinates();
		Coordinate firstCoord = coords[0];
		Coordinate lastCoord = coords[coords.length-1];
		int numTouches = endpointTouchesConfluence(firstCoord) ? 1 : 0;
		numTouches += endpointTouchesConfluence (lastCoord) ? 1 : 0;
		return numTouches;
	}
	
	/**
	 * Checks whether the given coordinate touches water only
	 * @param c
	 * @return
	 * @throws IOException
	 */
	protected boolean endpointTouchesConfluence(Coordinate c) throws IOException {
		Point p = geometryFactory.createPoint(c);
		
		//first find water features that touch the given coordinate (at any point)
		Filter touchesFilter = filterFactory.touches(
				filterFactory.property(waterGeometryPropertyName), 
				filterFactory.literal(p));
		SimpleFeatureCollection matches = waterFeatures.getFeatures(touchesFilter);
		
		//identify which of the touching water features touch at endpoints only (not interior points)
		int numWaterWithEndpointTouching = 0;		
		SimpleFeatureIterator matchesIt = matches.features();
		while(matchesIt.hasNext()) {
			SimpleFeature waterFeature = matchesIt.next();
			LineString waterGeom = (LineString)waterFeature.getDefaultGeometry();
			if(hasEndpoint(waterGeom, c)) {
				numWaterWithEndpointTouching++;
			}
		}
		matchesIt.close();
		
		boolean result = numWaterWithEndpointTouching > 0;
		return result;
	}
	

	
	
	/**
	 * Checks if a SimpleFeature with the same ID as "section" exists in "sectionSet".  If so, returns
	 * the version of the feature from the sectionSet.  If not, returns the section given as a parameter
	 * @param sectionSet
	 * @param section
	 * @return
	 */
	private SimpleFeature getLatest(SimpleFeatureCollection sectionSet, SimpleFeature section) {
		Filter fidFilter = filterFactory.id(section.getIdentifier());
		SimpleFeatureCollection matches = sectionSet.subCollection(fidFilter);
		SimpleFeatureIterator matchesIt = matches.features();
		if (matchesIt.hasNext()) {
			SimpleFeature match = matchesIt.next();
			return match;
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
	private void addOrUpdate(DefaultFeatureCollection fc, SimpleFeature f) {
		Filter fidFilter = filterFactory.id(f.getIdentifier());
		SimpleFeatureCollection matches = fc.subCollection(fidFilter);
		SimpleFeatureIterator matchesIt = matches.features();
		
		//if a feature with the same FID as the given feature already exists, 
		//remove the existing feature (it will be replaced below)
		while(matchesIt.hasNext()) {
			SimpleFeature match = matchesIt.next();
			fc.remove(match);
		}
		
		//add the new feature
		fc.add(f);
	}
	
	/**
	 * checks if the endpoints of a given linestring equals the given coordinate
	 */
	public boolean hasEndpoint(LineString route, Coordinate c) {
		Coordinate[] coords = route.getCoordinates();
		Coordinate firstCoord = coords[0];
		Coordinate lastCoord = coords[coords.length-1];
		boolean result = firstCoord.equals(c) || lastCoord.equals(c);
		return result;
	}
	
	/**
	 * checks if the interior points of a given linestring equals the given coordinate.
	 * (interior points are any points other than the endpoints)
	 */
	public boolean hasInteriorPoint(LineString route, Coordinate c) {
		Coordinate[] coords = route.getCoordinates();
		for(int i = 1; i < coords.length-1; i++) {
			Coordinate coord = coords[i];
			if (coord.equals(c)) {
				return true;
			}
		}
		return false;
	}
}
