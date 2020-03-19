package ca.bc.gov.catchment.algorithms;

import java.util.ArrayList;
import java.util.List;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.LineString;

import ca.bc.gov.catchment.utils.SpatialUtils;

/**
 * This algorithm works by removing vertices that are seem as unnecessary
 * A vertex is considered unnecessary if the next vertex is higher and
 * the previous vertex was not deemed unnecessary.
 * 
 * The smoothing process can be repeated multiple times using the 'iterations' parameter
 * @author Brock
 *
 */
public class EliminationSmoother extends Smoother {

	@Override
	public LineString smooth(LineString g, int iterations) {
		LineString line = g;
		for (int i = 0; i < iterations; i++) {
			line = smoothImpl(line);
		}
		return line;		
	}

	public LineString smoothImpl(LineString g) {
		List<Coordinate> smoothedCoords = new ArrayList<Coordinate>();
		int nextIndex = 0;
		while (nextIndex < g.getNumPoints()) {
			Coordinate c0 = g.getCoordinateN(nextIndex);
			smoothedCoords.add(c0);
			if (nextIndex+2 < g.getNumPoints()) {
				Coordinate c1 = g.getCoordinateN(nextIndex+1);
				Coordinate c2 = g.getCoordinateN(nextIndex+2);
				//look 2 points ahead.  If 2 ahead has higher elevation than 1 ahead, then
				//omit 1 ahead
				if (c2.getZ() > c1.getZ()) {
					nextIndex +=2;
					continue;
				}
			}
			
			nextIndex++;
			
		}
		LineString smoothedLine = SpatialUtils.toLineString(smoothedCoords);
		return smoothedLine;
	}
	
}
