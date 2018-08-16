#ifndef NV_TRACKER_H_
#define NV_TRACKER_H_
#include <thread>
#include <condition_variable>
#include <opencv2/highgui.hpp>



namespace nv
{
    namespace tracker
    {
        class NVTracker
        {
        public:
            NVTracker();

            ~NVTracker();

            void Resume();

            void Pause();

            void NotifyCameraReady();

            void NotifyCameraIdle();

            bool PushImage(int width, int height, unsigned char* buf, bool block_caller);

            bool PopImage(cv::Mat& result);

            void _Run();

            void Destroy();

        private:

            enum TrackerMessage{
                MSG_NONE = 0,
                MSG_FRAME_AVAIABLE,
                MSG_FRAME_RELEASE,
                MSG_LOOP_EXIT
            };

            enum TrackerMessage msg_;

            std::mutex pc_mut_;
            std::condition_variable push_cond_, pop_cond_;

            std::mutex tl_mut_;
            std::condition_variable tl_cond_;

            cv::Mat mat_list_[2];
            int     image_index_;

            bool start_;
            bool is_push_;
            bool is_pop_;

            bool       cam_configured_;

            int     width_;
            int     height_;



            unsigned char* buf_;
        };
    }
}



#endif //NV_TRACKER_H_