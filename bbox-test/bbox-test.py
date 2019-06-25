
#Example usage:
#  python bbox-test.py -i C:\Users\Brock\Documents\BandersGeo\projects\catchment-delineation\pipeline-results\bc-kotl-1021\1\bc-kotl-1021-1.water.voronoi-in.txt -out-dir C:\Users\Brock\Documents\BandersGeo\projects\catchment-delineation\bbox-tests -bbox 1552005.6,482650.7,1812279.8,626731.1

import os
import json
import glob
import math
import argparse
import datetime
import subprocess

JAVA_PATH = "C:/Java/jdk1.8.0_161/bin/java.exe"
JAVA_CLASSPATH = "C:/git_repos/banders/chimp/catchment-delineation-helpers/target/classes;C:/Users/Brock/.m2/repository/org/geotools/gt-main/20.0/gt-main-20.0.jar;C:/Users/Brock/.m2/repository/org/geotools/gt-api/20.0/gt-api-20.0.jar;C:/Users/Brock/.m2/repository/org/locationtech/jts/jts-core/1.16.0/jts-core-1.16.0.jar;C:/Users/Brock/.m2/repository/org/jdom/jdom2/2.0.6/jdom2-2.0.6.jar;C:/Users/Brock/.m2/repository/javax/media/jai_core/1.1.3/jai_core-1.1.3.jar;C:/Users/Brock/.m2/repository/org/geotools/gt-geopkg/20.0/gt-geopkg-20.0.jar;C:/Users/Brock/.m2/repository/org/geotools/gt-coverage/20.0/gt-coverage-20.0.jar;C:/Users/Brock/.m2/repository/javax/media/jai_imageio/1.1/jai_imageio-1.1.jar;C:/Users/Brock/.m2/repository/it/geosolutions/imageio-ext/imageio-ext-tiff/1.1.25/imageio-ext-tiff-1.1.25.jar;C:/Users/Brock/.m2/repository/it/geosolutions/imageio-ext/imageio-ext-utilities/1.1.25/imageio-ext-utilities-1.1.25.jar;C:/Users/Brock/.m2/repository/it/geosolutions/imageio-ext/imageio-ext-geocore/1.1.25/imageio-ext-geocore-1.1.25.jar;C:/Users/Brock/.m2/repository/it/geosolutions/imageio-ext/imageio-ext-streams/1.1.25/imageio-ext-streams-1.1.25.jar;C:/Users/Brock/.m2/repository/javax/media/jai_codec/1.1.3/jai_codec-1.1.3.jar;C:/Users/Brock/.m2/repository/org/jaitools/jt-zonalstats/1.5.0/jt-zonalstats-1.5.0.jar;C:/Users/Brock/.m2/repository/org/jaitools/jt-utils/1.5.0/jt-utils-1.5.0.jar;C:/Users/Brock/.m2/repository/it/geosolutions/jaiext/affine/jt-affine/1.1.1/jt-affine-1.1.1.jar;C:/Users/Brock/.m2/repository/it/geosolutions/jaiext/algebra/jt-algebra/1.1.1/jt-algebra-1.1.1.jar;C:/Users/Brock/.m2/repository/it/geosolutions/jaiext/bandmerge/jt-bandmerge/1.1.1/jt-bandmerge-1.1.1.jar;C:/Users/Brock/.m2/repository/it/geosolutions/jaiext/bandselect/jt-bandselect/1.1.1/jt-bandselect-1.1.1.jar;C:/Users/Brock/.m2/repository/it/geosolutions/jaiext/bandcombine/jt-bandcombine/1.1.1/jt-bandcombine-1.1.1.jar;C:/Users/Brock/.m2/repository/it/geosolutions/jaiext/border/jt-border/1.1.1/jt-border-1.1.1.jar;C:/Users/Brock/.m2/repository/it/geosolutions/jaiext/buffer/jt-buffer/1.1.1/jt-buffer-1.1.1.jar;C:/Users/Brock/.m2/repository/it/geosolutions/jaiext/crop/jt-crop/1.1.1/jt-crop-1.1.1.jar;C:/Users/Brock/.m2/repository/it/geosolutions/jaiext/iterators/jt-iterators/1.1.1/jt-iterators-1.1.1.jar;C:/Users/Brock/.m2/repository/it/geosolutions/jaiext/lookup/jt-lookup/1.1.1/jt-lookup-1.1.1.jar;C:/Users/Brock/.m2/repository/it/geosolutions/jaiext/mosaic/jt-mosaic/1.1.1/jt-mosaic-1.1.1.jar;C:/Users/Brock/.m2/repository/it/geosolutions/jaiext/nullop/jt-nullop/1.1.1/jt-nullop-1.1.1.jar;C:/Users/Brock/.m2/repository/it/geosolutions/jaiext/rescale/jt-rescale/1.1.1/jt-rescale-1.1.1.jar;C:/Users/Brock/.m2/repository/it/geosolutions/jaiext/scale/jt-scale/1.1.1/jt-scale-1.1.1.jar;C:/Users/Brock/.m2/repository/it/geosolutions/jaiext/scale2/jt-scale2/1.1.1/jt-scale2-1.1.1.jar;C:/Users/Brock/.m2/repository/it/geosolutions/jaiext/stats/jt-stats/1.1.1/jt-stats-1.1.1.jar;C:/Users/Brock/.m2/repository/com/google/guava/guava/25.1-jre/guava-25.1-jre.jar;C:/Users/Brock/.m2/repository/com/google/code/findbugs/jsr305/3.0.2/jsr305-3.0.2.jar;C:/Users/Brock/.m2/repository/org/checkerframework/checker-qual/2.0.0/checker-qual-2.0.0.jar;C:/Users/Brock/.m2/repository/com/google/errorprone/error_prone_annotations/2.1.3/error_prone_annotations-2.1.3.jar;C:/Users/Brock/.m2/repository/com/google/j2objc/j2objc-annotations/1.1/j2objc-annotations-1.1.jar;C:/Users/Brock/.m2/repository/org/codehaus/mojo/animal-sniffer-annotations/1.14/animal-sniffer-annotations-1.14.jar;C:/Users/Brock/.m2/repository/it/geosolutions/jaiext/translate/jt-translate/1.1.1/jt-translate-1.1.1.jar;C:/Users/Brock/.m2/repository/it/geosolutions/jaiext/utilities/jt-utilities/1.1.1/jt-utilities-1.1.1.jar;C:/Users/Brock/.m2/repository/it/geosolutions/jaiext/warp/jt-warp/1.1.1/jt-warp-1.1.1.jar;C:/Users/Brock/.m2/repository/it/geosolutions/jaiext/zonal/jt-zonal/1.1.1/jt-zonal-1.1.1.jar;C:/Users/Brock/.m2/repository/it/geosolutions/jaiext/binarize/jt-binarize/1.1.1/jt-binarize-1.1.1.jar;C:/Users/Brock/.m2/repository/it/geosolutions/jaiext/format/jt-format/1.1.1/jt-format-1.1.1.jar;C:/Users/Brock/.m2/repository/it/geosolutions/jaiext/colorconvert/jt-colorconvert/1.1.1/jt-colorconvert-1.1.1.jar;C:/Users/Brock/.m2/repository/it/geosolutions/jaiext/errordiffusion/jt-errordiffusion/1.1.1/jt-errordiffusion-1.1.1.jar;C:/Users/Brock/.m2/repository/it/geosolutions/jaiext/orderdither/jt-orderdither/1.1.1/jt-orderdither-1.1.1.jar;C:/Users/Brock/.m2/repository/it/geosolutions/jaiext/colorindexer/jt-colorindexer/1.1.1/jt-colorindexer-1.1.1.jar;C:/Users/Brock/.m2/repository/it/geosolutions/jaiext/imagefunction/jt-imagefunction/1.1.1/jt-imagefunction-1.1.1.jar;C:/Users/Brock/.m2/repository/it/geosolutions/jaiext/piecewise/jt-piecewise/1.1.1/jt-piecewise-1.1.1.jar;C:/Users/Brock/.m2/repository/it/geosolutions/jaiext/classifier/jt-classifier/1.1.1/jt-classifier-1.1.1.jar;C:/Users/Brock/.m2/repository/it/geosolutions/jaiext/rlookup/jt-rlookup/1.1.1/jt-rlookup-1.1.1.jar;C:/Users/Brock/.m2/repository/it/geosolutions/jaiext/vectorbin/jt-vectorbin/1.1.1/jt-vectorbin-1.1.1.jar;C:/Users/Brock/.m2/repository/it/geosolutions/jaiext/shadedrelief/jt-shadedrelief/1.1.1/jt-shadedrelief-1.1.1.jar;C:/Users/Brock/.m2/repository/org/geotools/gt-jdbc/20.0/gt-jdbc-20.0.jar;C:/Users/Brock/.m2/repository/org/geotools/gt-data/20.0/gt-data-20.0.jar;C:/Users/Brock/.m2/repository/commons-dbcp/commons-dbcp/1.4/commons-dbcp-1.4.jar;C:/Users/Brock/.m2/repository/commons-collections/commons-collections/3.2.2/commons-collections-3.2.2.jar;C:/Users/Brock/.m2/repository/org/xerial/sqlite-jdbc/3.23.1/sqlite-jdbc-3.23.1.jar;C:/Users/Brock/.m2/repository/commons-io/commons-io/2.6/commons-io-2.6.jar;C:/Users/Brock/.m2/repository/org/geotools/xsd/gt-xsd-core/20.0/gt-xsd-core-20.0.jar;C:/Users/Brock/.m2/repository/picocontainer/picocontainer/1.2/picocontainer-1.2.jar;C:/Users/Brock/.m2/repository/commons-jxpath/commons-jxpath/1.3/commons-jxpath-1.3.jar;C:/Users/Brock/.m2/repository/org/eclipse/emf/org.eclipse.emf.common/2.12.0/org.eclipse.emf.common-2.12.0.jar;C:/Users/Brock/.m2/repository/org/eclipse/emf/org.eclipse.emf.ecore/2.12.0/org.eclipse.emf.ecore-2.12.0.jar;C:/Users/Brock/.m2/repository/org/eclipse/xsd/org.eclipse.xsd/2.12.0/org.eclipse.xsd-2.12.0.jar;C:/Users/Brock/.m2/repository/org/eclipse/emf/org.eclipse.emf.ecore.xmi/2.12.0/org.eclipse.emf.ecore.xmi-2.12.0.jar;C:/Users/Brock/.m2/repository/org/geotools/xsd/gt-xsd-fes/20.0/gt-xsd-fes-20.0.jar;C:/Users/Brock/.m2/repository/org/geotools/ogc/net.opengis.fes/20.0/net.opengis.fes-20.0.jar;C:/Users/Brock/.m2/repository/org/geotools/ogc/org.w3.xlink/20.0/org.w3.xlink-20.0.jar;C:/Users/Brock/.m2/repository/org/geotools/ogc/net.opengis.ows/20.0/net.opengis.ows-20.0.jar;C:/Users/Brock/.m2/repository/org/geotools/xsd/gt-xsd-gml3/20.0/gt-xsd-gml3-20.0.jar;C:/Users/Brock/.m2/repository/org/geotools/xsd/gt-xsd-gml2/20.0/gt-xsd-gml2-20.0.jar;C:/Users/Brock/.m2/repository/org/geotools/xsd/gt-xsd-ows/20.0/gt-xsd-ows-20.0.jar;C:/Users/Brock/.m2/repository/org/geotools/xsd/gt-xsd-filter/20.0/gt-xsd-filter-20.0.jar;C:/Users/Brock/.m2/repository/org/geotools/gt-referencing/20.0/gt-referencing-20.0.jar;C:/Users/Brock/.m2/repository/org/ejml/ejml-ddense/0.34/ejml-ddense-0.34.jar;C:/Users/Brock/.m2/repository/org/ejml/ejml-core/0.34/ejml-core-0.34.jar;C:/Users/Brock/.m2/repository/commons-pool/commons-pool/1.5.4/commons-pool-1.5.4.jar;C:/Users/Brock/.m2/repository/org/geotools/gt-metadata/20.0/gt-metadata-20.0.jar;C:/Users/Brock/.m2/repository/org/geotools/gt-opengis/20.0/gt-opengis-20.0.jar;C:/Users/Brock/.m2/repository/systems/uom/systems-common-java8/0.7.2/systems-common-java8-0.7.2.jar;C:/Users/Brock/.m2/repository/tec/uom/uom-se/1.0.8/uom-se-1.0.8.jar;C:/Users/Brock/.m2/repository/javax/measure/unit-api/1.0/unit-api-1.0.jar;C:/Users/Brock/.m2/repository/tec/uom/lib/uom-lib-common/1.0.2/uom-lib-common-1.0.2.jar;C:/Users/Brock/.m2/repository/si/uom/si-quantity/0.7.1/si-quantity-0.7.1.jar;C:/Users/Brock/.m2/repository/si/uom/si-units-java8/0.7.1/si-units-java8-0.7.1.jar;C:/Users/Brock/.m2/repository/jgridshift/jgridshift/1.0/jgridshift-1.0.jar;C:/Users/Brock/.m2/repository/net/sf/geographiclib/GeographicLib-Java/1.49/GeographicLib-Java-1.49.jar;C:/Users/Brock/.m2/repository/org/geotools/gt-epsg-hsql/20.0/gt-epsg-hsql-20.0.jar;C:/Users/Brock/.m2/repository/org/hsqldb/hsqldb/2.4.1/hsqldb-2.4.1.jar;C:/Users/Brock/.m2/repository/org/geotools/gt-epsg-extension/20.0/gt-epsg-extension-20.0.jar;C:/Users/Brock/.m2/repository/org/rogach/jopenvoronoi/jopenvoronoi-main/1.0-SNAPSHOT/jopenvoronoi-main-1.0-SNAPSHOT.jar;C:/Users/Brock/.m2/repository/commons-cli/commons-cli/1.4/commons-cli-1.4.jar;C:/Users/Brock/.m2/repository/org/apache/commons/commons-math3/3.4.1/commons-math3-3.4.1.jar"
VORONOI_PATH = "C:/git_repos/banders/catchments/catchment-delineation-cpp/voronoi-catchments/x64/Release/voronoi-catchments.exe"

