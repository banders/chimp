package ca.bc.gov.catchment.scripts;

import java.io.IOException;

import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureSource;

import ca.bc.gov.catchment.algorithms.IdentifyAdjacentSlopesAlg;
import ca.bc.gov.catchment.tin.TinPolys;

public class ComputeAdjacentSlopes extends CLItoAlgorithmBridge {

	private static TinPolys tinPolys;
	
	public static void main(String[] argv) {
		CLItoAlgorithmBridge transformer = new ComputeAdjacentSlopes();
		
		//add extra command-line options to specify a second data set
		Options customOptions = new Options();
		customOptions.addOption(new Option("tinPolysFilename", true, "geopackage file with Tin Polygons"));
		customOptions.addOption(new Option("tinPolysTable", true, "target table in 'tinPolysFilename'"));
		
		transformer.start(argv, customOptions);
		
	}

	@Override
	public SimpleFeatureCollection transformBatch(SimpleFeatureCollection inTinEdges) {

		//load values of extra command line options
		String tinPolysFilename = getOptionValue("tinPolysFilename");
		String tinPolysTable = getOptionValue("tinPolysTable");
		SimpleFeatureSource tinPolysFs = CLItoAlgorithmBridge.loadFeautreSource(
				tinPolysFilename, 
				tinPolysTable
				);
		tinPolys = new TinPolys(tinPolysFs);
		
		IdentifyAdjacentSlopesAlg alg = new IdentifyAdjacentSlopesAlg(tinPolys);
		try {
			return alg.process(inTinEdges, getOutTable());
		} catch (IOException e) {
			e.printStackTrace();
			System.out.println("process failed");
		}
		return null;
	}
	
}
