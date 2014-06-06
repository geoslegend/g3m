

package org.glob3.mobile.tools;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.InvalidPropertiesFormatException;
import java.util.List;
import java.util.Properties;

import org.glob3.mobile.generated.Angle;
import org.glob3.mobile.generated.GEOJSONParser;
import org.glob3.mobile.generated.Geodetic2D;
import org.glob3.mobile.generated.IFactory;
import org.glob3.mobile.generated.IJSONParser;
import org.glob3.mobile.generated.ILogger;
import org.glob3.mobile.generated.IMathUtils;
import org.glob3.mobile.generated.IStringBuilder;
import org.glob3.mobile.generated.LayerTilesRenderParameters;
import org.glob3.mobile.generated.LogLevel;
import org.glob3.mobile.generated.Sector;
import org.glob3.mobile.specific.Factory_JavaDesktop;
import org.glob3.mobile.specific.JSONParser_JavaDesktop;
import org.glob3.mobile.specific.Logger_JavaDesktop;
import org.glob3.mobile.specific.MathUtils_JavaDesktop;
import org.glob3.mobile.specific.StringBuilder_JavaDesktop;
import org.glob3.mobile.tools.conversion.jbson2bjson.JBson2BJson;
import org.glob3.mobile.tools.conversion.jbson2bjson.JBson2BJsonException;


public class MergedVectorialLOD {

   //-- Internal constants definition ------------------------------------------------------------------

   final static String  PARAMETERS_FILE         = "parameters.merged.xml";
   final static String  METADATA_FILENAME       = "metadata.json";
   final static String  EMPTY_GEOJSON           = "{\"type\":\"FeatureCollection\",\"features\":null}";
   final static String  INTERNAL_SRID           = "4326";
   final static String  MERCATOR_PYRAMID        = "MERCATOR";
   final static String  WGS84_PYRAMID           = "WGS84";

   final static double  OVERLAP_PERCENTAGE      = 5.0;
   final static int     CONNECTION_TIMEOUT      = 5;                                                   //seconds
   final static int     PIXELS_PER_TILE         = 256;
   final static int     SQUARED_PIXELS_PER_TILE = (int) Math.pow(
                                                         (PIXELS_PER_TILE + (PIXELS_PER_TILE * ((2 * OVERLAP_PERCENTAGE) / 100))),
                                                         2);
   final static long    VERTEX_THRESHOLD        = 10000;
   final static int     INITIAL_AREA_FACTOR     = 3;
   final static int     MAX_TUNNING_ATTEMPS     = 10;

   final static boolean VERBOSE                 = false;

   private enum GeomType {
      POINT,
      LINESTRING,
      MULTILINESTRING,
      POLYGON,
      MULTIPOLYGON
   }

   //-- Data base connection parameters ----------------------------------------------------------------
   private static String                     HOST               = "igosoftware.dyndns.org";
   private static String                     PORT               = "5414";
   private static String                     USER               = "postgres";
   private static String                     PASSWORD           = "postgres1g0";
   private static String                     DATABASE_NAME      = "vectorial_test";

   //-- Vectorial LOD generation algorithm parameters --------------------------------------------------
   private static float                      QUALITY_FACTOR     = 1.0f;
   private static boolean                    MERCATOR           = true;                         // MERCATOR: EPSG:3857, EPSG:900913 (Google)
   private static int                        FIRST_LEVEL        = 0;
   private static int                        MAX_LEVEL          = 3;
   private static int                        MAX_DB_CONNECTIONS = 2;
   private static String                     OUTPUT_FORMAT      = "geojson";                    // valid values: geojson, geobson, both
   private static String                     ROOT_FOLDER        = "LOD";

   //-- Data source and filter parameters --------------------------------------------------------------
   private static String[]                   DATABASE_TABLES;
   private static String[]                   FILTER_CRITERIA;
   private static String[][]                 PROPERTIES;

   //-- Common variables for all data sources -----------------------------------------------------------
   private static DataBaseService            _dataBaseService   = null;
   private static String                     _lodFolder         = null;
   private static String                     _geojsonFolder     = null;
   private static String                     _geobsonFolder     = null;
   private static String                     _metadataFileName  = null;
   private static GConcurrentService         _concurrentService;
   private static LayerTilesRenderParameters _renderParameters;
   private static String                     _projection        = null;
   private static int                        _firstLevelCreated = 0;
   private static int                        _lastLevelCreated  = 0;

   //-- Different variables for any data source ---------------------------------------------------------
   private static Sector                     _globalBoundSector = TileSector.FULL_SPHERE_SECTOR;
   //   private static GeomType                   _geomType          = null;
   //   private static String                     _theGeomColumnName = null;                         //"the_geom"; 
   //   private static String                     _geomSRID          = null;

   //-- New for merged vectorial LOD ---------------------------------------------------

   private static List<DataSource>           _dataSources       = new ArrayList<DataSource>();

   //----------------------------------------------------------------------------------

   /*
    * For handling postgis database access and connections
    */
   private static class DataBaseService {

      public final String               _host;
      public final String               _port;
      public final String               _user;
      public final String               _password;
      public final String               _dbName;
      private final java.sql.Connection _connectionPool[];
      private int                       _poolIndex;


      public DataBaseService(final String host,
                             final String port,
                             final String user,
                             final String password,
                             final String dbName) {

         _host = host;
         _port = port;
         _user = user;
         _password = password;
         _dbName = dbName;
         _connectionPool = createConnectionPool();
         _poolIndex = MAX_DB_CONNECTIONS - 1;
      }


      private java.sql.Connection[] createConnectionPool() {

         final java.sql.Connection connPool[] = new java.sql.Connection[MAX_DB_CONNECTIONS];

         /* 
          * Load the JDBC driver and establish a connection. 
          */
         try {
            Class.forName("org.postgresql.Driver");
            //         final String url = "jdbc:postgresql://192.168.1.14:5432/vectorial_test";
            final String connectUrl = "jdbc:postgresql://" + _host + ":" + _port + "/" + _dbName;
            //         System.out.println("connectUrl: " + connectUrl);

            /* Create connection 
             */
            //_conn = DriverManager.getConnection(connectUrl, "postgres", "postgres1g0");
            for (int i = 0; i < MAX_DB_CONNECTIONS; i++) {
               connPool[i] = DriverManager.getConnection(connectUrl, _user, _password);
            }
         }
         catch (final ClassNotFoundException e) {
            ILogger.instance().logError("class not found error: " + e.getMessage());
         }
         catch (final SQLException e) {
            ILogger.instance().logError("SQL error creating connection: " + e.getMessage());
         }

         return connPool;
      }


      public java.sql.Connection getConnection() {

         _poolIndex = (_poolIndex + 1) % MAX_DB_CONNECTIONS;

         return _connectionPool[_poolIndex];
      }


      public void releaseConnections() {

         try {
            for (final Connection conn : _connectionPool) {
               conn.close();
            }
         }
         catch (final SQLException e) {
            ILogger.instance().logError("SQL error releasing connection: " + e.getMessage());
         }

      }
   }


   public static boolean createDataBaseService(final String host,
                                               final String port,
                                               final String user,
                                               final String password,
                                               final String dataBaseName) {

      MAX_DB_CONNECTIONS = _concurrentService.getThreadsNumber();
      _dataBaseService = new DataBaseService(host, port, user, password, dataBaseName);

      // Check one of the service connections before return 
      try {
         final Connection conn = _dataBaseService.getConnection();
         if ((conn != null) && conn.isValid(CONNECTION_TIMEOUT)) {
            return true;
         }
      }
      catch (final SQLException e) {
         e.printStackTrace();
      }

      return false;
   }


   private static class DataSource {

      public final String   _sourceTable;
      public final String   _geomFilterCriteria;
      public final String[] _includeProperties;
      public TileSector     _boundSector       = TileSector.FULL_SPHERE_SECTOR;
      public GeomType       _geomType          = null;
      public String         _theGeomColumnName = null;
      public String         _geomSRID          = null;


      public DataSource(final String sourceTable,
                        final String geomFilterCriteria,
                        final String... includeProperties) {

         _sourceTable = sourceTable;
         _geomFilterCriteria = geomFilterCriteria;
         _includeProperties = includeProperties;
      }


      //      public DataSource(final TileSector boundSector,
      //                        final GeomType geomType,
      //                        final String theGeomColumnName,
      //                        final String geomSRID,
      //                        final String sourceTable,
      //                        final String geomFilterCriteria,
      //                        final String... includeProperties) {
      //
      //         _boundSector = boundSector;
      //         _geomType = geomType;
      //         _theGeomColumnName = theGeomColumnName;
      //         _geomSRID = geomSRID;
      //         _sourceTable = sourceTable;
      //         _geomFilterCriteria = geomFilterCriteria;
      //         _includeProperties = includeProperties;
      //      }


