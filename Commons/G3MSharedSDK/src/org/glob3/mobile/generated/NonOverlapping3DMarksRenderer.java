package org.glob3.mobile.generated; 
import java.util.*;

public class NonOverlapping3DMarksRenderer extends DefaultRenderer
{

    private int _maxVisibleMarks;
    Planet _planet;

    private java.util.ArrayList<NonOverlapping3DMark> _visibleMarks = new java.util.ArrayList<NonOverlapping3DMark>();
    private java.util.ArrayList<NonOverlapping3DMark> _marks = new java.util.ArrayList<NonOverlapping3DMark>();
    private java.util.ArrayList<NonOverlapping3DMark> _anchors = new java.util.ArrayList<NonOverlapping3DMark>();

    private void computeMarksToBeRendered(Camera cam, Planet planet)
    {
        _visibleMarks.clear();
    
        //Initialize shape to something
      /* Shape* _shape = new EllipsoidShape(new Geodetic3D(Angle::fromDegrees(0),
                                                          Angle::fromDegrees(0),
                                                          5),
                                           ABSOLUTE,
                                           Vector3D(100000, 100000, 100000),
                                           16,
                                           0,
                                           false,
                                           false,
                                           Color::fromRGBA(1, 1, 1, .5));
      
      
        NonOverlapping3DMark* center = new NonOverlapping3DMark(_shape, _shape, Geodetic3D::fromDegrees(0, 0, -_planet->getRadii()._x));
        _visibleMarks.push_back(center);*/
    
        final Frustum frustrum = cam.getFrustumInModelCoordinates();
    
        for (int i = 0; i < _marks.size(); i++)
        {
            NonOverlapping3DMark m = _marks.get(i);
    
            if (_visibleMarks.size() < _maxVisibleMarks && frustrum.contains(m.getCartesianPosition(planet)))
            {
                _visibleMarks.add(m);
            }
            else
            {
                //Resetting marks location of invisible anchors
                m.resetShapePositionVelocityAndForce();
            }
        }
    }

    private long _lastPositionsUpdatedTime;

    private GLState _connectorsGLState;
    private void renderConnectorLines(G3MRenderContext rc)
    {
        //TODO - cylinders? lines? project 3d line onto 2d?
    
    
    
        /*if (_connectorsGLState == NULL){
            _connectorsGLState = new GLState();
        
            _connectorsGLState->addGLFeature(new FlatColorGLFeature(Color::black()), false);
        }
        
        _connectorsGLState->clearGLFeatureGroup(NO_GROUP);
        
        FloatBufferBuilderFromCartesian3D pos3D = FloatBufferBuilderFromCartesian3D(NO_CENTER, Vector3D(0, 0, 0));*/
    
        //TODO - traverse to find edges:
        /*for (int i = 0; i < _visibleMarks.size(); i++){
            Vector2F sp = _visibleMarks[i]->getScreenPos();
           // Vector2F asp = _visibleMarks[i]->getScreenPos();
        
            pos2D.add(sp._x, -sp._y);
            //pos2D.add(asp._x, -asp._y);
        
        }*/
        /*std::vector<bool> visited = std::vector<bool>(false);
        
        for(int i = 0; i < _visibleMarks.size(); i++) {
            std::queue<NonOverlapping3DMark*> q;
            if(!(_visibleMarks[i]->isVisited())) {
                _visibleMarks[i]->setVisited(true);
                q.push(_visibleMarks[i]);
                while(!q.empty()) {
                    Vector3D sp = _visibleMarks[i]->getCartesianPosition(planet);
                    NonOverlapping3DMark* mark = q.front();
                    Vector3F sp2 = mark->getCartesianPosition(planet);
                    pos3D.add(sp._x, -sp._y, sp._z);
                    pos3D.add(sp2._x, -sp2._y, sp._z);
                    for(int j = 0; j < mark->getNeighbors().size(); j++) {
                        NonOverlapping3DMark* n = mark->getNeighbors()[j];
                        if(!(n->isVisited())) {
                            n->setVisited(true);
                            q.push(n);
                        }
                    }
                    mark->setVisited(true);
                    q.pop(); // and mark as visited here
                }
            }
            setAllVisibleAsUnvisited();
        }*/
        //TODO
       /* _connectorsGLState->addGLFeature( new Geometry2DGLFeature(pos2D.create(),
                                                                  2,
                                                                  0,
                                                                  true,
                                                                  0,
                                                                  3.0f,
                                                                  true,
                                                                  10.0f,
                                                                  Vector2F(0.0f,0.0f)),
                                         false);
       
        _connectorsGLState->addGLFeature(new ViewportExtentGLFeature((int)rc->getCurrentCamera()->getViewPortWidth(),
                                                                     (int)rc->getCurrentCamera()->getViewPortHeight()), false);
       
        rc->getGL()->drawArrays(GLPrimitive::lines(), 0, pos2D.size()/2, _connectorsGLState, *rc->getGPUProgramManager());*/
    }

