package ca.bc.gov.catchment.algorithms;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.geotools.data.DataUtilities;
import org.geotools.data.collection.CollectionFeatureSource;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.feature.SchemaException;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.geometry.jts.JTSFactoryFinder;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.junit.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.util.Assert;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;

import ca.bc.gov.catchment.synthetic.TestHelper;
import ca.bc.gov.catchment.utils.SpatialUtils;

public class GridMaximaFinderTest {

	private SimpleFeatureSource makeTestElevationData() {
		List<Geometry> points = new ArrayList<Geometry>();
		
		try {
			points.add(TestHelper.geometryFromWkt("PointZ (1675716.87140765064395964 501588.40726083074696362 1148)"));
			points.add(TestHelper.geometryFromWkt("PointZ (1675719.17832200205884874 501572.38022207375615835 1161)"));
			points.add(TestHelper.geometryFromWkt("PointZ (1675735.47584640677087009 501564.99623497616266832 1158)"));
			points.add(TestHelper.geometryFromWkt("PointZ (1675740.54152039904147387 501595.37228671630145982 1140)"));
			points.add(TestHelper.geometryFromWkt("PointZ (1675755.77104858378879726 501590.02765810955315828 1151)"));
			points.add(TestHelper.geometryFromWkt("PointZ (1675784.10549222840927541 501606.27812392730265856 1134)"));
			points.add(TestHelper.geometryFromWkt("PointZ (1675732.43165448075160384 501617.08325403521303087 1135)"));
			points.add(TestHelper.geometryFromWkt("PointZ (1675716.0972041713539511 501625.17001742799766362 1119)"));
			points.add(TestHelper.geometryFromWkt("PointZ (1675699.87238071113824844 501639.57118442817591131 1116)"));
			points.add(TestHelper.geometryFromWkt("PointZ (1675777.00450045056641102 501563.19284317991696298 1165)"));
		} catch (ParseException e) {
			throw new IllegalStateException("Post-condition exception: Unable to create points.  WKT parse failed.");
		}		
	
		SimpleFeatureType outFeatureType = null;
		try {
			outFeatureType = DataUtilities.createType("points", "geometry:Point:srid=3005");
		} catch (SchemaException e1) {
			throw new IllegalStateException("Unable to create feature type for points");
		}
				
		SimpleFeatureCollection fc = SpatialUtils.geomCollectionToSimpleFeatureCollection(points, outFeatureType);
		SimpleFeatureSource fs = new CollectionFeatureSource(fc);
		
		
		return fs;
	}
	
	@Test
	public void testGetMaximaPoints() throws IOException {
		
		SimpleFeatureSource elevationPoints = makeTestElevationData();
		ReferencedEnvelope extent = elevationPoints.getBounds();
		double resolution = Math.max(extent.getHeight(), extent.getWidth());
		
		GridMaximaFinder finder = new GridMaximaFinder(extent, resolution, elevationPoints);
		SimpleFeatureCollection localMaxima = finder.getMaximaPoints();
		
		Assert.isTrue(localMaxima.size() == 1, "expected to find one local maxima");
		
		SimpleFeatureIterator it = localMaxima.features();
		SimpleFeature firstFeature = null;
		if (it.hasNext()) {
			firstFeature = it.next();
		}
		it.close();
		Point firstPoint = (Point)firstFeature.getDefaultGeometry();
		double localMaximum = firstPoint.getCoordinate().getZ();
		
		Assert.isTrue(localMaximum == 1165, "expected to local maximum to be 1165. found "+localMaximum);
	}
}



