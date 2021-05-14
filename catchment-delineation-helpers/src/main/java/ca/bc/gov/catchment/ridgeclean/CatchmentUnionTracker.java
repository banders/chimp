package ca.bc.gov.catchment.ridgeclean;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.factory.CommonFactoryFinder;
import org.locationtech.jts.geom.Polygon;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.filter.Filter;
import org.opengis.filter.FilterFactory2;
import org.opengis.filter.identity.FeatureId;

public class CatchmentUnionTracker {
	
	private FilterFactory2 filterFactory;
	private SimpleFeatureCollection originalCatchments;
	private List<Catchment> newCatchments;
	
	public CatchmentUnionTracker (SimpleFeatureCollection originalCatchments) {
		this.originalCatchments = originalCatchments;
		this.newCatchments  = new ArrayList<Catchment>();
		this.filterFactory = CommonFactoryFinder.getFilterFactory2();
	}

	public void union(Catchment catchmentA, Catchment catchmentB) {
		
		//merge B into A (then keep A, and discard B)
		catchmentA.union(catchmentB);
		newCatchments.remove(catchmentB);	
	}
	
	public Catchment getCatchment(FeatureId fid) {
		//System.out.println("getCatchment "+fid);
		for (Catchment c : newCatchments) {
			if (c.contains(fid)) {
				//System.out.println(" found existing");
				return c;
			}
		}
		
		//System.out.println(" registering new");
		//no new catchment includes the given fid, so register a new catchment object and return it
		Catchment newCatchment = new Catchment(fid, getOriginalPolygon(fid));
		newCatchments.add(newCatchment);
		return newCatchment;
	}
	
	private Polygon getOriginalPolygon(FeatureId fid) {
		//System.out.println("getOriginalPolygon "+fid);
		Filter filter = filterFactory.id(fid);
		SimpleFeatureCollection matches = originalCatchments.subCollection(filter);
		if (matches.size() == 1) {
			//System.out.println("one match");
			SimpleFeatureIterator it = matches.features();
			SimpleFeature f = it.next();
			it.close();
			Polygon p = (Polygon)f.getDefaultGeometry();
			if (p == null) {
				throw new IllegalStateException("Null polygon for featureId "+fid);
			}
			return p;
		}
		else if (matches.size() == 0) {
			throw new IllegalArgumentException("Unknown featureId "+fid);
		}
		else {
			throw new IllegalStateException("Multiple features match featureId="+fid);
		}
		
	}
	
	public List<Catchment> getAllCatchments() {
		return this.newCatchments;
	}
	
}