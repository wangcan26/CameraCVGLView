//
// Created by root on 18-8-10.
//

#ifndef FACE_TRACKER_ANDROID_NV_CAM_BACKGROUND_H
#define FACE_TRACKER_ANDROID_NV_CAM_BACKGROUND_H
#include <GLES2/gl2.h>


namespace nv
{
    namespace render
    {
        class NVRenderer;
        class NVCameraBackground {
        public:
            NVCameraBackground(NVRenderer *renderer);
            ~NVCameraBackground();

            void Render(bool flip);

        private:
            NVRenderer *renderer_;
            GLuint  vertex_id_;
            GLuint  uv_id_;
            GLuint  uv_flip_id;
            GLuint  indice_id_;
            GLuint  program_id_;
            GLuint  position_handle_;
            GLuint  uv_handle_;
            GLuint  texture_handle_;

        };
    }

}

#endif //FACE_TRACKER_ANDROID_NV_CAM_BACKGROUND_H
