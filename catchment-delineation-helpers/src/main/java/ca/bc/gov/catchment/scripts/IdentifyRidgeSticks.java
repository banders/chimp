package ca.bc.gov.catchment.scripts;

import java.io.IOException;

import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureSource;

import ca.bc.gov.catchment.algorithms.IdentifyAdjacentSlopesAlg;
import ca.bc.gov.catchment.tin.TinPolys;

/**
 * Given a tin (both TinPolys and TinEdges), identifies all tin edges which are ridges
 * i.e. those that slope downward on either side.
 * 
 * Run as a command line script:
 * 	TODO: example here
 * 
 * @author Brock
 *
 */
public class IdentifyRidgeSticks extends CLItoAlgorithmBridge {

	private static final double DEFAULT_MAX_ADJACENT_SLOPE_FOR_RIDGE_STICKS = 0;
	
	public static void main(String[] argv) {
		CLItoAlgorithmBridge transformer = new IdentifyRidgeSticks();
		
		//add extra command-line options to specify a second data set
		Options customOptions = new Options();
		customOptions.addOption(new Option("tinPolysFilename", true, "geopackage file with Tin Polygons"));
		customOptions.addOption(new Option("tinPolysTable", true, "target table in 'tinPolysFilename'"));
		customOptions.addOption(new Option("maxSlope", true, "only keep sticks if both adjacent slopes are less than the given value (degrees, negative downward). suggested values <= 0. default is 0."));
		
		transformer.start(argv, customOptions);
		
	}

	@Override
	public SimpleFeatureCollection transformBatch(SimpleFeatureSource inFeatureSource) throws IOException {
		
		double maxSlope = getOptionValue("maxSlope") != null ? Double.parseDouble(getOptionValue("maxSlope")) : DEFAULT_MAX_ADJACENT_SLOPE_FOR_RIDGE_STICKS;	
		
		//get tin polys from the input parameters
		String tinPolysFilename = getOptionValue("tinPolysFilename");
		String tinPolysTable = getOptionValue("tinPolysTable");
		SimpleFeatureSource tinPolysFs = CLItoAlgorithmBridge.loadFeautreSource(
				tinPolysFilename, 
				tinPolysTable
				);
		TinPolys tinPolys = new TinPolys(tinPolysFs);
		
		//get tin edges from the input parameters
		SimpleFeatureSource tinEdges = inFeatureSource;
		
		//extract a list of TIN edges which are ridges (slope downward on either side).
		//the resulting TIN edges are called "ridge sticks"		
		IdentifyAdjacentSlopesAlg ridgeStickExtractor = new IdentifyAdjacentSlopesAlg(tinPolys);
		
		SimpleFeatureCollection ridgeSticksFc = null;
		try {
			ridgeSticksFc = ridgeStickExtractor.process(tinEdges.getFeatures(), 
					getOutTable(), 
					maxSlope);
		} catch (Exception e) {
			System.out.println("unable to extract ridge sticks from the TIN");
			e.printStackTrace();
		}
		return ridgeSticksFc;
		
	}
	
}
