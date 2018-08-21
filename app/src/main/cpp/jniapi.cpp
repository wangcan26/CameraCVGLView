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

std::string
jstring2str(JNIEnv* env, jstring jstr)
{
    char*   rtn   =   NULL;
    jclass   clsstring   =   env->FindClass("java/lang/String");
    jstring   strencode   =   env->NewStringUTF("GB2312");
    jmethodID   mid   =   env->GetMethodID(clsstring,   "getBytes",   "(Ljava/lang/String;)[B");
    jbyteArray   barr=   (jbyteArray)env->CallObjectMethod(jstr,mid,strencode);
    jsize   alen   =   env->GetArrayLength(barr);
    jbyte*   ba   =   env->GetByteArrayElements(barr,JNI_FALSE);
    if(alen   >   0)
    {
        rtn   =   (char*)malloc(alen+1);
        memcpy(rtn,ba,alen);
        rtn[alen]=0;
    }
    env->ReleaseByteArrayElements(barr,ba,0);
    std::string stemp(rtn);
    free(rtn);

    return   stemp;
}

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

JNIEXPORT void JNICALL NATIVE_METHOD(nativeCreateApp)(JNIEnv* jenv, jobject obj, jstring path)
{
    kApp = new nv::NVApp();
    kApp->Init(jstring2str(jenv, path));
}


JNIEXPORT void JNICALL NATIVE_METHOD(nativeResumeApp)(JNIEnv* jenv, jobject obj)
{
    if(kApp != 0)
        kApp->Resume();
}


JNIEXPORT void JNICALL NATIVE_METHOD(nativePauseApp)(JNIEnv* jenv, jobject obj)
{
    if(kApp != 0)
        kApp->Pause();
}


JNIEXPORT void JNICALL NATIVE_METHOD(nativeDestroyApp)(JNIEnv* jenv, jobject obj)
{
    if(kApp != 0)
    {
        kApp->Deinit();
        delete kApp;
        kApp = 0;
    }

}


JNIEXPORT void JNICALL NATIVE_METHOD(nativeNotifyCameraReady)(JNIEnv* jenv, jobject obj)
{
    if(kApp != 0)
    {
        if(kApp->Render() != 0)
        kApp->Render()->NotifyCameraReady();

        if(kApp->tracker() != 0)
            kApp->tracker()->NotifyCameraReady();
    }
}


JNIEXPORT void JNICALL NATIVE_METHOD(nativeNotifyCameraWait)(JNIEnv* jenv, jobject obj)
{

    if(kApp != 0 && kApp->tracker() != 0)
        kApp->tracker()->NotifyCameraWait();
}

JNIEXPORT void JNICALL NATIVE_METHOD(nativeSetSurface)(JNIEnv* jenv, jobject obj, jobject surface)
{

    if(surface != 0){
        kWindow = ANativeWindow_fromSurface(jenv, surface);
        LOG_INFO("nv log native set surface window %p", kWindow);

        if(kApp != 0)
        {
            if(kApp->Render() != 0)
                kApp->Render()->SetWindow(kWindow);
        }


    }else{

        if(kApp != 0)
        {
            if(kApp->Render() != 0)
                kApp->Render()->SetWindow(0);
        }

        if(kWindow != 0)
        {
            ANativeWindow_release(kWindow);
            kWindow = 0;
        }
        LOG_INFO("nv log native release surface window end");
    }
}

JNIEXPORT jobject JNICALL NATIVE_METHOD(nativeSurfaceTexture)(JNIEnv* jenv, jobject obj, jboolean flip)
{
    jclass      clazz = jenv->FindClass("android/graphics/SurfaceTexture");
    jmethodID   mid_construct = jenv->GetMethodID(clazz, "<init>", "(I)V");
    mid_update_tex = jenv->GetMethodID(clazz, "updateTexImage", "()V");

    if(jni_surfacetexture == 0)
    {
        jobject obj_texture = jenv->NewObject(clazz, mid_construct, kApp->Render()->GetSurfaceTextureId());
        jni_surfacetexture = jenv->NewGlobalRef(obj_texture);
        //If flip the camera background
        kApp->Render()->FlipBackground(flip);
    }

    return jni_surfacetexture;
}



