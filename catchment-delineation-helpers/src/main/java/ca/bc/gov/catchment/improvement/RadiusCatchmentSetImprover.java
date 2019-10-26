package ca.bc.gov.catchment.improvement;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import org.geotools.data.DataUtilities;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.factory.CommonFactoryFinder;
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

import ca.bc.gov.catchment.fitness.CatchmentValidity;
import ca.bc.gov.catchment.fitness.GeometryFitnessFinder;
import ca.bc.gov.catchment.routes.LineStringRouter;
import ca.bc.gov.catchment.routes.RouteException;
import ca.bc.gov.catchment.water.WaterAnalyzer;
import ca.bc.gov.catchments.utils.SpatialUtils;

public class RadiusCatchmentSetImprover extends CatchmentSetImprover {

	private static final boolean MOVE_JUNCTIONS = true;
	
	private SimpleFeatureSource tinEdges;
	private SimpleFeatureSource catchmentEdges;
	private GeometryFitnessFinder fitnessFinder;
	private LineStringRouter router;
	private double radius;	
	
	private FilterFactory2 filterFactory;
	private GeometryFactory geometryFactory;
	private SimpleFeatureType tinEdgesFeatureType;
	private String tinEdgesGeometryPropertyName;
	private CatchmentValidity catchmentValidityChecker;
	
	public RadiusCatchmentSetImprover(
			SimpleFeatureSource waterFeatures, 
			SimpleFeatureSource tinEdges,
			SimpleFeatureSource catchmentEdges,
			GeometryFitnessFinder fitnessFinder, 
			double radius) {
		super(waterFeatures);
		this.tinEdges = tinEdges;
		this.catchmentEdges = catchmentEdges;
		this.fitnessFinder = fitnessFinder;
		this.router = new LineStringRouter(tinEdges);
		this.radius = radius;		
		
		this.tinEdgesFeatureType = tinEdges.getSchema();
		this.tinEdgesGeometryPropertyName = tinEdgesFeatureType.getGeometryDescriptor().getLocalName();
		
		this.filterFactory = CommonFactoryFinder.getFilterFactory2();
		this.geometryFactory = JTSFactoryFinder.getGeometryFactory();
		
		this.catchmentValidityChecker = new CatchmentValidity(waterFeatures);
	}

