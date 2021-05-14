package ca.bc.gov.catchment.ridgeclean;

import java.util.ArrayList;
import java.util.List;

import org.locationtech.jts.geom.Polygon;
import org.opengis.filter.identity.FeatureId;

public class Catchment {

	private Polygon polygon;
	private List<FeatureId> fids;
	
	public Catchment(FeatureId fid, Polygon polygon) {
		if (fid == null) {
			throw new IllegalArgumentException("fid must not be null");
		}
		if (polygon == null) {
			throw new IllegalArgumentException("polygon must not be null");
		}
		this.polygon = polygon;
		this.fids = new ArrayList<FeatureId>();
		this.fids.add(fid);
	}
	
	/**
	 * merger the other object into this object
	 * @param other
	 */
	public void union(Catchment other) {
		this.polygon = (Polygon)polygon.union(other.getPolygon());
		this.fids.addAll(other.getFids());
	}
	
	public Polygon getPolygon() {
		return this.polygon;
	}
	
	public List<FeatureId> getFids() {
		return this.fids;
	}
	
	public boolean contains(FeatureId fid) {
		return this.fids.contains(fid);
	}
	
}
