#pragma once
#include "pch.h"

//standard includes
#include <iostream>
#include <fstream>
#include <cassert>
#include <string>
#include <chrono>
#include <sstream> 
#include <algorithm>

//CGAL general
#include <CGAL/exceptions.h>
#include <CGAL/assertions.h>
#include <CGAL/assertions_behaviour.h>

// define the kernel
#include <CGAL/Interval_nt.h>
#include <CGAL/Simple_cartesian.h>
#include <CGAL/Segment_Delaunay_graph_storage_traits_2.h>

typedef CGAL::Field_with_sqrt_tag		CM;
typedef CGAL::Field_with_sqrt_tag		FM;
typedef CGAL::Field_tag					EM;

typedef CGAL::Simple_cartesian<double> Kernel_6;

#include <CGAL/Segment_Delaunay_graph_2.h>
#include <CGAL/Segment_Delaunay_graph_traits_2.h>
typedef CGAL::Segment_Delaunay_graph_traits_2<Kernel_6>  graphTraits_6;
typedef CGAL::Segment_Delaunay_graph_2<graphTraits_6> SDG2_Voronoi_6;

using namespace std;
class Voronoi_6 {

private:
	string _inFile;
	string _outFile;
	int _precision;

	//values for these variables are computed from the input data
	double _inputMinX;
	double _inputMaxX;
	double _inputMinY;
	double _inputMaxY;

	array<double, 4> estimateOutputBbox();
	void recordPoint(SDG2_Voronoi_6::Point_2 point);
	void recordSite(SDG2_Voronoi_6::Site_2 site);
	double integerDivisionToDouble(string integerDivisionAsString);
	int getSplitIndex(string token, array<double, 4> outputBbox);
	string cleanLineWithFractions(string line, array<double, 4> outputBbox);
	string lineToWkt(string line);

public:
	Voronoi_6(string inFile, string outFile);
	void process();
};