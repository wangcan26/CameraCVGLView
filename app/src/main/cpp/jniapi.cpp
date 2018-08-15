#include <android/native_window.h> // requires ndk r5 or newer
#include <android/native_window_jni.h> // requires ndk r5 or newer
#include <android/bitmap.h>
#include <condition_variable>
#include <opencv2/highgui.hpp>
#include <opencv2/imgproc.hpp>
#include "jniapi.h"
#include "logger.h"
#include "nvapp.h"
#include "nvrenderer.h"
#include "nvtracker.h"
#include "global_interface.h"



#define LOG_TAG "JniApi"



extern "C"
{

//Global variables
UnionJNIEnvToVoid g_uenv;
JavaVM *g_vm = NULL;
JNIEnv *g_env = NULL;
int     g_attatched = 1;


static nv::NVApp *kApp = 0;
static ANativeWindow *kWindow = 0;

jobject     jni_surfacetexture = 0;
jmethodID   mid_update_tex;
static bool request_update_tex = false;
static std::mutex kMutex;



void android_app_update_tex_image()
{
    ATTATCH_JNI_THREAD
    if(g_vm->GetEnv(&g_uenv.venv, JNI_VERSION_1_4) != JNI_OK)
    {

        return;
    }

    g_env = g_uenv.env;

    std::lock_guard<std::mutex> lk(kMutex);
    if(jni_surfacetexture != 0 && request_update_tex)
    {
        LOG_INFO("nv log jni update surface texture");
        g_env->CallVoidMethod(jni_surfacetexture, mid_update_tex);
        request_update_tex = false;
    }

    DETATCH_JNI_THREAD
}

jint JNI_OnLoad(JavaVM* vm, void* reserved)
{
    g_uenv.venv = 0;
    jint result = -1;
    JNIEnv *env = 0;

    if(vm->GetEnv(&g_uenv.venv, JNI_VERSION_1_4)!=JNI_OK){
        goto fail;
    }

    LOG_INFO("nv log jni load successful");
    env = g_uenv.env;
    result = JNI_VERSION_1_4;
    g_vm = vm;
    g_env = env;

    fail:

    return result;
}

void JNI_OnUnload(JavaVM *vm, void *reserved)
{

}

JNIEXPORT void JNICALL NATIVE_METHOD(nativeCreateApp)(JNIEnv* jenv, jobject obj)
{
    kApp = new nv::NVApp();
    kApp->Init();
}


JNIEXPORT void JNICALL NATIVE_METHOD(nativeResumeApp)(JNIEnv* jenv, jobject obj)
{
    kApp->Resume();
}


JNIEXPORT void JNICALL NATIVE_METHOD(nativePauseApp)(JNIEnv* jenv, jobject obj)
{
    kApp->Pause();
}


JNIEXPORT void JNICALL NATIVE_METHOD(nativeDestroyApp)(JNIEnv* jenv, jobject obj)
{
    kApp->Deinit();
}

JNIEXPORT void JNICALL NATIVE_METHOD(nativeSetSurface)(JNIEnv* jenv, jobject obj, jobject surface)
{

    if(surface != 0){

        kWindow = ANativeWindow_fromSurface(jenv, surface);
        LOG_INFO("nv log native set surface window %p", kWindow);
        kApp->Render()->SetWindow(kWindow);

    }else{
        kApp->Render()->SetWindow(0);
        ANativeWindow_release(kWindow);
        LOG_INFO("nv log native release surface window ");
        kWindow = 0;
    }
}

JNIEXPORT jobject JNICALL NATIVE_METHOD(nativeSurfaceTexture)(JNIEnv* jenv, jobject obj, jboolean flip)
{
    jclass      clazz = jenv->FindClass("android/graphics/SurfaceTexture");
    jmethodID   mid_construct = jenv->GetMethodID(clazz, "<init>", "(I)V");
    mid_update_tex = jenv->GetMethodID(clazz, "updateTexImage", "()V");

    jobject obj_texture = jenv->NewObject(clazz, mid_construct, kApp->Render()->GetSurfaceTextureId());
    jni_surfacetexture = jenv->NewGlobalRef(obj_texture);

    //If flip the camera background
    kApp->Render()->FlipBackground(flip);
    return obj_texture;

}

JNIEXPORT void JNICALL NATIVE_METHOD(nativeDestroyTexture)(JNIEnv* jenv, jobject obj){
    jenv->DeleteGlobalRef(jni_surfacetexture);
    jni_surfacetexture = 0;
}

JNIEXPORT void JNICALL NATIVE_METHOD(nativeRequestUpdateTexture)(JNIEnv* jenv, jobject obj){
    std::lock_guard<std::mutex> lk(kMutex);
    request_update_tex = true;
}

JNIEXPORT void JNICALL NATIVE_METHOD(nativeProcessImage)(JNIEnv* jenv, jobject obj, jint width, jint height, jbyteArray data)
{
    int len = jenv->GetArrayLength(data);

    unsigned char *buf = new unsigned char[len];
    jenv->GetByteArrayRegion(data, 0, len, reinterpret_cast<jbyte *>(buf));

    //On Image Reader Thread
    kApp->tracker()->PushImage(width, height, buf, false);
}

//https://www.jianshu.com/p/08dcc910b088
JNIEXPORT void JNICALL NATIVE_METHOD(nativeTestIMage)(JNIEnv* jenv, jobject obj, jobject bitmap)
{
    cv::Mat image;
    if(!kApp->tracker()->PopImage(image))return;

    //Convert image to bitmap
    AndroidBitmapInfo info;
    void *pixels = 0;
    try{
        CV_Assert(AndroidBitmap_getInfo(jenv, bitmap, &info) >=0);
        LOG_INFO("nv log jni pixel %d %d ", image.rows, image.cols);
        CV_Assert(info.format == ANDROID_BITMAP_FORMAT_RGBA_8888 || info.format == ANDROID_BITMAP_FORMAT_RGB_565);
        CV_Assert(image.dims == 2 && info.height == (uint32_t)image.rows && info.width == (uint32_t)image.cols);
        CV_Assert(image.type() == CV_8UC1 || image.type() == CV_8UC3 || image.type() == CV_8UC4);
        CV_Assert(AndroidBitmap_lockPixels(jenv, bitmap, &pixels) >=0);
        CV_Assert(pixels);

        cv::Mat tmp(info.height, info.width, CV_8UC4, pixels);
        if(info.format == ANDROID_BITMAP_FORMAT_RGBA_8888){
            if(image.type() == CV_8UC1){
                cv::cvtColor(image, tmp, cv::COLOR_GRAY2RGBA);
            }else if(image.type() == CV_8UC3)
            {
                cv::cvtColor(image, tmp, cv::COLOR_RGB2RGBA);
            }else if(image.type() == CV_8UC4){
                image.copyTo(tmp);
            }
        }else{
            cv::Mat tmp(info.height, info.width, CV_8UC2, pixels);
            if(image.type() == CV_8UC1){
                cv::cvtColor(image, tmp, cv::COLOR_GRAY2BGR565);
            }else if(image.type() == CV_8UC3){
                cv::cvtColor(image, tmp, cv::COLOR_RGB2BGR565);
            }else if(image.type() == CV_8UC4){
                cv::cvtColor(image, tmp, cv::COLOR_RGBA2BGR565);
            }
        }
        AndroidBitmap_unlockPixels(jenv, bitmap);
        return;
    }catch (const cv::Exception &e){
        //LOG_INFO("nv log jni nativeTestImage except\n");
        AndroidBitmap_unlockPixels(jenv, bitmap);
        return;
    }catch (...){
        //LOG_INFO("nv log jni nativeTestImage except\n");
        AndroidBitmap_unlockPixels(jenv, bitmap);
        return;
    }

}


}