      public void setBoundSector(final TileSector boundSector) {
         _boundSector = boundSector;
      }


      public void setGeomType(final GeomType geomType) {
         _geomType = geomType;
      }


      public void setTheGeomColumnName(final String theGeomColumnName) {
         _theGeomColumnName = theGeomColumnName;
      }


      public void setGeomSRID(final String geomSRID) {
         _geomSRID = geomSRID;
      }

   }


   /**
    * 
    * @param dataSourceTable
    *           : table from postgis database containing the vectorial data
    * @param sector
    *           : geometry bounding sector for any of the tiles
    * @param qualityFactor
    *           : value used to adjust simplification tolerance during Douglas-Peucker simplification. Greater values entail less
    *           tolerance, and so on less vertex filtered and more vertex generate for the resultant geometry. Usual values
    *           between 1.0 to 10.0.
    * @param geomFilterCriteria
    *           : filter criteria using pure database query format that will be included in a where clause. i.e. "continent" like
    *           'Euro%' AND "pop_est" > 10000000"
    * @param includeProperties
    *           : fields/columns associated to the vectorial data that shall be included as feature properties in the resultant
    *           geoJson data.
    * @return : String with the vectorial data in geoJson format.
    * 
    */
   public static String selectGeometries(final DataSource dataSource,
                                         final Sector sector,
                                         final float qualityFactor,
                                         final String geomFilterCriteria,
                                         final String... includeProperties) {

      String geoJsonResult = null;
      int areaFactor = INITIAL_AREA_FACTOR;
      float qf = qualityFactor;

      try {
         // -- query example --
         // --SELECT row_to_json(fc) FROM ( SELECT 'FeatureCollection' As type, array_to_json(array_agg(f)) As features FROM ( SELECT 'Feature' As type, ST_AsGeoJSON(sg)::json As geometry, row_to_json((SELECT l FROM (SELECT "mapcolor7", "scalerank") As l)) As properties FROM ( SELECT ST_SimplifyPreserveTopology(ST_Intersection(the_geom,bbox),0.091) as sg, "mapcolor7", "scalerank" FROM ne_10m_admin_0_countries WHERE ST_Intersects(the_geom,bbox) and ST_Area(Box2D(the_geom))>0.078 and true ) As lg ) As f ) As fc;
         //-------------------

         //-- full query for geometry select
         String fullQuery = buildSelectQuery(dataSource, sector, qf, areaFactor);

         if (fullQuery == null) {
            ILogger.instance().logError("Invalid data for sector: " + sector.toString() + ". ");
            return null;
         }

         //System.out.println("fullQuery: " + fullQuery);

         // first attempt: usual parameters
         geoJsonResult = executeQuery(fullQuery);

         if (geoJsonResult == null) {
            return null;
         }

         long numVertex = getGeomVertexCount(geoJsonResult);

         if ((numVertex <= VERTEX_THRESHOLD) || dataSource._geomType.equals(GeomType.POINT)) {
            return geoJsonResult;
         }

         //ILogger.instance().logWarning("Too much vertex for sector, area tunning: " + sector.toString());

         final int areaStep = 1;
         final float qfStep = 2.0f;
         int numAttepms = 0;
         boolean optimizeArea = true;

         //tunning loop
         while (numVertex > VERTEX_THRESHOLD) {

            ILogger.instance().logWarning("Too much vertex (" + numVertex + ") for sector: " + sector.toString());

            //to force alternative optimization. first attemp, try area; second attempt try quality factor
            if (optimizeArea) {
               // second attempt: increase area filter factor
               areaFactor += areaStep;
            }
            else {
               // third attempt: reduce quality factor
               qf = qf / qfStep;
            }

            fullQuery = buildSelectQuery(dataSource, sector, qf, areaFactor);

            geoJsonResult = executeQuery(fullQuery);

            if (geoJsonResult == null) {
               return null;
            }

            numVertex = getGeomVertexCount(geoJsonResult);
            if ((numVertex <= VERTEX_THRESHOLD) || (numAttepms >= MAX_TUNNING_ATTEMPS)) {
               return geoJsonResult;
            }

            numAttepms++;
            optimizeArea = !optimizeArea;
         }

         return geoJsonResult;
      }
      catch (final SQLException e) {
         ILogger.instance().logError("SQL error getting data for sector: " + sector.toString() + ". " + e.getMessage());
      }

      return geoJsonResult;
   }


   private static String executeQuery(final String query) throws SQLException {

      String result = null;

      final Connection conn = _dataBaseService.getConnection();
      final Statement st = conn.createStatement();

      final ResultSet rs = st.executeQuery(query);

      if (!rs.next()) {
         st.close();
         return null; //no data on this bbox
      }

      result = rs.getString(1);
      st.close();

      //      if (result.contains("null")) {
      if ((isEmptyString(result)) || result.contains(EMPTY_GEOJSON)) {
         return null;
      }

      return result;
   }


   private static long getGeomVertexCount(final String geoJson) {

      return GEOJSONParser.parseJSON(geoJson, false).getCoordinatesCount();
   }


