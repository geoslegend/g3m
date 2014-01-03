//
//  LabelImageBuilder.hpp
//  G3MiOSSDK
//
//  Created by Diego Gomez Deck on 1/3/14.
//
//

#ifndef __G3MiOSSDK__LabelImageBuilder__
#define __G3MiOSSDK__LabelImageBuilder__

#include "IImageBuilder.hpp"

#include "GFont.hpp"
#include "Color.hpp"

class LabelImageBuilder : public IImageBuilder {
private:
  const std::string _text;
  const GFont       _font;
  const Color       _color;
  const float       _margin;

public:

  LabelImageBuilder(const std::string& text,
                    const GFont&       font = GFont::sansSerif(),
                    const Color&       color = Color::white(),
                    const float        margin = 1) :
  _text(text),
  _font(font),
  _color(color),
  _margin(margin)
  {
//    const float fontSize = 20;
//
//    const Color color       = Color::white();
//    const Color shadowColor = Color::black();
//
//    const int separation = 2;
  }

  void build(const G3MContext* context,
             IImageBuilderListener* listener,
             bool deleteListener);

  const std::string getImageName();
  
};

#endif
