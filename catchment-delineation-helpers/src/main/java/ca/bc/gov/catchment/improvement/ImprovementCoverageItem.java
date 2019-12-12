package ca.bc.gov.catchment.improvement;

import java.util.ArrayList;
import java.util.List;

public class ImprovementCoverageItem {

	private int countTotal;
	private int countValid;
	private List<String> fids;
	
	public ImprovementCoverageItem() {
		this.countTotal = 0;
		this.countValid = 0;
		this.fids = new ArrayList<String>();
	}
	
	public void incrementCountTotal(String fid) {
		this.countTotal++;
		if (fid != null) {
			if (!fids.contains(fid)) {
				fids.add(fid);
			}
		}
	}
	
	public void incrementCountValid(String fid) {
		this.countValid++;
		if (fid != null) {
			if (!fids.contains(fid)) {
				fids.add(fid);
			}
		}
	}
	
	public int getCountTotal() {
		return this.countTotal;
	}

	public int getCountValid() {
		return this.countValid;
	}
	
	public List<String> getFids() {
		return this.fids;
	}
	
	public String getFidsAsString() {
		return fids.toString();
	}
	
}
