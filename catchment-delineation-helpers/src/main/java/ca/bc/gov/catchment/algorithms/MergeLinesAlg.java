package ca.bc.gov.catchment.algorithms;

import java.util.Collection;

import org.geotools.data.DataUtilities;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.feature.SchemaException;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.operation.linemerge.LineMerger;
import org.opengis.feature.simple.SimpleFeatureType;

import ca.bc.gov.catchments.utils.SpatialUtils;

public class MergeLinesAlg {

	private SimpleFeatureCollection result;
	
	public MergeLinesAlg(SimpleFeatureCollection fc,  String outTable, int outSrid) {
		result = process(fc, outTable, outSrid);
	}
	
	private SimpleFeatureCollection process(SimpleFeatureCollection inFeatures,  String outTable, int outSrid) {
		Collection<Geometry> inGeometries = SpatialUtils.simpleFeatureCollectionToGeomCollection(inFeatures);
		
		//run the JTS line merge algorithm
		LineMerger lm = new LineMerger();
		lm.add(inGeometries);
		Collection<Geometry> merged = (Collection<Geometry>)lm.getMergedLineStrings();
		
		//convert output geometries back into features, then add them to a SimpleFeatureCollection
		SimpleFeatureType outFeatureType = null;
		try {
			outFeatureType = DataUtilities.createType(outTable, 
					"geometry:LineString:srid="+outSrid); 
		} catch (SchemaException e) {
			System.out.println("Unable to create feature type: "+outTable);
			e.printStackTrace();
			System.exit(1);
		}
		
		SimpleFeatureCollection outFeatureCollection = SpatialUtils.geomCollectionToSimpleFeatureCollection(merged, outFeatureType);
		return outFeatureCollection;	
	}
	
	public SimpleFeatureCollection getResult() {
		return this.result;
	}
	
}
