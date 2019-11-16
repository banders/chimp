package ca.bc.gov.catchment.fitness;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.factory.CommonFactoryFinder;
import org.geotools.factory.Hints;
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
import org.opengis.filter.identity.FeatureId;

import ca.bc.gov.catchment.water.WaterAnalyzer;

public class CatchmentValidity {

	private FilterFactory2 filterFactory2D;
	private GeometryFactory geometryFactory;
	private SimpleFeatureSource waterFeatures;
	private SimpleFeatureType waterFeatureType;
	private String waterGeometryPropertyName;
	private WaterAnalyzer waterAnalyzer;
	
	public CatchmentValidity(SimpleFeatureSource waterFeatures) {
		Hints filterHints = new Hints( Hints.FEATURE_2D, true ); // force 2D queries
		this.filterFactory2D = CommonFactoryFinder.getFilterFactory2(filterHints);
		this.geometryFactory = JTSFactoryFinder.getGeometryFactory();
		this.waterFeatures = waterFeatures;
		this.waterFeatureType = waterFeatures.getSchema();
		this.waterGeometryPropertyName = waterFeatureType.getGeometryDescriptor().getLocalName();
		this.waterAnalyzer = new WaterAnalyzer(waterFeatures);
	}
	
	public boolean areRoutesValidWrtWater(SimpleFeatureCollection catchments) throws IOException {
		SimpleFeatureIterator catchmentIt = catchments.features();
		try {
			while(catchmentIt.hasNext()) {
				SimpleFeature catchment = catchmentIt.next();
				LineString route = (LineString)catchment.getDefaultGeometry();
				boolean isValid = isRouteValidWrtWater(route);
				if (!isValid) {
					return false;
				}
			}
		} 
		finally {
			catchmentIt.close();
		}
		return true;
	}
	
	public boolean areRoutesValidWrtWater(List<LineString> catchmentRoutes) throws IOException {
		for(LineString route : catchmentRoutes) {
			boolean isValid = isRouteValidWrtWater(route);
			if (!isValid) {
				return false;
			}
		}
		return true;
	}
	
	public boolean areSectionsValidWrtWater(List<SimpleFeature> catchmentSections) throws IOException {
		for(SimpleFeature f : catchmentSections) {
			LineString route = (LineString)f.getDefaultGeometry();
			boolean isValid = isRouteValidWrtWater(route);
			if (!isValid) {
				return false;
			}
		}
		return true;
	}
	
	/**
	 * checks if a catchment route is valid with respect (w.r.t.) water features. 
	 * validity depends on:
	 * - segments of the route do not overlap segments of the water
	 * - vertices of the route do not touch vertices of the water (except at a confluence)
	 * @throws IOException 
	 */
	public boolean isRouteValidWrtWater(LineString route) throws IOException {
		
		//identify route line SEGMENTS that cover water line SEGMENTS
		//i.e a route segment with both endpoints the same as a water segment
		Filter containsFilter = filterFactory2D.contains(
				filterFactory2D.property(waterGeometryPropertyName), 
				filterFactory2D.literal(route));
		SimpleFeatureCollection nonEndpointCrossings = waterFeatures.getFeatures(containsFilter);
		if (nonEndpointCrossings.size() > 0) {
			return false;
		}
		
		//check that each non-confluence VERTEX of the route doesn't touch water
		for(Coordinate c : route.getCoordinates()) {
			if (!waterAnalyzer.isConfluence(c)) {
				Point p = geometryFactory.createPoint(c);
				
				Filter touchesFilter = filterFactory2D.dwithin(
						filterFactory2D.property(waterGeometryPropertyName),
						filterFactory2D.literal(p),
						0,
						"meters"
						);
				SimpleFeatureCollection touchingWaterFeatures = waterFeatures.getFeatures(touchesFilter);
				if (touchingWaterFeatures.size() > 0) {
					return false;
				}
			}			
		}
		
		return true;
	}
	
	public boolean isRouteValidWrtCatchments(Geometry route, SimpleFeatureCollection catchments) throws IOException {
		FeatureId fidToIgnore = null;
		return isRouteValidWrtCatchments(route, catchments, fidToIgnore);
	}
	
	public boolean isRouteValidWrtCatchments(Geometry route, SimpleFeatureCollection catchments, FeatureId fidToIgnore) throws IOException {
		List<FeatureId> fidsToIgnore = new ArrayList<FeatureId>();
		fidsToIgnore.add(fidToIgnore);
		return isRouteValidWrtCatchments(route, catchments, fidsToIgnore);
	}
	
	public boolean isRouteValidWrtCatchments(Geometry route, SimpleFeatureCollection catchments, List<FeatureId> fidsToIgnore) throws IOException {
		String catchmentGeometryPropertyName = "geometry"; //catchments.getSchema().getGeometryDescriptor().getLocalName();
		Coordinate[] routeCoords = route.getCoordinates();

		//filter to confirm that segments don't overlap other catchments
		Filter filter = filterFactory2D.overlaps(
				filterFactory2D.property(catchmentGeometryPropertyName),
				filterFactory2D.literal(route)
				);
		if (routeCoords.length > 2) {
			//apply a second filter to confirm non-endpoints don't touch other catchments.
			Coordinate[] coordsWithoutEnds = new Coordinate[routeCoords.length]; 
			for(int i = 1; i < routeCoords.length-1; i++) {
				coordsWithoutEnds[i-1] = routeCoords[i]; 
			}
			Geometry routeWithoutEnds = geometryFactory.createMultiPointFromCoords(coordsWithoutEnds);
			Filter filterB = filterFactory2D.intersects(
					filterFactory2D.property(catchmentGeometryPropertyName),
					filterFactory2D.literal(routeWithoutEnds)
					);
			filter = filterFactory2D.or(filter, filterB);
		}
		for (FeatureId featureId : fidsToIgnore) {
			//apply filter to ignore the given fid
			Filter filterC = filterFactory2D.not(filterFactory2D.id(featureId));
			filter = filterFactory2D.and(filter, filterC);
		}
		
		SimpleFeatureCollection matches = catchments.subCollection(filter);
		
		return matches.size() < 1;

		//return true;
	}
	
	public boolean areSectionsValidWrtCatchments(List<SimpleFeature> sections, SimpleFeatureCollection catchments) throws IOException {
		DefaultFeatureCollection theseSections = new DefaultFeatureCollection();
		
		List<FeatureId> ignoreFids = new ArrayList<FeatureId>();
		for(SimpleFeature f: sections) {
			ignoreFids.add(f.getIdentifier());
		}
		
		//check against the main catchment set
		for(SimpleFeature f: sections) {
			LineString route = (LineString)f.getDefaultGeometry();
			boolean isValid = isRouteValidWrtCatchments(route, catchments, ignoreFids);
			if (!isValid) {
				return false;
			}
			theseSections.add(f);
		}
		
		//check against the other features in 'sections'
		for(SimpleFeature f: sections) {
			LineString route = (LineString)f.getDefaultGeometry();
			boolean isValid = isRouteValidWrtCatchments(route, theseSections, f.getIdentifier());
			if (!isValid) {
				return false;
			}
		}
		
		return true;
	}
	
}
