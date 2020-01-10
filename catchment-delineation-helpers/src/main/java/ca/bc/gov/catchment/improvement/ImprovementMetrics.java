package ca.bc.gov.catchment.improvement;

public class ImprovementMetrics {

	private int numAlternativesTested;
	private int numValidAlternativesTested;
	private int numImprovementRequests;
	private int numImproved;
	private long runtimeMs;

	public ImprovementMetrics() {
		numImprovementRequests = 0;
		numImproved = 0;
		numAlternativesTested = 0;
		numValidAlternativesTested = 0;
		runtimeMs = 0;
	}
	
	public int getNumImproved() {
		return numImproved;
	}

	public void setNumImproved(int numImproved) {
		this.numImproved = numImproved;
	}

	public void incrementNumImproved() {
		numImproved++;
	}
	
	public void incrementNumAlternativesTested() {
		numAlternativesTested++;
	}
	
	public int getNumAlternativesTested() {
		return numAlternativesTested;
	}

	public void setNumAlternativesTested(int numTests) {
		this.numAlternativesTested = numTests;
	}

	public long getRuntimeMs() {
		return runtimeMs;
	}

	public void setRuntimeMs(long runtimeMs) {
		this.runtimeMs = runtimeMs;
	}

	public int getNumImprovementRequests() {
		return numImprovementRequests;
	}

	public void setNumImprovementRequests(int numImprovementRequests) {
		this.numImprovementRequests = numImprovementRequests;
	}
	
	public void incrementNumImprovementRequests() {
		numImprovementRequests++;
	}
	
	public int getNumValidAlternativesTested() {
		return numValidAlternativesTested;
	}

	public void setNumValidAlternativesTested(int numValidAlternativesTested) {
		this.numValidAlternativesTested = numValidAlternativesTested;
	}
	
	public void incrementNumValidAlternativesTested() {
		numValidAlternativesTested++;
	}
	
	public long getAvgRuntimeMsPerRequest() {
		if (runtimeMs <= 0|| numAlternativesTested <= 0 ) {
			return -1;
		}
		long avg = Math.round(((double)runtimeMs) / ((double)numImprovementRequests));
		return avg;
	}
	
	public long getAvgRuntimeMsPerTest() {
		if (runtimeMs <= 0|| numAlternativesTested <= 0 ) {
			return -1;
		}
		long avg = Math.round(((double)runtimeMs) / ((double)numAlternativesTested));
		return avg;
	}
	
	/**
	 * increments statistics from this record to include information from
	 * the other record.
	 * @param other
	 */
	public void merge(ImprovementMetrics other) {
		if (other == null) {
			return;
		}
		setNumAlternativesTested(getNumAlternativesTested() + other.getNumAlternativesTested());
		setNumValidAlternativesTested(getNumValidAlternativesTested() + other.getNumValidAlternativesTested());
		setRuntimeMs(getRuntimeMs() + other.getRuntimeMs());
		setNumImproved(getNumImproved() + other.getNumImproved());
		setNumImprovementRequests(getNumImprovementRequests() + other.getNumImprovementRequests());
	}
	
}
