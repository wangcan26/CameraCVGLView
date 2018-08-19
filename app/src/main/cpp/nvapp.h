#ifndef NV_APP_H_
#define NV_APP_H_

#include <thread>

namespace nv
{
    namespace render
    {
        class NVRenderer;
    }

    namespace tracker
    {
        class NVTracker;
    }

    class NVApp{
    public:
        NVApp();

        ~NVApp();

        void Init(const std::string &path);

        void Resume();

        void Pause();

        void Deinit();

        render::NVRenderer *Render();
        tracker::NVTracker *tracker();

    protected:
        void CreateGLThread();
        void DestroyGLThread();

        void CreateTrackerThread();
        void DestroyTrackerThread();

    private:
        render::NVRenderer *renderer_;
        tracker::NVTracker *tracker_;

        std::thread    gl_thread_;
        std::thread    tracker_thread_;

    };
}


#endif //NV_APP_H_