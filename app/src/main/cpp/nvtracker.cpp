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
        int NVTracker::kMaxImages = 1;
        NVTracker::NVTracker(const std::string& path):
                app_path_(path),
                msg_(MSG_NONE),
                image_index_(0),
                start_(false),
                can_pop_(false),
                pop_(false),
                cam_configured_(false),
                model_(0)
        {

        }

        NVTracker::~NVTracker() {

        }

        void NVTracker::Resume() {
            start_ = false;
            image_index_ = 0;
        }

        void NVTracker::Pause() {
            LOG_INFO("NVTracker Destroy  ...");
            std::lock_guard<std::mutex> lk(pc_mut_);
            tl_cond_.notify_one();
            pop_cond_.notify_one();

            msg_= MSG_LOOP_EXIT;
        }

        void NVTracker::Destroy() {

            for(int i= 0; i < kMaxImages; i++)
            {
                if(images_[i].buf_ != 0)
                {
                    delete images_[i].buf_;
                    images_[i].buf_ = 0;
                }
            }

            if(model_ != 0)
            {
                delete model_;
                model_ = 0;
            }


        }

        void NVTracker::NotifyCameraReady() {

        }

        void NVTracker::NotifyCameraIdle() {
            std::lock_guard<std::mutex> tl_lk(tl_mut_);
            LOG_INFO("NVTracker Run In idle ...");
            cam_configured_ = false;
        }

        bool NVTracker::PushImage(int width, int height, unsigned  char* buf) {
            ///Producer
            Image *image = &images_[image_index_];

            if(image->buf_ !=0 )
            {
                LOG_INFO("NVTracker Producer-Consumer Push In... %d", image_index_);
                delete image->buf_;
                image->buf_ = 0;
            }
            image->buf_ = buf;

            image->width_ = width;
            image->height_ = height;

            if(!start_)
            {
                image_index_ = kMaxImages -image_index_;
            }

            msg_ = MSG_FRAME_AVAIABLE;

            //image array has  full images
            if(image_index_ == 0&& !start_)
            {
                std::lock_guard<std::mutex> tl_lk(tl_mut_);
                start_ = true;
                tl_cond_.notify_one();
            }

            return true;
        }

         bool NVTracker::PopImage(Image& image) {
            //Accessor
            std::unique_lock<std::mutex> lk(pc_mut_);
             if(!can_pop_){
                 lk.unlock();
                 return false;
             }

             if(!pop_)
             {
                 pop_ = true;
                 pop_cond_.wait(lk);
             }

             image = pop_image_;

             lk.unlock();
            return true;
        }


        void NVTracker::_Capture(cv::Mat& gray){
            Image *image = &images_[kMaxImages -image_index_];
            LOG_INFO("NVTracker Producer-Consumer Pop Out... %d", kMaxImages -image_index_);
            gray =  cv::Mat(image->height_, image->width_, CV_8UC1, image->buf_);
            cv::flip(gray, gray, 0);
            mat_list_[image_index_] = gray;
        }

        void NVTracker::_PopImage(const Image& image) {
            pop_image_.width_ = image.width_;
            pop_image_.height_ = image.height_;
            int len = pop_image_.width_*pop_image_.height_;
            pop_image_.buf_ = new unsigned char[len];
            memcpy(pop_image_.buf_, image.buf_, len);
        }


        void NVTracker::_ProcessIO(const std::string& path) {
            std::string ft_file(path+"/face2.tracker");

            if(model_ == 0)
            {
                model_ =  new FACETRACKER::Tracker(ft_file.c_str());
            }

        }

        void NVTracker::_Run() {
            //Wait for Image Array Ready
            std::unique_lock<std::mutex> tl_lk(tl_mut_);
            tl_cond_.wait(tl_lk);
            tl_lk.unlock();

            //Read files into tracker
            _ProcessIO(app_path_);
            cv::Mat gray;

            float tic = nv::NVClock();
            while(start_)
            {

                switch (msg_)
                {
                    case MSG_FRAME_AVAIABLE:
                    {
                        ///Consumer
                        if(image_index_ == 1 && !can_pop_)
                        {
                            can_pop_ = true;
                        }

                        _Capture(gray);


                        std::unique_lock<std::mutex> lk(pc_mut_);
                        if(pop_)
                        {
                            Image *image = &images_[kMaxImages -image_index_];
                            _PopImage(*image);
                            pop_ = false;
                        }
                        pop_cond_.notify_one();
                        lk.unlock();


                        image_index_ = kMaxImages -image_index_;
                        msg_ = MSG_NONE;
                        float  toc = nv::NVClock();
                        LOG_INFO("NVTracker Run In... %d, %d, %f ms\n", gray.rows, gray.cols, toc-tic);
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
            }
        }
    }
}