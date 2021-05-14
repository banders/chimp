"""
Converts all .laz files within a given folder to .csv file (containing x,y,z)
Depends on command-line access to pdal

THIS FILE IS DEPRECATED.  INSTEAD CALL data_acess.format_conversion.laz_to_csv(...) FROM
ANOTHER SCRIPT, OR USE THE SCRIPT combine_laz_as_gpkg.py



Example usage:
  conda activate <venv name>


  python -m tools.laz2csv -in-dir C:/Users/Brock/Documents/BandersGeo/projects/catchment-delineation/data/lidar/82f017/raw -out-dir C:/Users/Brock/Documents/BandersGeo/projects/catchment-delineation/data/lidar/82f017/ground --keep-intermediate-files  -resample-dist 1 -classification 2

"""
import os
import glob
import json
import argparse
from subprocess import call


def main():

  argParser = argparse.ArgumentParser(description="runs a group of catchment delineation processing tools")
  argParser.add_argument('-in-dir', dest='in_dir', action='store', default=None, required=True, help='directory containing the input .laz files')
  argParser.add_argument('-out-dir', dest='out_dir', action='store', default=None, required=True, help='directory to save the output .csv files to')
  argParser.add_argument('-resample-dist', dest='resample_dist', action='store', default=1, required=False, help='resample so no two points are closer than the given distance')
  argParser.add_argument('-classification', dest='classification', action='store', default=None, required=False, help='a LAS classification code.  if specified, only points with this value are included.  2 is ground.  9 is water.')

  try:
    args = argParser.parse_args()
  except argparse.ArgumentError as e:
    argParser.print_help()
    sys.exit(1)

  in_filenames = glob.glob(os.path.join(args.in_dir, "*.laz"))
  for in_filename_with_path in in_filenames:
    
    [_, in_filename_no_path] =  os.path.split(in_filename_with_path)
    [in_filename_no_ext, _] = os.path.splitext(in_filename_no_path)
    out_filename_with_path = os.path.join(args.out_dir, "{}.{}".format(in_filename_no_ext, "csv"))
    pdal_pipeline_filename_with_path = os.path.join(args.out_dir, "{}.pipeline.{}".format(in_filename_no_ext, "json"))

    reader = {
          "type":"readers.las",
          "filename": in_filename_with_path,
          "default_srs": "EPSG:3005"
        }
    writer = {
          "type":"writers.text",
          "format":"csv",
          "order":"X,Y,Z",
          "keep_unspecified":"false",
          "filename": out_filename_with_path
        }
    filters = []
    if args.classification != None:
      filters.append({
        "type":"filters.range",
        "limits":"Classification[{}:{}]".format(args.classification, args.classification)
      })
    if args.resample_dist:
      filters.append({
        "type":"filters.sample",
        "radius": args.resample_dist
      })
    

    #add reader, filters and writer to the pipeline
    pdal_pipeline = []
    pdal_pipeline.append(reader)
    for filter in filters:
      pdal_pipeline.append(filter)
    pdal_pipeline.append(writer)

    #save the pipeline json file
    with open(pdal_pipeline_filename_with_path, 'w') as pdal_pipeline_file:
      pdal_pipeline_json = json.dump(pdal_pipeline, pdal_pipeline_file)

    #run the pipeline
    c = "pdal pipeline {}".format(pdal_pipeline_filename_with_path)
    print(c)
    resp = call(c.split())

    #cleanup
    os.remove(pdal_pipeline_filename_with_path)


if __name__ == "__main__":
  main()