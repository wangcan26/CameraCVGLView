//
// Created by root on 18-8-10.
//

#include "nv_cam_background.h"

#define LOG_TAG "NVCameraBackground"

#include "nvrenderer.h"
#include "logger.h"

static auto kVertexShader =
        "attribute vec4 vPosition;\n"
                "attribute vec2 vUv;\n"
                "varying vec2 oUv;\n"
                "void main() {\n"
                "oUv = vUv;\n"
                "  gl_Position = vPosition;\n"
                "}\n";

static auto kFragmentShader =
        "#extension GL_OES_EGL_image_external:require\n"
        "precision mediump float;\n"
                "varying vec2 oUv;\n"
                "uniform samplerExternalOES uTexture;\n"
                "void main() {\n"
                "  vec4 color = texture2D(uTexture, oUv);\n"
                "  gl_FragColor = color;\n"
                "}\n";

static const GLfloat kTriangleVertices[] = { -1.0f, -1.0f, -1.0f, 1.0f,
                                      1.0f, 1.0f, 1.0f, -1.0f };

static const GLfloat kUvs[] = {1.0, 1.0, 0.0, 1.0, 0.0, 0.0, 1.0, 0.0};

static const GLfloat kUvs_flip[] = {0.0, 1.0, 1.0, 1.0, 1.0, 0.0, 0.0, 0.0};

static const GLushort kIndices[] = {0, 3, 1, 1, 3, 2};

namespace nv
{
    namespace render
    {
        NVCameraBackground::NVCameraBackground(NVRenderer *renderer):
            renderer_(renderer)
        {
            //bind data to gpu
            glGenBuffers(1, &vertex_id_);
            glBindBuffer(GL_ARRAY_BUFFER, vertex_id_);
            glBufferData(GL_ARRAY_BUFFER, sizeof(GLfloat)*8, kTriangleVertices, GL_STATIC_DRAW);

            glGenBuffers(1, &uv_id_);
            glBindBuffer(GL_ARRAY_BUFFER, uv_id_);
            glBufferData(GL_ARRAY_BUFFER, sizeof(GLfloat)*8, kUvs, GL_STATIC_DRAW);

            glGenBuffers(1, &uv_flip_id);
            glBindBuffer(GL_ARRAY_BUFFER, uv_flip_id);
            glBufferData(GL_ARRAY_BUFFER, sizeof(GLfloat)*8, kUvs_flip, GL_STATIC_DRAW);

            glBindBuffer(GL_ARRAY_BUFFER, 0);

            glGenBuffers(1, &indice_id_);
            glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, indice_id_);
            glBufferData(GL_ELEMENT_ARRAY_BUFFER, sizeof(GLushort)*6, kIndices, GL_STATIC_DRAW);
            glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, 0);

            //create program
            program_id_ = renderer->CreateProgram(kVertexShader, kFragmentShader);
            if (!program_id_) {
                LOG_ERROR("CameraBackground Could not create program.");
            }
            //Get variables from glsl
            position_handle_ = glGetAttribLocation(program_id_, "vPosition");

            uv_handle_ = glGetAttribLocation(program_id_, "vUv");
            texture_handle_ = glGetUniformLocation(program_id_, "uTexture");

        }

        NVCameraBackground::~NVCameraBackground() {

        }

        void NVCameraBackground::Render(bool flip) {
            glUseProgram(program_id_);

            glBindTexture(GL_TEXTURE_EXTERNAL_OES, renderer_->GetSurfaceTextureId());
            glActiveTexture(GL_TEXTURE0);
            glUniform1i(texture_handle_, 0);

            glVertexAttribPointer(position_handle_, 2, GL_FLOAT, GL_FALSE, 0, kTriangleVertices);
            glEnableVertexAttribArray(position_handle_);
            if(flip)
            {
                glVertexAttribPointer(uv_handle_, 2, GL_FLOAT, GL_FALSE, 0, kUvs_flip );
            }else{
                glVertexAttribPointer(uv_handle_, 2, GL_FLOAT, GL_FALSE, 0, kUvs );
            }

            glEnableVertexAttribArray(uv_handle_);
            glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, indice_id_);
            glDrawElements(GL_TRIANGLES, 6, GL_UNSIGNED_SHORT, 0);

            glBindTexture(GL_TEXTURE_EXTERNAL_OES, 0);
            glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, 0);
        }
    }
}