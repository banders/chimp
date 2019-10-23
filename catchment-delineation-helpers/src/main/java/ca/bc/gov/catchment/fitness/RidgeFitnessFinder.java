package ca.bc.gov.catchment.fitness;

import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.factory.CommonFactoryFinder;
import org.geotools.factory.Hints;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.filter.Filter;
import org.opengis.filter.FilterFactory2;

import ca.bc.gov.catchment.tin.Edge;
import ca.bc.gov.catchment.tin.Triangle;
import ca.bc.gov.catchments.utils.SpatialUtils;

/**
 * Defines functions used to calculate how well a given geometry aligns with 
 * ridges in the elevation model.
 * The elevation model is represented by a triangulated irregular network (TIN)
 * Fitness is defined on multiple scales:
 *   - the fitness of a two-point line segment is the basic unit of fitness
 *   - the scale of a n-point line segment is a sum of the fitnesses of the component two-point line segments
 *   - the scale of a collection of line segments is a sum of the fitnesses of the component n-point line segments 
 * @author Brock
 *
 */
public class RidgeFitnessFinder extends GeometryFitnessFinder {

	private static final double SLOPE_TO_ASPECT_WEIGHT_RATIO = 0.1; //should be > 0
	private static final double FITNESS_SCALE_FACTOR = 100;
	
	private SimpleFeatureSource tinPolys;
	private FilterFactory2 filterFactory;
	private SimpleFeatureType tinPolysFeatureType;
	private String tinPolysGeometryProperty;
	
	public RidgeFitnessFinder(SimpleFeatureSource tinPolys) {
		this.tinPolys = tinPolys;
		this.filterFactory = CommonFactoryFinder.getFilterFactory2();
		this.tinPolysFeatureType = tinPolys.getSchema();
		this.tinPolysGeometryProperty = tinPolysFeatureType.getGeometryDescriptor().getLocalName();
	}
	
