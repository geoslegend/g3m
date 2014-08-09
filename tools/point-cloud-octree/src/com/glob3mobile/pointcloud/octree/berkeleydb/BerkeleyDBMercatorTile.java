

package com.glob3mobile.pointcloud.octree.berkeleydb;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.glob3.mobile.generated.Angle;
import org.glob3.mobile.generated.Geodetic2D;
import org.glob3.mobile.generated.Geodetic3D;
import org.glob3.mobile.generated.Sector;

import com.glob3mobile.pointcloud.octree.PersistentOctree;
import com.glob3mobile.pointcloud.octree.Utils;
import com.sleepycat.je.Cursor;
import com.sleepycat.je.CursorConfig;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.Environment;
import com.sleepycat.je.LockMode;
import com.sleepycat.je.OperationStatus;
import com.sleepycat.je.Transaction;
import com.sleepycat.je.TransactionConfig;


public class BerkeleyDBMercatorTile
         implements
            PersistentOctree.Node {

   private static final int MAX_POINTS_PER_TILE = 1024 * 256;


   private static enum Format {
      LatLonHeight((byte) 1);

      private final byte _formatID;


      Format(final byte formatID) {
         _formatID = formatID;
      }


      private int sizeOf(final float[] values) {
         return values.length * 4;
      }


      private static Format getFromID(final byte formatID) {
         for (final Format each : Format.values()) {
            if (each._formatID == formatID) {
               return each;
            }
         }
         throw new RuntimeException("Invalid FormatID=" + formatID);
      }
   }


   private static class TileHeader {
      private final byte[] _id;
      private final Sector _sector;


      private TileHeader(final byte[] id,
                         final Sector sector) {
         _id = id;
         _sector = sector;
      }


      private TileHeader[] createChildren() {
         final Geodetic2D lower = _sector._lower;
         final Geodetic2D upper = _sector._upper;

         final Angle splitLongitude = Angle.midAngle(lower._longitude, upper._longitude);
         final Angle splitLatitude = calculateSplitLatitude(lower._latitude, upper._latitude);

         final Sector s0 = new Sector( //
                  new Geodetic2D(splitLatitude, lower._longitude), //
                  new Geodetic2D(upper._latitude, splitLongitude));

         final Sector s1 = new Sector( //
                  new Geodetic2D(splitLatitude, splitLongitude), //
                  new Geodetic2D(upper._latitude, upper._longitude));

         final Sector s2 = new Sector( //
                  new Geodetic2D(lower._latitude, lower._longitude), //
                  new Geodetic2D(splitLatitude, splitLongitude));

         final Sector s3 = new Sector( //
                  new Geodetic2D(lower._latitude, splitLongitude), //
                  new Geodetic2D(splitLatitude, upper._longitude));


         final TileHeader child0 = createChild((byte) 0, s0);
         final TileHeader child1 = createChild((byte) 1, s1);
         final TileHeader child2 = createChild((byte) 2, s2);
         final TileHeader child3 = createChild((byte) 3, s3);
         return new TileHeader[] { child0, child1, child2, child3 };
      }


      private TileHeader createChild(final byte index,
                                     final Sector sector) {
         final int length = _id.length;
         final byte[] childId = new byte[length + 1];
         System.arraycopy(_id, 0, childId, 0, length);
         childId[length] = index;
         return new TileHeader(childId, sector);
      }

   }


   private static final byte[]     ROOT_ID          = {};
   private static final TileHeader ROOT_TILE_HEADER = new TileHeader(ROOT_ID, Sector.FULL_SPHERE);


   static BerkeleyDBMercatorTile createDeepestEnclosingTile(final BerkeleyDBOctree octree,
                                                            final Sector targetSector,
                                                            final Geodetic3D averagePoint,
                                                            final List<Geodetic3D> points) {
      final TileHeader tile = deepestEnclosingTile(targetSector);

      return new BerkeleyDBMercatorTile(octree, tile._id, tile._sector, averagePoint, new ArrayList<>(points));
   }


   private static TileHeader deepestEnclosingTile(final Sector targetSector) {
      return deepestEnclosingTile(ROOT_TILE_HEADER, targetSector);
   }


   private static TileHeader deepestEnclosingTile(final TileHeader candidate,
                                                  final Sector targetSector) {
      final TileHeader[] children = candidate.createChildren();
      for (final TileHeader child : children) {
         if (child._sector.fullContains(targetSector)) {
            return deepestEnclosingTile(child, targetSector);
         }
      }
      return candidate;
   }


   private static double _upperLimitInDegrees = 85.0511287798;
   private static double _lowerLimitInDegrees = -85.0511287798;


   private static double getMercatorV(final Angle latitude) {
      if (latitude._degrees >= _upperLimitInDegrees) {
         return 0;
      }
      if (latitude._degrees <= _lowerLimitInDegrees) {
         return 1;
      }

      final double pi4 = Math.PI * 4;

      final double latSin = Math.sin(latitude._radians);
      return 1.0 - ((Math.log((1.0 + latSin) / (1.0 - latSin)) / pi4) + 0.5);
   }


   private static Angle toLatitude(final double v) {
      final double exp = Math.exp(-2 * Math.PI * (1.0 - v - 0.5));
      final double atan = Math.atan(exp);
      return Angle.fromRadians((Math.PI / 2) - (2 * atan));
   }


   private static Angle calculateSplitLatitude(final Angle lowerLatitude,
                                               final Angle upperLatitude) {
      final double middleV = (getMercatorV(lowerLatitude) + getMercatorV(upperLatitude)) / 2;

      return toLatitude(middleV);
   }


   private final BerkeleyDBOctree _octree;
   private final byte[]           _id;
   private final Sector           _sector;
   private Geodetic3D             _averagePoint;
   private final Format           _format;
   private int                    _pointsCount;
   private List<Geodetic3D>       _points = null;


   private BerkeleyDBMercatorTile(final BerkeleyDBOctree octree,
                                  final byte[] id,
                                  final Sector sector,
                                  final int pointsCount,
                                  final Geodetic3D averagePoint,
                                  final Format format) {
      _octree = octree;
      _id = id;
      _sector = sector;
      _pointsCount = pointsCount;
      _averagePoint = averagePoint;
      _format = format;
   }


   private BerkeleyDBMercatorTile(final BerkeleyDBOctree octree,
                                  final byte[] id,
                                  final Sector sector,
                                  final Geodetic3D averagePoint,
                                  final List<Geodetic3D> points) {
      _octree = octree;
      _id = id;
      _sector = sector;
      _pointsCount = points.size();
      _points = points;
      _averagePoint = averagePoint;
      _format = null;
   }


   private int getLevel() {
      return _id.length;
   }


   byte[] getBerkeleyDBKey() {
      return Arrays.copyOf(_id, _id.length);
   }


   @Override
   public String getID() {
      final StringBuilder builder = new StringBuilder();
      for (final byte each : _id) {
         builder.append(each);
      }
      return builder.toString();
   }


   @Override
   public Sector getSector() {
      return _sector;
   }


   @Override
   public String toString() {
      return "MercatorTile [id=" + getID() + //
             ", sector=" + Utils.toString(_sector) + //
             ", level=" + getLevel() + //
             ", points=" + _pointsCount + //
             "]";
   }


   @SuppressWarnings("unused")
   private static int sizeOf(final double any) {
      return 8;
   }


   @SuppressWarnings("unused")
   private static int sizeOf(final int any) {
      return 4;
   }


   @SuppressWarnings("unused")
   private static int sizeOf(final byte any) {
      return 1;
   }


   private byte[] createNodeEntry(final Format format) {
      final byte version = 1;
      final byte subversion = 0;

      final Sector sector = getSector();
      final double lowerLatitude = sector._lower._latitude._radians;
      final double lowerLongitude = sector._lower._longitude._radians;
      final double upperLatitude = sector._upper._latitude._radians;
      final double upperLongitude = sector._upper._longitude._radians;

      final double averageLatitude = _averagePoint._latitude._radians;
      final double averageLongitude = _averagePoint._longitude._radians;
      final double averageHeight = _averagePoint._height;

      final byte formatID = format._formatID;

      final int entrySize = sizeOf(version) + //
                            sizeOf(subversion) + //
                            sizeOf(lowerLatitude) + //
                            sizeOf(lowerLongitude) + //
                            sizeOf(upperLatitude) + //
                            sizeOf(upperLongitude) + //
                            sizeOf(_pointsCount) + //
                            sizeOf(averageLatitude) + //
                            sizeOf(averageLongitude) + //
                            sizeOf(averageHeight) + //
                            sizeOf(formatID);

      final ByteBuffer byteBuffer = ByteBuffer.allocate(entrySize);
      byteBuffer.put(version);
      byteBuffer.put(subversion);
      byteBuffer.putDouble(lowerLatitude);
      byteBuffer.putDouble(lowerLongitude);
      byteBuffer.putDouble(upperLatitude);
      byteBuffer.putDouble(upperLongitude);
      byteBuffer.putInt(_pointsCount);
      byteBuffer.putDouble(averageLatitude);
      byteBuffer.putDouble(averageLongitude);
      byteBuffer.putDouble(averageHeight);
      byteBuffer.put(formatID);

      return byteBuffer.array();
   }


   private float[] createValues() {
      final int bufferSize = _points.size();
      final double averageLatitudeInRadians = _averagePoint._latitude._radians;
      final double averageLongitudeInRadians = _averagePoint._longitude._radians;
      final double averageHeight = _averagePoint._height;

      final float[] values = new float[bufferSize * 3];
      int i = 0;
      for (final Geodetic3D point : _points) {
         final float deltaLatitudeInRadians = (float) (point._latitude._radians - averageLatitudeInRadians);
         final float deltaLongitudeInRadians = (float) (point._longitude._radians - averageLongitudeInRadians);
         final float deltaHeight = (float) (point._height - averageHeight);

         values[i++] = deltaLatitudeInRadians;
         values[i++] = deltaLongitudeInRadians;
         values[i++] = deltaHeight;
      }

      return values;
   }


   private byte[] createNodeDataEntry(final Format format) {
      final float[] values = createValues();
      final int entrySize = format.sizeOf(values);

      final ByteBuffer byteBuffer = ByteBuffer.allocate(entrySize);
      for (final float value : values) {
         byteBuffer.putFloat(value);
      }

      return byteBuffer.array();
   }


   void save() {
      final BerkeleyDBMercatorTile ancestor = getAncestorOrSameLevel();
      if (ancestor != null) {
         System.out.println("==> found ancestor (" + ancestor.getID() + ") for tile " + getID());

         ancestor.mergePoints(_averagePoint, _points);
         return;
      }

      final List<BerkeleyDBMercatorTile> descendants = getDescendants();
      if ((descendants != null) && !descendants.isEmpty()) {
         final StringBuilder builder = new StringBuilder();

         builder.append("[ ");
         for (final BerkeleyDBMercatorTile e : descendants) {
            builder.append(e.getID());
            builder.append(" ");
         }
         builder.append("]");

         System.out.println(">>> tile " + getID() + " has " + descendants.size() + " descendants " + builder.toString());

         return;
      }

      rawSave();
   }


   private List<BerkeleyDBMercatorTile> getDescendants() {
      final List<BerkeleyDBMercatorTile> result = new ArrayList<BerkeleyDBMercatorTile>();

      final Environment env = _octree.getEnvironment();
      final Database nodeDB = _octree.getNodeDB();

      final Transaction txn = null;
      final CursorConfig cursorConfig = new CursorConfig();


      try (final Cursor cursor = nodeDB.openCursor(txn, cursorConfig)) {
         final DatabaseEntry keyEntry = new DatabaseEntry(_id);
         final DatabaseEntry dataEntry = new DatabaseEntry();
         final OperationStatus status = cursor.getSearchKeyRange(keyEntry, dataEntry, LockMode.DEFAULT);
         if (status == OperationStatus.SUCCESS) {
            result.add(fromDB(_octree, keyEntry.getData(), dataEntry.getData()));

            while (cursor.getNext(keyEntry, dataEntry, LockMode.DEFAULT) == OperationStatus.SUCCESS) {
               final byte[] key = keyEntry.getData();
               if (Utils.isGreaterThan(key, _id)) {
                  break;
               }
               result.add(fromDB(_octree, keyEntry.getData(), dataEntry.getData()));
            }
         }
      }

      return result;
   }


   private void rawSave() {
      final Environment env = _octree.getEnvironment();
      final Database nodeDB = _octree.getNodeDB();
      final Database nodeDataDB = _octree.getNodeDataDB();

      final Format format = Format.LatLonHeight;

      final DatabaseEntry key = new DatabaseEntry(getBerkeleyDBKey());

      final TransactionConfig tnxConfig = new TransactionConfig();
      final Transaction txn = env.beginTransaction(null, tnxConfig);

      nodeDB.put(txn, key, new DatabaseEntry(createNodeEntry(format)));
      nodeDataDB.put(txn, key, new DatabaseEntry(createNodeDataEntry(format)));

      txn.commit();
   }


   private void mergePoints(final Geodetic3D newAveragePoint,
                            final List<Geodetic3D> newPoints) {
      final int mergedPointsLength = getPointsCount() + newPoints.size();
      if (mergedPointsLength > MAX_POINTS_PER_TILE) {
         split(newAveragePoint, newPoints);
      }
      else {
         updateFromPoints(newAveragePoint, newPoints);
      }
   }


   private void split(final Geodetic3D newAveragePoint,
                      final List<Geodetic3D> newPoints) {
      final int TODO;
      System.out.println("**> split for " + getID() + " not yet implemented");

      final TileHeader header = new TileHeader(_id, _sector);
      final TileHeader[] children = header.createChildren();
      for (final TileHeader child : children) {

      }
   }


   private static double weightedAverage(final double value1,
                                         final int count1,
                                         final double value2,
                                         final int count2) {
      return ((value1 * count1) + (value2 * count2)) / (count1 + count2);
   }


   private static Angle weightedAverage(final Angle value1,
                                        final int count1,
                                        final Angle value2,
                                        final int count2) {
      return Angle.fromRadians(weightedAverage(value1._radians, count1, value2._radians, count2));
   }


   private static Geodetic3D weightedAverage(final Geodetic3D value1,
                                             final int count1,
                                             final Geodetic3D value2,
                                             final int count2) {

      final Angle averageLatitude = weightedAverage( //
               value1._latitude, count1, //
               value2._latitude, count2);

      final Angle averageLongitude = weightedAverage( //
               value1._longitude, count1, //
               value2._longitude, count2);

      final double averageHeight = weightedAverage( //
               value1._height, count1, //
               value2._height, count2);

      return new Geodetic3D(averageLatitude, averageLongitude, averageHeight);
   }


   private void updateFromPoints(final Geodetic3D newAveragePoint,
                                 final List<Geodetic3D> newPoints) {
      final int oldPointsCount = getPointsCount();
      final int newPointsSize = newPoints.size();
      final int mergedPointsSize = oldPointsCount + newPointsSize;

      final List<Geodetic3D> mergedPoints = new ArrayList<Geodetic3D>(mergedPointsSize);
      mergedPoints.addAll(getPoints());
      mergedPoints.addAll(newPoints);

      final Geodetic3D mergedAveragePoints = weightedAverage( //
               _averagePoint, oldPointsCount, //
               newAveragePoint, newPointsSize);

      System.out.println(" merged " + mergedPointsSize + " points, old=" + oldPointsCount + ", new=" + newPointsSize);

      _pointsCount = mergedPointsSize;
      _points = mergedPoints;
      _averagePoint = mergedAveragePoints;

      rawSave();
   }


   private BerkeleyDBMercatorTile getAncestorOrSameLevel() {
      //      byte[] ancestorId = removeTrailing(_id);
      byte[] ancestorId = _id;
      while (ancestorId != null) {
         final BerkeleyDBMercatorTile ancestor = _octree.readTile(ancestorId);
         if (ancestor != null) {
            return ancestor;
         }
         ancestorId = removeTrailing(ancestorId);
      }
      return null;
   }


   private static byte[] removeTrailing(final byte[] id) {
      final int length = id.length;
      if (length == 0) {
         return null;
      }
      return Arrays.copyOf(id, length - 1);
   }


   static BerkeleyDBMercatorTile fromDB(final BerkeleyDBOctree octree,
                                        final byte[] id,
                                        final byte[] data) {
      final ByteBuffer byteBuffer = ByteBuffer.wrap(data);

      final byte version = byteBuffer.get();
      if (version != 1) {
         throw new RuntimeException("Invalid version=" + version);
      }
      final byte subversion = byteBuffer.get();
      if (subversion != 0) {
         throw new RuntimeException("Invalid subversion=" + subversion);
      }

      final double lowerLatitude = byteBuffer.getDouble();
      final double lowerLongitude = byteBuffer.getDouble();
      final double upperLatitude = byteBuffer.getDouble();
      final double upperLongitude = byteBuffer.getDouble();
      final Sector sector = new Sector( //
               Geodetic2D.fromRadians(lowerLatitude, lowerLongitude), //
               Geodetic2D.fromRadians(upperLatitude, upperLongitude));

      final int pointsCount = byteBuffer.getInt();
      final double averageLatitude = byteBuffer.getDouble();
      final double averageLongitude = byteBuffer.getDouble();
      final double averageHeight = byteBuffer.getDouble();

      final Geodetic3D averagePoint = new Geodetic3D( //
               Angle.fromRadians(averageLatitude), //
               Angle.fromRadians(averageLongitude), //
               averageHeight);


      final byte formatID = byteBuffer.get();
      final Format format = Format.getFromID(formatID);

      return new BerkeleyDBMercatorTile(octree, id, sector, pointsCount, averagePoint, format);
   }


   @Override
   public List<Geodetic3D> getPoints() {
      if (_points == null) {
         _points = loadPoints();
      }
      return _points;
   }


   @Override
   public int getPointsCount() {
      return _pointsCount;
   }


   private List<Geodetic3D> loadPoints() {
      switch (_format) {
         case LatLonHeight:

            final Database nodeDataDB = _octree.getNodeDataDB();

            final DatabaseEntry dataEntry = new DatabaseEntry();
            final OperationStatus status = nodeDataDB.get(null, new DatabaseEntry(_id), dataEntry, LockMode.READ_COMMITTED);
            if (status != OperationStatus.SUCCESS) {
               throw new RuntimeException("Unsupported status=" + status);
            }

            final ByteBuffer byteBuffer = ByteBuffer.wrap(dataEntry.getData());

            final List<Geodetic3D> points = new ArrayList<Geodetic3D>(_pointsCount);

            final double averageLatitude = _averagePoint._latitude._radians;
            final double averageLongitude = _averagePoint._longitude._radians;
            final double averageHeight = _averagePoint._height;

            for (int i = 0; i < _pointsCount; i++) {
               final double lat = byteBuffer.getFloat() + averageLatitude;
               final double lon = byteBuffer.getFloat() + averageLongitude;
               final double height = byteBuffer.getFloat() + averageHeight;

               final Geodetic3D point = new Geodetic3D( //
                        Angle.fromRadians(lat), //
                        Angle.fromRadians(lon), //
                        height);
               points.add(point);
            }

            if (_pointsCount != points.size()) {
               throw new RuntimeException("Inconsistency in pointsCount");
            }
            return Collections.unmodifiableList(points);

         default:
            throw new RuntimeException("Unsupported format: " + _format);
      }

   }


   @Override
   public Geodetic3D getAveragePoint() {
      return _averagePoint;
   }


}