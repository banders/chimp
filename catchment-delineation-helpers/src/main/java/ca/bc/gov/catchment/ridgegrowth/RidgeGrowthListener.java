package ca.bc.gov.catchment.ridgegrowth;

import org.locationtech.jts.geom.LineString;

public interface RidgeGrowthListener {

	public void onRidgeSuccess(RidgeGrowthTask task, LineString ridge);
	
	public void onRidgeError(RidgeGrowthTask task);
	
	public void onFinished();
}
