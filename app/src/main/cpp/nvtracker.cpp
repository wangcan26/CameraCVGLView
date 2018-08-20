#include <opencv2/imgproc.hpp>
#include <chrono>
#include "nvapp.h"
#include "nvtracker.h"
#include "logger.h"
#include "nv_utils.h"

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
                start_(true),
                is_process_(false),
                pop_(false),
                cam_configured_(false),
                model_(0)
        {
            //Read files into tracker
            _ProcessIO(app_path_);
        }

        NVTracker::~NVTracker() {

        }

        void NVTracker::Resume() {
            LOG_INFO("NVTracker Lifecycle Resume");

        }

        void NVTracker::Pause() {
            LOG_INFO("NVTracker Lifecycle Pause");

        }

        void NVTracker::Destroy() {
            std::unique_lock<std::mutex> lk(tl_mut_);
            LOG_INFO("NVTracker Lifecycle Destroy");
            msg_= MSG_LOOP_EXIT;
            tl_cond_.notify_one();
            lk.unlock();

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
            std::unique_lock<std::mutex> tl_lk(tl_mut_);
            LOG_INFO("NVTracker Lifecycle Camera Ready");
            tl_cond_.notify_one();
            tl_lk.unlock();

        }

        void NVTracker::NotifyCameraWait()  {
            LOG_INFO("NVTracker Lifecycle Camera Wait");
            std::lock_guard<std::mutex> lk(tl_mut_);
            image_index_ = 0;
            msg_ = MSG_WAIT_READY;
        }

        bool NVTracker::PushImage(int width, int height, unsigned  char* buf) {
            ///Producer
            Image *image = &images_[image_index_];

            if(image->buf_ !=0 )
            {
                delete image->buf_;
                image->buf_ = 0;
            }
            image->buf_ = buf;

            image->width_ = width;
            image->height_ = height;


            //image array has  full images
            LOG_INFO("NVTracker Producer-Consumer Push In... %d  %d", image_index_, msg_);

            ///Consumer
            if(!is_process_)
            {

                if (image_index_ == 1 ) {
                    is_process_ = true;
                }
                image_index_ = kMaxImages - image_index_;

            }



            if(msg_ == MSG_NONE && is_process_)
            {
                msg_ = MSG_FRAME_AVAIABLE;
            }

            return true;
        }

         bool NVTracker::PopImage(Image& image) {
            //Accessor
            std::unique_lock<std::mutex> lk(pc_mut_);
             if(!is_process_){
                 lk.unlock();
                 return false;
             }

             if(!pop_)
             {
                 pop_ = true;
                 pop_cond_.wait(lk);
                 lk.unlock();
                 image = pop_image_;
             }


            return true;
        }


        void NVTracker::_WaitForCamReady() {
            std::unique_lock<std::mutex> tl_lk(tl_mut_);
            tl_cond_.wait(tl_lk);
            tl_lk.unlock();
        }


        bool NVTracker::_Capture(cv::Mat& gray){
            bool res = true;
            switch (msg_) {
                case MSG_FRAME_AVAIABLE:
                {
                    LOG_INFO("NVTracker Msg Frame Avaiable");

                    Image *image = &images_[kMaxImages - image_index_];
                    LOG_INFO("NVTracker Producer-Consumer Pop Out... %d",
                             kMaxImages - image_index_);
                    mat_list_[image_index_] = cv::Mat(image->height_, image->width_, CV_8UC1,
                                                      image->buf_);
                    //Next Frame
                    image_index_ = kMaxImages - image_index_;
                    msg_ = MSG_NONE;
                }
                    break;
                case MSG_WAIT_READY:

                    _WaitForCamReady();
                    if(msg_ != MSG_LOOP_EXIT)
                        msg_ = MSG_NONE;
                    LOG_INFO("NVTracker Lifecycle Msg Ready");
                    std::this_thread::sleep_for(std::chrono::milliseconds(10));
                    res = false;
                    break;
                case MSG_LOOP_EXIT:
                {
                    LOG_INFO("NVTracker Lifecycle msg Exit");
                    res = false;
                    start_ = false;
                    image_index_ = 0;
                    is_process_ = false;
                    msg_ = MSG_NONE;
                }
                    break;
                default:
                    res = false;
                    break;
            }
            if(res) gray = mat_list_[kMaxImages - image_index_];

            return res;
        }

        void NVTracker::_PopImage(const cv::Mat& image) {
            pop_image_.width_ = image.cols;
            pop_image_.height_ = image.rows;
            int len = pop_image_.width_*pop_image_.height_;
            pop_image_.buf_ = new unsigned char[len];
            memcpy(pop_image_.buf_, image.data, len);
        }


        void NVTracker::_ProcessIO(const std::string& path) {
            std::string ft_file(path+"/face2.tracker");

            if(model_ == 0)
            {
                model_ =  new FACETRACKER::Tracker(ft_file.c_str());
            }

        }

        void NVTracker::_Run() {
            msg_ = MSG_WAIT_READY;

            cv::Mat gray;
            float tic = nv::NVClock();
            while(start_)
            {
                if(!_Capture(gray)) {
                    continue;
                }
                cv::flip(gray, gray, 0);

                if(pop_)
                {
                    std::unique_lock<std::mutex> lk(pc_mut_);
                    LOG_INFO("NVTracker Run PopImage");
                    _PopImage(gray);
                    pop_ = false;
                    pop_cond_.notify_one();
                    lk.unlock();
                }



                float  toc = nv::NVClock();
                //LOG_INFO("NVTracker Run In... %d, %d, %f ms\n", gray.rows, gray.cols, toc-tic);
                tic = toc;
            }
        }
    }
}