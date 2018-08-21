#include "nvapp.h"
#include "nvrenderer.h"
#include "nvtracker.h"
#include "logger.h"
#define LOG_TAG "NVApp"

namespace nv
{
    NVApp::NVApp():
            renderer_(0),
            tracker_(0)
    {

    }

    NVApp::~NVApp() {

    }

    void NVApp::Init(const std::string &path) {
        renderer_ = new render::NVRenderer(this);
        tracker_ = new tracker::NVTracker(this, path);
        CreateGLThread();
        CreateTrackerThread();
    }

    void NVApp::Resume() {
        if(renderer_ != 0)
        {
            renderer_->Resume();
        }

        if(tracker_ != 0)
        {
            tracker_->Resume();
        }

    }

    void NVApp::Pause() {
        if(renderer_ != 0)
        {
            renderer_->Pause();
        }

        if(tracker_ != 0)
        {
            tracker_->Pause();

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
            {
                LOG_INFO("NVRenderer Lifecycle thread join begin");
                gl_thread_.join();
                LOG_INFO("NVRenderer Lifecycle thread join end");
            }
        }
    }

    void NVApp::CreateTrackerThread() {
        LOG_INFO("NVTracker Lifecycle create tracker thread");
        if(tracker_ != 0)
        {
            tracker_thread_ = std::thread(&tracker::NVTracker::_Run, tracker_);
        }
    }

    void NVApp::DestroyTrackerThread() {

        if(tracker_ != 0)
        {
            LOG_INFO("NVTracker Lifecycle destroy tracker thread begin");
            if(tracker_thread_.joinable())
            {
                tracker_thread_.join();
            }
            LOG_INFO("NVTracker Lifecycle destroy tracker thread end");
        }
    }




}