   //--
   //-- Release 4.6: fix bug when includeProperties=null
   //--
   public static String buildSelectQuery(final DataSource dataSource,
                                         final Sector sector,
                                         final float qualityFactor,
                                         final double areaFactor) {

      //--i.e: SELECT row_to_json(fc) FROM ( SELECT 'FeatureCollection' As type, array_to_json(array_agg(f)) As features FROM ( SELECT 'Feature' As type, ST_AsGeoJSON(sg)::json As geometry, row_to_json((SELECT l FROM (SELECT "mapcolor7", "scalerank") As l)) As properties FROM ( SELECT ST_SimplifyPreserveTopology(ST_Intersection(the_geom,bbox),0.091) as sg, "mapcolor7", "scalerank" FROM ne_10m_admin_0_countries WHERE ST_Intersects(the_geom,bbox) and ST_Area(Box2D(the_geom))>0.078 and true ) As lg ) As f ) As fc;
      //--i.e. properties=null: SELECT row_to_json(fc) FROM ( SELECT 'FeatureCollection' As type, array_to_json(array_agg(f)) As features FROM ( SELECT 'Feature' As type, ST_AsGeoJSON(sg)::json As geometry, row_to_json(null) As properties FROM ( SELECT ST_SimplifyPreserveTopology(ST_Intersection(the_geom,ST_SetSRID(ST_MakeBox2D(ST_Point(-89.98956298828124,-90.24749095327094), ST_Point(-89.97747802734375,-84.80268998131024)),4326)),0.00966764) as sg FROM ne_10m_admin_0_countries WHERE ST_Intersects(the_geom,ST_SetSRID(ST_MakeBox2D(ST_Point(-89.98956298828124,-90.24749095327094), ST_Point(-89.97747802734375,-84.80268998131024)),4326)) and ST_Area(Box2D(the_geom))>7.46805548092793E-6) As lg ) As f ) As fc;

      String baseQuery0 = "SELECT row_to_json(fc) FROM ( SELECT 'FeatureCollection' As type, array_to_json(array_agg(f)) As features FROM ( SELECT 'Feature' As type, ST_AsGeoJSON(sg)::json As geometry, row_to_json(";
      if (dataSource._includeProperties != null) {
         baseQuery0 = baseQuery0 + "(SELECT l FROM (SELECT ";
      }
      else {
         baseQuery0 = baseQuery0 + "null";
      }

      String baseQuery1 = "";
      String baseQuery2 = "";
      if (dataSource._geomSRID.equals(INTERNAL_SRID)) {
         if (dataSource._includeProperties != null) {
            baseQuery1 = ") As l)) As properties FROM ( SELECT ST_SimplifyPreserveTopology(ST_Intersection(";
            baseQuery2 = ") as sg, ";
         }
         else {
            baseQuery1 = ") As properties FROM ( SELECT ST_SimplifyPreserveTopology(ST_Intersection(";
            baseQuery2 = ") as sg ";
         }
      }
      else {
         if (dataSource._includeProperties != null) {
            baseQuery1 = ") As l)) As properties FROM ( SELECT ST_Transform(ST_SimplifyPreserveTopology(ST_Intersection(";
            baseQuery2 = ")," + INTERNAL_SRID + ") as sg, ";
         }
         else {
            baseQuery1 = ") As properties FROM ( SELECT ST_Transform(ST_SimplifyPreserveTopology(ST_Intersection(";
            baseQuery2 = ")," + INTERNAL_SRID + ") as sg ";
         }
      }

      final String baseQuery3 = " FROM ";
      final String baseQuery4 = " WHERE ST_Intersects(";
      //final String baseQuery5 = ") and (";
      String baseQuery5 = ") and ";

      //final String baseQuery6 = ")) As lg ) As f ) As fc";
      final String baseQuery6 = ") As lg ) As f ) As fc";

      final List<Sector> extendedSector = TileSector.getExtendedSector(sector, OVERLAP_PERCENTAGE);
      final String bboxQuery = buildSectorQuery(dataSource, extendedSector);

      if (bboxQuery == null) {
         return null;
      }

      final String propsQuery = buildPropertiesQuery(dataSource._includeProperties);
      final String simplifyTolerance = Float.toString(getMaxVertexTolerance(sector, qualityFactor));
      final String filterCriteria = buildFilterCriterium(dataSource, areaFactor, bboxQuery, extendedSector);
      //         System.out.println("FILTER CRITERIA: " + filterCriteria);

      if (filterCriteria.toUpperCase().trim().startsWith("ORDER")) {
         baseQuery5 = ") ";
      }

      //-- full query final where first cut, second simplify
      final String fullQuery = baseQuery0 + propsQuery + baseQuery1 + dataSource._theGeomColumnName + "," + bboxQuery + "),"
                               + simplifyTolerance + baseQuery2 + propsQuery + baseQuery3 + dataSource._sourceTable + baseQuery4
                               + dataSource._theGeomColumnName + "," + bboxQuery + baseQuery5 + filterCriteria + baseQuery6;


      //      System.out.println("fullQuery: " + fullQuery);

      // -- query example --
      // -- SELECT row_to_json(fc) FROM ( SELECT 'FeatureCollection' As type, array_to_json(array_agg(f)) As features FROM ( SELECT 'Feature' As type, ST_AsGeoJSON(sg)::json As geometry, row_to_json((SELECT l FROM (SELECT "mapcolor7", "scalerank") As l)) As properties FROM ( SELECT ST_SimplifyPreserveTopology(ST_Intersection(the_geom,bbox),0.091) as sg, "mapcolor7", "scalerank" FROM ne_10m_admin_0_countries WHERE ST_Intersects(the_geom,bbox) and ST_Area(Box2D(the_geom))>0.078 and true ) As lg ) As f ) As fc;
      // -- SELECT row_to_json(fc) FROM ( SELECT 'FeatureCollection' As type, array_to_json(array_agg(f)) As features FROM ( SELECT 'Feature' As type, ST_AsGeoJSON(sg)::json As geometry, row_to_json(null) As properties FROM ( SELECT ST_SimplifyPreserveTopology(ST_Intersection(the_geom,ST_SetSRID(ST_MakeBox2D(ST_Point(-89.98956298828124,-90.24749095327094), ST_Point(-89.97747802734375,-84.80268998131024)),4326)),0.00966764) as sg FROM ne_10m_admin_0_countries WHERE ST_Intersects(the_geom,ST_SetSRID(ST_MakeBox2D(ST_Point(-89.98956298828124,-90.24749095327094), ST_Point(-89.97747802734375,-84.80268998131024)),4326)) and ST_Area(Box2D(the_geom))>7.46805548092793E-6) As lg ) As f ) As fc;
      //-------------------

      return fullQuery;
   }


   private static TileSector getGeometriesBound(final DataSource dataSource) {

      TileSector boundSector = TileSector.FULL_SPHERE_SECTOR;
      Connection conn = null;
      Statement st = null;
      try {
         conn = _dataBaseService.getConnection();
         st = conn.createStatement();

         String bboxQuery = "";
         //System.out.println("bboxQuery: " + bboxQuery);
         if (dataSource._geomSRID.equals(INTERNAL_SRID)) {
            bboxQuery = "SELECT Box2D(ST_Extent(" + dataSource._theGeomColumnName + ")) from " + dataSource._sourceTable;
         }
         else {
            //-- SELECT Box2D(ST_Transform(ST_SetSRID(ST_Extent(way),900913),4326)) FROM planet_osm_polygon
            bboxQuery = "SELECT Box2D(ST_Transform(ST_SetSRID(ST_Extent(" + dataSource._theGeomColumnName + "),"
                        + dataSource._geomSRID + ")," + INTERNAL_SRID + ")) FROM " + dataSource._sourceTable;
         }

         //         System.out.println("bboxQuery: " + bboxQuery);

         final ResultSet rs = st.executeQuery(bboxQuery);

         if (!rs.next()) {
            st.close();
            return TileSector.FULL_SPHERE_SECTOR;
         }

         final String bboxStr = rs.getString(1);
         st.close();

         boundSector = parseBoundSectorFromBbox(bboxStr);
         //System.out.println("boundSector: " + boundSector.toString());
      }
      catch (final SQLException e) {
         ILogger.instance().logError("SQL error getting geometry bound sector: " + e.getMessage());
      }

      return boundSector;
   }


   private static TileSector parseBoundSectorFromBbox(final String bbox) {

      if ((bbox == null) || bbox.equals("")) {
         return TileSector.FULL_SPHERE_SECTOR;
      }

      System.out.println("    Source data bound: " + bbox);

      final int begin = bbox.indexOf("(") + 1;
      final int end = bbox.indexOf(")") - 1;
      final String subBbox = bbox.substring(begin, end);
      final String[] points = subBbox.split(",");
      final String[] lowerStr = points[0].split(" ");
      final String[] upperStr = points[1].split(" ");

      final Geodetic2D lower = new Geodetic2D(Angle.fromDegrees(Double.parseDouble(lowerStr[1])),
               Angle.fromDegrees(Double.parseDouble(lowerStr[0])));
      final Geodetic2D upper = new Geodetic2D(Angle.fromDegrees(Double.parseDouble(upperStr[1])),
               Angle.fromDegrees(Double.parseDouble(upperStr[0])));

      return new TileSector(lower, upper, null, 0, 0, 0);
   }


   private static Sector getGlobalBoundSector(final List<DataSource> dataSources) {

      Sector globalSector = dataSources.get(0)._boundSector;

      for (int index = 1; index < dataSources.size(); index++) {
         globalSector = globalSector.mergedWith(dataSources.get(index)._boundSector);
      }
      System.out.println();
      return globalSector;
   }


   private static GeomType getGeometriesType(final DataSource dataSource) {

      //http://postgis.net/docs/GeometryType.html
      //select GeometryType(way) from planet_osm_polygon LIMIT 1;

      final String geomQuery = "SELECT type FROM geometry_columns WHERE f_table_name='" + dataSource._sourceTable + "'";
      final String auxGeomQuery = "SELECT GeometryType(" + dataSource._theGeomColumnName + ") FROM " + dataSource._sourceTable
                                  + " LIMIT 1";

      try {
         final Connection conn = _dataBaseService.getConnection();
         final Statement st = conn.createStatement();

         ResultSet rs = st.executeQuery(geomQuery);

         if (!rs.next()) {
            st.close();
            return null;
         }

         String geomTypeStr = rs.getString(1);

         final GeomType geomType = parseGeometryType(geomTypeStr);

         if (geomType != null) {
            st.close();
            return geomType;
         }

         ILogger.instance().logWarning("Unknown geometry type. Attempt alternative strategy.");

         //-- alternative strategy for unknown geometry types. Query to any of the rows
         rs = st.executeQuery(auxGeomQuery);

         if (!rs.next()) {
            st.close();
            return null;
         }

         geomTypeStr = rs.getString(1);
         st.close();

         return parseGeometryType(geomTypeStr);

      }
      catch (final SQLException e) {
         ILogger.instance().logError("SQL error getting geometry type: " + e.getMessage());
      }
      return null;
   }


   private static GeomType parseGeometryType(final String type) {

      GeomType geomType = null;

      if (type.trim().equalsIgnoreCase("POLYGON")) {
         geomType = GeomType.POLYGON;
      }
      else if (type.trim().equalsIgnoreCase("MULTIPOLYGON")) {
         geomType = GeomType.MULTIPOLYGON;
      }
      else if (type.trim().equalsIgnoreCase("LINESTRING")) {
         geomType = GeomType.LINESTRING;
      }
      else if (type.trim().equalsIgnoreCase("MULTILINESTRING")) {
         geomType = GeomType.MULTILINESTRING;
      }
      else if (type.trim().equalsIgnoreCase("POINT")) {
         geomType = GeomType.POINT;
      }

      return geomType;
   }


