#ifndef NV_APP_H_
#define NV_APP_H_

#include <thread>

namespace nv
{
    namespace render
    {
        class NVRenderer;
    }
    class NVApp{
    public:
        NVApp();

        ~NVApp();

        void Init();

        void Resume();

        void Pause();

        void Deinit();

        render::NVRenderer *Render();

    protected:
        void CreateGLThread();

        void DestroyGLThread();

    private:
        render::NVRenderer *renderer_;

        std::thread    gl_thread_;

    };
}


#endif //NV_APP_H_