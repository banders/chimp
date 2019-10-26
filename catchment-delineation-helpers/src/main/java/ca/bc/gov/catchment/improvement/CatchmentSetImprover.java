package ca.bc.gov.catchment.improvement;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.factory.CommonFactoryFinder;
import org.geotools.feature.DefaultFeatureCollection;
import org.geotools.feature.simple.SimpleFeatureBuilder;
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

import ca.bc.gov.catchment.algorithms.NearestNeighbour3DMaker;
import ca.bc.gov.catchment.routes.RouteException;
import ca.bc.gov.catchment.water.WaterAnalyzer;

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
	private WaterAnalyzer waterAnalyzer;
	private DefaultFeatureCollection proposedSections;
	
	public CatchmentSetImprover(SimpleFeatureSource waterFeatures) {
		this.waterFeatures = waterFeatures;
		this.waterFeatureType = waterFeatures.getSchema();
		this.waterGeometryPropertyName = waterFeatureType.getGeometryDescriptor().getLocalName();
		this.filterFactory = CommonFactoryFinder.getFilterFactory2();
		this.geometryFactory = JTSFactoryFinder.getGeometryFactory();
		this.waterAnalyzer = new WaterAnalyzer(waterFeatures);
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
		proposedSections = new DefaultFeatureCollection();
		proposedSections.addAll(sectionSet.getFeatures());
		
		SimpleFeatureCollection inputSet = sectionSet.getFeatures();
		
		//first, process all sections that touch confluence points
		SimpleFeatureIterator inputSetIt = inputSet.features();
		while(inputSetIt.hasNext()) {
			SimpleFeature sectionToImprove = inputSetIt.next();
			LineString route = (LineString)sectionToImprove.getDefaultGeometry();
			boolean touchesConfluence = getNumEndsTouchingConfluence(route) > 0;
			if (!touchesConfluence) {
				continue; 
			}
			sectionToImprove = getLatest(sectionToImprove);
			SectionModification modification = improve(sectionToImprove);
			
			addOrUpdate(modification.getModifiedSection());
			for (SimpleFeature touchingSection : modification.getModifiedTouchingSections()) {
				System.out.println(" updated section with fid="+touchingSection.getIdentifier());
				addOrUpdate(touchingSection);
			}
			System.out.println("---");
		}
		inputSetIt.close();
		
		//process all junction points
		inputSetIt = inputSet.features();
		while(inputSetIt.hasNext()) {
		
			SimpleFeature section = inputSetIt.next();
			section = getLatest(section);
			LineString route = (LineString)section.getDefaultGeometry();
			Coordinate[] coords = route.getCoordinates();
			
			List<Coordinate> coordsToConsider = new ArrayList<Coordinate>();
			Coordinate firstCoord = coords[0];
			Coordinate lastCoord = coords[coords.length-1];
			if (waterAnalyzer.isConfluence(firstCoord)) {
				coordsToConsider.add(firstCoord);
			}
			if (waterAnalyzer.isConfluence(lastCoord)) {
				coordsToConsider.add(lastCoord);
			}
			
			for(Coordinate junction : coordsToConsider) {	
				JunctionModification junctionModification = null;
				try {
					junctionModification = improveJunction(junction);
				}
				catch(Exception e) {
					e.printStackTrace();
					continue;
				}
				if (junctionModification.getModifiedSections().size() > 0) {
					System.out.println(" moved junction "+junctionModification.getOriginalJunction()+" to "+junctionModification.getModifiedJunction());
				}
				
				for (SimpleFeature touchingSection : junctionModification.getModifiedSections()) {
					System.out.println("  updated section with fid="+touchingSection.getIdentifier());
					addOrUpdate(touchingSection);
				}
				System.out.println("---");
			}		
		
		}
		inputSetIt.close();
		
		//third, process all sections that don't touch confluence points
		inputSetIt = inputSet.features();
		while(inputSetIt.hasNext()) {
			SimpleFeature sectionToImprove = inputSetIt.next();
			LineString route = (LineString)sectionToImprove.getDefaultGeometry();
			boolean touchesConfluence = getNumEndsTouchingConfluence(route) > 0;
			if (touchesConfluence) {
				continue;
			}
			sectionToImprove = getLatest(sectionToImprove);
			SectionModification modification = improve(sectionToImprove);
			
			addOrUpdate(modification.getModifiedSection());
			for (SimpleFeature touchingSection : modification.getModifiedTouchingSections()) {
				addOrUpdate(touchingSection);
			}
			System.out.println("---");
		}
		inputSetIt.close();
		
		return proposedSections;
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
	 * proposed a new position for the junction (and new routes for the touching lines) that
	 * give a better fit
	 * @param originalJunction
	 * @return
	 * @throws IOException
	 */
	public abstract JunctionModification improveJunction(Coordinate originalJunction) throws IOException;
	
	/**
	 * Counts the number of endpoints of the given route that touch a confluence point
	 * @throws IOException 
	 */
	protected int getNumEndsTouchingConfluence(LineString route) throws IOException {
		Coordinate[] coords = route.getCoordinates();
		Coordinate firstCoord = coords[0];
		Coordinate lastCoord = coords[coords.length-1];
		int numTouches = waterAnalyzer.isConfluence(firstCoord) ? 1 : 0;
		numTouches += waterAnalyzer.isConfluence (lastCoord) ? 1 : 0;
		return numTouches;
	}
		
	/**
	 * Checks if a SimpleFeature with the same ID as "section" exists in "sectionSet".  If so, returns
	 * the version of the feature from the sectionSet.  If not, returns the section given as a parameter
	 * @param sectionSet
	 * @param section
	 * @return
	 */
	protected SimpleFeature getLatest(SimpleFeature section) {
		Filter fidFilter = filterFactory.id(section.getIdentifier());
		SimpleFeatureCollection matches = proposedSections.subCollection(fidFilter);
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
	private void addOrUpdate(SimpleFeature f) {
		Filter fidFilter = filterFactory.id(f.getIdentifier());
		SimpleFeatureCollection matches = proposedSections.subCollection(fidFilter);
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
			proposedSections.remove(toRemove);
		}
		
		//add the new feature
		proposedSections.add(f);
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
	
	public boolean isAdjacentToConfluence(Coordinate currentCoord, LineString route) throws IOException {
		Coordinate[] coordsInRoute = route.getCoordinates();
		if (coordsInRoute.length < 2) {
			return false;
		}
		
		Coordinate start = coordsInRoute[0];
		Coordinate secondFromStart = coordsInRoute[1];
		
		if (waterAnalyzer.isConfluence(start) && secondFromStart.equals(currentCoord)) {
			return true;
		}
		
		Coordinate end = coordsInRoute[coordsInRoute.length-1];
		Coordinate secondFromEnd = coordsInRoute[coordsInRoute.length-2];
		
		if (waterAnalyzer.isConfluence(end) && secondFromEnd.equals(currentCoord)) {
			return true;
		}
		
		return false;
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
	
	public WaterAnalyzer getWaterAnalyzer() {
		return this.waterAnalyzer;
	}
	
	public SimpleFeatureCollection getProposedSections() {
		return this.proposedSections;
	}
}