   private static String buildPropertiesQuery(final String... includeProperties) {

      if (includeProperties == null) {
         //return "false";
         return "";
      }

      String result = "";
      boolean first = true;

      for (final String prop : includeProperties) {
         if (first) {
            result = getQuotedString(prop);
            first = false;
         }
         else {
            result = result + ", " + getQuotedString(prop);
         }
      }

      return result;
   }


   private static String getQuotedString(final String data) {

      return "\"" + data.trim() + "\"";
   }


   //   /*
   //    * version 0.14: 
   //    * - tolerance obtained from the sector area downscaled by squared pixels per tile. 
   //    * - qualityFactor to adjust tolerance (usual values: from 1.0 to 10.0)
   //    *   greater values entail less tolerance on Douglas-Peuker algorithm and 
   //    *   so on less simplification and more vertex generated as result.
   //    */
   //   private static float getMaxVertexTolerance(final List<Sector> extendedSector,
   //                                              final float qualityFactor) {
   //
   //      final float tolerance2 = (float) Math.sqrt((TileSector.getAngularAreaInSquaredDegrees(extendedSector) / (qualityFactor * SQUARED_PIXELS_PER_TILE)));
   //      //System.out.println("tolerance2: " + tolerance2);
   //
   //      return tolerance2;
   //   }


   /*
    * version 0.13: 
    * - tolerance obtained from the sector deltaLongitude downscaled by 500. 
    * - qualityFactor to adjust tolerance (usual values: from 1.0 to 10.0)
    *   greater values entail less tolerance on Douglas-Peuker algorithm and 
    *   so on less simplification and more vertex generated as result.
    */
   private static float getMaxVertexTolerance(final Sector sector,
                                              final float qualityFactor) {

      final double hypotenuse = Math.sqrt(Math.pow(sector._deltaLatitude._degrees, 2)
                                          + Math.pow(sector._deltaLongitude._degrees, 2));

      final float tolerance = (float) (hypotenuse / (qualityFactor * 512f));

      if (VERBOSE) {
         System.out.println("tolerance: " + tolerance);
      }

      return tolerance;
   }


   private static String buildFilterCriterium(final DataSource dataSource,
                                              final double areaFactor,
                                              final String bboxQuery,
                                              final List<Sector> extendedSector) {

      //http://postgis.refractions.net/documentation/manual-1.4/ST_NPoints.html
      //http://postgis.refractions.net/docs/ST_Extent.html
      //http://postgis.refractions.net/docs/ST_Area.html

      if (dataSource._geomType == null) {
         return dataSource._geomFilterCriteria;
      }

      if (dataSource._geomType == GeomType.POINT) {
         return dataSource._geomFilterCriteria;
      }

      final double sectorArea = TileSector.getAngularAreaInSquaredDegrees(extendedSector);
      final double factor = areaFactor * areaFactor;

      //final String andFilter = (filterCriteria.equalsIgnoreCase("true")) ? "" : " and " + filterCriteria;
      String andFilter = "";
      if (!dataSource._geomFilterCriteria.trim().equalsIgnoreCase("true")) {
         andFilter = (dataSource._geomFilterCriteria.toUpperCase().trim().startsWith("ORDER")) ? " "
                                                                                                 + dataSource._geomFilterCriteria
                                                                                              : " and "
                                                                                                + dataSource._geomFilterCriteria;
      }

      return "ST_Area(Box2D(" + dataSource._theGeomColumnName + "))>"
             + Double.toString(factor * (sectorArea / SQUARED_PIXELS_PER_TILE)) + andFilter;

      //-- only for release 2.0 of buildSelectQuery()
      //      return "ST_Area(Box2D(sg))>" + Double.toString(factor * (sectorArea / SQUARED_PIXELS_PER_TILE)) + " and " + filterCriteria;

      //--------------------------------------------

      //      return "ST_Area(Box2D(ST_Intersection(" + _theGeomColumnName + "," + bboxQuery + ")))>"
      //             + Double.toString(factor * (sectorArea / SQUARED_PIXELS_PER_TILE)) + " and " + filterCriteria;

      //      return "ST_Area(ST_Intersection(ST_SetSRID(Box2D(" + _theGeomColumnName + "),4326)," + bboxQuery + "))>"
      //             + Double.toString(factor * (sectorArea / SQUARED_PIXELS_PER_TILE)) + " and " + filterCriteria;

      // ST_Area(Box2D(ST_Intersection(the_geom,ST_SetSRID(ST_MakeBox2D(ST_Point(-49.5,38.426561832270956), ST_Point(4.5,69.06659668046103)),4326))))>0.08169412
   }


   private static String buildSectorQuery(final DataSource dataSource,
                                          final List<Sector> extendedSector) {

      //SELECT row_to_json(fc) FROM ( SELECT 'FeatureCollection' As type, array_to_json(array_agg(f)) As features FROM (SELECT 'Feature' As type, ST_AsGeoJSON(ST_SimplifyPreserveTopology(ST_Intersection(lg.the_geom,ST_Union(ST_SetSRID(ST_MakeBox2D(ST_Point(175.5,-94.5), ST_Point(180.0,-40.5)),4326),ST_SetSRID(ST_MakeBox2D(ST_Point(-180.0,-94.5), ST_Point(-130.5,-40.5)),4326))),0.027))::json As geometry, row_to_json((SELECT l FROM (SELECT "continent", "pop_est") As l)) As properties FROM (SELECT * FROM ne_10m_admin_0_countries WHERE (true)) As lg WHERE ST_Intersects(the_geom,ST_Union(ST_SetSRID(ST_MakeBox2D(ST_Point(175.5,-94.5), ST_Point(180.0,-40.5)),4326),ST_SetSRID(ST_MakeBox2D(ST_Point(-180.0,-94.5), ST_Point(-130.5,-40.5)),4326)))) As f ) As fc;
      //ST_Union(ST_SetSRID(ST_MakeBox2D(ST_Point(175.5,-94.5), ST_Point(180.0,-40.5)),4326),ST_SetSRID(ST_MakeBox2D(ST_Point(-180.0,-94.5), ST_Point(-130.5,-40.5)),4326))

      if ((extendedSector.size() < 1) || (extendedSector.size() > 2)) {
         return null;
      }

      if (extendedSector.size() == 1) {

         return buildSectorQuery(dataSource._geomSRID, extendedSector.get(0));
      }

      final String resultQuery = "ST_Union(" + buildSectorQuery(dataSource._geomSRID, extendedSector.get(0)) + ","
                                 + buildSectorQuery(dataSource._geomSRID, extendedSector.get(1)) + ")";

      return resultQuery;
   }


   private static String buildSectorQuery(final String srid,
                                          final Sector sector) {

      String resultQuery = "ST_SetSRID(ST_MakeBox2D(ST_Point(";
      resultQuery = resultQuery + Double.toString(sector._lower._longitude._degrees) + ","
                    + Double.toString(sector._lower._latitude._degrees) + "), ST_Point("
                    + Double.toString(sector._upper._longitude._degrees) + ","
                    + Double.toString(sector._upper._latitude._degrees) + ")),4326)";

      if (!srid.equals(INTERNAL_SRID)) {
         resultQuery = "ST_Transform(" + resultQuery + "," + srid + ")";
      }

      //System.out.println("BOX QUERY: " + resultQuery);

      return resultQuery;
   }


   private static String getGeometryColumnName(final String dataSourceTable) {

      final String geomQuery = "SELECT f_geometry_column FROM geometry_columns WHERE f_table_name='" + dataSourceTable + "'";
      //System.out.println("getGeometryColumnNameQuery: " + geomQuery);

      Connection conn = null;
      Statement st = null;
      ResultSet rs = null;
      try {
         conn = _dataBaseService.getConnection();
         st = conn.createStatement();
         rs = st.executeQuery(geomQuery);

         if (!rs.next()) {
            st.close();
            return null;
         }

         final String geomColumnName = rs.getString(1);
         st.close();

         return geomColumnName;
      }
      catch (final SQLException e) {
         ILogger.instance().logError("SQL error getting geometry column: " + e.getMessage());
      }
      return null;
   }


