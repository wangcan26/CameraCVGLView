#include <opencv2/imgproc.hpp>
#include <chrono>
#include "nvapp.h"
#include "nvtracker.h"
#include "nvrenderer.h"
#include "logger.h"
#include "nv_utils.h"
#include "global_interface.h"

#define LOG_TAG "NVTracker"

namespace nv
{
    namespace tracker
    {
        int NVTracker::kMaxImages = 1;
        NVTracker::NVTracker(NVApp* app, const std::string& path):
                app_(app),
                app_path_(path),
                msg_(MSG_NONE),
                image_index_(0),
                last_image_index_(0),
                start_(true),
                is_process_(false),
                do_process_(false),
                pop_(false),
                is_pause_(false),
                timestamp_(0.0),
                cam_configured_(false)
        {

        }

        NVTracker::~NVTracker() {

        }

        void NVTracker::Resume() {
            LOG_INFO("NVTracker Lifecycle Resume");
            is_pause_ = false;
        }

        void NVTracker::Pause() {
            LOG_INFO("NVTracker Lifecycle Pause");
            is_pause_ = true;
        }

        void NVTracker::Destroy() {
            LOG_INFO("NVTracker Lifecycle Destroy send msg exit");
            std::unique_lock<std::mutex> msg_lk(msg_mut_);
            is_process_ = false;
            msg_= MSG_LOOP_EXIT;
            msg_lk.unlock();

            LOG_INFO("NVTracker Lifecycle Destroy notify thread go");
            std::unique_lock<std::mutex> tl_lk(tl_mut_);
            tl_cond_.notify_one();
            tl_lk.unlock();
        }

        void NVTracker::NotifyCameraReady() {
            std::unique_lock<std::mutex> tl_lk(tl_mut_);
            LOG_INFO("NVTracker Lifecycle Camera Ready");
            tl_cond_.notify_one();
            tl_lk.unlock();
        }

        void NVTracker::NotifyCameraWait()  {
            LOG_INFO("NVTracker Lifecycle Camera Wait");
            std::lock_guard<std::mutex> msg_lk(msg_mut_);
            msg_ = MSG_WAIT_READY;
        }

        bool NVTracker::PushImage(int width, int height, unsigned  char* buf, double timestamp) {
            ///Producer
            Image *image = &images_[image_index_];  /// 0 side  0 1 0
            if(image->buf_ !=0 )
            {
                delete image->buf_;
                image->buf_ = 0;
            }
            image->buf_ = buf;
            image->width_ = width;
            image->height_ = height;
            image->timestamp_ = timestamp;

            LOG_INFO("nv log timestamp Test image tracker Push In... %d  %f", image_index_,
                     images_[image_index_].timestamp_);

            //image array has  full images
            ///Consumer
            if(!is_process_)
            {

                if (image_index_ == 1 ) { // 0, 1
                    is_process_ = true;
                }
                image_index_ = kMaxImages - image_index_;
            }


            if(is_process_ && msg_ == MSG_NONE)
            {
                msg_ = MSG_FRAME_AVAIABLE;
            }

            return true;
        }

        bool NVTracker::PopImage(Image& image) {
            //Accessor
            std::unique_lock<std::mutex> lk(pc_mut_);
            if(!do_process_){
                lk.unlock();
                return false;
            }

            if(!pop_)
            {
                pop_ = true;
                pc_cond_.wait(lk);
                image = pop_image_;
            }
            lk.unlock();


            return true;
        }

        void NVTracker::_WaitForCamReady() {
            std::unique_lock<std::mutex> tl_lk(tl_mut_);
            tl_cond_.wait(tl_lk);
            tl_lk.unlock();
        }

        ////////////////////////////////////////////////////////////
        //                    | push in           \|/ pop out   ||//
        //                   \/                   \/
        //|| Processor ||   0 side    ||       1 side           ||//
        //|| Producer  ||   0 side    ||       1 side           ||  is process //
        //|| images_   || image_index || kMaxImages- image_index||//
        //                   |    __________________|             //
        //                   |___|____________________
        //                       |                     |
        //                       |                    \|/
        //                      \/ to gray            \/
        //|| Consumer  ||   0 side               ||  1 side      ||  do_process //

