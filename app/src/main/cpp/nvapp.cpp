#include "nvapp.h"
#include "nvrenderer.h"

namespace nv
{
    NVApp::NVApp():
            renderer_(0)
    {

    }

    NVApp::~NVApp() {

    }

    void NVApp::Init() {
        renderer_ = new render::NVRenderer();
        CreateGLThread();
    }

    void NVApp::Resume() {
        if(renderer_ != 0)
        {
            renderer_->Resume();
        }
    }

    void NVApp::Pause() {
        if(renderer_ != 0)
        {
            renderer_->Pause();
        }
    }

    void NVApp::Deinit() {


        if(renderer_ != 0)
        {
            renderer_->Destroy();
            DestroyGLThread();
            delete renderer_;
            renderer_ = 0;
        }

    }

    render::NVRenderer *NVApp::Render()
    {
        return renderer_;
    }

    void NVApp::CreateGLThread() {
        if(renderer_ != 0)
        {
            gl_thread_ = std::thread(&render::NVRenderer::_Run, renderer_);
        }
    }

    void NVApp::DestroyGLThread() {
        if(renderer_ != 0)
        {
            if(gl_thread_.joinable())
                gl_thread_.join();
        }
    }


}
