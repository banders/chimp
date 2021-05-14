package ca.bc.gov.catchment.algorithms;

import java.util.ArrayList;
import java.util.List;

import org.geotools.geometry.jts.ReferencedEnvelope;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.math.MathUtil;

public class GridBuilder {

	private ReferencedEnvelope extent;
	private double resolution;
	private double gutterFraction; //0-1, the fraction of gutter (padding) around each cell
	
	public GridBuilder(ReferencedEnvelope extent, double resolution) {
		this(extent, resolution, 0);
	}

	public GridBuilder(ReferencedEnvelope extent, double resolution, double gutterFraction) {
		this.extent = extent;
		this.resolution = resolution;
		this.gutterFraction = gutterFraction;
	}
	
	public List<ReferencedEnvelope> getGridCells() {
		List<ReferencedEnvelope> results = new ArrayList<ReferencedEnvelope>();
		
		double gridDX = resolution;
	    double gridDY = resolution;

	    //Note: this will skip any partial cell at the end of a row or a colummn.
	    double nCellsX = extent.getWidth() / gridDX;
	    double nCellsY = extent.getHeight() / gridDY;
	    
	    double gutterFrac = MathUtil.clamp(gutterFraction, 0.0, 1.0);
	    double gutterOffsetX = gridDX * gutterFrac/2;
	    double gutterOffsetY = gridDY * gutterFrac/2;
	    double cellFrac = 1.0 - gutterFrac;
	    double cellDX = cellFrac * gridDX;
	    double cellDY = cellFrac * gridDY;
	    
	    for (int x = 0; x < nCellsX; x++) {
	      for (int y = 0; y < nCellsY; y++) {
	      	double orgX = extent.getMinX() + x * gridDX + gutterOffsetX;
	      	double orgY = extent.getMinY() + y * gridDY + gutterOffsetY;
	        ReferencedEnvelope cellEnvelope = new ReferencedEnvelope(
	        		new Envelope(orgX, orgX + cellDX, orgY, orgY + cellDY), 
	        		extent.getCoordinateReferenceSystem());
	        results.add(cellEnvelope);
	        
	      }
	    }
	    return results;
	}
	

	
}
