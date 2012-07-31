//
//  G3MWidget.cpp
//  G3MiOSSDK
//
//  Created by José Miguel S N on 31/05/12.
//  Copyright (c) 2012 __MyCompanyName__. All rights reserved.
//

#include "G3MWidget.hpp"

#include "ITimer.hpp"
#include "Renderer.hpp"
#include "Camera.hpp"
#include "IGL.hpp"
#include "TexturesHandler.cpp"
#include "Downloader.hpp"
#include "IDownloader.hpp"


G3MWidget::G3MWidget(IFactory*        factory,
                     ILogger*         logger,
                     IGL*             gl,
                     TexturesHandler* texturesHandler,
                     Downloader*      downloaderOLD,
                     IDownloader*     downloader,
                     const Planet*    planet,
                     Renderer*        renderer,
                     Renderer*        busyRenderer,
                     int              width,
                     int              height,
                     Color            backgroundColor,
                     const bool       logFPS):
_factory(factory),
_logger(logger),
_gl(gl),
_texturesHandler(texturesHandler),
_planet(planet),
_renderer(renderer),
_busyRenderer(busyRenderer),
_camera(new Camera(planet, width, height)),
_backgroundColor(backgroundColor),
_timer(factory->createTimer()),
_renderCounter(0),
_totalRenderTime(0),
_logFPS(logFPS),
_downloaderOLD(downloaderOLD),
_downloader(downloader),
_rendererReady(false) // false until first call to G3MWidget::render()
{
  initializeGL();
  
  InitializationContext ic(_factory, _logger, _planet, _downloaderOLD, _downloader);
  _renderer->initialize(&ic);
}

G3MWidget* G3MWidget::create(IFactory*        factory,
                             ILogger*         logger,
                             IGL*             gl,
                             TexturesHandler* texturesHandler,
                             Downloader *     downloaderOLD,
                             IDownloader*     downloader,
                             const Planet*    planet,
                             Renderer*        renderer,
                             Renderer*        busyRenderer,
                             int              width,
                             int              height,
                             Color             backgroundColor,
                             const bool       logFPS) {
  if (logger != NULL) {
    logger->logInfo("Creating G3MWidget...");
  }
  
  ILogger::setInstance(logger);
  
  return new G3MWidget(factory,
                       logger,
                       gl,
                       texturesHandler,
                       downloaderOLD,
                       downloader,
                       planet,
                       renderer,
                       busyRenderer,
                       width, height,
                       backgroundColor,
                       logFPS);
}

void G3MWidget::initializeGL() {
  _gl->enableDepthTest();
#ifdef C_CODE
  _gl->enableCullFace(BACK);
#endif
#ifdef JAVA_CODE
  _gl.enableCullFace(CullFace.BACK);
#endif
}

G3MWidget::~G3MWidget() { 
  delete _factory;
  delete _logger;
  delete _gl;
#ifdef C_CODE
  delete _planet;
#endif
  delete _renderer;
  delete _camera;
  delete _texturesHandler;
  delete _timer;
#ifdef C_CODE
  delete _downloaderOLD;
#endif
  delete _downloader;
}

void G3MWidget::onTouchEvent(const TouchEvent* myEvent) {
  if (_rendererReady) {
    _renderer->onTouchEvent(myEvent);
  }
}

void G3MWidget::onResizeViewportEvent(int width, int height) {
  if (_rendererReady) {
    _renderer->onResizeViewportEvent(width, height);
  }
}

int G3MWidget::render() {
  _timer->start();
  _renderCounter++;
  
  RenderContext rc(_factory, _logger, _planet, _gl, _camera, _texturesHandler, _downloaderOLD, _downloader);
  
  _rendererReady = _renderer->isReadyToRender(&rc);
  Renderer* selectedRenderer = _rendererReady ? _renderer : _busyRenderer;

  // Clear the scene
  _gl->clearScreen(_backgroundColor);
  
  int timeToRedraw = selectedRenderer->render(&rc);
  
  const TimeInterval elapsedTime = _timer->elapsedTime();
  if (elapsedTime.milliseconds() > 100) {
    //_logger->logWarning("Frame took too much time: %dms" , elapsedTime.milliseconds());
  }
  _totalRenderTime += elapsedTime.milliseconds();
  
  if ((_renderCounter % 60) == 0) {
    if (_logFPS) {
      const double averageTimePerRender = (double) _totalRenderTime / _renderCounter;
      const double fps = 1000.0 / averageTimePerRender;
      _logger->logInfo("FPS=%f" , fps);
    }
    
    _renderCounter = 0;
    _totalRenderTime = 0;
  }
  
  return timeToRedraw;

}
