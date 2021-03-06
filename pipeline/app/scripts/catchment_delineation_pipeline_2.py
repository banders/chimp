"""

Example usage:
  python -m app.scripts.catchment_delineation_pipeline_2 -run-config run-configs\bc-kotl-1000.json -start-step 1 -run-id 2

"""

import os
import json
import glob
import argparse
from subprocess import call
import time

DEFAULT_SETTINGS_FILENAME = "settings.json"
DEFAULT_SIMPLIFY_DISTANCE_TOLERANCE = 2
DEFAULT_DENSIFY_DISTANCE_SPACING = 50
DEFAULT_SNAP_PRECISION_SCALE = 10000 #4 decimal places (e.g. to the nearest 0.0001 
WATER_FEATURES_TABLE = "water_features"
SEGMENTED_WATER_FEATURES_TABLE = "{}_segmented".format(WATER_FEATURES_TABLE)
VORONOI_EDGES_TABLE = "voronoi_edges"
VORONOI_CLEANED_TABLE = "voronoi_edges_kept_p4"
CATCHMENT_LINES_TABLE = "catchment_lines"
ISOLATED_HIGH_DENSITY_POINTS_TABLE = "points"
POINT_CLOUD_TABLE_PARTIAL_3D = "point_cloud"
POINT_CLOUD_TABLE_FULL_3D = "point_cloud_3d"
POINT_CLOUD_TABLE_FULL_3D_ADJUSTED = "point_cloud_3d_adjusted"
POINT_CLOUD_TABLE_FINAL = POINT_CLOUD_TABLE_FULL_3D
BREAKLINES_TABLE = "breaklines"
BREAKLINES_TABLE_3D = "breaklines_3d"
TIN_EDGES_TABLE = "tin_edges"
TIN_EDGES_COLORED_TABLE = "tin_edges_colored"
TIN_POLYS_TABLE = "tin_polys"
TIN_CENTROIDS_TABLE = "tin_centroids"
RIDGE_STICKS_TABLE = "ridge_sticks"
RIDGE_CLUMPS_TABLE = "ridge_clumps"
SMOOTHING_ALGORITHM = "midpoint"  #one of [midpoint,elimination]
LOCAL_MAXIMA_GRID_RESOLUTION = 100
LOCAL_MAXIMA_TABLE = "local_maxima"
DEFAULT_RIDGE_STICK_MAX_SLOPE = -0.5
DEFAULT_RIDGE_STICK_BUFFER_DISTANCE = 10
DEFAULT_RIDGE_CLUMP_MIN_AREA = 4000 #unit: m2

def main():
  argParser = argparse.ArgumentParser(description="runs a group of catchment delineation processing tools")
  argParser.add_argument('-run-config', dest='run_config_file', action='store', default=None, required=True, help='Name of the run config JSON file')
  argParser.add_argument('-run-id', dest='run_id', action='store', default=None, required=True, help='Name of the run config JSON file')
  argParser.add_argument('-start-step', dest='start_step', action='store', default=1, required=False, help='# of step to start with (e.g. 1, 2, 3, ...)')
  argParser.add_argument('-last-step', dest='last_step', action='store', default=100, required=False, help='# of step to finish with (e.g. 1, 2, 3, ...)')
  argParser.add_argument('-settings', dest='settings', action='store', default=DEFAULT_SETTINGS_FILENAME, required=False, help='path to settings json file')
