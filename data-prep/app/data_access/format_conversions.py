"""

Dependencies:
  - laz_to_csv depends on:
    - command-line access to pdal.  It may be necessary to first activate a pdal environment
      with a command such as:
        conda activate <pdal-venv>
  - csv_to_gpkg depends on:
    - command-line access to pdal.  It may be necessary to first activate a pdal environment
      with a command such as:
        conda activate <pdal-venv>
"""

import json
from subprocess import call

def laz_to_csv(laz_filename_with_path, csv_filename_with_path, pdal_pipeline_filename_with_path, resample_dist=None, classification=None):
    """
    :param resample_dist" a distance (in the same unit as the input laz file's SRS) used to determine which points
    are omitted from the output.  resampling is implemented using PDAL's "sample" filter.
    """
    reader = {
          "type":"readers.las",
          "filename": laz_filename_with_path,
          "default_srs": "EPSG:3005"
        }
    writer = {
          "type":"writers.text",
          "format":"csv",
          "order":"X,Y,Z",
          "keep_unspecified":"false",
          "filename": csv_filename_with_path
        }
    filters = []

    if classification != None:
      filters.append({
        "type":"filters.range",
        "limits":"Classification[{}:{}]".format(classification, classification)
      })

    if resample_dist:
      filters.append({
        "type":"filters.sample",
        "radius": resample_dist
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

def csv_to_gpkg(csv_filename_with_path, gpkg_filename_with_path, source_epsg, target_epsg=None, layer_name="records", append=False, add_index=True):

    if not source_epsg:
      raise ValueError("Must specify 'source_epsg'")
    if not target_epsg:
      target_epsg = source_epsg

    append_str = "" if not append else " -append -update"
    add_index_str = "YES" if add_index else "NO"

    c = "ogr2ogr -s_srs {} -t_srs {} -nln {} -oo X_POSSIBLE_NAMES=X -oo Y_POSSIBLE_NAMES=Y -oo Z_POSSIBLE_NAMES=Z -doo SPATIAL_INDEX={} -f GPKG {} {}{}".format(source_epsg, target_epsg, layer_name, add_index_str, gpkg_filename_with_path, csv_filename_with_path, append_str)
    print(c)
    resp = call(c.split())