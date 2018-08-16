#include <opencv2/imgproc.hpp>
#include <chrono>
#include "nvtracker.h"
#include "logger.h"
#include "nv_utils.h"

#define LOG_TAG "NVTracker"

namespace nv
{
    namespace tracker
    {
        NVTracker::NVTracker():
                msg_(MSG_NONE),
                image_index_(0),
                start_(true),
                cam_configured_(false),
                is_push_(false),
                is_pop_(false),
                width_(640),
                height_(480),
                buf_(0)
        {

        }

        NVTracker::~NVTracker() {

        }

        void NVTracker::Resume() {
            start_ = true;
        }

        void NVTracker::Pause() {
            LOG_INFO("NVTracker Destroy  ...");
            std::lock_guard<std::mutex> lk(pc_mut_);
            tl_cond_.notify_one();
            msg_= MSG_LOOP_EXIT;
        }

        void NVTracker::Destroy() {


        }

        void NVTracker::NotifyCameraReady() {

            std::lock_guard<std::mutex> tl_lk(tl_mut_);
            tl_cond_.notify_one();
        }

        void NVTracker::NotifyCameraIdle() {
            std::lock_guard<std::mutex> tl_lk(tl_mut_);
            LOG_INFO("NVTracker Run In idle ...");
            cam_configured_ = false;
        }

        bool NVTracker::PushImage(int width, int height, unsigned  char* buf, bool block_caller) {
            std::unique_lock<std::mutex> lk(pc_mut_);
            /*if(image_index_ == 2)
            {
                LOG_INFO("nv log Tracker PushImage %d\n", image_index_);
                push_cond_.wait(lk);
            }*/
            if(is_push_){
                delete  buf;
                lk.unlock();
                return false;
            }

            buf_ = buf;

            width_ = width;
            height_ = height;

            //pop_cond_.notify_one();
            is_push_ = true;
            msg_ = MSG_FRAME_AVAIABLE;

            lk.unlock();
            return true;
        }

         bool NVTracker::PopImage(cv::Mat& result) {
            std::unique_lock<std::mutex> lk(pc_mut_);
            if(!is_pop_){
                lk.unlock();
                return false;
            }
            /*if(image_index_ == 0)
            {
                LOG_INFO("nv log Tracker PopImage %d\n", image_index_-1);
                pop_cond_.wait(lk);
            }
            */
            result = mat_list_[image_index_];
            // LOG_INFO("nv log Tracker PopImage %d\n", image_index_);
            //image_index_--;
            //push_cond_.notify_one();
             is_pop_ =false;
            lk.unlock();
            return true;
        }

        void NVTracker::_Run() {
            //A little later than gl thread
            LOG_INFO("NVTracker Run In While begin ...");

            std::unique_lock<std::mutex> tl_lk(tl_mut_);
            tl_cond_.wait(tl_lk);
            tl_lk.unlock();
            std::this_thread::sleep_for(std::chrono::milliseconds(1000));

            LOG_INFO("NVTracker Run In While end ...");
            while(start_)
            {
                std::unique_lock<std::mutex> pc_lk(pc_mut_);
                float tic = nv::NVClock();
                switch (msg_)
                {
                    case MSG_FRAME_AVAIABLE:
                    {
                        cv::Mat gray =  cv::Mat(height_, width_, CV_8UC1, buf_);
                        cv::flip(gray, gray, 0);
                        mat_list_[image_index_] = gray;

                        is_push_ = false;
                        is_pop_ = true;
                        msg_ = MSG_NONE;

                        float  toc = nv::NVClock();
                        LOG_INFO("NVTracker PreProcess rgb w-h......:%d-%d , time comsume: %f ms.......\n",width_, height_, toc-tic);
                        tic = toc;
                    }
                        break;
                    case MSG_LOOP_EXIT:
                    {
                        start_ = false;
                        msg_ = MSG_NONE;
                    }
                        break;
                    default:
                        break;
                }
                pc_lk.unlock();
            }
            LOG_INFO("NVTracker Run out While end ...");
        }
    }
}