   private static String getGeometriesSRID(final DataSource dataSource) {

      final String sridQuery = "SELECT srid FROM geometry_columns WHERE f_table_name='" + dataSource._sourceTable + "'";
      final String auxSridQuery = "SELECT ST_SRID(" + dataSource._theGeomColumnName + ") FROM " + dataSource._sourceTable
                                  + " LIMIT 1";

      try {
         final Connection conn = _dataBaseService.getConnection();
         final Statement st = conn.createStatement();

         ResultSet rs = st.executeQuery(sridQuery);

         int geomSRID = 0;
         if (rs.next()) {
            geomSRID = rs.getInt(1);

            if (isValidSRID(geomSRID)) {
               st.close();
               return Integer.toString(geomSRID);
            }
         }

         ILogger.instance().logWarning("Unknown SRID: " + geomSRID + ". Attempt alternative strategy.");

         //-- alternative strategy for unknown SRIDs. Query to any of the rows
         rs = st.executeQuery(auxSridQuery);

         if (!rs.next()) {
            st.close();
            return null;
         }
         geomSRID = rs.getInt(1);
         st.close();

         if (isValidSRID(geomSRID)) {
            return Integer.toString(geomSRID);
         }

         return null;
      }
      catch (final SQLException e) {
         ILogger.instance().logError("SQL error getting geometries SRID: " + e.getMessage());
      }
      return null;
   }


   private static boolean isValidSRID(final int srid) {

      //-- SELECT COUNT(srid) from spatial_ref_sys WHERE srid='4326'
      final String sridQuery = "SELECT COUNT(srid) from spatial_ref_sys WHERE srid='" + Integer.toString(srid) + "'";

      Connection conn = null;
      Statement st = null;
      ResultSet rs = null;
      try {
         conn = _dataBaseService.getConnection();
         st = conn.createStatement();
         rs = st.executeQuery(sridQuery);

         if (!rs.next()) {
            st.close();
            return false;
         }

         final int containSRID = rs.getInt(1);
         st.close();

         return (containSRID > 0);
      }
      catch (final SQLException e) {
         ILogger.instance().logError("SQL during SRID validation: " + e.getMessage());
      }

      return false;
   }


   //   private static String getGeometriesSRID(final String dataSourceTable) {
   //
   //      //-- SELECT Find_SRID('public', 'tiger_us_state_2007', 'the_geom_4269')
   //      final String sridQuery = "SELECT Find_SRID('public', '" + dataSourceTable + "', '" + _theGeomColumnName + "')";
   //      //System.out.println("sridQuery: " + sridQuery);
   //
   //      try {
   //         final Connection conn = _dataBaseService.getConnection();
   //         final Statement st = conn.createStatement();
   //
   //         final ResultSet rs = st.executeQuery(sridQuery);
   //
   //         if (!rs.next()) {
   //            st.close();
   //            return null;
   //         }
   //
   //         final int geomSRID = rs.getInt(1);
   //
   //         return Integer.toString(geomSRID);
   //      }
   //      catch (final SQLException e) {
   //         ILogger.instance().logError("SQL error getting SRID: " + e.getMessage());
   //      }
   //      return null;
   //   }


   private static String getTimeMessage(final long ms) {
      return getTimeMessage(ms, true);
   }


   //   public String getSectorString(final Sector sector) {
   //      return "Sector [level=" + sector._level + ", row=" + sector._row + ", column=" + sector._column+"]";
   //   }

   private static String getTimeMessage(final long ms,
                                        final boolean rounded) {
      if (ms < 1000) {
         return ms + "ms";
      }

      if (ms < 60000) {
         final double seconds = ms / 1000d;
         return (rounded ? Math.round(seconds) : seconds) + "s";
      }

      final long minutes = ms / 60000;
      final double seconds = (ms - (minutes * 60000d)) / 1000d;
      if (seconds <= 0) {
         return minutes + "m";
      }
      return minutes + "m " + (rounded ? Math.round(seconds) : seconds) + "s";
   }


   private static String getGeojsonFileName(final TileSector sector) {

      return getFileName(sector, false);
   }


   private static String getGeobsonFileName(final TileSector sector) {

      return getFileName(sector, true);
   }


   private static String getFileName(final TileSector sector,
                                     final boolean isBson) {

      final String baseFolder = (isBson) ? _geobsonFolder : _geojsonFolder;

      final String folderName = baseFolder + File.separatorChar + sector._level;
      if (!new File(folderName).exists()) {
         new File(folderName).mkdir();
         //TODO: -- provisional: dejarlo comentado mientras generemos tiles vacios. Descomentar luego --
         //         if (sector._level <= _firstLevelCreated) {
         //            _firstLevelCreated = sector._level;
         //         }
         //         if (sector._level >= _lastLevelCreated) {
         //            _lastLevelCreated = sector._level;
         //         }
         // -------------------------------------------------------------------------
      }

      final String subFolderName = folderName + File.separatorChar + sector._column;
      if (!new File(subFolderName).exists()) {
         new File(subFolderName).mkdir();
      }

      final String fileName = (isBson) ? subFolderName + File.separatorChar + getTileBsonName(sector)
                                      : subFolderName + File.separatorChar + getTileGeojsonName(sector);

      return fileName;
   }


   private static String getTileGeojsonName(final TileSector sector) {

      //return sector._column + ".geojson";
      return sector.getRow(_renderParameters) + ".geojson";
   }


   private static String getTileBsonName(final TileSector sector) {

      //return sector._column + ".bson";
      return sector.getRow(_renderParameters) + ".geobson";
   }


   private static String getTmpGeojsonName(final TileSector sector) {

      return sector._level + "_" + sector._column + "_" + sector.getRow(_renderParameters) + ".geojson";
   }


   @SuppressWarnings("unused")
   private static String getTileLabel(final TileSector sector) {

      return sector._level + "/" + sector._column + "/" + sector.getRow(_renderParameters);
   }


   private static void createFolderStructure(final List<DataSource> dataSources) {

      if (!new File(ROOT_FOLDER).exists()) {
         new File(ROOT_FOLDER).mkdir();
      }

      //final String projection = (_renderParameters._mercator) ? "MERCATOR" : "WGS84";
      //_lodFolder = ROOT_DIRECTORY + File.separatorChar + dataSource._sourceTable + "_" + NUM_LEVELS + "-LEVELS_" + _projection;
      final String mergedName = buildMergedTableName(dataSources);
      _lodFolder = ROOT_FOLDER + File.separatorChar + mergedName + "_LEVELS_" + FIRST_LEVEL + "-" + MAX_LEVEL + "_" + _projection;

      if (!new File(_lodFolder).exists()) {
         new File(_lodFolder).mkdir();
      }

      if (generateGeojson()) {
         _geojsonFolder = _lodFolder + File.separatorChar + "GEOJSON";
         if (!new File(_geojsonFolder).exists()) {
            new File(_geojsonFolder).mkdir();
         }
      }

      if (generateGeobson()) {
         _geobsonFolder = _lodFolder + File.separatorChar + "GEOBSON";
         if (!new File(_geobsonFolder).exists()) {
            new File(_geobsonFolder).mkdir();
         }
      }
   }


   private static String buildMergedTableName(final List<DataSource> dataSources) {

      if (dataSources.size() == 1) {
         return dataSources.get(0)._sourceTable;
      }

      String mergedName = "";
      int index = 0;
      for (final DataSource ds : dataSources) {
         mergedName = mergedName + ds._sourceTable.substring(0, ds._sourceTable.length() / 2);
         if (index < (dataSources.size() - 1)) {
            mergedName = mergedName + "_";
         }
         index++;
      }

      return mergedName;
   }


   private static ArrayList<TileSector> createFirstLevelTileSectors(final List<DataSource> dataSources) {

      final ArrayList<TileSector> levelZeroTileSectors = new ArrayList<TileSector>();

      final Angle fromLatitude = _renderParameters._topSector._lower._latitude;
      final Angle fromLongitude = _renderParameters._topSector._lower._longitude;

      final Angle deltaLan = _renderParameters._topSector._deltaLatitude;
      final Angle deltaLon = _renderParameters._topSector._deltaLongitude;

      final int topSectorSplitsByLatitude = _renderParameters._topSectorSplitsByLatitude;
      final int topSectorSplitsByLongitude = _renderParameters._topSectorSplitsByLongitude;

      final Angle tileHeight = deltaLan.div(topSectorSplitsByLatitude);
      final Angle tileWidth = deltaLon.div(topSectorSplitsByLongitude);

      for (int row = 0; row < topSectorSplitsByLatitude; row++) {
         final Angle tileLatFrom = tileHeight.times(row).add(fromLatitude);
         final Angle tileLatTo = tileLatFrom.add(tileHeight);

         for (int col = 0; col < topSectorSplitsByLongitude; col++) {
            final Angle tileLonFrom = tileWidth.times(col).add(fromLongitude);
            final Angle tileLonTo = tileLonFrom.add(tileWidth);

            final Geodetic2D tileLower = new Geodetic2D(tileLatFrom, tileLonFrom);
            final Geodetic2D tileUpper = new Geodetic2D(tileLatTo, tileLonTo);
            final Sector sector = new Sector(tileLower, tileUpper);

            final TileSector tileSector = new TileSector(sector, null, 0, row, col);

            for (final DataSource ds : dataSources) {
               if (tileSector.intersects(ds._boundSector)) {
                  levelZeroTileSectors.add(tileSector);
                  break;
               }
            }
         }
      }

      return levelZeroTileSectors;
   }


