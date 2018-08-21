#include <stdlib.h>
#include <thread>
#include <chrono>
#include <android/native_window.h>

#include "nvapp.h"
#include "nvrenderer.h"
#include "nvtracker.h"
#include "logger.h"
#include "global_interface.h"
#include "nv_cam_background.h"

#define LOG_TAG "NVRenderer"

namespace nv
{
    namespace render
    {
        NVRenderer::NVRenderer(NVApp *app):
                app_(app),
                msg_(MSG_NONE),
                window_(0),
                display_(0),
                surface_(0),
                context_(0),
                config_(0),
                cam_background_(0),
                width_(0),
                height_(0),
                surface_texture_id_(0),
                flip_background_(false),
                window_init_(false),
                pause_(false),
                run_(true)
        {


        }

        NVRenderer::~NVRenderer() {

        }

        void NVRenderer::Resume() {
            pause_ = false;
        }


        void NVRenderer::Pause() {
            pause_ = true;
        }

        void NVRenderer::Destroy() {

            std::unique_lock<std::mutex> gl_lk(gl_mut_);
            msg_ = MSG_LOOP_EXIT;
            gl_cond_.notify_one();
            gl_lk.unlock();
        }

        void NVRenderer::SetWindow(ANativeWindow *window) {
            std::lock_guard<std::mutex> msg_lk(msg_mut_);
            if(window)
            {
                window_ = window;
                if(window_init_)
                {
                    msg_ = MSG_WINDOW_UPDATE;
                    LOG_INFO("nv log renderer SetWindow Update");
                    return;
                }

                LOG_INFO("nv log renderer SetWindow Create");
                msg_ = MSG_WINDOW_CREATE;

                //Block the UI thread
                std::unique_lock<std::mutex> win_lk(win_mut_);
                win_cond_.wait(win_lk);
                win_lk.unlock();
                LOG_INFO("nv log renderer unblock ui thread");
            }else{
                msg_ = MSG_WINDOW_DESTROY;
                LOG_INFO("nv log renderer set window null");
            }

        }

        //Run on the gl thread
        void NVRenderer::_Run() {
            //LOG_INFO("nv log renderer run loop");
            _renderLoop();
        }

        //Create a gl context and surface
        bool NVRenderer::Initialise() {
            LOG_INFO("nv log renderer initialise");
            const EGLint attribs[] = {
                    EGL_SURFACE_TYPE, EGL_WINDOW_BIT,
                    EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT,
                    EGL_BLUE_SIZE, 8,
                    EGL_GREEN_SIZE, 8,
                    EGL_RED_SIZE, 8,
                    EGL_ALPHA_SIZE, 8,
                    EGL_NONE
            };
            //opengl es2.0 context
            EGLint contextAtt[] = { EGL_CONTEXT_CLIENT_VERSION, 2, EGL_NONE, EGL_NONE };
            EGLDisplay display;
            EGLConfig config;
            EGLint numConfigs;
            EGLint format;
            EGLSurface surface;
            EGLContext context;
            GLfloat ratio;

            if ((display = eglGetDisplay(EGL_DEFAULT_DISPLAY)) == EGL_NO_DISPLAY) {
                LOG_ERROR("eglGetDisplay() returned error %d", eglGetError());
                return false;
            }
            if (!eglInitialize(display, 0, 0)) {
                LOG_ERROR("eglInitialize() returned error %d", eglGetError());
                return false;
            }

            if (!eglChooseConfig(display, attribs, &config, 1, &numConfigs)) {
                LOG_ERROR("eglChooseConfig() returned error %d", eglGetError());
                ShutDown();
                return false;
            }

            if (!eglGetConfigAttrib(display, config, EGL_NATIVE_VISUAL_ID, &format)) {
                LOG_ERROR("eglGetConfigAttrib() returned error %d", eglGetError());
                ShutDown();
                return false;
            }

            ANativeWindow_setBuffersGeometry(window_, 0, 0, format);

            if (!(surface = eglCreateWindowSurface(display, config, window_, 0))) {
                LOG_ERROR("eglCreateWindowSurface() returned error %d", eglGetError());
                ShutDown();
                return false;
            }

            if (!(context = eglCreateContext(display, config, 0, contextAtt))) {
                LOG_ERROR("eglCreateContext() returned error %d", eglGetError());
                ShutDown();
                return false;
            }

            if (!eglMakeCurrent(display, surface, surface, context)) {
                LOG_ERROR("eglMakeCurrent() returned error %d", eglGetError());
                ShutDown();
                return false;
            }

            if (!eglQuerySurface(display, surface, EGL_WIDTH, &width_) ||
                !eglQuerySurface(display, surface, EGL_HEIGHT, &height_)) {
                LOG_ERROR("eglQuerySurface() returned error %d", eglGetError());
                ShutDown();
                return false;
            }

            display_ = display;
            surface_ = surface;
            context_ = context;
            config_ = config;

            window_init_ = true;


            CheckGlError("glBindFramebuffer");
            glClearColor(0.0, 1.0, 0.0, 1.0);
            CheckGlError("glClearColor");
            glClear(GL_DEPTH_BUFFER_BIT | GL_COLOR_BUFFER_BIT);
            CheckGlError("glClear");
            glViewport(0, 0, width_, height_);
            CheckGlError("glViewport");

            //surfaceTexture Id Used for rendering camera preview
            CreateSurfaceTextureId();
            //Notify ui thread to go
            std::unique_lock<std::mutex> win_lk(win_mut_);
            win_cond_.notify_one();
            win_lk.unlock();

            //Wait for camera session ready
            LOG_INFO("nv log renderer initialise wait for camera ready");
            /*std::unique_lock<std::mutex> gl_lk(gl_mut_);
            gl_cond_.wait(gl_lk);
            gl_lk.unlock();*/
            LOG_INFO("nv log renderer initialise wait for camera ready end");

            //Create camera background
            cam_background_ = new NVCameraBackground(this);

            std::unique_lock<std::mutex> msg_lk(msg_mut_);
            if(msg_ == MSG_WINDOW_CREATE)
            {
                msg_ = MSG_NONE;
            }
            msg_lk.unlock();


            return true;
        }

