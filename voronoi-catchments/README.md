# voronoi-catchments

This is a C++ which creates a first approximation of catchment boundaries using 
a line Voronoi algorithm on a data set of water feature line segments.  The 
application has been tested primarily against rivers, streams and lakes from 
the (BC Freshwater Atlas)[https://www2.gov.bc.ca/gov/content/data/geographic-data-services/topographic-data/freshwater].

## Installation Dependencies:

### CGAL

For installation of CGAL on windows, follow the instructions here: https://www.cgal.org/download/windows.html

Summary:

* Download and install Visual Studio (e.g. Community edition 2017)
* Download and install CMake: https://cmake.org/
* Download the CGAL installer: https://www.cgal.org/download/last
	Install anywhere.  The install location is referred to as [CGAL_HOME] throughout these instructions
	Note: this installs all the source files, but it doesn't build them.  that's another step.
* Build CGAL
* Download and install the Boost library binary files: https://sourceforge.net/projects/boost/files/boost-binaries/
	Install anywyere.  The install location is referred to as [BOOST_HOME] throughout these instructions
  - If using Visual Studio 2017 then get the boost installation files for VC++ v14.x. 
     For Visual Studio 2013, VC++ v12.x

## Build CGAL

This step requires the CMAKE-GUI utility.  If you don't yet have CMAKE, install it now:
  https://cmake.org/

- Open cmake-gui.exe
- In the GUI, set I set the following values: 
  Where is the source code: [CGAL_HOME]
  Where to build the binaries: [CGAL_HOME]\build
- Click "Configure"
  - Choose "Visual Studio 15 2017 Win64" for the 64-bit version with VS 2017.  Note: the 15 here is not 
    the VC++ version, so it doesn't necessarily match the "14" for VS 2017.
- Disable WITH_CGAL_IMAGEIO
- Click Configure again
- Click "Generate".  A new VS "solution" is created in [CGAL_HOME]\build.  A file CGAL.sln is created
  in the build folder.
- Open [CGAL_HOME]\build\CGAL.sln in Visual Studio.  Build the ALL_BUILD project in both Release and Debug.
- Done. [CGAL_HOME] now contains the include files and library files needed to build projects against CGAL.


## Install Visual Studio

Download and install visual studio from:

	https://visualstudio.microsoft.com/downloads/

The voronoi-catchment application has been tested with VS 2017 Community Edition, which uses VC++ v14.x.  

### Visual Studio project properties:

In Visual Studio, start a new CPP project.

Set the following values in the visual studio project so that your application can find the 
CGAL and Boost headers and libraries:

  Configuration Properties > C/C++ > All Options > Additional Include Directories

  ```
	[CGAL_HOME]\build\include;[CGAL_HOME]\auxiliary\gmp\include;[CGAL_HOME]\include;[BOOST_HOME]
  ```

  Configuration Properties > Linker > All Options > Additional Library directories
	
  ```
  [CGAL_HOME]\build\lib;[CGAL_HOME]\auxiliary\gmp\lib;[BOOST_HOME]\lib64-msvc-14.0
  ```

  Configuration Properties > Linker > All Options > Additional Dependencies

  ```
  All .lib files from [CGAL_HOME]/build/lib, plus...
	  CGAL_Core-vc140-mt-4.14.lib
	  CGAL_Core-vc140-mt-gd-4.14.lib
	  CGAL-vc140-mt-4.14.lib
	  CGAL-vc140-mt-gd-4.14.lib
	libgmp-10.lib
	libmpfr-4.lib
  ```

Set the following values to help your application find the CGAL libraries at run time (when running from visual studio)

  Configuration Properties > Debugging > Path

  ```
	PATH=[CGAL_HOME]\build\bin;[CGAL_HOME]\auxiliary\gmp\lib
  ```

  Note: these folders contain CGAL DLL files that the voronoi application depends on at run time.

### Run the application

Build the application in Visual Studio, then run from the command line with:

[PATH_TO_VORONOI_CATCHMENT_EXE] [INPUT_TXT_FILE] [OUTPUT_WKT_FILE] [VORONOI_IMPLEMENTATION_NUMBER]

e.g. 

```
voronoi-catchments.exe in.txt out.wkt 4
```

where:

[PATH_TO_VORONOI_CATCHMENT_EXE] is the location of the voronoi-catchments executable file
build with Visual Studio.  For example: [PATH_TO_VS_PROJECT_FILES]/voronoi-catchments/x64/Release/voronoi-catchments.exe

[INPUT_FILE] is the path of a plain text file with one two-point line segment per line. 
Each line of the file must be of this format:

```
s [x1] [y1] [x2] [y2]
```

e.g. 

```
s 1296216.0749999993 465912.58400000073  1296243.273 465886.7280000001
s 1296243.273 465886.7280000001  1296288.7280000001 465856.6899999995
...
```

[OUTPUT_WKT_FILE] is the path of the well-known text file that will be output by voronoi-catchments.

[VORONOI_IMPLEMENTATION_NUMBER] is a number from 1-5.  The different numbers represent different
configurations of the voronoi algorithm.  2 works well for BC Freshwater Atlas data that has not been
modified. 4 and 5 work better for BC Freshwater Atlas data that has been modified (e.g. points densified
and/or snapped to a precision grid).

## Troubleshooting:

* When I run the program, I get "CGAL::Uncertain_conversion_exception".  Why?
	There is a comment in the CGAL FAQ about this:  https://www.cgal.org/FAQ.html#uncertain_exception