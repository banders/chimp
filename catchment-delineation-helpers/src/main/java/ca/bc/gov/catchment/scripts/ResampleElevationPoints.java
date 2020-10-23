package ca.bc.gov.catchment.scripts;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.feature.SchemaException;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.locationtech.jts.geom.Coordinate;

import ca.bc.gov.catchment.algorithms.ElevationEstimator;
import ca.bc.gov.catchment.algorithms.NearestNeighbour;
import ca.bc.gov.catchment.algorithms.RandomPointsBuilder;
import ca.bc.gov.catchment.utils.SpatialUtils;

public class ResampleElevationPoints extends CLItoAlgorithmBridge {

	private static final int K = 5;
	private static final double MIN_SAMPLE_DISTANCE = 1; //in unit of inFeatureSource
	
	public static void main(String[] argv) {
		CLItoAlgorithmBridge transformer = new ResampleElevationPoints();
		
		//add extra command-line options to specify a second data set
		Options customOptions = new Options();
		customOptions.addOption(new Option("resolution", true, "target resolution in metres"));
		
		System.out.println("Starting");
		transformer.start(argv, customOptions);
		
	}

	@Override
	public SimpleFeatureCollection transformBatch(SimpleFeatureSource inFeatureSource) throws IOException {
		
		System.out.println("transformBatch");

		//load values of extra command line options
		int resolution = Integer.parseInt(getOptionValue("resolution"));		
		
		ReferencedEnvelope inBounds = inFeatureSource.getBounds();
		ElevationEstimator elevationEstimator = new ElevationEstimator();
		
		//create random coords within the bounds of the input set
		RandomPointsBuilder pointBuilder = new RandomPointsBuilder(inBounds, resolution);
		List<Coordinate> randomCoords = pointBuilder.getPoints();
		List<Coordinate> outCoords = new ArrayList<Coordinate>();
		
		double initialSampleDistance = Math.max(MIN_SAMPLE_DISTANCE, resolution/10.0);
		NearestNeighbour nn = new NearestNeighbour(inFeatureSource, initialSampleDistance);
		for (Coordinate coord : randomCoords) {			
			
			try {
				List<Coordinate> nearbyCoords = nn.getKNearestCoords(coord, K);
				double elevation = elevationEstimator.estimateElevationFromNearbyPoints(coord, nearbyCoords);
				coord.setZ(elevation);
				outCoords.add(coord);
				System.out.println(coord);
			} catch (IllegalArgumentException e) {				
				//randomCoords.remove(coord);
				System.out.println("skipping");
			}			
			
		}		
		
		SimpleFeatureCollection outFeatures = SpatialUtils.coordListToSimpleFeatureCollection(outCoords, inFeatureSource.getSchema());
		
		
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