class SampleArea:
  _next_id = 1
  def __init__(self, bbox):
    self.bbox = bbox
    self.has_bbox_header = False
    self.id = SampleArea._next_id #"{}_{}_{}_{}".format(self.bbox[0], self.bbox[1], self.bbox[2], self.bbox[3])
    SampleArea._next_id += 1
  def get_id(self):
    return self.id 
  def set_has_bbox_header(self, b):
    self.has_bbox_header = b
  def set_num_segments(self, n):
    self.num_segments = n
  def set_in_filename(self, f):
    self.in_filename = f
  def set_out_filename(self, f):
    self.out_filename = f
  def set_success(self, s):
    self.success = s

def main():
  argParser = argparse.ArgumentParser(description="runs a group of catchment delineation processing tools")
  argParser.add_argument('-i', dest='in_filename', action='store', default=None, required=True, help='input voronoi file')
  argParser.add_argument('-out-dir', dest='out_dir', action='store', default=None, required=True, help='location for output files')
  argParser.add_argument('-bbox', dest='bbox', action='store', default=None, required=False, help='xmin,ymin,xmax,ymax')
  argParser.add_argument('-division', dest='division', action='store', default=4, required=False, help='how many equal chunks to divide each bbox into')
  argParser.add_argument('-voronoi-config-num', dest='voronoi_config_num', action='store', default=5, required=False, help='voronoi config num')
  argParser.add_argument('--in-file-excludes-bbox-header', dest='excludes_bbox_header', action='store_const', const=True, default=False, help='indicates whether the first 4 lines of the input file are the bbox')

  try:
    args = argParser.parse_args()
  except argparse.ArgumentError as e:
    argParser.print_help()
    sys.exit(1)

  run_id = datetime.datetime.now().strftime("%Y-%m-%d.%H-%M-%S")

  initial_bbox = [float(i) for i in args.bbox.split(",")]

  out_dir = "{}/{}".format(args.out_dir, run_id)
  if not os.path.exists(out_dir):
    os.makedirs(out_dir)  

  failures = []
  queue = []
  #sample_areas = subdivide(SampleArea(initial_bbox), int(args.division))
  initial_sample_area = SampleArea(initial_bbox)
  initial_sample_area.set_has_bbox_header(not args.excludes_bbox_header)
  sample_areas = [initial_sample_area]
  queue.extend(sample_areas)

  while len(queue):
    next_sample_area = queue.pop(len(queue)-1) #get last item (newest item)
    success = run_on_sample_area(next_sample_area, args.voronoi_config_num, args.in_filename, out_dir)
    if not success:
      failures.append(next_sample_area)
      if next_sample_area.num_segments > 1:
        subdivided_sample_areas = subdivide(next_sample_area, int(args.division))
        queue.extend(subdivided_sample_areas)


  print("Done.")
  print("Summary:")
  print("  {} failures".format(len(failures)))
  for sample_area in failures:
    print("    bbox: {}, # segments: {}".format(sample_area.bbox, sample_area.num_segments))

