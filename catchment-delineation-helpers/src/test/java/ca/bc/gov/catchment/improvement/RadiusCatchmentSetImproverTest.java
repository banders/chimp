package ca.bc.gov.catchment.improvement;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.geotools.data.collection.SpatialIndexFeatureCollection;
import org.geotools.data.collection.SpatialIndexFeatureSource;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.feature.SchemaException;
import org.junit.Test;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.util.Assert;

import ca.bc.gov.catchment.fitness.CatchmentValidity;
import ca.bc.gov.catchment.fitness.RidgeFitnessFinder;
import ca.bc.gov.catchment.synthetic.DummyFactory;
import ca.bc.gov.catchment.tin.TinEdges;
import ca.bc.gov.catchments.utils.SaveUtils;
import ca.bc.gov.catchments.utils.SpatialUtils;

public class RadiusCatchmentSetImproverTest {

	private static final boolean SAVE_RESULTS = true;
	private static final String SAVE_DIR = "C:\\Temp\\";
	
	
	@Test
	public void testImproveAll() throws Exception {
		String testName = "test-improve-all";
		String saveFilename = SAVE_DIR+testName+".gpkg";
		File saveFile = new File(saveFilename);
		if (SAVE_RESULTS && saveFile.exists()) {
			saveFile.delete();
		}
		
		SimpleFeatureSource waterFeatures = DummyFactory.createDummyWaterFeatures();
		SimpleFeatureSource catchmentEdges = DummyFactory.createDummyCatchments();
		TinEdges tinEdges = new TinEdges(DummyFactory.createDummyTinEdges());
		SimpleFeatureSource tinPolys = DummyFactory.createDummyTinPolys(tinEdges.getFeatureSource());
		RidgeFitnessFinder fitnessFinder = new RidgeFitnessFinder(tinPolys);
		double radius = 4;
		
		CatchmentSetImprover improver = new ZipperCatchmentSetImprover(
				waterFeatures, 
				tinEdges, 
				catchmentEdges,
				fitnessFinder, 
				radius);	
		
		SimpleFeatureCollection improvedCatchments = improver.improve(catchmentEdges);
		improvedCatchments = SpatialUtils.renameFeatureType(improvedCatchments, "catchment_lines_modified");
		
		CatchmentValidity validityChecker = new CatchmentValidity(waterFeatures);
		boolean isValid = validityChecker.areRoutesValidWrtWater(improvedCatchments);
		Assert.isTrue(isValid, "improved catchments are not valid w.r.t. water");
		
		if (SAVE_RESULTS) {
			try {
				SaveUtils.saveToGeoPackage(saveFilename, tinEdges.getFeatures());
				SaveUtils.saveToGeoPackage(saveFilename, waterFeatures.getFeatures());
				SaveUtils.saveToGeoPackage(saveFilename, catchmentEdges.getFeatures());
				SaveUtils.saveToGeoPackage(saveFilename, improvedCatchments);
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				throw e;
			}
		}
	}
}
