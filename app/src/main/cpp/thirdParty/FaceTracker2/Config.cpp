#include <assert.h>
#include "Config.h"

Configure *Configure::kSingleton = 0;
int  Configure::kFramesUtilResetWhenOutsideFrame = 50;

Configure::Configure() {
    kSingleton = 0;
    assert(!kSingleton);
    kSingleton = this;
}

Configure::~Configure() {
    if(kSingleton != 0)
    {
        assert(kSingleton);
        kSingleton = 0;
    }
}

Configure& Configure::GetSingleton() {
    assert(kSingleton);
    return (*kSingleton);
}

Configure* Configure::GetSingletonPtr() {
    return kSingleton;
}

void Configure::Init(const std::string &path) {
    path_ = path;
}

std::string Configure::GetModelPathName() {
    return path_+"/face.mytracker.binary";
}

std::string Configure::GetParamsPathName() {
    return path_ + "/face.mytrackerparams.binary";
}