def subdivide(sample_area, num_divisions):
  num_rows = int(math.sqrt(num_divisions))
  num_cols = num_rows
  initial_xmin = float(sample_area.bbox[0])
  initial_xmax = float(sample_area.bbox[2])
  initial_ymin = float(sample_area.bbox[1])
  initial_ymax = float(sample_area.bbox[3])
  initial_width = initial_xmax - initial_xmin
  initial_height = initial_ymax - initial_ymin

  division_width = initial_width / num_rows
  division_height = initial_height / num_cols
  subdivided_sample_areas = []
  for row in range(0, num_rows):
    for col in range(0, num_cols):
      division_xmin = initial_xmin + row * division_width
      division_xmax = division_xmin + division_width
      division_ymin = initial_ymin + col * division_height
      division_ymax = division_ymin + division_height
      division_bbox = [division_xmin, division_ymin, division_xmax, division_ymax]
      sample_area = SampleArea(division_bbox)
      subdivided_sample_areas.append(sample_area)
  return subdivided_sample_areas

def run_on_sample_area(sample_area, voronoi_config_num, all_in_filename, out_dir):
  in_filename = "{}/voronoi-in.{}.txt".format(out_dir, sample_area.get_id())
  out_filename = "{}/voronoi-out.{}.wkt".format(out_dir, sample_area.get_id())
  sample_area.set_in_filename(in_filename)
  sample_area.set_out_filename(out_filename)
  prepare_in_file(all_in_filename, in_filename, sample_area.bbox, has_bbox_header=sample_area.has_bbox_header, include_bounds=True)
  sample_area.set_num_segments(num_lines_in_file(in_filename))
  print("-----------------------")
  print("Starting voronoi...")
  print("  id: {}".format(sample_area.get_id()))
  print("  bbox: {}".format(sample_area.bbox))
  print("  in_file: {}".format(sample_area.in_filename))
  print("  out_file: {}".format(sample_area.out_filename))
  print("  num segments: {}".format(sample_area.num_segments))
  print("  running...")
  t1 = datetime.datetime.now()
  success = run_voronoi(sample_area.in_filename, sample_area.out_filename, voronoi_config_num)
  t2 = datetime.datetime.now()
  delta = t2 - t1
  print("  run time: {} seconds".format(delta.total_seconds()))
  print("  success?: {}".format(success))

  if not success:
    geopackage_filename = save_as_geopkg(sample_area)
    has_collapse = has_topological_collapse(geopackage_filename, "water_features_segmented")
    print("  collapse?: {}".format(has_collapse))

  sample_area.set_success(success)
  return success