        void NVRenderer::CreateSurfaceTextureId() {
            glGenTextures(1, &surface_texture_id_);
            if(surface_texture_id_ >0)
            {
                glBindTexture(GL_TEXTURE_EXTERNAL_OES, surface_texture_id_);
                CheckGlError("surfaceTexture glBindTexture");
                //Linear filter type without mipmap
                glTexParameterf(GL_TEXTURE_EXTERNAL_OES, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
                glTexParameterf(GL_TEXTURE_EXTERNAL_OES, GL_TEXTURE_MAG_FILTER, GL_LINEAR);

                //Wrap clmap to edge
                glTexParameteri(GL_TEXTURE_EXTERNAL_OES, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
                glTexParameteri(GL_TEXTURE_EXTERNAL_OES, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
            }
        }

        void NVRenderer::FlipBackground(bool flip) {
            flip_background_ = flip;
        }

        void NVRenderer::NotifyCameraReady() {
            /*std::lock_guard<std::mutex> gl_lk(gl_mut_);
            gl_cond_.notify_one();*/
        }

        bool NVRenderer::WindowRestore() {
            EGLContext  context;
            EGLSurface  surface;


            if (!(surface = eglCreateWindowSurface(display_, config_, window_, 0))) {
                LOG_ERROR("eglCreateWindowSurface() returned error %d", eglGetError());
                ShutDown();
                return false;
            }

            if (!eglMakeCurrent(display_, surface, surface, context)) {
                LOG_ERROR("eglMakeCurrent() returned error %d", eglGetError());
                ShutDown();
                return false;
            }

            surface_ = surface;
            context_ = context;

            std::lock_guard<std::mutex> msg_lk(msg_mut_);
            msg_ = MSG_NONE;

        }

        bool NVRenderer::WindowChanged() {
            LOG_INFO("Renderer update Window");

            if (!eglMakeCurrent(display_, surface_, surface_, context_)) {
                LOG_ERROR("eglMakeCurrent() returned error %d", eglGetError());
                ShutDown();
                return false;
            }

            if (!eglQuerySurface(display_, surface_, EGL_WIDTH, &width_) ||
                !eglQuerySurface(display_, surface_, EGL_HEIGHT, &height_)) {
                LOG_ERROR("eglQuerySurface() returned error %d", eglGetError());
                ShutDown();
                return false;
            }

            msg_ = MSG_NONE;
            return true;
        }



        void NVRenderer::ShutDown() {
            if(!window_init_)return;
            LOG_INFO("nv log renderer Shutdown begin");

            if(cam_background_ != 0)
            {
                delete cam_background_;
                cam_background_ = 0;
            }

            LOG_INFO("Destroying context");

            eglMakeCurrent(display_, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
            eglDestroyContext(display_, context_);
            eglDestroySurface(display_, surface_);
            eglTerminate(display_);

            display_ = EGL_NO_DISPLAY;
            surface_ = EGL_NO_SURFACE;
            context_ = EGL_NO_CONTEXT;
            config_ = 0;
            window_ = 0;
            window_init_ = false;


            /*std::unique_lock<std::mutex> gl_lk(gl_mut_);
            gl_cond_.notify_all();
            gl_lk.unlock();*/

            std::unique_lock<std::mutex> msg_lk(msg_mut_);
            if(msg_ == MSG_WINDOW_DESTROY)
                msg_ = MSG_NONE;
            msg_lk.unlock();

            LOG_INFO("nv log renderer Shutdown end");

            return;
        }

        void NVRenderer::DrawFrame() {
            //LOG_INFO("nv log renderer drawframe");
            glClearColor(0.0, 1.0, 0.0, 1.0);
            CheckGlError("glClearColor");
            glClear(GL_DEPTH_BUFFER_BIT | GL_COLOR_BUFFER_BIT);
            glViewport(0, 0, width_, height_);

            RenderBackground();
        }

        void NVRenderer::RenderBackground() {
            android_app_update_tex_image();
            if(cam_background_ != 0)
                cam_background_->Render(flip_background_);
        }

        void NVRenderer::SwapBuffers() {
            if (!eglSwapBuffers(display_, surface_)) {
                LOG_ERROR("eglSwapBuffers() returned error %d", eglGetError());
            }
        }

        void NVRenderer::CheckGlError(const char *op) {
            for (GLint error = glGetError(); error; error
                                                            = glGetError()) {
                LOG_INFO("nv log renderer after %s() glError (0x%x)\n", op, error);
            }
        }

        GLuint NVRenderer::LoaderShader(GLenum shader_type, const char *source) {
            GLuint shader = glCreateShader(shader_type);
            if (shader) {
                glShaderSource(shader, 1, &source, NULL);
                glCompileShader(shader);
                GLint compiled = 0;
                glGetShaderiv(shader, GL_COMPILE_STATUS, &compiled);
                if (!compiled) {
                    GLint infoLen = 0;
                    glGetShaderiv(shader, GL_INFO_LOG_LENGTH, &infoLen);
                    if (infoLen) {
                        char* buf = (char*) malloc(infoLen);
                        if (buf) {
                            glGetShaderInfoLog(shader, infoLen, NULL, buf);
                            LOG_ERROR("Could not compile shader %d:\n%s\n",
                                      shader_type, buf);
                            free(buf);
                        }
                        glDeleteShader(shader);
                        shader = 0;
                    }
                }
            }
            return shader;
        }

        GLuint NVRenderer::CreateProgram(const char *vertex_source, const char *fragment_source) {
            GLuint vertex_shader = LoaderShader(GL_VERTEX_SHADER, vertex_source);
            if (!vertex_shader) {
                return 0;
            }

            GLuint pixel_shader = LoaderShader(GL_FRAGMENT_SHADER, fragment_source);
            if (!pixel_shader) {
                return 0;
            }

            GLuint program = glCreateProgram();
            if (program) {
                glAttachShader(program, vertex_shader);
                CheckGlError("glAttachShader");
                glAttachShader(program, pixel_shader);
                CheckGlError("glAttachShader");
                glLinkProgram(program);
                GLint linkStatus = GL_FALSE;
                glGetProgramiv(program, GL_LINK_STATUS, &linkStatus);
                if (linkStatus != GL_TRUE) {
                    GLint bufLength = 0;
                    glGetProgramiv(program, GL_INFO_LOG_LENGTH, &bufLength);
                    if (bufLength) {
                        char* buf = (char*) malloc(bufLength);
                        if (buf) {
                            glGetProgramInfoLog(program, bufLength, NULL, buf);
                            LOG_ERROR("Could not link program:\n%s\n", buf);
                            free(buf);
                        }
                    }
                    glDeleteProgram(program);
                    program = 0;
                }
            }
            return program;
        }


        void NVRenderer::_renderLoop() {
            //A little later than camerasession thread
            while(run_)
            {
                switch (msg_)
                {
                    case MSG_WINDOW_CREATE:
                        if(!window_init_)
                        {
                            if(!Initialise())
                            {
                                run_ = false;
                            }
                        }else{
                            if(WindowRestore())
                            {
                                run_ = false;
                            }
                        }
                        break;
                    case MSG_WINDOW_UPDATE:
                        WindowChanged();
                        break;
                    case MSG_WINDOW_DESTROY:
                        ShutDown();
                        break;
                    case MSG_LOOP_EXIT:
                        LOG_INFO("nv log renderer msg exit");
                        if(window_init_)
                        {
                            ShutDown();
                        }
                        run_ = false;
                        break;
                    default:
                        break;
                }


                if(window_init_ && !pause_)
                {

                    DrawFrame();
                    SwapBuffers();
                }
            }
            LOG_INFO("nv log renderer thread exit");
        }

    }//render
}
