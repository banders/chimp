package ca.bc.gov.catchment.algorithms;

import java.util.ArrayList;
import java.util.List;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;

import ca.bc.gov.catchment.utils.SpatialUtils;

/**
 * Smoothes linestrings using the following approach:
 * 
 *	 - Leave the first and last point untouched.
 *	 - For each pair of vertices along the divide, excluding the first and last, choose the midpoint between them.
 *	 - Make a new divide using the first point, the sequence of midpoints, the last point.
 *	 - Repeat the above process above, if desired, n times to achieve the desired smoothness.
 *	
 * The effect is to reduce "jaggedness" from lines, by cutting off peaks.
 * 
 * @author Brock Anderson
 *
 */
public class MidpointSmoother {

	public MidpointSmoother() {
		
	}
	
	public LineString smooth(Geometry g, int iterations) {
		if (g.getGeometryType() == "LineString") {
			return smooth((LineString)g, iterations);
		}
		throw new IllegalStateException("Unsupported geometry type: "+g.getGeometryType());
	}

	public LineString smooth(LineString g, int iterations) {
		if (g.getNumPoints() <= 2) {
			return g;
		};
		List<Coordinate> coords = SpatialUtils.toCoordinateList(g.getCoordinates());		
		for(int iterationNum = 0; iterationNum < iterations; iterationNum++) {
			List<Coordinate> smoothedCoords = new ArrayList<Coordinate>();
			Coordinate prev = null;
			for (int coordNum = 0; coordNum < coords.size(); coordNum++) {
				Coordinate c = coords.get(coordNum);
				if (prev != null) {
					double x = (c.getX() + prev.getX()) / 2;
					double y = (c.getY() + prev.getY()) / 2;
					double z = (c.getZ() + prev.getZ()) / 2;
					Coordinate m = new Coordinate(x, y, z);
					smoothedCoords.add(m);
				}
				if (coordNum == 0 || coordNum == coords.size()-1) { 
					//first and last coordinates don't change
					smoothedCoords.add(c);
				}
				prev = c;
			}
			
			//get ready for the next iteration.  also, at the end of processing 
			//"initialCoords" will contain the final result
			coords = smoothedCoords;
		}
		
		LineString smoothedGeometry = SpatialUtils.toLineString(coords);	

		return smoothedGeometry;
	}
	
}
