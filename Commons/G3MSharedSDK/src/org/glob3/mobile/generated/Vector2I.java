package org.glob3.mobile.generated; 
//
//  Vector2I.cpp
//  G3MiOSSDK
//
//  Created by Diego Gomez Deck on 10/19/12.
//
//

//
//  Vector2I.hpp
//  G3MiOSSDK
//
//  Created by Diego Gomez Deck on 10/19/12.
//
//



public class Vector2I {
  public final int _x;
  public final int _y;

  public Vector2I() {
     _x = 0;
     _y = 0;

  }

  public Vector2I(int x, int y) {
     _x = x;
     _y = y;

  }

  public Vector2I(Vector2I that) {
     _x = that._x;
     _y = that._y;

  }

  public static Vector2I zero() {
    return new Vector2I(0, 0);
  }

  public final boolean isZero() {
    return (_x == 0) && (_y == 0);
  }

  public final Vector2I add(Vector2I that) {
    return new Vector2I(_x + that._x, _y + that._y);
  }

  public final Vector2I sub(Vector2I that) {
    return new Vector2I(_x - that._x, _y - that._y);
  }

  public final Vector2I div(double v) {
    IMathUtils math = IMathUtils.instance();
    return new Vector2I(math.toInt(_x / v), math.toInt(_y / v));
  }

  public final double length() {
    return IMathUtils.instance().sqrt(squaredLength());
  }

  public final double squaredLength() {
    return _x * _x + _y * _y;
  }

  public final Angle orientation() {
    return Angle.fromRadians(IMathUtils.instance().atan2((double) _y, (double) _x));
  }

  public final MutableVector2I asMutableVector2I() {
    return new MutableVector2I(_x, _y);
  }

}