   private static void launchVectorialLODProcessing(final List<DataSource> dataSources) {

      final long start = System.currentTimeMillis();
      System.out.println("Starting merged vectorial LOD generation of datasources: ");
      for (final DataSource ds : dataSources) {
         System.out.println("    " + ds._sourceTable);
      }
      System.out.println();

      createFolderStructure(dataSources);

      for (final DataSource ds : dataSources) {
         System.out.println("DATA SOURCE: " + ds._sourceTable);

         final String theGeomColumnName = getGeometryColumnName(ds._sourceTable);
         if (theGeomColumnName != null) {
            ds.setTheGeomColumnName(theGeomColumnName);
            System.out.println("    Geometry column name: " + theGeomColumnName);
         }
         else {
            System.err.println("Invalid Geometry column. Exit application.");
            System.exit(1);
         }

         final String geomSRID = getGeometriesSRID(ds);
         if (geomSRID != null) {
            ds.setGeomSRID(geomSRID);
            System.out.println("    Source data SRID: " + geomSRID);
            if (!geomSRID.equals(INTERNAL_SRID)) {
               ILogger.instance().logInfo(
                        "Source data SRID different from 4326. For performance reasons consider reprojection of source data.");
            }
         }
         else {
            System.err.println("Invalid SRID of source data. Exit application.");
            System.exit(1);
         }

         final GeomType geomType = getGeometriesType(ds);
         if (geomType != null) {
            ds.setGeomType(geomType);
            System.out.println("    Source data type: " + geomType.toString());
         }

         final TileSector boundSector = getGeometriesBound(ds);
         ds.setBoundSector(boundSector);
      }

      _globalBoundSector = getGlobalBoundSector(dataSources);

      //assume full sphere topSector for tiles pyramid generation
      final ArrayList<TileSector> firstLevelTileSectors = createFirstLevelTileSectors(dataSources);

      System.out.println("Generating.. await termination...");
      for (final TileSector sector : firstLevelTileSectors) {
         generateMergedVectorialLOD(sector, dataSources);
         //processSubSectors(sector, dataSource);
      }

      //System.out.println("Running MAIN at: " + Thread.currentThread().getName());

      _concurrentService.awaitTermination();

      _dataBaseService.releaseConnections();

      writeMetadataFile();

      final long time = System.currentTimeMillis() - start;
      System.out.println("Vectorial LOD generation finished in " + getTimeMessage(time));
   }


   private static void generateMergedVectorialLOD(final TileSector sector,
                                                  final List<DataSource> dataSources) {

      boolean containsData = true;

      if (sector._level > MAX_LEVEL) {
         return;
      }

      if (sector._level >= FIRST_LEVEL) {
         containsData = false;
         for (final DataSource ds : dataSources) {

            if (!ds._boundSector.intersects(sector)) {
               continue;
            }

            //final boolean contained = generateVectorialLOD(sector, ds);
            containsData = generateVectorialLOD(sector, ds) || containsData;
         }
      }

      if (containsData) { //stop subdivision when there are not data inside this sector
         //final List<TileSector> subSectors = sector.getSubTileSectors();
         final List<TileSector> subSectors = sector.getSubTileSectors(_renderParameters._mercator);
         for (final TileSector s : subSectors) {
            processSubSectors(s, dataSources);
         }
      }

   }


   private static boolean generateVectorialLOD(final TileSector sector,
                                               final DataSource dataSource) {

      boolean containsData = true;

      final String geoJson = selectGeometries(dataSource, //
               sector.getSector(), //
               QUALITY_FACTOR, // 
               dataSource._geomFilterCriteria, //
               dataSource._includeProperties);

      if (geoJson != null) {
         //System.out.println("Generating: ../" + getTileLabel(sector));
         writeOutputFile(geoJson, sector);
      }
      else {
         //System.out.println("Skip empty tile: ../" + getTileLabel(sector));
         containsData = sectorContainsData(dataSource, sector.getSector());
         writeEmptyFile(sector);
      }

      return containsData;
   }


   /*
    * release 0.2
    * A new task will be created to process any subsector of the parent sector
    * 
    */
   private static void processSubSectors(final TileSector sector,
                                         final List<DataSource> dataSources) {

      final int subSectorLevel = sector._level;
      if (subSectorLevel > MAX_LEVEL) {
         return;
      }

      final Runnable task = new Runnable() {
         @Override
         public void run() {
            //System.out.println("Running at: " + Thread.currentThread().getName());
            generateMergedVectorialLOD(sector, dataSources);
         }
      };

      //_concurrentService.execute(task, subSectorLevel);
      _concurrentService.execute(task);
   }


   private static boolean sectorContainsData(final DataSource dataSource,
                                             final Sector sector) {

      final String checkQuery = buildCheckQuery(dataSource, sector);
      //System.out.println("checkQuery: " + checkQuery);

      if (checkQuery == null) {
         return false;
      }

      final Connection conn = _dataBaseService.getConnection();
      try {
         final Statement st = conn.createStatement();
         final ResultSet rs = st.executeQuery(checkQuery);
         if (!rs.next()) {
            st.close();
            return false; //no data on this bbox
         }

         final int result = rs.getInt(1);
         st.close();

         //         if (result > 0) {
         //            System.out.println("SECTOR CONTAINS DATA: " + result);
         //         }
         return (result > 0);
      }
      catch (final SQLException e) {
         ILogger.instance().logError("SQL error getting geometries intersection: " + e.getMessage());
      }

      return false;
   }


   private static String buildCheckQuery(final DataSource dataSource,
                                         final Sector sector) {

      //--i.e: SELECT COUNT(the_geom) FROM roads WHERE ST_Intersects(the_geom, ST_SetSRID(ST_MakeBox2D(ST_Point(-15.5,1.43), ST_Point(15.5,50.24)),4326)) 
      final String baseQuery0 = "SELECT COUNT(";
      final String baseQuery1 = ") FROM ";
      final String baseQuery2 = " WHERE ST_Intersects(";

      final List<Sector> extendedSector = TileSector.getExtendedSector(sector, OVERLAP_PERCENTAGE);
      final String bboxQuery = buildSectorQuery(dataSource, extendedSector);

      if (bboxQuery == null) {
         return null;
      }

      final String checkQuery = baseQuery0 + dataSource._theGeomColumnName + baseQuery1 + dataSource._sourceTable + baseQuery2
                                + dataSource._theGeomColumnName + "," + bboxQuery + ")";

      return checkQuery;
   }


   private static void writeMetadataFile() {

      if (_firstLevelCreated > _lastLevelCreated) {
         _firstLevelCreated = _lastLevelCreated;
      }

      //pyramid: { type: "epsg:4326", topSector: [], splitByLatitude: 1, splitsByLongitude: 1 }

      final String EPSG = (_projection.equals(MERCATOR_PYRAMID)) ? "\"EPSG:900913\"" : "\"EPSG:4326\"";
      final String pyramid = "{ type: " + EPSG + ", topSector: [" + _renderParameters._topSector._lower._latitude._degrees + ", "
                             + _renderParameters._topSector._lower._longitude._degrees + ", "
                             + _renderParameters._topSector._upper._latitude._degrees + ", "
                             + _renderParameters._topSector._upper._longitude._degrees + "], splitsByLatitude: "
                             + _renderParameters._topSectorSplitsByLatitude + ", splitsByLongitude: "
                             + _renderParameters._topSectorSplitsByLongitude + " }";

      //      final String metadata = "{ sector: [" + _boundSector._lower._latitude._degrees + ", "
      //                              + _boundSector._lower._longitude._degrees + ", " + _boundSector._upper._latitude._degrees + ", "
      //                              + _boundSector._upper._longitude._degrees + "], minLevel: " + _firstLevelCreated + ", maxLevel: "
      //                              + _lastLevelCreated + ", projection: " + _projection + " }";

      final String metadata = "{ sector: [" + _globalBoundSector._lower._latitude._degrees + ", "
                              + _globalBoundSector._lower._longitude._degrees + ", "
                              + _globalBoundSector._upper._latitude._degrees + ", "
                              + _globalBoundSector._upper._longitude._degrees + "], minLevel: " + _firstLevelCreated
                              + ", maxLevel: " + _lastLevelCreated + ", pyramid: " + pyramid + " }";

      _metadataFileName = _lodFolder + File.separatorChar + METADATA_FILENAME;
      final File metadataFile = new File(_metadataFileName);
      try {
         if (metadataFile.exists()) {
            metadataFile.delete();
         }
         new File(_metadataFileName).createNewFile();

         final FileWriter file = new FileWriter(_metadataFileName);
         file.write(metadata);
         file.flush();
         file.close();
         System.out.println("File " + METADATA_FILENAME + " created.");
      }
      catch (final IOException e) {
         ILogger.instance().logError("IO error creating metadata file: " + e.getMessage());
      }
   }


