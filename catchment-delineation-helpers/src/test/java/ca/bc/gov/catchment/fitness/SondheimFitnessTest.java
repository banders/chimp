package ca.bc.gov.catchment.fitness;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.geotools.data.simple.SimpleFeatureSource;
import org.junit.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.util.Assert;

import ca.bc.gov.catchment.synthetic.DummyFactory;
import ca.bc.gov.catchment.tin.TinPolys;
import ca.bc.gov.catchment.tin.Triangle;
import ca.bc.gov.catchment.utils.SpatialUtils;

public class SondheimFitnessTest {
	
	public SondheimFitnessTest() {
		
	}
	
	@Test
	public void testFitness1() throws IOException {
		
		//tests fitness for a case where both triangles adjacent to a given segment
		//are completely horizontal (i.e. slope = 0)
		
		double maxTinElevation = 2000;
		double avgSegmentElevation = 50;
		
		//shared edge
		Coordinate a = new Coordinate(0, 0, avgSegmentElevation);
		Coordinate b = new Coordinate(4, 4, avgSegmentElevation);
		LineString segment = SpatialUtils.toLineString(a, b);
		
		//triangles
		List<Triangle> triangles = new ArrayList<Triangle>();
		
		//primary triangle 1 (horizontal, no slope)
		Coordinate c = new Coordinate(1, 3, avgSegmentElevation);
		triangles.add(new Triangle(a, b, c));
		
		//primary triangle 2 (horizontal, no slope)
		Coordinate d = new Coordinate(3, 2, avgSegmentElevation);
		triangles.add(new Triangle(a, b, d));
		
		//arbitrary other triangle (this is needed only to influence the 'max elevation' parameter
		//used by the fitness function implementation)
		triangles.add(new Triangle(
				new Coordinate(100, 100, maxTinElevation), 
				new Coordinate(200, 100, maxTinElevation), 
				new Coordinate(150, 150, maxTinElevation)));
		
		TinPolys tinPolys = new TinPolys(DummyFactory.createTinPolys(triangles, "tin_polygons"));
		SondheimSectionFitness fitnessFinder = new SondheimSectionFitness(tinPolys);
		double fitness = fitnessFinder.fitness(a,  b);
		double inverseFitness = 1/fitness;
		double D = segment.getLength() / tinPolys.getMaxEdgeLength();
		double m = inverseFitness / D;
		
		Assert.isTrue(m > 3.6 && m < 3.7, "'measure' expected to be approximately 3.6889.  found "+m);
	}
	
	@Test
	public void testFitness2() throws IOException {
		
		//tests fitness for a case where the mimimum slope of an adjacent triangle is
		//+5 degrees (downward), the average elevation of the segment is 1000 and the
		//max elevation in the TIN is 2000
		
		double maxTinElevation = 2000;
		double avgSegmentElevation = 1000;
		
		//shared edge
		Coordinate a = new Coordinate(0, 0, avgSegmentElevation);
		Coordinate b = new Coordinate(10, 0, avgSegmentElevation);
		LineString segment = SpatialUtils.toLineString(a, b);
		
		//triangles
		List<Triangle> triangles = new ArrayList<Triangle>();
		
		//primary triangle 1 (63 degrees downward slope)
		Coordinate c = new Coordinate(5, 1, avgSegmentElevation-2);
		triangles.add(new Triangle(a, b, c));
		
		//primary triangle 2 (5 degrees downward slope)
		Coordinate d = new Coordinate(5, -1, avgSegmentElevation-0.087488664);
		triangles.add(new Triangle(a, b, d));
		
		//arbitrary other triangle (this is needed only to influence the 'max elevation' parameter
		//used by the fitness function implementation)
		triangles.add(new Triangle(
				new Coordinate(100, 100, maxTinElevation), 
				new Coordinate(200, 100, maxTinElevation), 
				new Coordinate(150, 150, maxTinElevation)));
		
		TinPolys tinPolys = new TinPolys(DummyFactory.createTinPolys(triangles, "tin_polygons"));
		SondheimSectionFitness fitnessFinder = new SondheimSectionFitness(tinPolys);
		double fitness = fitnessFinder.fitness(a,  b);
		double inverseFitness = 1/fitness;
		double D = segment.getLength() / tinPolys.getMaxEdgeLength();
		double m = inverseFitness / D;
		
		Assert.isTrue(m > 0.63 && m < 0.64, "'measure' expected to be approximately 0.6376.  found "+m);
	}
}
