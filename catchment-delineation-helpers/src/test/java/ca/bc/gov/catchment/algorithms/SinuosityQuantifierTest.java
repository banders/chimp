package ca.bc.gov.catchment.algorithms;

import org.junit.Test;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.util.Assert;

import ca.bc.gov.catchment.synthetic.TestHelper;

public class SinuosityQuantifierTest {

	@Test
	public void testGetSinuosity1() throws ParseException {
		LineString g = (LineString)TestHelper.geometryFromWkt("LINESTRING (0 0, 1 1)");
		SinuosityQuantifier sq = new SinuosityQuantifier();
		double s = sq.getSinuosity(g);
		
		Assert.isTrue(s == 1, "Expected sinuosity of 1.  Found "+s);
	}
	
	@Test
	public void testGetSinuosity2() throws ParseException {
		LineString g1 = (LineString)TestHelper.geometryFromWkt("LINESTRING (0 0, 1 1, 3 2)");
		LineString g2 = (LineString)TestHelper.geometryFromWkt("LINESTRING (0 0, 1 1, 4 2)");
		
		SinuosityQuantifier sq = new SinuosityQuantifier();
		double s1 = sq.getSinuosity(g1);
		double s2 = sq.getSinuosity(g2);
		
		Assert.isTrue(s1 < s2, "Expected sinuosity of s1 ("+s1+") to be less than that of s2 ("+s2+")");
		
	}
	
	@Test
	public void testGetSinuosity3() throws ParseException {
		LineString g1 = (LineString)TestHelper.geometryFromWkt("LINESTRING (0 0, 1 1, 3 2, 4 4, 6 9, 5 8)");
		LineString g2 = (LineString)TestHelper.geometryFromWkt("LINESTRING (0 0, 1 1, 4 2)");
		
		SinuosityQuantifier sq = new SinuosityQuantifier();
		double s1 = sq.getSinuosity(g1);
		double s2 = sq.getSinuosity(g2);
		
		Assert.isTrue(s1 > s2, "Expected sinuosity of s1 ("+s1+") to be greater than that of s2 ("+s2+")");
		
	}
}
