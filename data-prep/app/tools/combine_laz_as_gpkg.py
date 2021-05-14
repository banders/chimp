"""
Combines all .laz files into a single geopackage file.  
there is an intermediary step to convert each .laz file into a csv file.  (the csv files are then converted into
a geopackage).

Dependencies:
  - Depends on command-line access to pdal and gdal.  It may be necessary to first activate a pdal environment
    with a command such as:
      conda activate <pdal-venv>

Example usage:
  python -m app.tools.combine_laz_as_gpkg -in-dir "C:/Users/Brock/Documents/BandersGeo/projects/catchment-delineation/data/lidar/82f017/raw" -out-dir "C:/Users/Brock/Documents/BandersGeo/projects/catchment-delineation/data/lidar/82f017/ground" -out-gpkg-file "82f017-ground-1m.gpkg" -resample-dist 1 -classification 2 --keep-intermediate-files 

  python -m app.tools.combine_laz_as_gpkg -in-dir "C:/Users/Brock/Documents/BandersGeo/projects/catchment-delineation/data/lidar/82f017/raw" -out-dir "C:/Users/Brock/Documents/BandersGeo/projects/catchment-delineation/data/lidar/82f017/water" -out-gpkg-file "82f017-water-1m.gpkg" -resample-dist 1 -classification 9 --keep-intermediate-files 
"""
import os
import sys
import glob
import argparse
from app.data_access.format_conversions import laz_to_csv, csv_to_gpkg

def main():

  argParser = argparse.ArgumentParser(description="runs a group of catchment delineation processing tools")
  argParser.add_argument('-in-dir', dest='in_dir', action='store', default=None, required=True, help='directory containing the input .laz files')
  argParser.add_argument('-out-dir', dest='out_dir', action='store', default=None, required=True, help='directory to save the output files to (.gpkg and temporary .csv)')
  argParser.add_argument('-out-gpkg-file', dest='out_gpkg_file', action='store', default=None, required=True, help='file name without path of output geopackage')
  argParser.add_argument('-out-layer-name', dest='out_layer_name', action='store', default="points", required=False, help='name of layer in output gpkg file')
  argParser.add_argument('-resample-dist', dest='resample_dist', action='store', default=None, required=False, help='resample so no two points are closer than the given distance')
  argParser.add_argument('-classification', dest='classification', action='store', default=None, required=False, help='a LAS classification code.  if specified, only points with this value are included.  2 is ground.  9 is water.')
  argParser.add_argument('--keep-intermediate-files', dest='keep_intermediate', action='store_const', const=True, required=False, help='a flag indicating that intermediate files (.csv files) should be kept.  the default is to delete them.')
  argParser.add_argument('--add-spatial-index', dest='add_spatial_index', action='store_const', const=True, required=False, help='a flag indicating that a spatial index should be added')


  try:
    args = argParser.parse_args()
  except argparse.ArgumentError as e:
    argParser.print_help()
    sys.exit(1)

  source_epsg = "epsg:26911"
  target_epsg = "epsg:3005"

  gpkg_filename_with_path = os.path.join(args.out_dir, args.out_gpkg_file)  

  if os.path.exists(gpkg_filename_with_path):
    print("geopackage file already exists.")
    exit(1)

  in_filenames = glob.glob(os.path.join(args.in_dir, "*.laz"))

  if not len(in_filenames):
    print("no .laz files found in: '{}'".format(args.in_dir))
    sys.exit(1)

  #in_filenames = in_filenames[0:2]
  for laz_filename_with_path in in_filenames:
    
    [_, laz_filename_no_path] =  os.path.split(laz_filename_with_path)
    [in_filename_no_ext, _] = os.path.splitext(laz_filename_no_path)
    res = "-res{}".format(args.resample_dist) if args.resample_dist else None
    csv_filename_with_path = os.path.join(args.out_dir, "{}{}.{}".format(in_filename_no_ext, res, "csv"))
    pdal_pipeline_filename_with_path = os.path.join(args.out_dir, "{}.pipeline.{}".format(in_filename_no_ext, "json"))

    if not os.path.exists(csv_filename_with_path):
      laz_to_csv(laz_filename_with_path, csv_filename_with_path, pdal_pipeline_filename_with_path, args.resample_dist, args.classification)

    is_last = in_filenames[len(in_filenames)-1] == laz_filename_with_path
    add_index = args.add_spatial_index and is_last

    append = os.path.exists(gpkg_filename_with_path)
    csv_to_gpkg(csv_filename_with_path, gpkg_filename_with_path, source_epsg, target_epsg, layer_name=args.out_layer_name, append=append, add_index=add_index)

    #cleanup
    if not args.keep_intermediate:
      os.remove(csv_filename_with_path)
      os.remove(pdal_pipeline_filename_with_path)

if __name__ == "__main__":
  main()
