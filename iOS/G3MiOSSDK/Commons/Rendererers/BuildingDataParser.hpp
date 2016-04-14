//
//  BuildingDataParser.hpp
//  G3MiOSSDK
//
//  Created by Jose Miguel SN on 11/4/16.
//
//

#ifndef BuildingDataParser_hpp
#define BuildingDataParser_hpp

#include "CityGMLBuilding.hpp"
#include <vector>
#include <string>

class BuildingDataParser{
  
//  class StringExtractionResult{
//    
//  public:
//    std::string _string;
//    size_t _endingPos;
//    
//    StringExtractionResult(std::string string,
//                 size_t endingPos):
//    _string(string),
//    _endingPos(endingPos){}
//  };
//  
//  static StringExtractionResult extractSubStringBetween(const std::string& string,
//                                              const std::string& startTag,
//                                              const std::string& endTag,
//                                              const size_t startPos){
//    
//    size_t pos1 = string.find(startTag, startPos) + startTag.length();
//    size_t pos2 = string.find(endTag, pos1);
//    
//    if (pos1 == std::string::npos || pos2 == std::string::npos || pos1 < startPos || pos2 < startPos){
//      return StringExtractionResult("", std::string::npos);
//    }
//    
//    std::string str = string.substr(pos1, pos2-pos1);
//    
//    return StringExtractionResult(str, pos2 + endTag.length());
//  }
//  
public:
  
  static void includeDataInBuildingSet(const std::string& data,
                                       const std::vector<CityGMLBuilding*>& buildings);
  
  static Mesh* createPointCloudMesh(const std::string& data, const Planet* planet, const ElevationData* elevationData);
  
};

#endif /* BuildingDataParser_hpp */
