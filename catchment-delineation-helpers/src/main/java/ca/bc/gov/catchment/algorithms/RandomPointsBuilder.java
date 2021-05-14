package ca.bc.gov.catchment.algorithms;

import java.util.ArrayList;
import java.util.List;

import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.feature.DefaultFeatureCollection;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.math.MathUtil;

import ca.bc.gov.catchment.utils.SpatialUtils;

public class RandomPointsBuilder {

	private boolean isConstrainedToCircle = false;
	private double gutterFraction = 0;
	private double resolution;
	private Envelope extent;
	
	/**
	 * resolution is the distance between grid cell centres.  of the extent
	 * @param srid
	 * @param resolution
	 */
	public RandomPointsBuilder(Envelope extent, double resolution) {
		this.resolution = resolution;
		this.extent = extent;
	}
	
	public List<Coordinate> getPoints() {
		List<Coordinate> coords = new ArrayList<Coordinate>();		

	    double gridDX = resolution;
	    double gridDY = resolution;

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
	        Coordinate coord = randomPointInCell(orgX, orgY, cellDX, cellDY);
	        coords.add(coord);
	      }
	    }
	    
	    return coords;
	}
	
	private Coordinate randomPointInCell(double orgX, double orgY, double xLen, double yLen)
	  {
	  	if (isConstrainedToCircle) {
	  		return randomPointInCircle(
	  				orgX, 
	  				orgY, 
	  				xLen, yLen);
	  	}
	  	return randomPointInGridCell(orgX, orgY, xLen, yLen);
	  }
	  
	  private Coordinate randomPointInGridCell(double orgX, double orgY, double xLen, double yLen)
	  {
	    double x = orgX + xLen * Math.random();
	    double y = orgY + yLen * Math.random();
	    return new Coordinate(x, y);    
	  }

	  private static Coordinate randomPointInCircle(double orgX, double orgY, double width, double height)
	  {
	  	double centreX = orgX + width/2;
	  	double centreY = orgY + height/2;
	  		
	  	double rndAng = 2 * Math.PI * Math.random();
	  	double rndRadius = Math.random();
	    // use square root of radius, since area is proportional to square of radius
	    double rndRadius2 = Math.sqrt(rndRadius);
	  	double rndX = width/2 * rndRadius2 * Math.cos(rndAng); 
	  	double rndY = height/2 * rndRadius2 * Math.sin(rndAng); 
	  	
	    double x0 = centreX + rndX;
	    double y0 = centreY + rndY;
	    return new Coordinate(x0, y0);    
	  }
}


