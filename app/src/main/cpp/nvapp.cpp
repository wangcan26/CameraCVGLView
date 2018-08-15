#include "nvapp.h"
#include "nvrenderer.h"
#include "nvtracker.h"

namespace nv
{
    NVApp::NVApp():
            renderer_(0),
            tracker_(0)
    {

    }

    NVApp::~NVApp() {

    }

    void NVApp::Init() {
        renderer_ = new render::NVRenderer();
        CreateGLThread();

        tracker_ = new tracker::NVTracker();
        CreateTrackerThread();
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

        if(tracker_ != 0)
        {
            tracker()->Destroy();
            DestroyTrackerThread();
            delete  tracker_;
            tracker_ = 0;
        }

    }

    render::NVRenderer *NVApp::Render()
    {
        return renderer_;
    }

    tracker::NVTracker* NVApp::tracker() {
        return tracker_;
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

    void NVApp::CreateTrackerThread() {
        if(tracker_ != 0)
        {
            tracker_thread_ = std::thread(&tracker::NVTracker::_Run, tracker_);
        }
    }

    void NVApp::DestroyTrackerThread() {
        if(tracker_ != 0)
        {
            if(tracker_thread_.joinable())
            {
                tracker_thread_.join();
            }
        }
    }




}
