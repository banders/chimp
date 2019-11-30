package ca.bc.gov.catchment.algorithms;

import java.io.IOException;

import org.geotools.data.simple.SimpleFeatureSource;
import org.junit.Test;

import ca.bc.gov.catchment.synthetic.DummyFactory;

public class NearestNeighbour3DMakerTest {


	@Test
	public void testMake3DCopy() throws IOException {
		int kNeighbours = 3;
		SimpleFeatureSource pointCloud = DummyFactory.createDummyPointCloud3d();
		NearestNeighbour3DMaker threeDMaker = new NearestNeighbour3DMaker(pointCloud, kNeighbours);
	}
}
