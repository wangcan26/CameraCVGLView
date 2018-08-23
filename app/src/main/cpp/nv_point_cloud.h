#ifndef NV_POINT_CLOUD_H_
#define NV_POINT_CLOUD_H_

#include <GLES2/gl2.h>

namespace nv
{
    namespace render
    {
        class NVRenderer;
        class NVPointCloud
        {
        public:
            NVPointCloud(NVRenderer *renderer);
            ~NVPointCloud();

            void Render();

        private:
            NVRenderer *renderer_;
            GLuint  vertex_id_;
            GLuint  program_id_;
            GLuint  position_handle_;
        };
    }
}



#endif //NV_POINT_CLOUD_H_