        bool NVTracker::_Capture(cv::Mat& gray){
            bool res = false;
            switch (msg_) {
                case MSG_FRAME_AVAIABLE:
                {
                    //std::lock_guard<std::mutex> msg_lk(msg_mut_);
                    //Next Frame
                    image_index_ = kMaxImages - image_index_; /// switch to push side  1 0 1
                    Image *image = &images_[kMaxImages - image_index_]; /// pop side 0 1 0 //Get newest image
                    timestamp_ = image->timestamp_;

                    LOG_INFO("NVTracker Do Image Process total time %f ", timestamp_ - android_app_acquire_tex_timestamp());


                    LOG_INFO("nv log timestamp Test image Pop Out... %d %f",
                             image_index_, image->timestamp_);
                    if(!do_process_)
                    {
                        if(image_index_ == 0) // 0, 1
                        {
                            do_process_ = true;
                            gray = cv::Mat(image->height_, image->width_, CV_8UC1,
                                           new unsigned char[image->height_*image->width_]);
                            app_->Render()->StartSync();
                        }
                    }

                    cv::Mat frame = cv::Mat(image->height_, image->width_, CV_8UC1,
                                            image->buf_);
                    if(do_process_)
                    {
                        res = true;
                        frame.copyTo(gray);
                    }

                    if(msg_ != MSG_LOOP_EXIT )
                    {
                        msg_ = MSG_NONE;
                    }
                }
                    break;
                case MSG_WAIT_READY:
                {
                    _WaitForCamReady();
                    if(msg_ != MSG_LOOP_EXIT)
                        msg_ = MSG_NONE;
                    res = false;
                }
                    break;
                case MSG_LOOP_EXIT:
                {
                    LOG_INFO("NVTracker Lifecycle msg Exit");
                    res = false;
                    start_ = false;
                    image_index_ = 0;

                    for(int i= 0; i < kMaxImages; i++)
                    {
                        if(images_[i].buf_ != 0)
                        {
                            delete images_[i].buf_;
                            images_[i].buf_ = 0;
                        }
                    }

                    if(gray.data != 0)
                    {
                        delete gray.data;
                        gray.data = 0;
                    }

                    if(app_->Render() != 0)
                    {
                        app_->Render()->StopSync();
                    }

                    do_process_ = false;

                    msg_ = MSG_NONE;
                }
                    break;
                default:
                    break;
            }

            return res;
        }

        void NVTracker::_PopImage(const cv::Mat& image) {
            pop_image_.width_ = image.cols;
            pop_image_.height_ = image.rows;
            int len = pop_image_.width_*pop_image_.height_;
            LOG_INFO("NVTracker _PopImage data %d ", len);
            pop_image_.buf_ = new unsigned char[len];
            memcpy(pop_image_.buf_, image.data, len);
        }


        void NVTracker::_ProcessIO(const std::string& path) {


        }


        void NVTracker::_Run() {

            std::unique_lock<std::mutex> msg_lk(msg_mut_);
            if(msg_ == MSG_NONE)
                msg_ = MSG_WAIT_READY;
            msg_lk.unlock();


            //Read files into tracker
            _ProcessIO(app_path_);


            cv::Mat gray;

            while(start_)
            {

                if(!_Capture(gray)) {
                    continue;
                }
                cv::flip(gray, gray, 0);

                if(!is_pause_)
                {
                    if(app_->Render() != 0)
                    {
                        app_->Render()->SyncTracker();
                    }

                    if(pop_)
                    {
                        pop_ = false;
                        std::unique_lock<std::mutex> lk(pc_mut_);
                        _PopImage(gray);
                        pc_cond_.notify_one();
                        lk.unlock();
                    }
                }

                float tic = nv::NVClock();
                float toc = tic;

            }


        }

    }
}