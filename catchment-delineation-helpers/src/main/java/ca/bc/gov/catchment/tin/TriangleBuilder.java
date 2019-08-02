package ca.bc.gov.catchment.tin;

import java.util.ArrayList;
import java.util.List;

public class TriangleBuilder {
	private List<Triangle> triangles;
	private Edge rootEdge;
	
	public TriangleBuilder(Edge rootEdge) {
		this.rootEdge = rootEdge;
		triangles = new ArrayList<Triangle>();
		triangles.add(new Triangle(rootEdge));
	}
	
	public void addEdge(Edge e) {
		List<Triangle> receiverTriangles = getTrianglesThatCanReceiveEdge(e);
		for(Triangle t : receiverTriangles) {
			t.addEdge(e);
		}
		
		//if no triangles can receive the edge, create a new triangle
		if (receiverTriangles.size() == 0) {
			Triangle t = new Triangle();
			t.addEdge(rootEdge);
			if (t.canReceiveEdge(e)) {
				t.addEdge(e);
				triangles.add(t);
			}
			else {
				//System.out.println("cannot receive edge: "+ e);
			}
		}			
	}
	
	private List<Triangle> getTrianglesThatCanReceiveEdge(Edge e) {
		List<Triangle> result = new ArrayList<Triangle>();
		for(Triangle t : triangles) {
			if (t.canReceiveEdge(e)) {
				result.add(t);
			}
		}
		return result;
	}
	
	public List<Triangle> getCompleteTriangles() {
		List<Triangle> result = new ArrayList<Triangle>();
		for(Triangle t : triangles) {
			if (t.isComplete()) {
				result.add(t);
			}
		}
		return result;
	}
	
	public String getSummary() {
		String s = triangles.size()+ " triangle(s):\n";
		for(Triangle t : triangles) {
			s = s + " " +t.toString();
		}
		return s;
	}
}