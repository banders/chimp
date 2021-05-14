package ca.bc.gov.catchment.tin;

import java.io.IOException;

import org.geotools.data.simple.SimpleFeatureSource;
import org.junit.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.util.Assert;
import org.opengis.feature.simple.SimpleFeature;

import ca.bc.gov.catchment.synthetic.DummyFactory;

public class TinTest {

	@Test
	public void testGetCoordinateAt() throws IOException {
		SimpleFeatureSource fs = DummyFactory.createDummyTinEdges();
		TinEdges tinEdges = new TinEdges(fs);

		SimpleFeature firstFeat= fs.getFeatures().features().next();
		Geometry firstGeom = (Geometry)firstFeat.getDefaultGeometry();
		Coordinate coordFromTin = firstGeom.getCoordinate();
		Coordinate coord2d = new Coordinate(coordFromTin.x, coordFromTin.y);
		
		Coordinate result = tinEdges.getCoordinateAt(coord2d);
		System.out.println(coord2d.x+","+coord2d.y+","+coord2d.z);
		System.out.println(result.x+","+result.y+","+result.z);
		
		Assert.isTrue(result != null, "expected a non-null result");
		Assert.isTrue(coord2d.x== result.x && coord2d.y == result.y, "expected a non-null result");		
	}
	
}
