package ca.bc.gov.catchment.algorithms;

import java.io.IOException;
import java.util.List;

import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.factory.CommonFactoryFinder;
import org.geotools.feature.DefaultFeatureCollection;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.locationtech.jts.geom.Geometry;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.filter.Filter;
import org.opengis.filter.FilterFactory2;

import ca.bc.gov.catchment.utils.SpatialUtils;

/**
 * Finds the the highest point within each cell of a grid defined by a given extend and resolution
 * @author Brock Anderson
 */

public class GridMaximaFinder {

	private List<ReferencedEnvelope> gridCells;
	private SimpleFeatureSource elevationPoints;
	private FilterFactory2 filterFactory;
	
	public GridMaximaFinder(ReferencedEnvelope extent, double resolution, SimpleFeatureSource elevationPoints) {
		GridBuilder gridBuilder = new GridBuilder(extent, resolution);
		this.gridCells = gridBuilder.getGridCells();
		this.filterFactory = CommonFactoryFinder.getFilterFactory2();
		this.elevationPoints = elevationPoints;
	}
	
	/**
	 * get a collection of features, each of which is a local maxima within the specified grid
	 * @return
	 * @throws IOException
	 */
	public SimpleFeatureCollection getMaximaPoints() throws IOException {		
		DefaultFeatureCollection results = new DefaultFeatureCollection();
		
		String geometryPropertyName = elevationPoints.getSchema().getGeometryDescriptor().getLocalName();
		
		for(ReferencedEnvelope cell : this.gridCells) {
			Filter bboxFilter = filterFactory.bbox(
					filterFactory.property(geometryPropertyName),
					cell
					);
			SimpleFeatureCollection featuresInCell = elevationPoints.getFeatures(bboxFilter);
			
			SimpleFeature highest = getHighest(featuresInCell);
			results.add(highest);			
		}
		
		return results;
	}
	
	/**
	 * Gets the feature with the highest Z.  If multiple features share the same high value, only one of 
	 * of those is returned (arbitrarily chosen)
	 * @param fc
	 * @return
	 */
	private SimpleFeature getHighest(SimpleFeatureCollection fc) {
		SimpleFeature highest = null;
		double highestZ = Double.NaN;
		SimpleFeatureIterator it = fc.features();
		while(it.hasNext()) {
			SimpleFeature f = it.next();
			Geometry g = (Geometry)f.getDefaultGeometry();
			double z = SpatialUtils.getHighestZ(g);
			if (highest == null || z > highestZ) {
				highest = f;
				highestZ = z;
			}
		}
		it.close();
		return highest;
	}
}