    private void computeForces(Camera cam, Planet planet)
    {
    
        //Compute Mark Anchor Screen Positions
        /*for (int i = 0; i < _anchors.size(); i++) {
            _anchors[i]->computeAnchorScreenPos(cam, planet);
        }*/
    
        //Compute Mark Forces
       /* for (int i = 0; i < _visibleMarks.size(); i++) {
            NonOverlapping3DMark* mark = _visibleMarks[i];
            mark->applyHookesLaw(planet);
       
            for (int j = i+1; j < _visibleMarks.size(); j++) {
                mark->applyCoulombsLaw(_visibleMarks[j], planet);
            }
       
            for (int j = 0; j < _visibleMarks.size(); j++) {
                if (i != j && !_visibleMarks[j]->hasAnchor()){
                    mark->applyCoulombsLawFromAnchor(_visibleMarks[j]);
                }
            for (int j = 0; j < _anchors.size(); j++) {
                _anchors[i]->applyCoulombsLaw(mark, planet);
            }
        }*/
    
        for (int i = 0; i < _visibleMarks.size(); i++)
        {
            NonOverlapping3DMark mark = _visibleMarks.get(i);
            if(!mark.isAnchor())
            {
               mark.applyHookesLaw(planet);
    
                for (int j = 0; j < _visibleMarks.size(); j++)
                {
                    if(i == j)
                       continue;
                    mark.applyCoulombsLaw(_visibleMarks.get(j), planet);
                    }
                }
        }
    }
    private void renderMarks(G3MRenderContext rc, GLState glState)
    {
    
        renderConnectorLines(rc);
    
        //Draw Anchors and Marks
        for (int i = 0; i < _visibleMarks.size(); i++)
        {
            _visibleMarks.get(i).getCartesianPosition(rc.getPlanet()); //updates shapes positions
            _visibleMarks.get(i).render(rc, glState);
        }
    }
    private void applyForces(long now, Camera cam)
    {
    
        if (_lastPositionsUpdatedTime != 0) //If not First frame
        {
    
            //Update Position based on last Forces
            for (int i = 0; i < _visibleMarks.size(); i++)
            {
                    _visibleMarks.get(i).updatePositionWithCurrentForce(now - _lastPositionsUpdatedTime, cam.getViewPortWidth(), cam.getViewPortHeight(), _planet);
            }
        }
    
        _lastPositionsUpdatedTime = now;
    }



    public NonOverlapping3DMarksRenderer()
    {
       this(30);
    }
    public NonOverlapping3DMarksRenderer(int maxVisibleMarks)
    {
       _maxVisibleMarks = maxVisibleMarks;
       _lastPositionsUpdatedTime = 0;
       _connectorsGLState = null;
    }

    public void dispose()
    {
        _connectorsGLState._release();
    
        for (int i = 0; i < _marks.size(); i++)
        {
            if (_marks.get(i) != null)
               _marks.get(i).dispose();
        }
    }

    public final void addMark(NonOverlapping3DMark mark)
    {
        _marks.add(mark);
        if(mark.isAnchor())
        {
            _anchors.add(mark);
        }
    
    }

    public final void setAllVisibleAsUnvisited()
    {
        for(int i = 0; i < _visibleMarks.size(); i++)
        {
            _visibleMarks.get(i).setVisited(false);
        }
    }

    public RenderState getRenderState(G3MRenderContext rc)
    {
        return RenderState.ready();
    }

