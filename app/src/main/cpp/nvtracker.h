#ifndef NV_TRACKER_H_
#define NV_TRACKER_H_
#include <thread>
#include <condition_variable>
#include <opencv2/highgui.hpp>
#include <FaceTracker/Tracker.h>


namespace nv
{
    namespace tracker
    {
        class NVTracker
        {
        public:
            struct Image{
                unsigned char* buf_;
                int width_;
                int height_;

                Image():width_(640),
                        height_(480),
                        buf_(0)
                {

                }
            };


            NVTracker(const std::string& path);

            ~NVTracker();

            void Resume();

            void Pause();

            void NotifyCameraReady();

            void NotifyCameraIdle();

            bool PushImage(int width, int height, unsigned char* buf);

            bool PopImage(Image& image);

            void _Run();

            void Destroy();

        protected:

            void _Capture(cv::Mat& gray);

            void _ProcessIO(const std::string& path);

            void _PopImage(const Image& image);

        private:

            enum TrackerMessage{
                MSG_NONE = 0,
                MSG_FRAME_AVAIABLE,
                MSG_LOOP_EXIT
            };

            std::mutex pc_mut_;
            std::condition_variable push_cond_, pop_cond_;

            std::mutex tl_mut_;
            std::condition_variable tl_cond_;

            static int kMaxImages;

            std::string  app_path_;
            enum TrackerMessage msg_;
            Image       images_[2];
            Image       pop_image_;

            cv::Mat mat_list_[2];
            int     image_index_;

            bool        start_;
            bool        can_pop_;
            bool        pop_;

            bool       cam_configured_;

            // Related args about tracker
            FACETRACKER::Tracker   *model_;

        };
    }
}



#endif //NV_TRACKER_H_