	@Override
	public SectionModification improve(SimpleFeature section) throws IOException {
		boolean debug = section.getIdentifier().getID().equals("catchment_lines.32800");
		System.out.println("improving fid:"+section.getIdentifier().getID());
		
		//if any touching sections are suggested to be moved as part of the best route, 
		//record them here.
		List<SimpleFeature> movedTouchingSections = new ArrayList<SimpleFeature>();
		
		//convenient access to the geometry and coordinates of the original section
		LineString originalRoute = (LineString)section.getDefaultGeometry();
		
		if (!SpatialUtils.is3D(originalRoute)) {
			throw new IllegalArgumentException("All coordinates of the section's geometry must have z values (elevation)");
		}
		
		Coordinate[] originalCoords = originalRoute.getCoordinates();
		
		//if the catchment section touches a confluence, ensure that processing starts
		//ad the confluence end (not the junction end)
		if (!getWaterAnalyzer().isConfluence(originalCoords[0])) {
			originalRoute = (LineString)originalRoute.reverse();
			originalCoords = originalRoute.getCoordinates();
		}
		
		//System.out.println(" original route: "+originalRoute);
		
		//objects that store work-in-progress version of the route as it is being built
		Coordinate[] pickedCoords = new Coordinate[0];
		
		//loop over each coordinate from the original set.  Attempt to improve each
		for (int originalCoordIndex = 0; originalCoordIndex < originalCoords.length; originalCoordIndex++) {
			Coordinate originalCoord = originalCoords[originalCoordIndex];
			
			//boolean isConfluence = getWaterAnalyzer().isConfluence(originalCoord); //endpointTouchesConfluence(originalCoord);
			boolean isEndPoint = hasEndpoint(originalRoute, originalCoord);
			boolean isAdjacentToConfluence = isAdjacentToConfluence(originalCoord, originalRoute);
			
			//System.out.println("inspecting coordinate: "+originalCoord);

			//Some points don't get moved:
			// - endpoints (confluence points and junction points)
			// - the point adjacent to a confluence point
			boolean isFixed = isEndPoint || isAdjacentToConfluence;			
			if (isFixed) {
				pickedCoords = addCoordinate(pickedCoords, originalCoord);
				continue;
			}
			
			//Coordinate[] hybridCoords = getHybridCoords(pickedCoords, originalCoords);
			Coordinate[] uninspectedCoords = getUninspectedCoords(originalCoord, originalCoords);
			Coordinate[] alternativeCoords = suggestAlternativeCoordsSorted(originalCoord, uninspectedCoords);
			
			//System.out.println("found "+ alternativeCoords.length+" alternatives for "+ originalCoord);
			
			//alternativeCoords = addCoordinate(alternativeCoords, pickedCoords[pickedCoords.length-1]);
			//System.out.println("  alternative coords: "+alternativeCoords.length);
			
			//loop through the alternative coords, creating a proposed route through each
			//stop at first alternative coordinate that contributes to a valid route
			boolean foundImprovementForCurrentCoordinate = false;
			for(Coordinate alternativeCoord : alternativeCoords) {
				System.out.println(" considering alternative "+alternativeCoord );
				
				//non-endpoint
				if (!isEndPoint) {
					Coordinate[] includedCoords = addCoordinate(pickedCoords, alternativeCoord);
					Coordinate[] excludedCoords = {}; 
					try {
						LineString routeToConsider = router.makeRoute(includedCoords, excludedCoords);
						boolean isValidWrtWater = catchmentValidityChecker.isRouteValidWrtWater(routeToConsider);
						boolean isValid = isValidWrtWater;
						if(debug) {
							System.out.println("  route: "+routeToConsider);
						}
						
						if (isValid) {
							if(debug) {
								System.out.println(" " +originalCoord+"  -->  "+alternativeCoord);
							}
							
							pickedCoords = includedCoords;
							//pickedRoute = routeToConsider;
							foundImprovementForCurrentCoordinate = true;
							//System.out.println("  Moved a point up hill from "+originalCoord+" to "+alternativeCoord);
							break;
						}
					} catch (RouteException e) {
						//System.out.println("no valid route containing alternative point.  coords were:");						
						//for(Coordinate c: includedCoords) {
						//	System.out.print(c+" ");
						//}
						//System.out.println();
						//router was unable to find a valid route containing the alternative point
						continue;
					}
				}
			}
			
			//if the current coordinate cannot be improved, copy the original value to the 
			//"pickedCoords" set.
			if (!foundImprovementForCurrentCoordinate) {
				//System.out.println("picking original:"+originalCoord);
				if(debug) {
					System.out.println(" "+originalCoord+"  -->  "+originalCoord + " (no change)");
				}
				pickedCoords = addCoordinate(pickedCoords, originalCoord);
			}
			
		}

		SectionModification modification = new SectionModification(section);
		
		//convert the picked coordinates into a route
		LineString pickedRoute = originalRoute;
		try {
			pickedRoute = router.makeRoute(pickedCoords);
			boolean isValidWrtWater = catchmentValidityChecker.isRouteValidWrtWater(pickedRoute);
			boolean isValidWrtCatchments = catchmentValidityChecker.isRouteValidWrtCatchments(pickedRoute, getProposedSections(), section.getIdentifier());
			
			if (!isValidWrtWater) {
				throw new RouteException("route touches water");
			}
			if (!isValidWrtCatchments) {
				throw new RouteException("route touches another catchment");
			}

		} catch (RouteException e) {
			System.out.println("Warning: Picked route isn't valid. "+e.getMessage());
			return modification;
		}
		
		//determine fitness of the original route and the proposed new route
		double originalRouteFitness = 0;
		double modifiedRouteFitness = 0;
		try {
			originalRouteFitness = fitnessFinder.fitness(originalRoute);
			modifiedRouteFitness = fitnessFinder.fitness(pickedRoute);
		} catch(IllegalArgumentException e) {
			System.out.println("Warning: Unable to determine fitness.  No improvement could be found for this route.");
			return modification;
		}
		
		//prepare the response object describing the modification
		
		if (modifiedRouteFitness > originalRouteFitness) {
			System.out.println("section fitness improved from "+originalRouteFitness + " to " +modifiedRouteFitness);
			//add the modified primary line segment to the result object 
			SimpleFeatureType type = section.getFeatureType();
			String fid = section.getIdentifier().getID();
			SimpleFeature modifiedSection = SpatialUtils.geomToFeature(pickedRoute, type, fid);
			modification.setModifiedSection(modifiedSection);
			modification.setModifiedTouchingSections(movedTouchingSections);
			
			//System.out.println(" improved route: "+pickedRoute);
		}
		else {
			System.out.println("section fitness could not be improved from "+originalRouteFitness);
			System.out.println(" best alternative route fitness was "+modifiedRouteFitness);
		}
		
		return modification;
		
	}

