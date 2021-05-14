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
import ca.bc.gov.catchment.algorithms.NearestNeighbour;
import ca.bc.gov.catchment.algorithms.RandomPointsBuilder;
import ca.bc.gov.catchment.utils.SpatialUtils;

public class ResampleElevationPoints extends CLItoAlgorithmBridge {

	private static final int K = 10;
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

		//load values of extra command line options
		double targetResolution = Double.parseDouble(getOptionValue("resolution"));		
		
		ReferencedEnvelope inBounds = inFeatureSource.getBounds();
		ElevationEstimator elevationEstimator = new ElevationEstimator();
		
		//create random coords within the bounds of the input set
		RandomPointsBuilder pointBuilder = new RandomPointsBuilder(inBounds, targetResolution);
		List<Coordinate> randomCoords = pointBuilder.getPoints();
		List<Coordinate> outCoords = new ArrayList<Coordinate>();
		
		double initialSampleDistance = Math.max(MIN_SAMPLE_DISTANCE, targetResolution / 2.0); //this should really be calculated from the initial data resolution.  The magic number 5 won't always be suitable. 
		NearestNeighbour nn = new NearestNeighbour(inFeatureSource, initialSampleDistance);
		
		//metrics used for progress reporting
		Date lastPing = new Date();
		int numSuccess = 0;
		int totalNumProcessed = 0;
		
		for (Coordinate coord : randomCoords) {			
			
			try {
				List<Coordinate> nearbyCoords = nn.getKNearestCoords(coord, K);
				double elevation = elevationEstimator.estimateElevationFromNearbyPoints(coord, nearbyCoords);
				coord.setZ(elevation);
				outCoords.add(coord);
				//System.out.println(coord);
				numSuccess++;
			} catch (IllegalArgumentException e) {				
				//randomCoords.remove(coord);
				//System.out.println("skipping");
			}	
			
			totalNumProcessed++;
			
			//progress reporting
			Date now = new Date();
			if (now.getTime() - lastPing.getTime() > 20000) {	
				
				int percentComplete = (int)Math.floor(totalNumProcessed * 100.0f / randomCoords.size());
				System.out.println("processed "+ totalNumProcessed + " of " +randomCoords.size() +" ("+percentComplete+"%). "+numSuccess+" successes");
				lastPing = new Date();
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
