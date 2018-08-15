#ifndef NV_UTILS_H_
#define NV_UTILS_H_

#include <time.h>


namespace nv
{
    int64_t NVGetTimeNsec() {
        struct timespec now;
        clock_gettime(CLOCK_REALTIME, &now);
        return (int64_t) (now.tv_sec*1000000000LL + now.tv_nsec);
    }

    float NVClock() {
        static struct timespec _base;
        static bool firstCall = true;

        if (firstCall) {
            clock_gettime(CLOCK_MONOTONIC, &_base);
            firstCall = false;
        }

        struct timespec t;
        clock_gettime(CLOCK_MONOTONIC, &t);
        float secDiff = (float)(t.tv_sec - _base.tv_sec);
        float msecDiff = (float)((t.tv_nsec - _base.tv_nsec) / 1000000);
        return secDiff*1000.0f + msecDiff;
    }

}

#endif //NV_UTILS_H_