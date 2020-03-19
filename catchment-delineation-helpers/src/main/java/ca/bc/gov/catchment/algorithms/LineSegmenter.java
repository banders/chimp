package ca.bc.gov.catchment.algorithms;

import java.util.ArrayList;
import java.util.List;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.LineString;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;

import ca.bc.gov.catchment.utils.SpatialUtils;

public class LineSegmenter {

	/** 
	 * split on all vertices
	 * @param section
	 * @return
	 */
	public List<LineString> segment(LineString section) {
		Coordinate[] coords = section.getCoordinates();
		List<Coordinate> coordsToSplitOn = SpatialUtils.toCoordinateList(coords);
		return segment(section, coordsToSplitOn);
	}
	
	public List<LineString> segment(LineString lineToSplit, List<Coordinate> coordsToSplitOn) {

		List<LineString> segments = new ArrayList<LineString>();
		
		List<Coordinate> coordsOfCurrentPiece = new ArrayList<Coordinate>();
		//coordsOfCurrentPiece.add(lineToSplit.getCoordinateN(0));
		
		Coordinate lastCoord = lineToSplit.getCoordinateN(lineToSplit.getNumPoints()-1);
		
		for(Coordinate c : lineToSplit.getCoordinates()) {
			if (!coordsOfCurrentPiece.contains(c)) {
				coordsOfCurrentPiece.add(c);
			}
			boolean endOfPiece = coordsOfCurrentPiece.size() >= 2 && (coordsToSplitOn.contains(c) || c.equals(lastCoord));
			if (endOfPiece) {				
				LineString pieceGeometry = SpatialUtils.toLineString(coordsOfCurrentPiece);
				segments.add(pieceGeometry);	
				
				//reset for next piece
				coordsOfCurrentPiece.clear();
				coordsOfCurrentPiece.add(c);
			}
		}
		
		return segments;
	}
	
	public List<SimpleFeature> segment(SimpleFeature inFeature, List<Coordinate> coordsToSplitOn, int startFid) {
		int nextFid = startFid;
		SimpleFeatureType featureType = inFeature.getFeatureType();
		LineString section = (LineString)inFeature.getDefaultGeometry();
		List<LineString> segments = segment(section, coordsToSplitOn);
		List<SimpleFeature> result = new ArrayList<SimpleFeature>();
		for (LineString segment : segments) {
			String fid = (nextFid++)+"";
			SimpleFeature outFeature = SpatialUtils.geomToFeature(segment, featureType, fid);
			result.add(outFeature);
		}
		return result;
	}
	 
}
