package ca.bc.gov.catchment.scripts;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.geotools.data.DataStore;
import org.geotools.data.DataStoreFinder;
import org.geotools.data.collection.SpatialIndexFeatureCollection;
import org.geotools.data.collection.SpatialIndexFeatureSource;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.factory.CommonFactoryFinder;
import org.geotools.factory.Hints;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.referencing.CRS;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.filter.Filter;
import org.opengis.filter.FilterFactory2;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

import ca.bc.gov.catchment.utils.SaveUtils;

/**
 * Abstract class representing a process that will apply an input-to-output transformation
 * algorithm (defined by the subclass) on a geospatial data set provided in a geopackage file.
 * 
 * It is intended to be subclassed by command line scripts (i.e. classes with "main(String[] argv)" 
 * methods). The primary aim of this class is to provide a simple means to convert an algorithm into a command 
 * line script which launches the algorithm. 
 * 
 * The script assumes some default command line options:
 *  -i <input filename>
 *  -inTable <table within input geopackage file that contains the features to process>
 *  -o <output filename>
 *  -outTable <table to be created in the output geopackage file>
 *  -bbox <bbox to process> (optional)
 *  -bboxcrs <srid of the bbox> (required only if bbox is specified>
 *  
 * Subclasses may specify additional options that can be accessed and passed to the underlying algorithm
 * 
 * Subclass Usage:
<pre>
 public class MyTransformer extends BatchTransformer {

	public static void main(String[] argv) {
		BatchTransformer transformer = new MyTransformer();
		transformer.start(argv);
	}

	@Override
	public SimpleFeatureCollection transform(SimpleFeatureCollection inFeatures) {
		// TODO apply logic to transform the input into the desired output
		return null;
	}
	
 }
</pre>
 *  
 * @author Brock Anderson (brock@bandersgeo.ca)
 *
 */
public abstract class CLItoAlgorithmBridge implements BatchTransformer, StreamingTransformer {

	private static final String OPTION_IN_FILENAME = "i";
	private static final String OPTION_IN_TABLE = "inTable";
	private static final String OPTION_OUT_FILENAME = "o";
	private static final String OPTION_OUT_TABLE = "outTable";
	private static final String OPTION_STREAM = "stream";
	
	private Options allOptions;
	private CommandLine commandLine;
	private int baseMessageIndent;
	
	//values for options in the default set
	private String inFilename; 
	private String inTable;
	private String outFilename;
	private String outTable;
	private ReferencedEnvelope boundsToProcess;
	private int bboxSrid;
	private boolean isBatch; //false mean isStreaming
	
	//properties derived from the input data set
	private SimpleFeatureSource inFeatureSource;
	
	public CLItoAlgorithmBridge() {
	}
	
	public void start(String argv[]) {
		baseMessageIndent = 0;
		start(argv, null);
	}
	
	public void start(String argv[], Options customOptions) {
		message("Running "+this.getClass().getSimpleName(), baseMessageIndent++);
		allOptions = combineAllOptions(customOptions);
		
		//parse the command line into the defined options
		System.out.println(" Parsing command line options...");
		commandLine = null;
		try {
			commandLine = parseOptions(argv, allOptions);
		}
		catch (ParseException e) {
			HelpFormatter formatter = new HelpFormatter();
			formatter.printHelp( this.getClass().getSimpleName(), allOptions );
		}
		loadDefaultOptionValues();
			
		System.out.println("Loading feature source");
		
		//load spatial data from input file
		inFeatureSource = loadFeautreSource(inFilename, inTable);
		
				
		//transform the input into the output
		transform(inFeatureSource);
		
	}
	
	private Options combineAllOptions(Options customOptions) {
		//define default options
		Options allOptions = new Options();
		allOptions.addOption(OPTION_IN_FILENAME, true, "Input GeoPackage file");
		allOptions.addOption(OPTION_OUT_FILENAME, true, "Output GeoPackage file");
		allOptions.addOption(OPTION_IN_TABLE, true, "input table name");
		allOptions.addOption(OPTION_OUT_TABLE, true, "output table name");
		allOptions.addOption("bbox", true, "Bounding box representing area to process (format: 'xmin,ymin,xmax,ymax')");
		allOptions.addOption("bboxcrs", true, "CRS of the bounding box.  e.g. 'EPSG:3005' or 'EPSG:4326'");
		allOptions.addOption("bboxcrs", true, "CRS of the bounding box.  e.g. 'EPSG:3005' or 'EPSG:4326'");
		allOptions.addOption(OPTION_STREAM, false, "flag indicating whether to stream results to output.  (default is false: use batch mode)");
		
		//add custom options
		if (customOptions != null) {
			Collection<Option> customOptionCollection = customOptions.getOptions();
			for (Option option : customOptionCollection) {
				allOptions.addOption(option);
			}
		}
		
		return allOptions;
	}
	
