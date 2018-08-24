// CSIRO has filed various patents which cover the Software. 

// CSIRO grants to you a license to any patents granted for inventions
// implemented by the Software for academic, research and non-commercial
// use only.

// CSIRO hereby reserves all rights to its inventions implemented by the
// Software and any patents subsequently granted for those inventions
// that are not expressly granted to you.  Should you wish to license the
// patents relating to the Software for commercial use please contact
// CSIRO IP & Licensing, Gautam Tendulkar (gautam.tendulkar@csiro.au) or
// Nick Marsh (nick.marsh@csiro.au)

// This software is provided under the CSIRO OPEN SOURCE LICENSE
// (GPL2) which can be found in the LICENSE file located in the top
// most directory of the source code.

// Copyright CSIRO 2013

#include <tracker/IO.hpp>
#include <tracker/myFaceTracker.hpp>
#include <Config.h>
#include <tracker/ShapeModel.hpp>

using namespace FACETRACKER;
using namespace std;
//===========================================================================
FaceTrackerParams::~FaceTrackerParams()
{

}
//===========================================================================
FaceTracker::~FaceTracker()
{

}
//===========================================================================
FaceTracker* FACETRACKER::LoadFaceTracker(const char* fname)
{
  int type; FaceTracker* model=NULL;
  ifstream file(fname); assert(file.is_open()); file >> type; file.close();
  switch(type){
  case IO::MYFACETRACKER: 
    model = new myFaceTracker(fname); break;
  default:    
    file.open(fname,std::ios::binary); assert(file.is_open());
    file.read(reinterpret_cast<char*>(&type), sizeof(type));
    file.close();
    if(type == IOBinary::MYFACETRACKER){
      model = new myFaceTracker(fname, true);
    }
    else
      LOG_ERROR("ERROR(%s,%d) : unknown facetracker type %d\n",
                __FILE__,__LINE__,type);
  }return model;
}
//============================================================================
FaceTrackerParams * FACETRACKER::LoadFaceTrackerParams(const char* fname)
{
  int type; FaceTrackerParams * model = NULL;
  ifstream file(fname); assert(file.is_open()); file >> type; file.close();
  switch(type){
  case IO::MYFACETRACKERPARAMS: 
    model =  new myFaceTrackerParams(fname); 
    break;
  default:
    file.open(fname,std::ios::binary); assert(file.is_open());
    file.read(reinterpret_cast<char*>(&type), sizeof(type));
    file.close();
    if(type == IOBinary::MYFACETRACKERPARAMS)
      model = new myFaceTrackerParams(fname, true);
    else
      LOG_ERROR("ERROR(%s,%d) : unknown facetracker parameter type %d\n",
                __FILE__,__LINE__,type);
  }return model;
}
//============================================================================

FaceTracker *
FACETRACKER::LoadFaceTracker()
{
  return LoadFaceTracker(Configure::GetSingleton().GetModelPathName().c_str());
}

FaceTrackerParams *
FACETRACKER::LoadFaceTrackerParams()
{
  return LoadFaceTrackerParams(Configure::GetSingleton().GetParamsPathName().c_str());
}

// Compute the vectors for each axis i.e. x-axis, y-axis then
// z-axis. Alternatively the pitch, yaw, roll
cv::Mat_<double>
FACETRACKER::pose_axes(const Pose &pose)
{
  cv::Mat_<double> rv(3,3);
  Euler2Rot(rv, pose.pitch, pose.yaw, pose.roll);  
  return rv;
}
