package ca.bc.gov.catchment.uncertainty;

import org.locationtech.jts.geom.Coordinate;

public class UncertaintyHelpers {


	/**
	 * returns true if a could be higher than b, or the same as b (within uncertainty)
	 * @param a
	 * @param b
	 * @return
	 */
	public static boolean couldBeHigherOrSameWithinUncertainty(Coordinate a, Coordinate b, PointUncertainty uncertainty) {
		return a.getZ() + uncertainty.getUncertaityVertical() >= b.getZ() - uncertainty.getUncertaityVertical();
	}
	
	/**
	 * returns true if a could be higher than b (within uncertainty)
	 * @param a
	 * @param b
	 * @return
	 */
	public static boolean couldBeHigherWithinUncertainty(Coordinate a, Coordinate b, PointUncertainty uncertainty) {
		return a.getZ() + uncertainty.getUncertaityVertical() > b.getZ() - uncertainty.getUncertaityVertical();
	}
	
	/**
	 * returns true if a is definately higher than b (within uncertainty)
	 * @param a
	 * @param b
	 * @return
	 */
	public static boolean definatelyHigherWithinUncertainty(Coordinate a, Coordinate b, PointUncertainty uncertainty) {
		return a.getZ() - uncertainty.getUncertaityVertical() > b.getZ() + uncertainty.getUncertaityVertical();
	}
	
	/**
	 * returns true if a is definately higher than b (within uncertainty)
	 * @param a
	 * @param b
	 * @return
	 */
	public static boolean definatelyHigherOrSameWithinUncertainty(Coordinate a, Coordinate b, PointUncertainty uncertainty) {
		return a.getZ() - uncertainty.getUncertaityVertical() >= b.getZ() + uncertainty.getUncertaityVertical();
	}
	
}