	private CommandLine parseOptions(String argv[], Options options) throws ParseException {
		CommandLineParser parser = new DefaultParser();
		CommandLine cmd = parser.parse( options, argv);	
		for (Option option : cmd.getOptions()) {
			String name = option.getOpt();
			String value = cmd.getOptionValue(name);
			System.out.println("  -"+name+" "+value);
		}
		return cmd;
	}
	
	public String getOptionValue(String name) {
		return commandLine.getOptionValue(name);
	}
	
	public String getOptionValue(String name, String defaultVal) {
		return commandLine.getOptionValue(name, defaultVal);
	}
	
	/**
	 * loads values for options in the default set into member variables
	 * so they can be conveniently accessed by the member functions of this
	 * class
	 */
	private void loadDefaultOptionValues() {
		inFilename = commandLine.getOptionValue(OPTION_IN_FILENAME);
		inTable = commandLine.getOptionValue(OPTION_IN_TABLE);
		outFilename = commandLine.getOptionValue(OPTION_OUT_FILENAME); 
		outTable = commandLine.getOptionValue(OPTION_OUT_TABLE); 
		boolean isStream = commandLine.hasOption(OPTION_STREAM);
		isBatch = !isStream;
		
		String bboxStr = commandLine.getOptionValue("bbox");
		String bboxCrs = commandLine.getOptionValue("bboxcrs");
		
		if(bboxStr != null) {
			String[] pieces = bboxStr.split(",");
			double minX = Double.parseDouble(pieces[0]);
			double minY = Double.parseDouble(pieces[1]);
			double maxX = Double.parseDouble(pieces[2]);
			double maxY = Double.parseDouble(pieces[3]);
			boundsToProcess = new ReferencedEnvelope(minX,maxX,minY,maxY, null);
		}
		if (bboxCrs != null) {
			if (bboxCrs.startsWith("EPSG:")) {
				String srid = bboxCrs.substring(5);
				bboxSrid = Integer.parseInt(srid);
			}
			else {
				message("Unknown bboxcrs: "+bboxCrs);
				System.exit(1);
			}
		}
	}
	
	/**
	 * Loads the input feature collection defined in these command line options:
	 *  "i": (input geopackage file)
	 *  "inTable": (table name within the input geopackage file)
	 *  Also limits to features within the given envelope if these options are
	 *  specified:
	 *   "bbox"
	 *   "bboxcrs" 
	 * @return a SimpleFeatureCollection of features from the input which match the filter
	 */
	public static SimpleFeatureSource loadFeautreSource(String fileName, String tableName) {
		
		//load input
		Map<String, String> inputDatastoreParams = new HashMap<String, String>();
		inputDatastoreParams.put("dbtype", "geopkg");
		inputDatastoreParams.put("database", fileName);
		
		DataStore datastore = null;
		try {
			datastore = DataStoreFinder.getDataStore(inputDatastoreParams);
		} catch (IOException e) {
			System.out.println("Unable to open input file: "+fileName);
			e.printStackTrace();
			System.exit(1);
		}
		
		if (datastore == null) {
			System.out.println("Unable to open input datastore");
			System.exit(1);
		}
				
		SimpleFeatureSource featureSource = null;
		try {
			featureSource = datastore.getFeatureSource(tableName);
		} catch (IOException e1) {
			System.out.println("Unable to get in feature source: "+tableName);
			e1.printStackTrace();
			System.exit(1);
		}
		
		/*
		try {
			SpatialIndexFeatureCollection fastInFeatureCollection = new SpatialIndexFeatureCollection(featureSource.getFeatures());
			featureSource = new SpatialIndexFeatureSource(fastInFeatureCollection);
		}
		catch (IOException e) {
			System.out.println("Unable to index in feature source");
			e.printStackTrace();
			System.exit(1);
		}
		*/

		return featureSource;
	}
	