#  argParser.add_argument('--usecache', dest='usecache', action='store_const', const=True, default=False, help='...')

  try:
    args = argParser.parse_args()
  except argparse.ArgumentError as e:
    argParser.print_help()
    sys.exit(1)

  #open settings file
  try:
    with open(args.settings) as f:
      settings = json.load(f)
  except IOError as e:
    print("Unable to find settings file: {}".format(args.settings))
    exit(1)
  except (TypeError, ValueError) as e:
    print(e)
    print("Unable to parse settings file: {}".format(args.settings))
    exit(1)

  args.start_step = int(args.start_step)
  args.last_step = int(args.last_step)

  print (args.run_config_file)
  with open(args.run_config_file) as in_file:
    data = in_file.read()
    print(data)
    run_config = json.loads(data)

  test_id = run_config.get("test_id")
  run_id = args.run_id

  # Create output folder for all generated files
  # ---------------------------------------------------------------------------
  test_out_dir = os.path.join(settings.get("out_base_dir"), test_id)
  run_out_dir = test_out_dir
  
  if run_id == None:
    #auto-choose run id
    run_dirs = glob.glob(os.path.join(test_out_dir, "*"))
    run_dirs = [int(os.path.split(run_dir)[1]) for run_dir in run_dirs]
    if len(run_dirs):
      last_run_id = max(run_dirs)
      run_id = last_run_id + 1
  
  run_out_dir = os.path.join(test_out_dir, "{}".format(run_id))
  if not os.path.exists(run_out_dir):
    os.makedirs(run_out_dir)  
  

  data_bbox = run_config["input"]["data_bbox"]
  data_bbox_crs = run_config["input"]["data_bbox_crs"]

  elevation_file_with_path = run_config["input"].get("elevation_file")
  elevation_point_table = run_config["input"].get("elevation_point_table")
  elevation_breakline_table = run_config["input"].get("elevation_breakline_table")

  snap_precision_scale = run_config["options"].get("snap_precision_scale", DEFAULT_SNAP_PRECISION_SCALE)
  snap_grid_spacing = 1.0 / snap_precision_scale #in same unit as the water_feature coordinate reference system (e.g. m for Albers)
  touches_distance_tolerance = run_config["options"].get("touches_distance_tolerance", snap_grid_spacing/2)

  point_cloud_resolution = run_config["options"].get("resample_elevation_distance")
  ridge_stick_max_slope = run_config["options"].get("ridge_stick_max_slope", DEFAULT_RIDGE_STICK_MAX_SLOPE)
  ridge_stick_buffer_distance = run_config["options"].get("ridge_stick_buffer_distance", point_cloud_resolution/2 if point_cloud_resolution else DEFAULT_RIDGE_STICK_BUFFER_DISTANCE)
  ridge_clump_min_area = run_config["options"].get("ridge_clump_min_area", DEFAULT_RIDGE_CLUMP_MIN_AREA)

  #i/o filenames:
  #----------------------------------------------------------------------------
  water_feature_filename_with_path = run_config["input"]["water_feature_file"]
  [head, tail] = os.path.split(water_feature_filename_with_path)

  water_feature_segmented_filename = "{}-{}.water.segmented.gpkg".format(test_id, run_id)
  water_feature_segmented_filename_with_path = os.path.join(run_out_dir, water_feature_segmented_filename)

  voronoi_output_snapped_gpkg_filename = "{}-{}.voronoi-out.snapped.gpkg".format(test_id, run_id)
  voronoi_output_snapped_gpkg_filename_with_path = os.path.join(run_out_dir, voronoi_output_snapped_gpkg_filename)

  voronoi_output_cleaned_gpkg_filename = "{}-{}.voronoi-out.cleaned.gpkg".format(test_id, run_id)
  voronoi_output_cleaned_gpkg_filename_with_path = os.path.join(run_out_dir, voronoi_output_cleaned_gpkg_filename)

  initial_catchments_lines_merged_gpkg_filename = "{}-{}.initial-catchments.lines-merged.gpkg".format(test_id, run_id)
  initial_catchments_lines_merged_gpkg_filename_with_path = os.path.join(run_out_dir, initial_catchments_lines_merged_gpkg_filename)

  initial_catchments_simp_dens_gpkg_filename = "{}-{}.initial-catchment.simp-dens.gpkg".format(test_id, run_id)
  initial_catchments_simp_dens_gpkg_filename_with_path = os.path.join(run_out_dir, initial_catchments_simp_dens_gpkg_filename)

  initial_catchments_3d_gpkg_filename = "{}-{}.initial-catchment.3d.gpkg".format(test_id, run_id)
  initial_catchments_3d_gpkg_filename_with_path = os.path.join(run_out_dir, initial_catchments_3d_gpkg_filename)

  explicit_breaklines_gpkg_filename = "{}-{}.explicit-breaklines.simp-dens.gpkg".format(test_id, run_id)
  explicit_breaklines_gpkg_filename_with_path = os.path.join(run_out_dir, explicit_breaklines_gpkg_filename)

  point_cloud_gpkg_filename = "{}-{}.point-cloud.gpkg".format(test_id, run_id)
  point_cloud_gpkg_filename_with_path = os.path.join(run_out_dir, point_cloud_gpkg_filename)

  isolated_high_density_points_gpkg_filename = "{}-{}.isolated-high-density-points.gpkg".format(test_id, run_id)
  isolated_high_density_points_gpkg_filename_with_path = os.path.join(run_out_dir, isolated_high_density_points_gpkg_filename)

  breaklines_gpkg_filename = "{}-{}.breaklines.gpkg".format(test_id, run_id)
  breaklines_gpkg_filename_with_path = os.path.join(run_out_dir, breaklines_gpkg_filename)

  tin_gpkg_filename = "{}-{}.tin.gpkg".format(test_id, run_id)
  tin_gpkg_filename_with_path = os.path.join(run_out_dir, tin_gpkg_filename)

  ridge_sticks_gpkg_filename = "{}-{}.ridge-sticks.gpkg".format(test_id, run_id)
  ridge_sticks_gpkg_filename_with_path = os.path.join(run_out_dir, ridge_sticks_gpkg_filename)
  
  ridge_clumps_gpkg_filename = "{}-{}.ridge-clumps.gpkg".format(test_id, run_id)
  ridge_clumps_gpkg_filename_with_path = os.path.join(run_out_dir, ridge_clumps_gpkg_filename)

  grown_catchments_raw_gpkg_filename = "{}-{}.grown-catchments-raw.gpkg".format(test_id, run_id)
  grown_catchments_raw_gpkg_filename_with_path = os.path.join(run_out_dir, grown_catchments_raw_gpkg_filename)

  grown_catchments_gpkg_filename = "{}-{}.grown-catchments.gpkg".format(test_id, run_id)
  grown_catchments_gpkg_filename_with_path = os.path.join(run_out_dir, grown_catchments_gpkg_filename)

  catchments_smoothed_gpkg_filename = "{}-{}.catchments-smoothed.gpkg".format(test_id, run_id)
  catchments_smoothed_gpkg_filename_with_path = os.path.join(run_out_dir, catchments_smoothed_gpkg_filename)

  local_maxima_gpkg_filename = "{}-{}.local-maxima.gpkg".format(test_id, run_id)
  local_maxima_gpkg_filename_with_path = os.path.join(run_out_dir, local_maxima_gpkg_filename)



  #----------------------------------------------------------------------------

  tables = run_config["input"]["tables"]
  #table_names = tables.split(",")
  #streams_table = table_names[0]
  #linearboundaries_table = table_names[1]
  voronoi_config_num = run_config["options"].get("voronoi_config_num", 2)
  simplify_dist_tolerance = run_config["options"].get("simplify_dist_tolerance", DEFAULT_SIMPLIFY_DISTANCE_TOLERANCE)
  densify_dist_spacing = run_config["options"].get("densify_dist_spacing", DEFAULT_DENSIFY_DISTANCE_SPACING)

  #Check prerequisites

  if not run_config["options"].get("snap"):
    #snapping is applied to: water features and voronoi output
    #The voronoi algorithm can leave small precision offsets between line endpoints 
    #which are intended to touch (e.g. less than 1 cm offset).  These offsets present a problem
    #to the voronoi output cleaning process (which depends on lines precisely touching).  
    #To avoid the problem it is necessary to snap the voronoi output to a precision grid 
    #that is larger than the maximum unintentional offset between vertices.  Because 
    #snapping is required on the voronoi output, it is also enforced on the water features
    #so that nexus points exactly touch catchment junctions.
    print("It is required to enable snapping.  Please enabled and try again.")
    exit(1)


  if not run_config["options"]["simplify"] or not run_config["options"]["densify"]:
    #simplification and densification are applied to: water features and voronoi output
    #since simplification and densification are set for all or nothing, they are both
    #considered to be required (even though not technically required to be applied to water
    #features.)  Simplification is required for voronoi output to avoid
    #small distances between adjacent vertices, which can lead to "sliver triangles" during
    #triangulation.  Densification is required for voronoi output to break up some very 
    #long lines that reduce flexibility of the catchment adjustment algorithm to move lines.
    print("It is required to enable both simplification and densification.  Please ensure these are enabled and try again.")
    exit(1)

  #----------------------------------------------------------------------------


  #Start processing
  if args.start_step <= 1 and 1 <= args.last_step:
    print("")  
    print("---------------------------------------------------")
    print(" Step 1: Prep water features")
    print("---------------------------------------------------")
    print("")

    prep_water_features_input_filename_with_path = water_feature_filename_with_path

    #filter out unwanted edges here
    if ("whitelist" in run_config["input"] and run_config["input"]["whitelist"]) or ("blacklist" in run_config["input"] and run_config["input"]["blacklist"]):
      print("Filtering out unwanted edges...")
      edge_filter = ""
      if run_config["input"].get("whitelist"):
        edge_filter = "-whitelistfilter {}".format(run_config["input"].get("whitelist"))
      elif run_config["input"].get("blacklist"):
        edge_filter = "-blacklistfilter {}".format(run_config["input"].get("blacklist"))
      water_feature_edges_filtered_filename = "{}-{}.edges-filtered.gpkg".format(test_id, run_id)
      water_feature_edges_filtered_filename_with_path = os.path.join(run_out_dir, water_feature_edges_filtered_filename)    
      cmd1 = "{} -cp {} ca.bc.gov.catchment.scripts.FilterEdgeCodes -i {} -o {} -tables {} {}".format(settings.get("java_path"), settings.get("java_classpath"), prep_water_features_input_filename_with_path, water_feature_edges_filtered_filename_with_path, tables, edge_filter)
      resp = call(cmd1.split())
      if resp != 0:
        print("Failure.  Pipeline execution stopped early.")
        exit(1);
      prep_water_features_input_filename_with_path = water_feature_edges_filtered_filename_with_path
    #do not simplify or densify
    if not run_config["options"]["simplify"] and not run_config["options"]["densify"]:
      print("No changes will made to the water features")
    #simplify only
    elif run_config["options"]["simplify"] and not run_config["options"]["densify"]:
      print("Simplifying...")
      water_feature_simp_filename = "{}-{}.water.simp.gpkg".format(test_id, run_id)
      water_feature_simp_filename_with_path = os.path.join(run_out_dir, water_feature_simp_filename)    
      cmd1 = "{} -cp {} ca.bc.gov.catchment.scripts.SimplifyThenDensity -i {} -o {} -simplify -simplifyDistanceTolerance {} -tables {}".format(settings.get("java_path"), settings.get("java_classpath"), prep_water_features_input_filename_with_path, water_feature_simp_filename_with_path, simplify_dist_tolerance, tables)
      resp = call(cmd1.split())
      if resp != 0:
        print("Failure.  Pipeline execution stopped early.")
        exit(1);
      prep_water_features_input_filename_with_path = water_feature_simp_filename_with_path
    #densify only (this option is not supported)
    elif not run_config["options"]["simplify"] and run_config["options"]["densify"]:
      print("Densifying...")
      water_feature_simp_filename = "{}-{}.water.dens.gpkg".format(test_id, run_id)
      water_feature_simp_filename_with_path = os.path.join(run_out_dir, water_feature_simp_filename)    
      cmd1 = "{} -cp {} ca.bc.gov.catchment.scripts.SimplifyThenDensity -i {} -o {} -densify -densifyDistanceSpacing {} -tables {}".format(settings.get("java_path"), settings.get("java_classpath"), prep_water_features_input_filename_with_path, water_feature_simp_filename_with_path, densify_dist_spacing, tables)
      resp = call(cmd1.split())
      if resp != 0:
        print("Failure.  Pipeline execution stopped early.")
        exit(1);
      prep_water_features_input_filename_with_path = water_feature_simp_filename_with_path
    #simplify and densify
    elif run_config["options"]["simplify"] and run_config["options"]["densify"]:
      print("Simplifying and Densifying...")
      water_feature_simp_dens_filename = "{}-{}.water.simp-dens.gpkg".format(test_id, run_id)
      water_feature_simp_dens_filename_with_path = os.path.join(run_out_dir, water_feature_simp_dens_filename)
      cmd1 = "{} -cp {} ca.bc.gov.catchment.scripts.SimplifyThenDensity -i {} -o {} -simplify -simplifyDistanceTolerance {} -densify -densifyDistanceSpacing {} -tables {}".format(settings.get("java_path"), settings.get("java_classpath"), prep_water_features_input_filename_with_path, water_feature_simp_dens_filename_with_path, simplify_dist_tolerance, densify_dist_spacing, tables)
      resp = call(cmd1.split())
      if resp != 0:
        print("Failure.  Pipeline execution stopped early.")
        exit(1);
      prep_water_features_input_filename_with_path = water_feature_simp_dens_filename_with_path

    #snap
    if run_config["options"].get("snap"):
      print("Snapping to grid...")
      precisionScale = run_config["options"].get("snap_precision_scale")
      if not precisionScale:
        print("Option 'snap_precision_scale' must be specified in the run config when option 'snap' is true.")
        print("Failure.  Pipeline execution stopped early.")
        exit(1)
      water_feature_snap_filename = "{}-{}.water.snap.gpkg".format(test_id, run_id)
      water_feature_snap_filename_with_path = os.path.join(run_out_dir, water_feature_snap_filename)
      cmd1b = "{} -cp {} ca.bc.gov.catchment.scripts.SnapToGrid -i {} -o {} -tables {} -precisionScale {}".format(settings.get("java_path"), settings.get("java_classpath"), prep_water_features_input_filename_with_path, water_feature_snap_filename_with_path, tables, precisionScale)
      resp = call(cmd1b.split())
      if resp != 0:
        print("Failure.  Pipeline execution stopped early.")
        exit(1);
      prep_water_features_input_filename_with_path = water_feature_snap_filename_with_path

    #combine all water features (streams and lakes) into a single file.  
    #also split all geometries into segments
    print("Combining water features datasets and segmenting...")
    cmd1c = "{} -cp {} ca.bc.gov.catchment.scripts.SegmentLinestrings -i {} -o {} -tables {} -bbox {} -bboxcrs {} ".format(settings.get("java_path"), settings.get("java_classpath"), prep_water_features_input_filename_with_path, water_feature_segmented_filename_with_path, tables, data_bbox, data_bbox_crs)
    resp = call(cmd1c.split())
    if resp != 0:
      print("Failure.  Pipeline execution stopped early.")
      exit(1);
    prep_water_features_input_filename_with_path = water_feature_segmented_filename_with_path

    #check that the input data is valid
    print("");
    print("Checking validity of geometries that will be input into voronoi algorithm")
    cmd1d = "{} -cp {} ca.bc.gov.catchment.scripts.CheckCollapse -i {} -tables {}".format(settings.get("java_path"), settings.get("java_classpath"), prep_water_features_input_filename_with_path, SEGMENTED_WATER_FEATURES_TABLE)
    resp = call(cmd1d.split())
    if resp != 0:
      print("Input data is not suitable for voronoi algorithm.")
      if run_config["options"].get("halt_on_invalid_topo", True):
        print("Failure.  Pipeline execution stopped early.")
        exit(1);

  #i/o filenames for step 2
  voronoi_input_txt_filename = "{}-{}.water.voronoi-in.txt".format(test_id, run_id)
  voronoi_input_txt_filename_with_path = os.path.join(run_out_dir, voronoi_input_txt_filename)
  voronoi_input_gpkg_filename_with_path = water_feature_segmented_filename_with_path
  
  """
  
  if args.start_step <= 2 and 2 <= args.last_step:
    print("")  
    print("---------------------------------------------------")
    print(" Step 2: Translate water features to CGAL format")
    print("---------------------------------------------------")
    print("")  
    
    cmd2 = "{} -cp {} ca.bc.gov.catchment.scripts.PrepCgalVoronoiInput -i {} -o {} -table {}".format(settings.get("java_path"), settings.get("java_classpath"), prep_water_features_input_filename_with_path, voronoi_input_txt_filename_with_path, SEGMENTED_WATER_FEATURES_TABLE)
    resp = call(cmd2.split())
    if resp != 0:
      print("Failure.  Pipeline execution stopped early.")
      exit(1);
    
  #i/o filenames for step 3
  voronoi_output_wkt_filename = "{}-{}.voronoi-out.wkt".format(test_id, run_id)
  voronoi_output_wkt_filename_with_path = os.path.join(run_out_dir, voronoi_output_wkt_filename)

  if args.start_step <= 3 and 3 <= args.last_step:
    print("")  
    print("---------------------------------------------------")
    print(" Step 3: Generate Voronoi diagram as WKT lines")
    print("---------------------------------------------------")
    print("")  

    cmd3 = "{} {} {} {}".format(settings.get("voronoi_catchment_path"), voronoi_input_txt_filename_with_path, voronoi_output_wkt_filename_with_path, voronoi_config_num)
    print (cmd3)
    resp = call(cmd3.split())
    if resp != 0:
      print("Error {}".format(resp))
      print("Failure.  Pipeline execution stopped early.")
      exit(1);

  #i/o filenames for step 4
  voronoi_output_gpkg_filename = "{}-{}.voronoi-out.gpkg".format(test_id, run_id)
  voronoi_output_gpkg_filename_with_path = os.path.join(run_out_dir, voronoi_output_gpkg_filename)

  if args.start_step <= 4 and 4 <= args.last_step:
    print("")  
    print("---------------------------------------------------")
    print(" Step 4: Voronoi diagram to GeoPackage")
    print("---------------------------------------------------")
    print("")  

    cmd4 = "{} -Xmx4096m -cp {} ca.bc.gov.catchment.scripts.WKTList2GeoPackage -i {} -o {} -crs {}".format(settings.get("java_path"), settings.get("java_classpath"), voronoi_output_wkt_filename_with_path, voronoi_output_gpkg_filename_with_path, data_bbox_crs)
    resp = call(cmd4.split())
    if resp != 0:
      print("Failure.  Pipeline execution stopped early.")
      exit(1);

  #i/o filenames for step 5

  if args.start_step <= 5 and 5 <= args.last_step:
    print("")  
    print("---------------------------------------------------")
    print(" Step 5: Clean Voronoi edges")
    print("---------------------------------------------------")
    print("")  
    
    clean_voronoi_input_filename_with_path = voronoi_output_gpkg_filename_with_path
    
    if not os.path.exists(voronoi_output_snapped_gpkg_filename_with_path):
      if run_config["options"].get("snap"):
        print("Snapping to grid...")
        precisionScale = run_config["options"].get("snap_precision_scale")
        if not precisionScale:
          print("Option 'snap_precision_scale' must be specified in the run config when option 'snap' is true.")
          print("Failure.  Pipeline execution stopped early.")
          exit(1)
        cmd1b = "{} -cp {} ca.bc.gov.catchment.scripts.SnapToGrid -i {} -o {} -tables {} -precisionScale {}".format(settings.get("java_path"), settings.get("java_classpath"), voronoi_output_gpkg_filename_with_path, voronoi_output_snapped_gpkg_filename_with_path, VORONOI_EDGES_TABLE, precisionScale)
        resp = call(cmd1b.split())
        if resp != 0:
          print("Failure.  Pipeline execution stopped early.")
          exit(1);
        clean_voronoi_input_filename_with_path = voronoi_output_snapped_gpkg_filename_with_path
    else:
      print("skipping snap")
    
    #remove unwanted edges
    cmd5 = "{} -cp {} ca.bc.gov.catchment.scripts.CleanVoronoiOutput -voronoiEdgesFile {} -waterFeaturesFile {} -outFile {} -voronoiEdgesTable {} -waterFeaturesTable {} -touchesDistanceTolerance {} -startPhase 1".format(settings.get("java_path"), settings.get("java_classpath"), clean_voronoi_input_filename_with_path, voronoi_input_gpkg_filename_with_path, voronoi_output_cleaned_gpkg_filename_with_path, VORONOI_EDGES_TABLE, SEGMENTED_WATER_FEATURES_TABLE, touches_distance_tolerance)
    resp = call(cmd5.split())
    if resp != 0:
      print("Failure.  Pipeline execution stopped early.")
      exit(1);
    
    #merge segments into linestrings
    cmd6 = "{} -cp {} ca.bc.gov.catchment.scripts.MergeLines -i {} -inTable {} -o {} -outTable {}".format(settings.get("java_path"), settings.get("java_classpath"), voronoi_output_cleaned_gpkg_filename_with_path, VORONOI_CLEANED_TABLE, initial_catchments_lines_merged_gpkg_filename_with_path, CATCHMENT_LINES_TABLE)
    resp = call(cmd6.split())
    if resp != 0:
      print("Failure.  Pipeline execution stopped early.")
      exit(1);
    

    #simplify and densify the linestrings
    if run_config["options"]["simplify"] and run_config["options"]["densify"]:
      print("Simplifying and Densifying...")
      cmd1 = "{} -cp {} ca.bc.gov.catchment.scripts.SimplifyThenDensity -i {} -o {} -simplify -simplifyDistanceTolerance {} -densify -densifyDistanceSpacing {} -tables {}".format(settings.get("java_path"), settings.get("java_classpath"), initial_catchments_lines_merged_gpkg_filename_with_path, initial_catchments_simp_dens_gpkg_filename_with_path, simplify_dist_tolerance, densify_dist_spacing, CATCHMENT_LINES_TABLE)
      resp = call(cmd1.split())
      if resp != 0:
        print("Failure.  Pipeline execution stopped early.")
        exit(1);

  """


  
  """
  Create breaklines:
  Breaklines are lines that triangle edges in the TIN should not cross.
  The breakline set includes:
    - water feature lines
    - any other lines which should be considered (these are called "explicit breaklines")
  """
  if args.start_step <= 6 and 6 <= args.last_step:
    print("")  
    print("---------------------------------------------------")
    print(" Step 6: Prepare break lines")
    print("---------------------------------------------------")
    print("")  

    bbox = "-bbox {} -bboxcrs {}".format(data_bbox, data_bbox_crs)

    if not os.path.exists(breaklines_gpkg_filename_with_path):
      print("Adding water features to break line set")
      cmd6a = "{} -cp {} ca.bc.gov.catchment.scripts.SegmentAndAppendToSet -i {} -inTable {} -o {} -outTable {} -outLabel {} {}".format(settings.get("java_path"), settings.get("java_classpath"), water_feature_segmented_filename_with_path, SEGMENTED_WATER_FEATURES_TABLE, breaklines_gpkg_filename_with_path, BREAKLINES_TABLE, "water", bbox)
      resp = call(cmd6a.split())
      if resp != 0:
        print("Failure.  Pipeline execution stopped early.")
        exit(1);

      if os.path.exists(initial_catchments_simp_dens_gpkg_filename_with_path):
        print("Adding initial catchments to break line set")
        cmd6b = "{} -cp {} ca.bc.gov.catchment.scripts.SegmentAndAppendToSet -i {} -inTable {} -o {} -outTable {} -outLabel {} {}".format(settings.get("java_path"), settings.get("java_classpath"), initial_catchments_simp_dens_gpkg_filename_with_path, CATCHMENT_LINES_TABLE, breaklines_gpkg_filename_with_path, BREAKLINES_TABLE, "voronoi", bbox)
        resp = call(cmd6b.split())
        if resp != 0:
          print("Failure.  Pipeline execution stopped early.")
          exit(1);

      #explicit breaklines
      has_additional_breakline_data = run_config["input"].get("elevation_file") and run_config["input"].get("elevation_breakline_table") #"additional" means in addition to the water features
      if has_additional_breakline_data:

        #simplify and density the explicit breaklines
        if run_config["options"]["simplify"] and run_config["options"]["densify"]:
          print("Simplifying and Densifying...")
          cmd1 = "{} -cp {} ca.bc.gov.catchment.scripts.SimplifyThenDensity -i {} -o {} -simplify -simplifyDistanceTolerance {} -densify -densifyDistanceSpacing {} -tables {}".format(settings.get("java_path"), settings.get("java_classpath"), elevation_file_with_path, explicit_breaklines_gpkg_filename_with_path, simplify_dist_tolerance, densify_dist_spacing, elevation_breakline_table)
          resp = call(cmd1.split())
          if resp != 0:
            print("Failure.  Pipeline execution stopped early.")
            exit(1);

        print("Adding explicit breaklines")
        cmd6c = "{} -cp {} ca.bc.gov.catchment.scripts.SegmentAndAppendToSet -i {} -inTable {} -o {} -outTable {} -outLabel {} {}".format(settings.get("java_path"), settings.get("java_classpath"), explicit_breaklines_gpkg_filename_with_path, elevation_breakline_table, breaklines_gpkg_filename_with_path, BREAKLINES_TABLE, "other", bbox)
        resp = call(cmd6c.split())
        if resp != 0:
          print("Failure.  Pipeline execution stopped early.")
          exit(1);

    print("Done.")

  #create point cloud
  if args.start_step <= 7 and 7 <= args.last_step:
    print("")  
    print("---------------------------------------------------")
    print(" Step 7: Create point cloud")
    print("---------------------------------------------------")
    print("")  

    bbox = "-bbox {} -bboxcrs {}".format(data_bbox, data_bbox_crs)

    has_elevation_data = run_config["input"].get("elevation_file") and run_config["input"].get("elevation_point_table")
    if not has_elevation_data:
      print ("No elevation data provided.  Unable to create point cloud.")
      exit(1)

    if run_config["options"]["resample_elevation_distance"]:

      resampled_elevation_gpkg_filename = "{}-{}.elevation-resampled-{}.gpkg".format(test_id, run_id, run_config["options"]["resample_elevation_distance"])
      resampled_elevation_gpkg_filename_with_path = os.path.join(run_out_dir, resampled_elevation_gpkg_filename)
      original_elevation_file_with_path = elevation_file_with_path
      original_elevation_point_table = elevation_point_table
      elevation_file_with_path = resampled_elevation_gpkg_filename_with_path
      elevation_point_table = "points"

      if not os.path.exists(resampled_elevation_gpkg_filename_with_path):
        print("Resampling elevation points to {} m resolution".format(run_config["options"]["resample_elevation_distance"]))
        cmd7a = "{} -cp {} -Xms{} -Xmx{} ca.bc.gov.catchment.scripts.ResampleElevationPoints -i {} -inTable {} -o {} -outTable {} -resolution {} {}".format(settings.get("java_path"), settings.get("java_classpath"), settings.get("java_initial_heap_size"), settings.get("java_max_heap_size"), original_elevation_file_with_path, original_elevation_point_table, elevation_file_with_path, elevation_point_table, run_config["options"]["resample_elevation_distance"], bbox)
        resp = call(cmd7a.split())
        if resp != 0:
          print("Failure.  Pipeline execution stopped early.")
          exit(1);
      else:
        print("Elevation points have already been resampled")

    else:
      print("Using source elevation data at its original resolution")


    if not os.path.exists(point_cloud_gpkg_filename_with_path):

      #generate elevation points which have higher density in isolated areas
      if not os.path.exists(isolated_high_density_points_gpkg_filename_with_path):
        print("Adding additional, isolated high density points to point cloud")
        cmd6c = "{} -cp {} ca.bc.gov.catchment.scripts.AddMedialAxisPointsNearConfluences -i {} -inTable {} -o {} -outTable {} {}".format(settings.get("java_path"), settings.get("java_classpath"), water_feature_segmented_filename_with_path, SEGMENTED_WATER_FEATURES_TABLE, isolated_high_density_points_gpkg_filename_with_path, ISOLATED_HIGH_DENSITY_POINTS_TABLE, bbox)
        resp = call(cmd6c.split())
        if resp != 0:
          print("Failure.  Pipeline execution stopped early.")
          exit(1);

      #add the additional high density elevation points to the point cloud
      """
      if os.path.exists(isolated_high_density_points_gpkg_filename_with_path):
        print("Adding additional, isolated high density points to point cloud")
        cmd6c = "{} -cp {} ca.bc.gov.catchment.scripts.BuildPointCloud -i {} -inTable {} -inTypeCode {} -o {} -outTable {} {}".format(settings.get("java_path"), settings.get("java_classpath"), isolated_high_density_points_gpkg_filename_with_path, ISOLATED_HIGH_DENSITY_POINTS_TABLE, "E", point_cloud_gpkg_filename_with_path, POINT_CLOUD_TABLE_PARTIAL_3D, bbox)
        resp = call(cmd6c.split())
        if resp != 0:
          print("Failure.  Pipeline execution stopped early.")
          exit(1);
      """

      #add primary elevation points
      print("Adding elevation points to point cloud")
      cmd6c = "{} -cp {} -Xms{} -Xmx{} ca.bc.gov.catchment.scripts.BuildPointCloud -i {} -inTable {} -inTypeCode {} -o {} -outTable {} {}".format(settings.get("java_path"), settings.get("java_classpath"), settings.get("java_initial_heap_size"), settings.get("java_max_heap_size"), elevation_file_with_path, elevation_point_table, "E", point_cloud_gpkg_filename_with_path, POINT_CLOUD_TABLE_PARTIAL_3D, bbox)
      resp = call(cmd6c.split())
      if resp != 0:
        print("Failure.  Pipeline execution stopped early.")
        exit(1);
      

      #add water features
      #Is it necessary to add points from water features? They are included in the breakline set, and that may be sufficient
      print("Adding 2D vertices from water features to point cloud")
      cmd6a = "{} -cp {} -Xms{} -Xmx{} ca.bc.gov.catchment.scripts.BuildPointCloud -i {} -inTable {} -inTypeCode {} -o {} -outTable {} {}".format(settings.get("java_path"), settings.get("java_classpath"), settings.get("java_initial_heap_size"), settings.get("java_max_heap_size"), water_feature_segmented_filename_with_path, SEGMENTED_WATER_FEATURES_TABLE, "W", point_cloud_gpkg_filename_with_path, POINT_CLOUD_TABLE_PARTIAL_3D, bbox)
      resp = call(cmd6a.split())
      if resp != 0:
        print("Failure.  Pipeline execution stopped early.")
        exit(1);

      #add initial catchments
      if os.path.exists(initial_catchments_simp_dens_gpkg_filename_with_path):
        print("Adding 2D vertices from initial catchments to point cloud")
        cmd6b = "{} -cp {} -Xms{} -Xmx{} ca.bc.gov.catchment.scripts.BuildPointCloud -i {} -inTable {} -inTypeCode {} -o {} -outTable {} {}".format(settings.get("java_path"), settings.get("java_classpath"), settings.get("java_initial_heap_size"), settings.get("java_max_heap_size"), initial_catchments_simp_dens_gpkg_filename_with_path, CATCHMENT_LINES_TABLE, "C", point_cloud_gpkg_filename_with_path, POINT_CLOUD_TABLE_PARTIAL_3D, bbox)
        resp = call(cmd6b.split())
        if resp != 0:
          print("Failure.  Pipeline execution stopped early.")
          exit(1);


      #add breaklines
      #  Note: breaklines include water features
      print("Adding break lines to point cloud")
      cmd6b = "{} -cp {} -Xms{} -Xmx{} ca.bc.gov.catchment.scripts.BuildPointCloud -i {} -inTable {} -inTypeCode {} -o {} -outTable {} {}".format(settings.get("java_path"), settings.get("java_classpath"), settings.get("java_initial_heap_size"), settings.get("java_max_heap_size"), breaklines_gpkg_filename_with_path, BREAKLINES_TABLE, "B", point_cloud_gpkg_filename_with_path, POINT_CLOUD_TABLE_PARTIAL_3D, bbox)
      resp = call(cmd6b.split())
      if resp != 0:
        print("Failure.  Pipeline execution stopped early.")
        exit(1);

    print("Estimating elevation for points in cloud without z-coordinate")
    cmd6d = "{} -cp {} -Xms{} -Xmx{} ca.bc.gov.catchment.scripts.EstimateElevation -i {} -inTable {} -elevationFile {} -elevationTable {} -o {} -outTable {} -searchRadius 10 {}".format(settings.get("java_path"), settings.get("java_classpath"), settings.get("java_initial_heap_size"), settings.get("java_max_heap_size"), point_cloud_gpkg_filename_with_path, POINT_CLOUD_TABLE_PARTIAL_3D, elevation_file_with_path, elevation_point_table, point_cloud_gpkg_filename_with_path, POINT_CLOUD_TABLE_FULL_3D, bbox)
    resp = call(cmd6d.split())
    if resp != 0:
      print("Failure.  Pipeline execution stopped early.")
      exit(1);

    """
    print("Adjusting point cloud elevation values") 
    cmd7 = "{} -cp {} -Xms2g ca.bc.gov.catchment.scripts.AdjustPointCloud -i {} -inTable {} -o {} -outTable {} -waterFile {} -waterTable {}".format(settings.get("java_path"), settings.get("java_classpath"), point_cloud_gpkg_filename_with_path, POINT_CLOUD_TABLE_FULL_3D, point_cloud_gpkg_filename_with_path, POINT_CLOUD_TABLE_FULL_3D_ADJUSTED, water_feature_segmented_filename_with_path, WATER_FEATURES_TABLE)
    resp = call(cmd7.split())
    if resp != 0:
      print("Failure.  Pipeline execution stopped early.")
      exit(1);
    """

    print("Converting breaklines to 3D")
    cmd8 = "{} -cp {} -Xms{} -Xmx{} ca.bc.gov.catchment.scripts.AssignElevation -i {} -inTable {} -pointCloud3DFile {} -inPointCloud3DTable {} -o {} -outTable {} -searchRadius {} {}".format(settings.get("java_path"), settings.get("java_classpath"), settings.get("java_initial_heap_size"), settings.get("java_max_heap_size"), breaklines_gpkg_filename_with_path, BREAKLINES_TABLE, point_cloud_gpkg_filename_with_path, POINT_CLOUD_TABLE_FINAL, breaklines_gpkg_filename_with_path, BREAKLINES_TABLE_3D, touches_distance_tolerance, bbox)
    resp = call(cmd8.split())
    if resp == 0:
      pass
    elif resp == 102:
      print("Unable to save result to output table.  Assuming table already exists.")
      pass
    else:
      print("Failure.  Pipeline execution stopped early.")
      exit(1);

    if not os.path.exists(local_maxima_gpkg_filename_with_path):
      print("Identifying local maxima")
      cmd9 = "{} -cp {} -Xms{} -Xmx{} ca.bc.gov.catchment.scripts.FindLocalMaxima -i {} -inTable {} -o {} -outTable {} -resolution {} {}".format(settings.get("java_path"), settings.get("java_classpath"), settings.get("java_initial_heap_size"), settings.get("java_max_heap_size"), point_cloud_gpkg_filename_with_path, POINT_CLOUD_TABLE_FINAL, local_maxima_gpkg_filename_with_path, LOCAL_MAXIMA_TABLE, LOCAL_MAXIMA_GRID_RESOLUTION, bbox)
      resp = call(cmd9.split())
      if resp == 0:
        pass
      elif resp == 102:
        print("Unable to save result to output table.  Assuming table already exists.")
        pass
      else:
        print("Failure.  Pipeline execution stopped early.")
        exit(1);



  #create TIN
  if args.start_step <= 8 and 8 <= args.last_step:
    print("")  
    print("---------------------------------------------------")
    print(" Step 8: Create TIN")
    print("---------------------------------------------------")
    print("")  

    bbox = "-bbox {} -bboxcrs {}".format(data_bbox, data_bbox_crs)

    if not os.path.exists(tin_gpkg_filename_with_path):
      print("Creating TIN edges")
      cmd8a = "{} -cp {} -Xms{} -Xmx{} ca.bc.gov.catchment.scripts.CreateTIN -pointCloudFile {} -pointCloudTable {} -breakLinesFile {} -breakLinesTable {} -o {} -outTable {} {}".format(settings.get("java_path"), settings.get("java_classpath"), settings.get("java_initial_heap_size"), settings.get("java_max_heap_size"), point_cloud_gpkg_filename_with_path, POINT_CLOUD_TABLE_FINAL, breaklines_gpkg_filename_with_path, BREAKLINES_TABLE_3D, tin_gpkg_filename_with_path, TIN_EDGES_TABLE, bbox)
      resp = call(cmd8a.split())
      if resp != 0:
        print("Failure.  Pipeline execution stopped early.")
        exit(1);

      
      #Triangles aren't needed for catchment line growing (as least not the current implementation)
      print("Building TIN polygons")
      cmd8b = "{} -cp {} -Xms{} -Xmx{} ca.bc.gov.catchment.scripts.IdentifyTriangles -i {} -inTable {} -o {} -outPolyTable {} -outCentroidTable {} -touchesDistanceTolerance {} {}".format(settings.get("java_path"), settings.get("java_classpath"), settings.get("java_initial_heap_size"),settings.get("java_max_heap_size"), tin_gpkg_filename_with_path, TIN_EDGES_TABLE, tin_gpkg_filename_with_path, TIN_POLYS_TABLE, TIN_CENTROIDS_TABLE, touches_distance_tolerance, bbox)
      resp = call(cmd8b.split())
      if resp != 0:
        print("Failure.  Pipeline execution stopped early.")
        exit(1);
      

    
    #Triangles aren't needed for catchment line growing (as least not the current implementation)
    print("Computing slopes of triangles adjacent to tin edges")
    cmd8c = "{} -cp {} -Xms{} -Xmx{} ca.bc.gov.catchment.scripts.ComputeAdjacentSlopes -i {} -inTable {} -o {} -outTable {} -tinPolysFilename {} -tinPolysTable {} {}".format(settings.get("java_path"), settings.get("java_classpath"), settings.get("java_initial_heap_size"), settings.get("java_max_heap_size"), tin_gpkg_filename_with_path, TIN_EDGES_TABLE, tin_gpkg_filename_with_path, TIN_EDGES_COLORED_TABLE, tin_gpkg_filename_with_path, TIN_POLYS_TABLE, bbox)
    resp = call(cmd8c.split())
    if resp != 0:
      print("Failure.  Pipeline execution stopped early.")
      exit(1);
   
    
  #grow catchments
  if args.start_step <= 9 and 9 <= args.last_step:
    print("")  
    print("---------------------------------------------------")
    print(" Step 9: Build a ridge data set")
    print("---------------------------------------------------")
    print("")  

    bbox = "-bbox {} -bboxcrs {}".format(data_bbox, data_bbox_crs)

    step_10_start = time.time()
    
    if not os.path.exists(ridge_sticks_gpkg_filename_with_path):
      print("Identifying ridge sticks")
      cmd9b = "{} -cp {} -Djava.util.logging.config.file={} -Xms2g ca.bc.gov.catchment.scripts.IdentifyRidgeSticks -i {} -inTable {} -tinPolysFilename {} -tinPolysTable {} -o {} -outTable {} -maxSlope {} {}".format(settings.get("java_path"), settings.get("java_classpath"), settings.get("log_properties_file"), tin_gpkg_filename_with_path, TIN_EDGES_TABLE, tin_gpkg_filename_with_path, TIN_POLYS_TABLE, ridge_sticks_gpkg_filename_with_path, RIDGE_STICKS_TABLE, ridge_stick_max_slope, bbox)
      resp = call(cmd9b.split())
      if resp != 0:
        print("Failure.  Pipeline execution stopped early.")
        exit(1);
    else:
      print("{} already exists".format(ridge_sticks_gpkg_filename_with_path))
    
    if not os.path.exists(ridge_clumps_gpkg_filename_with_path):
      print("Building ridge clumps")
      cmd9b = "{} -cp {} -Djava.util.logging.config.file={} -Xms2g ca.bc.gov.catchment.scripts.RidgeSticks2Clumps -i {} -inTable {} -waterFile {} -waterTable {} -o {} -outTable {} -minArea {} -bufferDistance {} {}".format(settings.get("java_path"), settings.get("java_classpath"), settings.get("log_properties_file"), ridge_sticks_gpkg_filename_with_path, RIDGE_STICKS_TABLE, water_feature_segmented_filename_with_path, WATER_FEATURES_TABLE, ridge_clumps_gpkg_filename_with_path, RIDGE_CLUMPS_TABLE, ridge_clump_min_area, ridge_stick_buffer_distance, bbox)
      resp = call(cmd9b.split())
      if resp != 0:
        print("Failure.  Pipeline execution stopped early.")
        exit(1);
    else:
      print("{} already exists".format(ridge_clumps_gpkg_filename_with_path))

    step_10_end = time.time()
    step_10_duration_seconds = int(step_10_end - step_10_start)


  #grow catchments
  if args.start_step <= 10 and 10 <= args.last_step:
    print("")  
    print("---------------------------------------------------")
    print(" Step 10: Grow Catchments")
    print("---------------------------------------------------")
    print("")  

    step_10_start = time.time()

    #data_bbox = "1681352.8,507263.1,1681642.0,507479.9" #test case 1
    #data_bbox = "1683985.5,504660.8,1684098.9,504759.4" #test case 2
    #data_bbox = "1680546.3,501755.5,1682284.9,503082.5" #small
    #data_bbox = "1673235.6,499766.7,1679748.7,504957.6" #medium
    #data_bbox = "1669296.2,499296.8,1684879.1,508149.5" #almost 1 full mapsheet
    data_bbox = "1669146.7,494777.6,1685857.3,512827.4" #full 82f017 mapsheet
    data_bbox_crs = "EPSG:3005"
    print("NOTE: custom bbox: {}".format(data_bbox))

    bbox = "-bbox {} -bboxcrs {}".format(data_bbox, data_bbox_crs)

    if not os.path.exists(grown_catchments_raw_gpkg_filename_with_path):
      print("Growing catchments")
      cmd9 = "{} -cp {} -Djava.util.logging.config.file={} -Xms2g ca.bc.gov.catchment.scripts.GrowCatchments -waterFile {} -waterTable {} -tinEdgesFile {} -tinEdgesTable {} -o {} -outTable {} {}".format(settings.get("java_path"), settings.get("java_classpath"), settings.get("log_properties_file"), water_feature_segmented_filename_with_path, WATER_FEATURES_TABLE, tin_gpkg_filename_with_path, TIN_EDGES_TABLE, grown_catchments_raw_gpkg_filename_with_path, CATCHMENT_LINES_TABLE, bbox)
      resp = call(cmd9.split())
      if resp != 0:
        print("Failure.  Pipeline execution stopped early.")
        exit(1);
    else:
      print("{} already exists".format(grown_catchments_gpkg_filename_with_path))

    
    if not os.path.exists(grown_catchments_gpkg_filename_with_path):
      print("Cleaning catchments")
      cmd9b = "{} -cp {} -Djava.util.logging.config.file={} -Xms2g ca.bc.gov.catchment.scripts.CleanGrownCatchments -waterFile \"{}\" -waterTable {} -i {} -inTable {} -o {} -outTable {} {}".format(settings.get("java_path"), settings.get("java_classpath"), settings.get("log_properties_file"), water_feature_segmented_filename_with_path, WATER_FEATURES_TABLE, grown_catchments_raw_gpkg_filename_with_path, CATCHMENT_LINES_TABLE, grown_catchments_gpkg_filename_with_path, CATCHMENT_LINES_TABLE, bbox)
      resp = call(cmd9b.split())
      if resp != 0:
        print("Failure.  Pipeline execution stopped early.")
        exit(1);
    else:
      print("{} already exists".format(grown_catchments_gpkg_filename_with_path))
    
    step_10_end = time.time()
    step_10_duration_seconds = int(step_10_end - step_10_start)

  #cosmetic adjustments
  if args.start_step <= 11 and 11 <= args.last_step:
    print("")  
    print("---------------------------------------------------")
    print(" Step 11: Cosmetic adjustments")
    print("---------------------------------------------------")
    print("")  

    #smoothing
    print("Smoothing catchments")
    cmd6 = "{} -cp {} ca.bc.gov.catchment.scripts.SmoothGeometries -i {} -inTable {} -o {} -outTable {} -alg {}".format(settings.get("java_path"), settings.get("java_classpath"), grown_catchments_gpkg_filename_with_path, CATCHMENT_LINES_TABLE, catchments_smoothed_gpkg_filename_with_path, CATCHMENT_LINES_TABLE, SMOOTHING_ALGORITHM)

    resp = call(cmd6.split())
    if resp != 0:
      print("Failure.  Pipeline execution stopped early.")
      exit(1);

  print("---------------------------------------------------")
  print("")
  print("Pipeline done")


if __name__ == "__main__":
  main()