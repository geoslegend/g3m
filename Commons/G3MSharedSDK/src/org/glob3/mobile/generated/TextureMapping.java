package org.glob3.mobile.generated; 
//
//  TextureMapping.cpp
//  G3MiOSSDK
//
//  Created by Diego Gomez Deck on 12/07/12.
//  Copyright (c) 2012 IGO Software SL. All rights reserved.
//

//
//  TextureMapping.hpp
//  G3MiOSSDK
//
//  Created by José Miguel S N on 12/07/12.
//  Copyright (c) 2012 __MyCompanyName__. All rights reserved.
//


///#include <vector>

//class IGLTextureId;

//class G3MRenderContext;
//class IFloatBuffer;
//class GLGlobalState;
//class GPUProgramState;

public abstract class TextureMapping
{

  public void dispose()
  {
  }

  /**
   Returns a new GLGlobalState and changes the current program state
   */
//  virtual GLGlobalState* bind(const G3MRenderContext* rc, const GLGlobalState& parentState, GPUProgramState& progState) const = 0;

  public abstract boolean isTransparent(G3MRenderContext rc);

  public abstract void modifyGLGlobalState(GLGlobalState GLGlobalState);
  public abstract void modifyGPUProgramState(GPUProgramState progState);
}