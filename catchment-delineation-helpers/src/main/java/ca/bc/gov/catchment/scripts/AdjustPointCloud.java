package ca.bc.gov.catchment.scripts;

import java.io.IOException;

import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.feature.DefaultFeatureCollection;
import org.geotools.feature.SchemaException;
import org.geotools.geometry.jts.JTS;
import org.locationtech.jts.geom.Geometry;
import org.opengis.feature.simple.SimpleFeature;

import ca.bc.gov.catchment.algorithms.ElevationAdjuster;
import ca.bc.gov.catchment.algorithms.EliminationSmoother;
import ca.bc.gov.catchment.algorithms.MidpointSmoother;
import ca.bc.gov.catchment.algorithms.Smoother;
import ca.bc.gov.catchment.tin.TinPolys;
import ca.bc.gov.catchment.utils.SpatialUtils;
import ca.bc.gov.catchment.water.Water;

public class AdjustPointCloud extends CLItoAlgorithmBridge {
	
	public static void main(String[] argv) {
		CLItoAlgorithmBridge transformer = new AdjustPointCloud();
		
		Options customOptions = new Options();
		customOptions.addOption(new Option("waterFile", true, "geopackage containing water features"));
		customOptions.addOption(new Option("waterTable", true, "water features table name"));
		
		transformer.start(argv, customOptions);
		
	}

	@Override
	public SimpleFeatureCollection transformBatch(SimpleFeatureSource inFeatureSource) throws IOException {
				
		SimpleFeatureCollection inFeatures = applyDefaultFilter(inFeatureSource);
		
		//load values of extra command line options
		String waterFilename = this.getOptionValue("waterFile");
		String waterTable = this.getOptionValue("waterTable");
		SimpleFeatureSource waterFs = CLItoAlgorithmBridge.loadFeautreSource(
				waterFilename, 
				waterTable
				);
		Water water = new Water(waterFs);
		ElevationAdjuster elevationAdjuster = new ElevationAdjuster(water);
		
		DefaultFeatureCollection outFeatures = new DefaultFeatureCollection();
		SimpleFeatureIterator it = inFeatures.features();
		while(it.hasNext()) {
			SimpleFeature inFeature = it.next();
			Geometry inGeometry = (Geometry)inFeature.getDefaultGeometry();
			Geometry outGeometry = elevationAdjuster.adjustZ(inGeometry);
			SimpleFeature outFeature = SpatialUtils.geomToFeature(outGeometry, inFeature.getFeatureType(), inFeature.getID());
			outFeatures.add(outFeature);
		}
		it.close();
		
		try {
			return SpatialUtils.renameFeatureType(outFeatures, getOutTable());
		} catch(SchemaException e) {
			throw new RuntimeException("Unable to set table name '"+getOutTable()+"' for output features");
		}
		
	}
	
	
}
