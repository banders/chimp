package ca.bc.gov.catchment.improvement;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.factory.CommonFactoryFinder;
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
import ca.bc.gov.catchments.utils.SpatialUtils;

public class RadiusCatchmentSetImprover extends CatchmentSetImprover {

	private SimpleFeatureSource tinEdges;
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
			GeometryFitnessFinder fitnessFinder, 
			double radius) {
		super(waterFeatures);
		this.tinEdges = tinEdges;
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
		//System.out.println("improving fid:"+section.getIdentifier().getID());
		
		//convenient access to the geometry and coordinates of the original section
		LineString originalRoute = (LineString)section.getDefaultGeometry();
		Coordinate[] originalCoords = originalRoute.getCoordinates();
		
		//System.out.println(" original route: "+originalRoute);
		
		//objects that store work-in-progress version of the route as it is being built
		Coordinate[] pickedCoords = new Coordinate[0];
		LineString pickedRoute = originalRoute;
		
		//loop over each coordinate from the original set.  Attempt to improve each
		for (int originalCoordIndex = 0; originalCoordIndex < originalCoords.length; originalCoordIndex++) {
			Coordinate originalCoord = originalCoords[originalCoordIndex];
			
			boolean isConfluence = endpointTouchesConfluence(originalCoord);
			boolean isEndPoint = hasEndpoint(originalRoute, originalCoord);
			
			//System.out.println(" inspecting coordinate: "+originalCoord + " - endpoint?:"+isEndPoint+", confluence:?"+isConfluence);
			
			if (isConfluence) {
				//System.out.println("  Kept the confluence point in its original position");
				pickedCoords = addCoordinate(pickedCoords, originalCoord);
				try {
					pickedRoute = router.makeRoute(pickedCoords);
				} 
				catch(RouteException e) {
					throw new IllegalStateException("improvement algorithm failed.");
				}
				continue;
			}
			
			Coordinate[] hybridCoords = getHybridCoords(pickedCoords, originalCoords);
			Coordinate[] alternativeCoords = suggestAlternativeCoordsSorted(originalCoord, hybridCoords);
			//System.out.println("  alternative coords: "+alternativeCoords.length);
			
			//loop through the alternative coords, creating a proposed route through each
			//stop at first alternative coordinate that contributes to a valid route
			boolean foundImprovementForCurrentCoordinate = false;
			for(Coordinate alternativeCoord : alternativeCoords) {
				//System.out.println("   inspecting alternative: "+alternativeCoord);
				if (!isEndPoint) {
					Coordinate[] includedCoords = addCoordinate(pickedCoords, alternativeCoord);
					Coordinate[] excludedCoords = {originalCoord}; 
					try {
						LineString routeToConsider = router.makeRoute(includedCoords, excludedCoords);
						boolean isValid = catchmentValidityChecker.isRouteValidWrtWater(routeToConsider);
						//System.out.println("    routeToConsider: "+routeToConsider);
						
						if (isValid) {
							foundImprovementForCurrentCoordinate = true;
							pickedCoords = includedCoords;
							pickedRoute = routeToConsider;
							//System.out.println("  Moved a point up hill from "+originalCoord+" to "+alternativeCoord);
							break;
						}
					} catch (RouteException e) {
						//router was unable to find a valid route containing the alternative point
						continue;
					}
				}
				else { //endpoint
					//System.out.println("  Non-confluence endpoint ("+originalCoord+") not moved.  TODO: move move non-confluence endpoint.");
					//todo: attempt to find an improvement
					break;
				}
			}
			
			if (!foundImprovementForCurrentCoordinate) {
				pickedCoords = addCoordinate(pickedCoords, originalCoord);
				try {
					pickedRoute = router.makeRoute(pickedCoords);
				} catch (RouteException e) {
					throw new IllegalStateException("improvement algorithm failed.");
				}
			}
		}
		
		
		SectionModification modification = new SectionModification(section);
		
		double originalRouteFitness = fitnessFinder.fitness(originalRoute);
		double modifiedRouteFitness = fitnessFinder.fitness(pickedRoute);
		if (modifiedRouteFitness > originalRouteFitness) {
			System.out.println("fitness improved from "+originalRouteFitness + " to " +modifiedRouteFitness);
			//add the modified primary line segment to the result object 
			SimpleFeatureType type = section.getFeatureType();
			String fid = section.getIdentifier().getID();
			SimpleFeature modifiedSection = SpatialUtils.geomToFeature(pickedRoute, type, fid);
			modification.setModifiedSection(modifiedSection);

			//todo: add modified touching line segments to the result object
			System.out.println("TODO: add modified touching lines to result object");
			
			System.out.println(" improved route: "+pickedRoute);
		}
		else {
			System.out.println("fitness could not be improved from "+originalRouteFitness);
			System.out.println(" best alternative route fitness was "+modifiedRouteFitness);
		}
		
		return modification;
		
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
		
		List<Coordinate> proposedCoords = getTinCoordsInRadius(originalCoord, radius);
		//System.out.println(" "+proposedCoords.size()+" proposed coords within "+radius+" m of "+ originalCoord);
		
		//filter out coords that are closer to the points we want to avoid than to the original coordinate
		List<Coordinate> filteredCoords = new ArrayList<Coordinate>();
		for (Coordinate proposedCoord : proposedCoords) {
			boolean keep = proposedCoord.getZ() > originalCoord.getZ();
			for(Coordinate coordToAvoid : coordsToAvoid) {
				if (proposedCoord.distance(coordToAvoid) < proposedCoord.distance(originalCoord)) {
					keep = false;
					break;
				}
			}
			if (keep) {
				filteredCoords.add(proposedCoord);
			}
		}
		
		//sort the filtered coords by elevation (descending)
		filteredCoords.sort(new Comparator<Coordinate>() {
			public int compare(Coordinate c1, Coordinate c2) {
				double z1 = c1.getZ();
				double z2 = c2.getZ();
				if (z1 < z2) {
					return -1;
				}
				else if (z1 > z2) {
					return 1;
				}
				return 0;
			}
		});
		//System.out.println(" "+filteredCoords.size()+" filtered coords within "+radius+" m of "+ originalCoord);
		
		return SpatialUtils.toCoordinateArray(filteredCoords);
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
		while(matchesIt.hasNext()) {
			SimpleFeature match = matchesIt.next();
			Geometry g = (Geometry)match.getDefaultGeometry();
			for(Coordinate coord : g.getCoordinates()) {
				if (c.distance(coord) <= radius && !coords.contains(coord)) { //2d distance
					coords.add(coord);
				}
			}
		}
		return coords;
	}
	
	
	private Coordinate[] addCoordinate(Coordinate[] coords, Coordinate newCoord) {
		Coordinate[] result = new Coordinate[coords.length+1];
		for(int i = 0; i < coords.length; i++) {
			result[i] = coords[i];
		}
		result[coords.length] = newCoord;
		return result;
	}
}