   private static void writeEmptyFile(final TileSector sector) {

      try {
         if (generateGeojson()) {
            final File geojsonFile = new File(getGeojsonFileName(sector));
            if (!geojsonFile.exists()) {
               geojsonFile.createNewFile();
            }
         }
         if (generateGeobson()) {
            final File geobsonFile = new File(getGeobsonFileName(sector));
            if (!geobsonFile.exists()) {
               geobsonFile.createNewFile();
            }
         }
      }
      catch (final IOException e) {
         ILogger.instance().logError("IO Error writting output file: " + e.getMessage());
      }

   }


   private static void writeOutputFile(final String geoJson,
                                       final TileSector sector) {

      try {
         //TODO: -- provisional: dejarlo aqui mientras generemos tiles vacios. Quitar luego --
         if (sector._level < _firstLevelCreated) {
            _firstLevelCreated = sector._level;
         }
         if (sector._level > _lastLevelCreated) {
            _lastLevelCreated = sector._level;
         }
         // -------------------------------------------------------------------------

         final String geojsonFileName = getGeojsonFileName(sector);
         final File geojsonFile = new File(geojsonFileName);

         if (generateGeojson()) {
            //System.out.println("Generating: ../" + getTileLabel(sector) + ".geojson");
            if (!isEmptyFile(geojsonFile)) {
               addFeatureToExistingGeojsonFile(geoJson, geojsonFile);
            }
            else {
               final FileWriter file = new FileWriter(geojsonFileName);
               file.write(geoJson);
               //file.write("\n");
               file.flush();
               file.close();
            }
         }

         if (generateGeobson()) {
            final String geobsonFileName = getGeobsonFileName(sector);
            final File geobsonFile = new File(geobsonFileName);

            if (!isEmptyFile(geobsonFile)) {
               if (!isEmptyFile(geojsonFile)) {
                  addFeatureToExistingGeobsonFile(geoJson, geobsonFile, geojsonFile);
               }
               else {
                  final String tmpFileName = _lodFolder + File.separatorChar + getTmpGeojsonName(sector);
                  addFeatureToExistingGeobsonFile(geoJson, geobsonFile, new File(tmpFileName));
               }
            }
            else {
               final File bsonFile = new File(geobsonFileName);
               bsonFile.createNewFile();

               try {
                  JBson2BJson.instance().json2bson(geoJson, bsonFile, true);
                  if (!generateGeojson()) {
                     //System.out.println("Generating: ../" + getTileLabel(sector) + ".bson");
                  }
               }
               catch (final JBson2BJsonException e) {
                  ILogger.instance().logError("JBson2BJson Error generating geobson file: " + e.getMessage());
               }
            }
         }
      }
      catch (final IOException e) {
         ILogger.instance().logError("I/O Error generating output file: " + e.getMessage());
      }
   }


   private static void addFeatureToExistingGeojsonFile(final String geoJson,
                                                       final File geojsonFile) {

      final String feature = getFeatureFromGeojson(geoJson);
      //System.out.println("FEATURE: " + feature);

      try {
         String getData, putData;

         final FileReader fr = new FileReader(geojsonFile);
         final BufferedReader br = new BufferedReader(fr);

         final char fileContent[] = new char[(int) geojsonFile.length()];
         final int contentLength = br.read(fileContent);
         br.close();

         if (contentLength > 0) {
            final FileWriter fw = new FileWriter(geojsonFile);
            final BufferedWriter bw = new BufferedWriter(fw);

            getData = String.valueOf(fileContent);
            putData = getData.replace("}]}", "}, ");
            bw.write(putData);
            bw.write(feature);
            bw.write("]}");
            bw.flush();
            bw.close();
         }
      }
      catch (final IOException e) {
         ILogger.instance().logError("Error merging geojson output file: " + e.getMessage());
      }
   }


   private static String getFeatureFromGeojson(final String geoJson) {

      String feature = geoJson.replace("{\"type\":\"FeatureCollection\",\"features\":[", "");
      feature = feature.replace("}]}", "}");

      return feature;
   }


   private static void addFeatureToExistingGeobsonFile(final String geoJson,
                                                       final File geobsonFile,
                                                       final File geojsonFile) {

      try {
         if (!geojsonFile.exists()) {
            geojsonFile.createNewFile();
            JBson2BJson.instance().bson2json(geobsonFile, geojsonFile, true);
         }

         addFeatureToExistingGeojsonFile(geoJson, geojsonFile);
         JBson2BJson.instance().json2bson(geojsonFile, geobsonFile, true);
      }
      catch (final JBson2BJsonException e) {
         ILogger.instance().logError("JBson2BJson Error generating geobson file: " + e.getMessage());
      }
      catch (final IOException e) {
         ILogger.instance().logError("I/O Error generating geobson file: " + e.getMessage());
      }
   }


   private static boolean isEmptyFile(final File file) {
      return (!file.exists() || (file.length() == 0));
   }


   private static boolean isEmptyString(final String str) {
      return ((str == null) || (str.length() == 0));
   }


   private static boolean generateGeojson() {

      return (OUTPUT_FORMAT.equalsIgnoreCase("geojson") || OUTPUT_FORMAT.equalsIgnoreCase("both"));
   }


   private static boolean generateGeobson() {

      return (OUTPUT_FORMAT.equalsIgnoreCase("geobson") || OUTPUT_FORMAT.equalsIgnoreCase("both"));
   }


   private static void initializeConcurrentService() {

      //_concurrentService = GConcurrentService.createDefaultConcurrentService(MAX_LEVEL + 1, "G3m vectorial LOD");
      _concurrentService = new GConcurrentService();
   }


   private static void initilializeRenderParameters(final boolean mercator,
                                                    final int firstLevel,
                                                    final int maxLevel) {

      _renderParameters = mercator ? LayerTilesRenderParameters.createDefaultMercator(firstLevel, maxLevel)
                                  : LayerTilesRenderParameters.createDefaultWGS84(Sector.fullSphere(), firstLevel, maxLevel);

   }


   private static void initializeUtils() {

      if (ILogger.instance() == null) {
         ILogger.setInstance(new Logger_JavaDesktop(LogLevel.ErrorLevel));
      }

      if (IFactory.instance() == null) {
         IFactory.setInstance(new Factory_JavaDesktop());
      }

      if (IMathUtils.instance() == null) {
         IMathUtils.setInstance(new MathUtils_JavaDesktop());
      }

      if (IJSONParser.instance() == null) {
         IJSONParser.setInstance(new JSONParser_JavaDesktop());
      }

      IStringBuilder.setInstance(new StringBuilder_JavaDesktop());
   }


   private static void initialize() {

      //      initializeUtils();

      initilializeRenderParameters(MERCATOR, FIRST_LEVEL, MAX_LEVEL);

      //initializeConcurrentService();

      _firstLevelCreated = MAX_LEVEL;
      _lastLevelCreated = FIRST_LEVEL;
      _projection = (_renderParameters._mercator) ? MERCATOR_PYRAMID : WGS84_PYRAMID;

   }


   //   private static String[] parsePropertiesFromFile(final String propList) {
   //
   //      if ((propList == null) || propList.trim().equals("")) {
   //         return null;
   //      }
   //
   //      final String[] properties = propList.split(",");
   //
   //      for (int i = 0; i < properties.length; i++) {
   //         properties[i] = properties[i].trim();
   //      }
   //
   //      return properties;
   //   }

   private static String[] parseDataFromFile(final String dataList,
                                             final String separator) {

      if (isEmptyString(dataList)) {
         return null;
      }

      final String[] data = dataList.split(separator);

      for (int i = 0; i < data.length; i++) {
         data[i] = data[i].trim();
      }

      return data;
   }