JNIEXPORT void JNICALL NATIVE_METHOD(nativeDestroyTexture)(JNIEnv* jenv, jobject obj){
    std::lock_guard<std::mutex> lk(kMutex);
    jenv->DeleteGlobalRef(jni_surfacetexture);
    jni_surfacetexture = 0;
}

JNIEXPORT void JNICALL NATIVE_METHOD(nativeRequestUpdateTexture)(JNIEnv* jenv, jobject obj){
    std::lock_guard<std::mutex> lk(kMutex);
    request_update_tex = true;
}

JNIEXPORT void JNICALL NATIVE_METHOD(nativeProcessImage)(JNIEnv* jenv, jobject obj, jint width, jint height, jbyteArray data)
{

    if(data == 0)
    {
        return;
    }
    int len = jenv->GetArrayLength(data);

    unsigned char *buf = new unsigned char[len];
    jenv->GetByteArrayRegion(data, 0, len, reinterpret_cast<jbyte *>(buf));

    //On Image Reader Thread
    if(kApp != 0 && kApp->tracker() != 0)
        kApp->tracker()->PushImage(width, height, buf);
    else{
        delete buf;
        LOG_INFO("nv log jni push image not delete buf");
    }


}

//https://www.jianshu.com/p/08dcc910b088
JNIEXPORT void JNICALL NATIVE_METHOD(nativeTestIMage)(JNIEnv* jenv, jobject obj, jobject bitmap)
{

    nv::tracker::NVTracker::Image image;
    if(kApp != 0  && kApp->tracker() != 0&& !kApp->tracker()->PopImage(image)){
        if(image.buf_ != 0)
            delete image.buf_;
        return;
    }
    cv::Mat mat(image.height_, image.width_, CV_8UC1, image.buf_) ;

    //Convert image to bitmap
    AndroidBitmapInfo info;
    void *pixels = 0;
    try{
        CV_Assert(AndroidBitmap_getInfo(jenv, bitmap, &info) >=0);
        LOG_INFO("nv log jni pixel %d %d ", mat.rows, mat.cols);
        CV_Assert(info.format == ANDROID_BITMAP_FORMAT_RGBA_8888 || info.format == ANDROID_BITMAP_FORMAT_RGB_565);
        CV_Assert(mat.dims == 2 && info.height == (uint32_t)mat.rows && info.width == (uint32_t)mat.cols);
        CV_Assert(mat.type() == CV_8UC1 || mat.type() == CV_8UC3 || mat.type() == CV_8UC4);
        CV_Assert(AndroidBitmap_lockPixels(jenv, bitmap, &pixels) >=0);
        CV_Assert(pixels);

        cv::Mat tmp(info.height, info.width, CV_8UC4, pixels);
        if(info.format == ANDROID_BITMAP_FORMAT_RGBA_8888){
            if(mat.type() == CV_8UC1){
                cv::cvtColor(mat, tmp, cv::COLOR_GRAY2RGBA);
            }else if(mat.type() == CV_8UC3)
            {
                cv::cvtColor(mat, tmp, cv::COLOR_RGB2RGBA);
            }else if(mat.type() == CV_8UC4){
                mat.copyTo(tmp);
            }
        }else{
            cv::Mat tmp(info.height, info.width, CV_8UC2, pixels);
            if(mat.type() == CV_8UC1){
                cv::cvtColor(mat, tmp, cv::COLOR_GRAY2BGR565);
            }else if(mat.type() == CV_8UC3){
                cv::cvtColor(mat, tmp, cv::COLOR_RGB2BGR565);
            }else if(mat.type() == CV_8UC4){
                cv::cvtColor(mat, tmp, cv::COLOR_RGBA2BGR565);
            }
        }
        AndroidBitmap_unlockPixels(jenv, bitmap);
        delete image.buf_;
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