def num_lines_in_file(fname):
  i = 0;
  with open(fname) as f:
    for i, l in enumerate(f):
      pass
  return i + 1

def is_point_in_bbox(point, bbox):
  """
  :param point: array of x,y
  :param bbox: array of xmin,ymin,xmax,ymax
  """
  x = point[0]
  y = point[1]
  xmin = bbox[0]
  ymin = bbox[1]
  xmax = bbox[2]
  ymax = bbox[3]
  #print ("{}, {}".format(point, bbox))
  return x >= xmin and x <= xmax and y >= ymin and y <= ymax

def prepare_in_file(in_filename_initial, in_filename_final, bbox, has_bbox_header=True, include_bounds=True):
  in_file = open(in_filename_initial, "r")
  out_file = open(in_filename_final, "w")

  bounds_minx = None
  bounds_miny = None
  bounds_maxx = None
  bounds_maxy = None

  num_kept = 0
  num_discarded = 0
  line_num = 0;
  for line in in_file:
    line_num += 1

    #ignore first four lines of input file
    if has_bbox_header and line_num <= 4:
      continue
    pieces = line.split(" ")
    x1 = float(pieces[1])
    y1 = float(pieces[2])
    x2 = float(pieces[4])
    y2 = float(pieces[5])
    p1 = [x1, y1]
    p2 = [x2, y2]
    minx = min(x1, x2)
    miny = min(y1, y2)
    maxx = max(x1, x2)
    maxy = max(y1, y2)
    #print("{} {}".format(p1, p2))
    if is_point_in_bbox(p1, bbox) or is_point_in_bbox(p2, bbox):
      num_kept += 1
      out_file.write(line)

      if not bounds_minx or minx < bounds_minx:
        bounds_minx = minx
      if not bounds_miny or miny < bounds_miny:
        bounds_miny = miny
      if not bounds_maxx or maxx > bounds_maxx:
        bounds_maxx = maxx
      if not bounds_maxy or maxy > bounds_maxy:
        bounds_maxy = maxy

  #add four lines to representing the bbox file
  if include_bounds and num_kept > 0:
    bounds_minx -= 10
    bounds_miny -= 10
    bounds_maxx += 10
    bounds_maxy += 10
    out_file.write("s {} {}  {} {}\n".format(bounds_minx, bounds_miny, bounds_maxx, bounds_miny))
    out_file.write("s {} {}  {} {}\n".format(bounds_maxx, bounds_miny, bounds_maxx, bounds_maxy))
    out_file.write("s {} {}  {} {}\n".format(bounds_maxx, bounds_maxy, bounds_minx, bounds_maxy))
    out_file.write("s {} {}  {} {}\n".format(bounds_minx, bounds_maxy, bounds_minx, bounds_miny))
  
  in_file.close()
  out_file.close()

