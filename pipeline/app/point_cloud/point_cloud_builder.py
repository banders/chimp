import os.path
import pylas
import numpy as np
try:
  from osgeo import ogr, gdal
except:
  sys.exit('ERROR: cannot find GDAL/OGR modules.  please install them')

gdal.UseExceptions() 
ogr.UseExceptions()


# Constants
#------------------------------------------------------------------------------

SUPPORTED_POINT_CODES = {
  "E": "elevation",
  "W": "water",
  "C": "catchment boundary",
  "B": "breakline"
}
SUPPORTED_INPUT_FORMATS = [".gpkg", ".laz"]
SUPPORTED_OUTPUT_FORMATS = [".gpkg"]

# Classes
#------------------------------------------------------------------------------

class PointCloudBuilder():
  target_filename = None
  target_table = None

  def __init__(self, target_filename, target_table):
    if not target_filename:
      raise ValueError("must specify a target filename")
    target_ext = os.path.splitext(target_filename)[1].lower()
    if target_ext not in SUPPORTED_OUTPUT_FORMATS:
      raise ValueError("target filename must be one of these types: {}".format(SUPPORTED_OUTPUT_FORMATS))
    if not target_table:
      raise ValueError("must specify a target table")

    self.target_filename = target_filename
    self.target_table = target_table

  def load(self, src_filename, src_table=None, code=None):
    if not src_filename:
      raise ValueError("must specify a source filename")
    src_ext = os.path.splitext(src_filename)[1].lower()
    if src_ext not in SUPPORTED_INPUT_FORMATS:
      raise ValueError("source filename must be one of these types: {}".format(SUPPORTED_INPUT_FORMATS))
    if src_ext == ".gpkg":
      raise ValueError("must specify a souce table")
    if code not in SUPPORTED_POINT_CODES.keys():
      raise ValueError("code must be one of these: {}".format(SUPPORTED_POINT_CODES))

    with pylas.open(src_filename) as f:
      in_data = f.read()
      evlrs = in_data.evlrs
      #print(evlrs)
      #print(type(in_data))
      x = in_data.x
      y = in_data.y
      z = in_data.z
      classification = in_data.classification
      header = in_data.header
      print(header)
      print(header._fields_)
      point_format = in_data.point_format  
      print(point_format.dimension_names)
      for index in range(0, len(x)):
        point = {
          "x": x[index],
          "y": y[index],
          "z": z[index],
          "classification": classification[index]
        }
        #print(point)


#------------------------------------------------------------------------------

def test_laz():
  src_filename = "C:/Users/Brock/Documents/BandersGeo/projects/catchment-delineation/data/lidar/bc_082f017_3_2_4_xyes_8_utm11_180827.laz"
  src_table = None
  target_filname = "./test-point-cloud.gpkg"
  target_table = "point_cloud"

  pcb = PointCloudBuilder(target_filname, target_table)
  pcb.load(src_filename, code="E")

if __name__ == "__main__":
  test_laz()