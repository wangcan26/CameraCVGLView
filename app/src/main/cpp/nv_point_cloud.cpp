#include "nv_point_cloud.h"
#include "nvrenderer.h"
#include "logger.h"

#define LOG_TAG "NVPointCloud"


static auto kVertexShader =
        "#version 100\n"
        "attribute vec2 vPosition;\n"
        "void main(){\n"
        "   gl_PointSize = 10.0;\n"
        "   gl_Position = vec4(vPosition, 0.0, 1.0);\n"
        "}\n";

static auto kFragmentShader =
        "#version 100\n"
        "precision mediump float;\n"
        "void main(){\n"
        "   gl_FragColor = vec4(0.1215, 0.7372, 0.8235, 1.0);\n"
        "}\n";


namespace nv
{
    namespace render
    {
        NVPointCloud::NVPointCloud(NVRenderer *renderer):
            renderer_(renderer)
        {
            //Create Program Id
            program_id_ = renderer->CreateProgram(kVertexShader, kFragmentShader);
            if (!program_id_) {
                LOG_ERROR("NVPointCloud Could not create program.");
            }
            //Get variables from glsl
            position_handle_ = glGetAttribLocation(program_id_, "vPosition");
        }

        NVPointCloud::~NVPointCloud() {}

        void NVPointCloud::Render() {
            glUseProgram(program_id_);

            int num_of_points = 0;
            renderer_->GetPointCloudNum(&num_of_points);


            if(num_of_points <= 0)return;

            float *point_cloud_data;
            renderer_->GetPointCloudPoints(&point_cloud_data);
            LOG_INFO("NVPointCloud Render num_of_points %f, %f", point_cloud_data[0], point_cloud_data[1]);

            glEnableVertexAttribArray(position_handle_);
            glVertexAttribPointer(position_handle_, 2, GL_FLOAT, GL_FALSE, 0, point_cloud_data);


            glDrawArrays(GL_POINTS, 0, num_of_points);
            glUseProgram(0);
        }
    }
}