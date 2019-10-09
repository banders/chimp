package ca.bc.gov.catchment.voronoi;

import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

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
	 * removes from kept any feature that is in discarded
	 */
	public void clean() {
		
		
		//build a map of kept features keyed by FID
		Map<String, SimpleFeature> keptMap = new TreeMap<String, SimpleFeature>();
		SimpleFeatureIterator keptIt = keptVoronoiEdgesFeatureCollection.features();
		while(keptIt.hasNext()) {
			SimpleFeature f = keptIt.next();
			keptMap.put(f.getID(), f);
		}
		keptIt.close();
		
		//remove from the "kept" map any feature whish is in the discarded collection
		SimpleFeatureIterator discardedIt = discardedVoronoiEdgesFeatureCollection.features();
		while(discardedIt.hasNext()) {
			SimpleFeature f = discardedIt.next();
			if (keptMap.containsKey(f.getID())) {
				keptMap.remove(f.getID());
			}
		}
		discardedIt.close();
		
		//convert the "kept" map back into a feature collection
		
		DefaultFeatureCollection keptCollection = new DefaultFeatureCollection();
		Set<String> keptSet = keptMap.keySet();
		for (String key : keptSet) {
			SimpleFeature f = keptMap.get(key);
			keptCollection.add(f);
		}
		
		this.keptVoronoiEdgesFeatureCollection = keptCollection;
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
		System.out.println("KeptAndDiscarded.merge() can be very slow.  Use KeptAndDiscarded.clean() instead.");
		this.addKept(other.getKept());
		this.addDiscarded(other.getDiscarded());
		
		
		
		//if a feature is listed as discarded in other and kept in this,
		//change it to discarded
		SimpleFeatureIterator otherDiscardedIt = other.getDiscarded().features();
		while(otherDiscardedIt.hasNext()) {
			SimpleFeature otherDiscardedFeature = otherDiscardedIt.next();
			if (keptVoronoiEdgesFeatureCollection.contains(otherDiscardedFeature)) {
				keptVoronoiEdgesFeatureCollection.remove(otherDiscardedFeature);
			}
		}
		otherDiscardedIt.close();
		
		//if a feature is listed as kept in other and discarded in this,
		//change it to discarded
		SimpleFeatureIterator otherKeptIt = other.getKept().features();
		while(otherKeptIt.hasNext()) {
			SimpleFeature otherKeptFeature = otherKeptIt.next();
			if (keptVoronoiEdgesFeatureCollection.contains(otherKeptFeature)) {
				keptVoronoiEdgesFeatureCollection.remove(otherKeptFeature);
			}
		}
		otherKeptIt.close();
	}
	
	public void dispose() {
		keptVoronoiEdgesFeatureCollection.clear();
		discardedVoronoiEdgesFeatureCollection.clear();
	}
}
