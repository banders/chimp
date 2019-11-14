package ca.bc.gov.catchment.fitness;

import java.io.IOException;

import org.geotools.data.DataUtilities;
import org.geotools.data.collection.SpatialIndexFeatureCollection;
import org.geotools.data.collection.SpatialIndexFeatureSource;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.feature.DefaultFeatureCollection;
import org.geotools.feature.SchemaException;
import org.junit.Before;
import org.junit.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.util.Assert;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;

import ca.bc.gov.catchment.synthetic.DummyFactory;
import ca.bc.gov.catchment.tin.Triangle;
import ca.bc.gov.catchments.utils.SpatialUtils;

public class RidgeFitnessTest {

	public RidgeFitnessTest() throws IOException {
		
	}
	
	@Test
	public void testSegmentIsRidge1() throws IOException {
		
		//test a segment which is a ridge line (i.e. the two adjacent triangles 
		//each slope downward from their shared edge in opposite directions
		//  expected result: fitness is a positive number
		
		//shared edge
		Coordinate a = new Coordinate(0, 0, 10);
		Coordinate b = new Coordinate(4, 4, 10);
		
		//triangle 1
		Coordinate c = new Coordinate(1, 3, 9);
		Triangle t1 = new Triangle(a, b, c);
		
		//triangle 2
		Coordinate d = new Coordinate(3, 2, 9);
		Triangle t2 = new Triangle(a, b, d);
		
		SimpleFeatureSource tinPolys = DummyFactory.createTinPolys(t1, t2, "tin_polygons");
		RidgeFitnessFinder fitnessFinder = new RidgeFitnessFinder(tinPolys);
		double fitness = fitnessFinder.fitness(a,  b);
		
		Assert.isTrue(fitness > 0, "fitness on ridge expected to be positive.  found "+fitness);
	}
	
	@Test
	public void testSegmentIsRidge2() throws IOException {
		
		//test a segment which is a ridge line (i.e. the two adjacent triangles 
		//each slope downward from their shared edge in opposite directions
		//  expected result: fitness is a positive number
		
		//shared edge
		Coordinate a = new Coordinate(0, 0, 10.1);
		Coordinate b = new Coordinate(4, -4, 10);
		
		//triangle 1
		Coordinate c = new Coordinate(1, 3, 9);
		Triangle t1 = new Triangle(a, b, c);
		
		//triangle 2
		Coordinate d = new Coordinate(2, -3, 8);
		Triangle t2 = new Triangle(a, b, d);
		
		SimpleFeatureSource tinPolys = DummyFactory.createTinPolys(t1, t2, "tin_polygons");
		RidgeFitnessFinder fitnessFinder = new RidgeFitnessFinder(tinPolys);
		double fitness = fitnessFinder.fitness(a,  b);
		
		Assert.isTrue(fitness > 0, "fitness on ridge expected to be positive.  found "+fitness);
	}
	
	@Test
	public void testSegmentIsTrough1() throws IOException {
		
		//test a segment which is a trough line (i.e. the two adjacent triangles 
		//each slope upward from their shared edge in opposite directions
		//  expected result: fitness is a negative number
		
		//shared edge
		Coordinate a = new Coordinate(0, 0, 10);
		Coordinate b = new Coordinate(4, 4, 10);
		
		//triangle 1
		Coordinate c = new Coordinate(1, 3, 11);
		Triangle t1 = new Triangle(a, b, c);
		
		//triangle 2
		Coordinate d = new Coordinate(3, 2, 11);
		Triangle t2 = new Triangle(a, b, d);
		
		SimpleFeatureSource tinPolys = DummyFactory.createTinPolys(t1, t2, "tin_polygons");
		RidgeFitnessFinder fitnessFinder = new RidgeFitnessFinder(tinPolys);
		double fitness = fitnessFinder.fitness(a,  b);
		
		Assert.isTrue(fitness < 0, "fitness in trough expected to be negative.  found "+fitness);
	}
	
	@Test
	public void testSegmentIsTrough2() throws IOException {
		
		//test a segment which is a trough line (i.e. the two adjacent triangles 
		//each slope upward from their shared edge in opposite directions
		//  expected result: fitness is a negative number
		
		//shared edge
		Coordinate a = new Coordinate(0, 0, 10);
		Coordinate b = new Coordinate(5, 1, 10.1);
		
		//triangle 1
		Coordinate c = new Coordinate(1, 3, 11);
		Triangle t1 = new Triangle(a, b, c);
		
		//triangle 2
		Coordinate d = new Coordinate(3, -3, 12);
		Triangle t2 = new Triangle(a, b, d);
		
		SimpleFeatureSource tinPolys = DummyFactory.createTinPolys(t1, t2, "tin_polygons");
		RidgeFitnessFinder fitnessFinder = new RidgeFitnessFinder(tinPolys);
		double fitness = fitnessFinder.fitness(a,  b);
		
		Assert.isTrue(fitness < 0, "fitness in trough expected to be negative.  found "+fitness);
	}
	
