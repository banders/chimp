package ca.bc.gov.catchment.uncertainty;

/**
 * Represents uncertainty of a geographic location.  There is both a horizontal and a vertical component to uncertainty.
 * Unit of measure is not explicily defined, but will generally be the same as the unit of coordinate reference system
 * that instances describe.
 * @author Brock
 *
 */
public class PointUncertainty {

	private double uncertaintyHorizontal;
	private double uncertaityVertical;
	
	public PointUncertainty(double uncertaintyHorizontal, double uncertaityVertical) {
		this.uncertaintyHorizontal  = uncertaintyHorizontal;
		this.uncertaityVertical = uncertaityVertical;
	}

	public double getUncertaintyHorizontal() {
		return uncertaintyHorizontal;
	}

	public double getUncertaityVertical() {
		return uncertaityVertical;
	}
	
	
}
