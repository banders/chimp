package ca.bc.gov.catchment.scripts;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.feature.SchemaException;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.locationtech.jts.geom.Coordinate;

import ca.bc.gov.catchment.algorithms.ElevationEstimator;
import ca.bc.gov.catchment.algorithms.GridMaximaFinder;
import ca.bc.gov.catchment.algorithms.LocalMaximaFinder;
import ca.bc.gov.catchment.algorithms.NearestNeighbour;
import ca.bc.gov.catchment.algorithms.RandomPointsBuilder;
import ca.bc.gov.catchment.utils.SpatialUtils;

public class FindLocalMaxima extends CLItoAlgorithmBridge {

	public static void main(String[] argv) {
		CLItoAlgorithmBridge transformer = new FindLocalMaxima();
		
		//add extra command-line options to specify a second data set
		Options customOptions = new Options();
		customOptions.addOption(new Option("resolution", true, "target resolution in metres"));
		
		System.out.println("Starting");
		transformer.start(argv, customOptions);
		
	}

	@Override
	public SimpleFeatureCollection transformBatch(SimpleFeatureSource inFeatureSource) throws IOException {

		//load values of extra command line options
		double resolution = Double.parseDouble(getOptionValue("resolution"));	
		
		ReferencedEnvelope extent = inFeatureSource.getBounds();
		
		LocalMaximaFinder localMaxFinder = new LocalMaximaFinder(inFeatureSource, resolution);		
		SimpleFeatureCollection outFeatures = localMaxFinder.getMaximaPoints();
		
		try {
			outFeatures = SpatialUtils.renameFeatureType(outFeatures, getOutTable());
		} 
		catch (SchemaException e) {
			return null;
		}
		
		return outFeatures;

	}

	public void streamingTransform(SimpleFeatureCollection inFeatures) {
		// TODO Auto-generated method stub
		
	}
	
}