	/**
	 * proposed an improvement to the given junction
	 * @param originalJunction
	 * @return
	 * @throws IOException
	 */
	public JunctionModification improveJunction(Coordinate originalJunction) throws IOException {

		SortedMap<Double, JunctionModification> allResults = new TreeMap<Double, JunctionModification>();
		
		
		//identify other sections touching this junction
		List<SimpleFeature> sectionsTouchingJunction = getSectionsTouchingJunction(originalJunction);
		
		//extract only the route (geometry) from the touching sections, keeping these
		//in a list
		List<LineString> routesTouchingJunction = new ArrayList<LineString>();
		for (SimpleFeature touching : sectionsTouchingJunction) {
			routesTouchingJunction.add((LineString)touching.getDefaultGeometry());
		}
		
		
		Coordinate[] alternativeCoords = suggestAlternativeCoordsSorted(originalJunction, null);
		
		for (Coordinate alternativeJunction : alternativeCoords) {

			//attempt to move the junction point
			List<LineString> routesWithJunctionMoved = null;
			try {
				routesWithJunctionMoved = router.moveJunction(routesTouchingJunction, originalJunction, alternativeJunction);
			} catch (RouteException e) {
				continue; 
			}
			
			//check that all moved routes are valid
			boolean isValid = catchmentValidityChecker.areRoutesValidWrtWater(routesWithJunctionMoved);
			if (!isValid) {
				continue;
			}

			List<SimpleFeature> proposedSections = new ArrayList<SimpleFeature>();
			double junctionFitness = 0;
			for (int i = 0; i < routesWithJunctionMoved.size(); i++) {
				LineString route = routesWithJunctionMoved.get(i);
				SimpleFeature originalTouchingSection = sectionsTouchingJunction.get(i);
				SimpleFeature copyOftouchingSection = SimpleFeatureBuilder.copy(originalTouchingSection);
				copyOftouchingSection.setDefaultGeometry(route);
				proposedSections.add(copyOftouchingSection);
				junctionFitness += fitnessFinder.fitness(route);
			}
			
			//store the proposed modification.
			//after all alternative junctions are evaluated, the one giving the best fit
			//will be selected.
			JunctionModification junctionModification = new JunctionModification(originalJunction);
			junctionModification.setModifiedJunction(alternativeJunction);
			junctionModification.setModifiedSections(proposedSections);
			allResults.put(junctionFitness, junctionModification);

		}
		
		if (allResults.size() > 0) {
			//pick the junctionModification that had the best fit
			double bestFitness = allResults.lastKey();
			JunctionModification best = allResults.get(bestFitness);
			System.out.println("junction fitness improved");
			return best;
		}
		//no change
		System.out.println("junction fitness could not be improved");
		return new JunctionModification(originalJunction);
		
	}
	
	
	/**
	 * Creates a new array with same length as originalCoords.  Copies pickedCoords into the new array.
	 * If there are empty elements at the end of the new array, copy the corresponding elements from
	 * originalCoords.
	 * @param pickedCoords
	 * @param originalCoords
	 * @return
	 */
	private Coordinate[] getHybridCoords(Coordinate[] pickedCoords, Coordinate[] originalCoords) {
		Coordinate[] hybridCoords = new Coordinate[originalCoords.length];
		int i = 0;
		for (; i < pickedCoords.length; i++) {
			hybridCoords[i] = pickedCoords[i];
		}
		for (; i < originalCoords.length; i++) {
			hybridCoords[i] = originalCoords[i];
		}
		return hybridCoords;
	}
	
	private Coordinate[] getUninspectedCoords(Coordinate originalCoord, Coordinate[] originalCoords) {
		List<Coordinate> result = new ArrayList<Coordinate>();
		boolean doCopy = false;
		for(Coordinate c : originalCoords) {
			if (c.equals(originalCoord)) {
				doCopy = true;
			}
			if (doCopy) {
				result.add(c);
			}					
		}
		return SpatialUtils.toCoordinateArray(result);
	}
	