    public void render(G3MRenderContext rc, GLState glState)
    {
    
        final Camera cam = rc.getCurrentCamera();
        _planet = rc.getPlanet();
    
            ShapesRenderer sr = new ShapesRenderer();
            MeshRenderer _meshrender = new MeshRenderer();
    
    
        //TODO: edges working as expected. Midpoint seems to be correct, but not being drawn in the right position.
        for(int i = 0; i < _visibleMarks.size(); i++)
        {
    
            for(int j = 0; j < _visibleMarks.get(i).getNeighbors().size(); j++)
            {
                NonOverlapping3DMark neighbor = _visibleMarks.get(i).getNeighbors().get(j);
                Vector3D p1 = _visibleMarks.get(i).getCartesianPosition(_planet);
                Vector3D p2 = neighbor.getCartesianPosition(_planet);
                Vector3D mid = (p2.add(p1)).times(0.5);
                Vector3D mid2 = new Vector3D(mid._x, mid._y, mid._z);
    
                Geodetic3D p1g = new Geodetic3D(_planet.toGeodetic3D(p1));
                Geodetic3D p2g = new Geodetic3D(_planet.toGeodetic3D(p2));
                Geodetic3D midg = p1g.add(p2g).div(2.0f);
    
                Geodetic3D position = new Geodetic3D(_planet.toGeodetic3D(mid2)); //midpoint
                //Geodetic3D *position = new Geodetic3D(midg);
    
                Vector3D extent = new Vector3D(10000, 1000, p1.distanceTo(p2));
                //todo: rotation, mark as visited, don't allocate memory
                float borderWidth = 2F;
                Color col = Color.fromRGBA((float).5, (float) 1, (float) 1, (float) 1);
    
                // create vertices
                FloatBufferBuilderFromCartesian3D vertices = FloatBufferBuilderFromCartesian3D.builderWithoutCenter();
                vertices.add(p1);
                vertices.add(p2);
                ShortBufferBuilder indices = new ShortBufferBuilder();
                indices.add((short) 0);
                indices.add((short) 1);
    
    
                Mesh mesh = new IndexedMesh(GLPrimitive.lines(), true, vertices.getCenter(), vertices.create(), indices.create(), 1, 1, new Color(col));
                _meshrender.addMesh(mesh);
    
    
            }
        }
    
       // sr.render(rc, glState);
        _meshrender.render(rc, glState);
       // sr.removeAllShapes();
        //TODO: get rid of this stuff
        /*for(int i = 0; i < _visibleMarks.size(); i++) {
            _visibleMarks[i]->getShape()->setPosition(Geodetic3D::fromDegrees(0, 0, 1));
            _visibleMarks[i]->getShape()->setTranslation(_visibleMarks[i]->getCartesianPosition(_planet));
        }*/
    
        //todo: add this back
    
        //computeMarksToBeRendered(cam, _planet);
        _visibleMarks = _marks; //temporary
    
        computeForces(cam, _planet);
    
        applyForces(rc.getFrameStartTimer().nowInMilliseconds(), cam);
    
        renderMarks(rc, glState);
    }

    public boolean onTouchEvent(G3MEventContext ec, TouchEvent touchEvent)
    {
    
        if (touchEvent.getTapCount() == 1)
        {
            final float x = touchEvent.getTouch(0).getPos()._x;
            final float y = touchEvent.getTouch(0).getPos()._y;
            for (int i = 0; i < _visibleMarks.size(); i++)
            {
                if (_visibleMarks.get(i).onTouchEvent(x, y))
                {
                    return true;
                }
            }
        }
        return false;
    }

    public void onResizeViewportEvent(G3MEventContext ec, int width, int height)
    {
        for (int i = 0; i < _marks.size(); i++)
        {
            _marks.get(i).onResizeViewportEvent(width, height);
        }
    
    }

    public void start(G3MRenderContext rc)
    {

    }

    public void stop(G3MRenderContext rc)
    {

    }

    public SurfaceElevationProvider getSurfaceElevationProvider()
    {
        return null;
    }

    public PlanetRenderer getPlanetRenderer()
    {
        return null;
    }

    public boolean isPlanetRenderer()
    {
        return false;
    }

}