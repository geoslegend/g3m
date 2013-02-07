package org.glob3.mobile.generated; 
//***************************************************************

public class BusyEffect extends EffectWithForce {
  private BusyQuadRenderer _renderer;


  public BusyEffect(BusyQuadRenderer renderer) {
     super(1, 1);
     _renderer = renderer;
  }

  public void start(G3MRenderContext rc, TimeInterval when) {
  }

  public void doStep(G3MRenderContext rc, TimeInterval when) {
    super.doStep(rc, when);
    _renderer.incDegrees(3);
  }

  public void stop(G3MRenderContext rc, TimeInterval when) {
  }

  public void cancel(TimeInterval when) {
    // do nothing, just leave the effect in the intermediate state
  }

}