	@Override
	public double fitness(Coordinate c1, Coordinate c2) throws IOException {
		if (c1 == null || c2 == null) {
			throw new NullPointerException("coordinates must be non-null.");
		}
		
		//define a line segment between the two input coordinate
		//System.out.println("shared edge: "+c1+" ,"+c2);
		LineString segment = SpatialUtils.toLineString(c1, c2);
		double segmentLength = segment.getLength();
		Edge commonEdge = new Edge(c1, c2);
		
				
		//angle of segment in degrees (0-180), where 0 is East
		double segmentAngle = Math.toDegrees(Math.atan((c2.getY() - c1.getY()) / (c2.getX() - c1.getX() ))) % 180;
		
		//identify the two triangles which share the edge defined by the line segment
		List<Triangle> touchingTriangles = getTouchingTriangles(segment);
		if (touchingTriangles.size() != 2) {
			for(Triangle t : touchingTriangles) {
				System.out.println(t);
			}
			throw new IllegalArgumentException("Unable to determine fitness of a segment that doesn't have two touching triangles.  This segment has "+touchingTriangles.size()+ " touching triangles. Segment is: "+segment );
		}		
		Triangle t1 = touchingTriangles.get(0);
		Triangle t2 = touchingTriangles.get(1);
				
		Edge spine1 = t1.getSpineEdge(commonEdge);
		Edge spine2 = t2.getSpineEdge(commonEdge);		
		
		//alpha: difference between triangle aspect angle and segment angle (degrees)
		//	*calculate an alpha for each adjacent triangle
		double alpha1 = t1.getAspect() - spine1.getCompassAngle();
		double alpha2 = t2.getAspect() - spine2.getCompassAngle();
		
		//cosAlpha: the cosine of alpha
		double cosAlpha1 = Math.cos(Math.toRadians(alpha1)); 
		double cosAlpha2 = Math.cos(Math.toRadians(alpha2)); 
		
		//beta: a number which represents the aspect component and the slope component of a triangle.
		//  the aspect component is: cosAlpha multiplied by a factor which amplifies negative numbers (2^-cosAlpha)
		//  the slope component is: just the slope
		//  the two components are multiplied together along with their respective weights.
		//  *calculate an alpha for each adjacent triangle
		double beta1 = cosAlpha1 * Math.pow(2, -cosAlpha1) * SLOPE_TO_ASPECT_WEIGHT_RATIO * t1.getSlope(); 
		double beta2 = cosAlpha2 * Math.pow(2, -cosAlpha2) * SLOPE_TO_ASPECT_WEIGHT_RATIO * t2.getSlope(); 


		
		//fitness: combine the beta values for each triangle to derive a fitness value which 
		//  is positive when both triangle aspects face away from the adjoining segment and is
		//  negative for all other cases.
		double fitness = beta1 * beta2 * Math.max(beta1, beta2) * FITNESS_SCALE_FACTOR;
		
		//avoid -0
		if (fitness == 0) {
			fitness = 0;
		}
		
		//round
		//DecimalFormat df = new DecimalFormat("+#.######;-#.######");
		//String fitnessStr = df.format(fitness);
		//System.out.println("fitness: "+fitness+", fitnessStr: "+fitnessStr);
		//fitness = Double.parseDouble(fitnessStr);
		
		/*
				
		System.out.println(" segmentAngle: "+segmentAngle);
		
		System.out.println("t1:");
		System.out.println(t1);
		
		System.out.println("t2:");
		System.out.println(t2);
		
		System.out.println(" spine1:"+spine1);
		System.out.println(" spine2:"+spine2);
		
		System.out.println(" spineAngle1: "+spine1.getCompassAngle()); //-360 to +360
		System.out.println(" spineAngle2: "+spine2.getCompassAngle()); //-360 to +360
		
		System.out.println(" aspect1: "+t1.getAspect());
		System.out.println(" aspect2: "+t2.getAspect());
		
		System.out.println(" alpha1: "+alpha1);
		System.out.println(" alpha2: "+alpha2);
		
		System.out.println(" cosAlpha1: "+cosAlpha1);
		System.out.println(" cosAlpha2: "+cosAlpha2);
		
		System.out.println(" slope1: "+t1.getSlope());
		System.out.println(" slope2: "+t2.getSlope());
		
		System.out.println(" beta1: "+beta1);
		System.out.println(" beta2: "+beta2);
		
		System.out.println(" fitness: "+fitness);
		
		
		*/
		
		//weight the fitness based on the length of the line
		double weightedFitness = segmentLength * fitness;
		return weightedFitness;
	}

	private List<Triangle> getTouchingTriangles(LineString segment) throws IOException {
		
		List<Triangle> result = new ArrayList<Triangle>();
		
		Filter overlapsFilter = filterFactory.touches(
			filterFactory.property(tinPolysGeometryProperty),
			filterFactory.literal(segment)
		);
		SimpleFeatureCollection fc = tinPolys.getFeatures(overlapsFilter);
		
		SimpleFeatureIterator it = fc.features();
		while(it.hasNext()) {
			SimpleFeature f = it.next();
			Geometry g = (Geometry)f.getDefaultGeometry();
			Triangle t = new Triangle(g);
			if (!t.hasEdge(new Edge(segment.getCoordinates()))) {
				//triangle touches, but doesn't share the specified edge
				continue;
			}
			if (!t.isComplete()) {
				throw new IllegalStateException("Invalid triangle found.");
			}
			if (!result.contains(t)) {
				result.add(t);
			}
		}
		it.close();
		
		return result;
	}
	
	/*
	private Edge getSpine(Edge edgeOfT, Triangle t) {
		Coordinate topCoord = t.getOtherCoord(edgeOfT.getA(), edgeOfT.getB());
		Coordinate orthoCentreCoord = t.getOrthoCenter2D();
		Edge spine = new Edge(orthoCentreCoord, topCoord);
		Edge normalizedSpine = Edge.normalize(spine);
		
		//note: the orthocenter is not always inside the triangle.  
		//if it is outside, the the direction of the spine must be flipped
		if (!t.containsCoordinate2D(orthoCentreCoord)) {
			normalizedSpine = normalizedSpine.oppositeDirection(); 
		}
		return normalizedSpine;
	}
	*/
	
}
