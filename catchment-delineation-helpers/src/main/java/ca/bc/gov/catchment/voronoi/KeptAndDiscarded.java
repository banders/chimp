package ca.bc.gov.catchment.voronoi;

import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.feature.DefaultFeatureCollection;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;

public class KeptAndDiscarded {

	private DefaultFeatureCollection keptVoronoiEdgesFeatureCollection;
	private DefaultFeatureCollection discardedVoronoiEdgesFeatureCollection;
	
	public KeptAndDiscarded(SimpleFeatureType keptFeatureType, SimpleFeatureType discardedFeatureType) {
		keptVoronoiEdgesFeatureCollection = new DefaultFeatureCollection(keptFeatureType.getTypeName(), keptFeatureType);
		discardedVoronoiEdgesFeatureCollection  = new DefaultFeatureCollection(discardedFeatureType.getTypeName(), discardedFeatureType);
	}
	
	public SimpleFeatureCollection getKept() {
		return keptVoronoiEdgesFeatureCollection;
	}
	
	public void addKept(SimpleFeature f) {
		keptVoronoiEdgesFeatureCollection.add(f);
	}
	
	public void addKept(SimpleFeatureCollection fc) {
		keptVoronoiEdgesFeatureCollection.addAll(fc);
	}
	
	public int getNumKept() {
		return keptVoronoiEdgesFeatureCollection.size();
	}
	
	public SimpleFeatureCollection getDiscarded() {
		return discardedVoronoiEdgesFeatureCollection;
	}
	
	public void addDiscarded(SimpleFeature f) {
		discardedVoronoiEdgesFeatureCollection.add(f);
	}
	
	public void addDiscarded(SimpleFeatureCollection fc) {
		discardedVoronoiEdgesFeatureCollection.addAll(fc);
	}
	
	public int getNumDiscarded() {
		return discardedVoronoiEdgesFeatureCollection.size();
	}
	
	/**
	 * merges results from another KeptAndDiscarded.
	 * The following rules are:
	 * 1. if an item is listed as discarded in either group, ensure it is
	 *    listed as discarded in the result
	 * 2. There is no intersection between the kept and discarded sets
	 * @param other the data to merge in to this object
	 */
	public void merge(KeptAndDiscarded other) {
		
		System.out.println("merge 0");
		this.addKept(other.getKept());
		this.addDiscarded(other.getDiscarded());
		
		System.out.println("merge 1");
		//if a feature is listed as discarded in other and kept in this,
		//change it to discarded
		SimpleFeatureIterator otherDiscardedIt = other.getDiscarded().features();
		while(otherDiscardedIt.hasNext()) {
			SimpleFeature otherDiscardedFeature = otherDiscardedIt.next();
				
			//if (keptVoronoiEdgesFeatureCollection.contains(otherDiscardedFeature)) {
				keptVoronoiEdgesFeatureCollection.remove(otherDiscardedFeature);
			//}
			
		}
		
		System.out.println("merge 2");
		
		//if a feature is listed as kept in other and discarded in this,
		//change it to discarded
		SimpleFeatureIterator otherKeptIt = other.getKept().features();
		while(otherKeptIt.hasNext()) {
			SimpleFeature otherKeptFeature = otherKeptIt.next();
			
			//if (discardedVoronoiEdgesFeatureCollection.contains(otherKeptFeature)) {
				keptVoronoiEdgesFeatureCollection.remove(otherKeptFeature);
			//}
			
		}
	}
	
	public void dispose() {
		keptVoronoiEdgesFeatureCollection.clear();
		discardedVoronoiEdgesFeatureCollection.clear();
	}
}