	/**
	 * Applies a bbox filter to the given SimpleFeatureSource, and returns a feature collection
	 * containing features within that bbox
	 * @param inFeatureSource
	 * @return
	 * @throws IOException
	 */
	protected SimpleFeatureCollection applyDefaultFilter(SimpleFeatureSource inFeatureSource) throws IOException {
		
		String geometryPropertyName = inFeatureSource.getSchema().getGeometryDescriptor().getLocalName();;
		
		//Hints filterHints = new Hints( Hints.FEATURE_2D, true ); // force 2D queries
		FilterFactory2 filterFactory = CommonFactoryFinder.getFilterFactory2();
		SimpleFeatureCollection inFeatureCollection = null;
		if (boundsToProcess != null) {
			message("Filtering features in bbox...");
			Filter bboxFilter = filterFactory.bbox(filterFactory.property(geometryPropertyName), boundsToProcess);
			inFeatureCollection = inFeatureSource.getFeatures(bboxFilter);
		}
		else {
			inFeatureCollection = inFeatureSource.getFeatures();
		}
		return inFeatureCollection;
	}
	
	/**
	 * Applies either a batch transform or a streaming transform, depending on which option is selected
	 * @param inFeatures
	 */
	private void transform(SimpleFeatureSource inFeatureSource) {
		if (isBatch) {
			message("Applying batch transform...");
			SimpleFeatureCollection outFeatures = null;
			try {
				outFeatures = transformBatch(inFeatureSource);
			} catch (IOException e) {
				System.out.println("Unable to transform features");
				e.printStackTrace();
			}
	
			//save result to output file
			message("Saving...");
			try {
				SaveUtils.saveToGeoPackage(outFilename, outFeatures);
			} catch (IOException e) {
				System.out.println("Unable to save result to: "+outFilename+" (table '"+outTable+"')");
				e.printStackTrace();
			}
		}
		else {
			message("Applying streaming transform...");
			streamingTransform(inFeatureSource);
		}
	}
	
	// BatchTransformer Interface methods
	//-------------------------------------------------------------------------
	
	/**
	 * Transforms an input feature collection into an output feature collection.
	 * All features are processed before the the results are returned.
	 * Classes implementing this method will apply their own algorithm to perform
	 * the transformation.
	 * @param inFeatures
	 */
	public SimpleFeatureCollection transformBatch(SimpleFeatureSource inFeatureSource) throws IOException {
		throw new UnsupportedOperationException("Batch transform is not supported");
	}

	// StreamingTransformer Interface methods
	//-------------------------------------------------------------------------	
	
	/**
	 * Transforms an input feature collection into an output feature collection.
	 * Each feature is streamed to the output as it is processed.
	 * Classes implementing this method will apply their own algorithm to perform
	 * the transformation.  They should call onFeatureReady() on each feature
	 * as it is ready.
	 * @param inFeatures
	 */
	public void streamingTransform(SimpleFeatureSource inFeatureSource) {
		throw new UnsupportedOperationException("Streaming transform is not supported");
	}
	
	/**
	 * To be called by implementations of streamTransform() to indicate that a feature
	 * is ready to append to the output
	 */
	public void onFeatureReady(SimpleFeature feature) {
		throw new UnsupportedOperationException("onFeatureReady not yet implemented  TODO: implement as append to GeoPackage.");
	}
	
	
	// Helpers
	//-------------------------------------------------------------------------
	
	public void message(String msg) {
		this.message(msg, 0);
	}
	
	public void message(String msg, int indent) {
		indent += baseMessageIndent;
		String indentString = new String(new char[indent]).replace("\0", " ");
		System.out.println(indentString+msg);
	}
	
	// Getters and Setters
	//-------------------------------------------------------------------------
	
	public CommandLine getCommandLine() {
		return this.commandLine;
	}
	
	public String getOutTable() {
		return this.outTable;
	}
	
	public int getInSrid() {
		SimpleFeatureType featureType = inFeatureSource.getSchema();
		CoordinateReferenceSystem crs = featureType.getCoordinateReferenceSystem();
		int srid = -1;
		try {
			srid = CRS.lookupEpsgCode(crs, true);
		} catch (FactoryException e1) {
			System.out.println("Unable to lookup SRID for feature type "+featureType);
		}
		return srid;
	}

}