def save_as_geopkg(sample_area):
  out_geopkg_filename = sample_area.in_filename.replace(".txt", ".gpkg")
  cmd1 = "{} -cp {} ca.bc.gov.catchment.scripts.VoronoiInput2GeoPackage -i {} -o {}".format(JAVA_PATH, JAVA_CLASSPATH, sample_area.in_filename, out_geopkg_filename)
  resp = subprocess.call(cmd1.split(), stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
  if resp != 0:
    return None
  return out_geopkg_filename

def has_topological_collapse(geopackage_filename, table):
  cmd1 = "{} -cp {} ca.bc.gov.catchment.scripts.CheckCollapse -i {} -o {}".format(JAVA_PATH, JAVA_CLASSPATH, geopackage_filename, table)
  resp = subprocess.call(cmd1.split(), stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
  if resp != 0:
    return False
  return True


def run_voronoi(in_filename, out_filename, voronoi_config_num):
  """
  returns true if the voronoi alg runs without error, false otherwise
  """ 
  
  cmd1 = "{} {} {} {}".format(VORONOI_PATH, in_filename, out_filename, voronoi_config_num)
  print(cmd1)
  resp = subprocess.call(cmd1.split(), stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)

  if resp != 0:
    return False
  return True


if __name__ == "__main__":
  main()