   private static boolean initializeFromFile(final String fileName) {

      if (new File(fileName).exists()) {

         System.out.println("Initializing from file.. ");

         final Properties properties = new Properties();
         properties.clear();
         try {
            final BufferedInputStream stream = new BufferedInputStream(new FileInputStream(fileName));
            properties.loadFromXML(stream);

            String tmp;
            HOST = properties.getProperty("HOST").trim();

            if (!isEmptyString(HOST)) {
               System.out.println("HOST: " + HOST);
            }
            else {
               System.err.println("Invalid HOST argument.");
               System.exit(1);
            }

            PORT = properties.getProperty("PORT").trim();
            if (!isEmptyString(PORT)) {
               System.out.println("PORT: " + PORT);
            }
            else {
               System.err.println("Invalid PORT argument.");
               System.exit(1);
            }

            USER = properties.getProperty("USER").trim();
            if (!isEmptyString(USER)) {
               System.out.println("USER: " + USER);
            }
            else {
               System.err.println("Invalid USER argument.");
               System.exit(1);
            }

            PASSWORD = properties.getProperty("PASSWORD").trim();
            if (!isEmptyString(PASSWORD)) {
               System.out.println("PASSWORD: " + PASSWORD);
            }
            else {
               System.err.println("Invalid PASSWORD argument.");
               System.exit(1);
            }

            DATABASE_NAME = properties.getProperty("DATABASE_NAME").trim();
            if (!isEmptyString(DATABASE_NAME)) {
               System.out.println("DATABASE_NAME: " + DATABASE_NAME);
            }
            else {
               System.err.println("Invalid DATABASE_NAME argument.");
               System.exit(1);
            }

            tmp = properties.getProperty("QUALITY_FACTOR").trim();
            if (!isEmptyString(tmp)) {
               QUALITY_FACTOR = Float.parseFloat(tmp);
               System.out.println("QUALITY_FACTOR: " + QUALITY_FACTOR);
            }
            else {
               System.out.println();
               System.err.println("Invalid QUALITY_FACTOR argument. Using default QUALITY_FACTOR: " + QUALITY_FACTOR);
            }

            tmp = properties.getProperty("MERCATOR").trim();
            if (!isEmptyString(tmp)) {
               MERCATOR = Boolean.parseBoolean(tmp);
               if (MERCATOR) {
                  System.out.println("MERCATOR projection");
               }
               else {
                  System.out.println("WGS84 projection");
               }
            }
            else {
               System.err.println("Invalid PROJECTION specification.");
               System.exit(1);
            }

            tmp = properties.getProperty("FIRST_LEVEL").trim();
            if (!isEmptyString(tmp)) {
               FIRST_LEVEL = Integer.parseInt(tmp);
               System.out.println("FIRST_LEVEL: " + FIRST_LEVEL);
            }
            else {
               System.err.println("Invalid FIRST_LEVEL argument.");
               System.exit(1);
            }

            tmp = properties.getProperty("MAX_LEVEL").trim();
            if (!isEmptyString(tmp)) {
               MAX_LEVEL = Integer.parseInt(tmp);
               System.out.println("MAX_LEVEL: " + MAX_LEVEL);
               //NUM_LEVELS = (MAX_LEVEL - FIRST_LEVEL) + 1;
               //MAX_DB_CONNECTIONS = NUM_LEVELS;
            }
            else {
               System.err.println("Invalid MAX_LEVEL argument.");
               System.exit(1);
            }

            OUTPUT_FORMAT = properties.getProperty("OUTPUT_FORMAT").trim();
            if (!isEmptyString(OUTPUT_FORMAT)) {
               System.out.println("OUTPUT_FORMAT: " + OUTPUT_FORMAT);
            }
            else {
               System.err.println("Invalid OUTPUT_FORMAT argument.");
               System.exit(1);
            }

            tmp = properties.getProperty("OUTPUT_FOLDER").trim();
            if (!isEmptyString(tmp)) {
               ROOT_FOLDER = tmp;
               System.out.println("OUTPUT_FOLDER: " + ROOT_FOLDER);
            }
            else {
               System.out.println();
               System.err.println("Invalid OUTPUT_FOLDER argument. Using default output folder: " + ROOT_FOLDER);
            }


            //---------
            final String dataBaseTables = properties.getProperty("DATABASE_TABLE").trim();

            if (!isEmptyString(dataBaseTables)) {
               DATABASE_TABLES = parseDataFromFile(dataBaseTables, "/");
               System.out.println("DATABASE TABLES: " + dataBaseTables);
               //               for (final String element : DATABASE_TABLES) {
               //                  System.out.print(element);
               //                                    if (i == (DATABASE_TABLES.length - 1)) {
               //                                       System.out.println(".");
               //                                    }
               //                                    else {
               //                                       System.out.print(", ");
               //                                    }
               //               }
               //System.out.println();
            }
            else {
               System.out.println();
               System.err.println("Non database table argument. No property included from datasource.");
               System.exit(1);
            }

            //---------
            final String filterCriteria = properties.getProperty("FILTER_CRITERIA").trim();

            if (!isEmptyString(filterCriteria)) {
               FILTER_CRITERIA = parseDataFromFile(filterCriteria, "/");
               System.out.println("FILTER CRITERIA: " + filterCriteria);
               for (int i = 0; i < DATABASE_TABLES.length; i++) {
                  if (isEmptyString(FILTER_CRITERIA[i])) {
                     FILTER_CRITERIA[i] = "true";
                  }
                  //                  System.out.print(FILTER_CRITERIA[i]);
                  //                  if (i == (DATABASE_TABLES.length - 1)) {
                  //                     System.out.println(".");
                  //                  }
                  //                  else {
                  //                     System.out.print(", ");
                  //                  }
               }
               //System.out.println();
            }
            else {
               for (int i = 0; i < DATABASE_TABLES.length; i++) {
                  FILTER_CRITERIA[i] = "true";
               }
               System.out.println();
               System.err.println("Invalid FILTER_CRITERIA argument. Using default FILTER_CRITERIA=true.");
            }
            //---------

            PROPERTIES = new String[DATABASE_TABLES.length][];
            final String includeProperties = properties.getProperty("PROPERTIES").trim();

            if (!isEmptyString(includeProperties)) {
               final String[] propertiesList = parseDataFromFile(includeProperties, "/");

               System.out.println("PROPERTIES: " + includeProperties);
               for (int i = 0; i < DATABASE_TABLES.length; i++) {

                  if (!isEmptyString(propertiesList[i])) {
                     final String[] props = parseDataFromFile(propertiesList[i], ",");
                     if ((props != null) && (props.length > 0)) {
                        PROPERTIES[i] = props;
                        //                        System.out.print(propertiesList[i]);
                     }
                     //                     if (i == (DATABASE_TABLES.length - 1)) {
                     //                        System.out.println(".");
                     //                     }
                     //                     else {
                     //                        System.out.print(" / ");
                     //                     }
                  }
                  else {
                     PROPERTIES[i] = null;
                  }
               }
               System.out.println();
            }
            else {
               System.out.println();
               System.err.println("Non PROPERTIES argument. No property included from datasource.");
            }

            //-- Initialize data sources -----------------------------------------
            for (int i = 0; i < DATABASE_TABLES.length; i++) {
               final DataSource ds = new DataSource(DATABASE_TABLES[i], FILTER_CRITERIA[i], PROPERTIES[i]);
               _dataSources.add(ds);
            }

            return true;
         }
         catch (final FileNotFoundException e) {
            ILogger.instance().logError("Initialization file: " + PARAMETERS_FILE + ", not found !");
         }
         catch (final InvalidPropertiesFormatException e) {
            ILogger.instance().logError("Initialization file invalid format: " + e.getMessage());
         }
         catch (final IOException e) {
            ILogger.instance().logError("Initialization file IO error: " + e.getMessage());
         }

      }

      ILogger.instance().logWarning("Initialization file: " + PARAMETERS_FILE + ", not found !");
      return false;
   }


   public static void main(final String[] args) {

      initializeUtils();

      initializeConcurrentService();

      if (initializeFromFile(PARAMETERS_FILE)) {

         System.out.print("Connecting to " + DATABASE_NAME + " postGIS database.. ");

         if (createDataBaseService(HOST, PORT, USER, PASSWORD, DATABASE_NAME)) {

            System.out.println("done.");

            initialize();

            // batch mode to generate full LOD pyramid for a vectorial data source
            launchVectorialLODProcessing(_dataSources);

         }
         else {
            System.out.println("Failed. Error connecting to database.");
         }
      }
      else {
         ILogger.instance().logError("Initialization error. Check parameters at " + PARAMETERS_FILE + " file.");
      }

   }

}