	@Test
	public void testSegmentIsMidSlope1() throws IOException {
		
		//test a segment which is not a ridge line and which is also not a trough. (therefore
		//the segment is part way up a mountain such that the adjacent triangles
		//both slope in the same direction (i.e. one side of segment is uphill, one side is downhill)
		//  expected result: fitness is negative
		
		//shared edge
		Coordinate a = new Coordinate(0, 0, 10);
		Coordinate b = new Coordinate(5, 0, 10);
		
		//triangle 1
		Coordinate c = new Coordinate(3, 1, 11);
		Triangle t1 = new Triangle(a, b, c);
		
		//triangle 2
		Coordinate d = new Coordinate(3, -1, 9);
		Triangle t2 = new Triangle(a, b, d);
		
		SimpleFeatureSource tinPolys = DummyFactory.createTinPolys(t1, t2, "tin_polygons");
		RidgeFitnessFinder fitnessFinder = new RidgeFitnessFinder(tinPolys);
		double fitness = fitnessFinder.fitness(a,  b);
		
		Assert.isTrue(fitness < 0, "fitness mid-slope expected to be negative.  found "+fitness);
	}
	
	@Test
	public void testSegmentIsMidSlope2() throws IOException {
		
		//test a segment which is not a ridge line and which is also not a trough. (therefore
		//the segment is part way up a mountain such that the adjacent triangles
		//both slope in the same direction (i.e. one side of segment is uphill, one side is downhill)
		//  expected result: fitness is negative
		
		//shared edge
		Coordinate a = new Coordinate(0, 0, 10);
		Coordinate b = new Coordinate(5, 1, 10.1);
		
		//triangle 1
		Coordinate c = new Coordinate(3, 3, 9);
		Triangle t1 = new Triangle(a, b, c);
		
		//triangle 2
		Coordinate d = new Coordinate(2, -2, 12);
		Triangle t2 = new Triangle(a, b, d);
		
		SimpleFeatureSource tinPolys = DummyFactory.createTinPolys(t1, t2, "tin_polygons");
		RidgeFitnessFinder fitnessFinder = new RidgeFitnessFinder(tinPolys);
		double fitness = fitnessFinder.fitness(a,  b);
		
		Assert.isTrue(fitness < 0, "fitness mid-slope expected to be negative.  found "+fitness);
	}
	
	@Test
	public void testSegmentHorizontalRidge() throws IOException {
		
		//test a segment which is horizontal and a ridge
		
		//shared edge (horizontal)
		Coordinate a = new Coordinate(0, 0, 10);
		Coordinate b = new Coordinate(5, 0, 10);
		
		//triangle 1
		Coordinate c = new Coordinate(3, 3, 9);
		Triangle t1 = new Triangle(a, b, c);
		
		//triangle 2
		Coordinate d = new Coordinate(3, -3, 9);
		Triangle t2 = new Triangle(a, b, d);
		
		SimpleFeatureSource tinPolys = DummyFactory.createTinPolys(t1, t2, "tin_polygons");
		RidgeFitnessFinder fitnessFinder = new RidgeFitnessFinder(tinPolys);
		double fitness = fitnessFinder.fitness(a,  b);
		
		Assert.isTrue(fitness > 0, "fitness on ridge expected to be positive.  found "+fitness);
	}
	
	@Test
	public void testSegmentVerticalRidge() throws IOException {
		
		//test a segment which is vertical and a ridge
		
		//shared edge (vertical)
		Coordinate a = new Coordinate(0, 0, 10);
		Coordinate b = new Coordinate(0, 5, 10);
		
		//triangle 1
		Coordinate c = new Coordinate(-3, 3, 8);
		Triangle t1 = new Triangle(a, b, c);
		
		//triangle 2
		Coordinate d = new Coordinate(3, 2, 9);
		Triangle t2 = new Triangle(a, b, d);
		
		SimpleFeatureSource tinPolys = DummyFactory.createTinPolys(t1, t2, "tin_polygons");
		RidgeFitnessFinder fitnessFinder = new RidgeFitnessFinder(tinPolys);
		double fitness = fitnessFinder.fitness(a,  b);
		
		Assert.isTrue(fitness > 0, "fitness on ridge expected to be positive.  found "+fitness);
	}
	
	
	@Test
	public void testAspectsBothParallelToSegment() throws IOException {
		
		//test two triangles that both slope in the same direction as the connecting
		//line segment
		//  expected result: fitness is 0
		
		//shared edge
		Coordinate a = new Coordinate(0, 0, 10);
		Coordinate b = new Coordinate(4, 0, 9);
		
		//triangle 1
		Coordinate c = new Coordinate(4, 1, 9);
		Triangle t1 = new Triangle(a, b, c);
		
		//triangle 2
		Coordinate d = new Coordinate(4, -1, 9);
		Triangle t2 = new Triangle(a, b, d);
		
		SimpleFeatureSource tinPolys = DummyFactory.createTinPolys(t1, t2, "tin_polygons");
		RidgeFitnessFinder fitnessFinder = new RidgeFitnessFinder(tinPolys);
		double fitness = fitnessFinder.fitness(a,  b);
		
		Assert.isTrue(fitness == 0, "fitness expected to be 0.  found "+fitness);
	}
	

}