	/**
	 * returns an array of candidate coordinates that could replace the given coordinate 
	 * candiates are chosen as follows:
	 *  - find all coords in a given radius of the original coordinate
	 *  - 
	 * results are sorted by elevation (descending)
	 * @param originalCoord
	 * @return
	 * @throws IOException 
	 */
	private Coordinate[] suggestAlternativeCoordsSorted(final Coordinate originalCoord, Coordinate[] coordsToAvoid) throws IOException {
		
		if (coordsToAvoid == null) {
			coordsToAvoid = new Coordinate[0];
		}
		if (Double.isNaN(originalCoord.getZ())) {
			throw new IllegalArgumentException("original coordinate must have a z value (elevation)");
		}
		
		List<Coordinate> proposedCoords = getTinCoordsInRadius(originalCoord, radius);
		//System.out.println(" "+proposedCoords.size()+" proposed coords within "+radius+" m of "+ originalCoord);
		
		//filter out coords that are closer to the points we want to avoid than to the original coordinate
		List<Coordinate> filteredCoords = new ArrayList<Coordinate>();
		for (Coordinate proposedCoord : proposedCoords) {
			if(filteredCoords.contains(proposedCoord)) {
				continue;
			}
			if (Double.isNaN(proposedCoord.getZ())) {
				throw new IllegalArgumentException("coordinates from TIN must have z values (elevation)");
			}
			boolean keep = proposedCoord.getZ() > originalCoord.getZ();
			if (!keep) {
				//System.out.println("too low");
				continue;
			}
			for(Coordinate coordToAvoid : coordsToAvoid) {
				if (proposedCoord.distance(coordToAvoid) < proposedCoord.distance(originalCoord)) {
					keep = false;					
					break;
				}
			}
			if (keep) {
				filteredCoords.add(proposedCoord);
			}
			else {
				//System.out.println("too close to blacklisted coord");
			}
		}
		//System.out.println(" "+filteredCoords.size()+" filtered coords within "+radius+" m of "+ originalCoord);
		
		//sort the filtered coords by elevation (descending)
		filteredCoords.sort(new Comparator<Coordinate>() {
			public int compare(Coordinate c1, Coordinate c2) {
				double z1 = c1.getZ();
				double z2 = c2.getZ();
				if (z1 > z2) {
					return -1;
				}
				else if (z1 < z2) {
					return 1;
				}
				return 0;
			}
		});
		//System.out.println(" "+filteredCoords.size()+" filtered coords within "+radius+" m of "+ originalCoord);
		
		return SpatialUtils.toCoordinateArray(filteredCoords);
	}

	private List<SimpleFeature> getSectionsTouchingJunction(Coordinate junction) throws IOException {
		Point p = geometryFactory.createPoint(junction);
		List<SimpleFeature> touchingSections = new ArrayList<SimpleFeature>();
		
		//find routes from the original catchment set
		Filter touchesFilter = filterFactory.touches(
				filterFactory.property(tinEdgesGeometryPropertyName), 
				filterFactory.literal(p)
				);
		
		SimpleFeatureCollection originalTouchingCatchmentEdges = catchmentEdges.getFeatures(touchesFilter);
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
	
	private List<Coordinate> getTinCoordsInRadius(Coordinate c, double radius) throws IOException  {
		Point p = geometryFactory.createPoint(c);
		List<Coordinate> coords = new ArrayList<Coordinate>();
		
		//first find water features that touch the given coordinate (at any point)
		Filter radiusFilter = filterFactory.dwithin(
				filterFactory.property(tinEdgesGeometryPropertyName), 
				filterFactory.literal(p),
				radius,
				"meter");
		SimpleFeatureCollection matches = tinEdges.getFeatures(radiusFilter);
		
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
	
	
	private Coordinate[] addCoordinate(Coordinate[] coords, Coordinate newCoord) {
		
		//don't allow repeated coords
		if (coords.length > 0 && coords[coords.length-1].equals(newCoord)) {
			return coords;
		}
		
		Coordinate[] result = new Coordinate[coords.length+1];
		for(int i = 0; i < coords.length; i++) {
			result[i] = coords[i];
		}
		result[coords.length] = newCoord;
		return result;
	}
	

}
