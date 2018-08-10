#include <android/native_window.h> // requires ndk r5 or newer
#include <android/native_window_jni.h> // requires ndk r5 or newer
#include <condition_variable>
#include "jniapi.h"
#include "logger.h"
#include "nvapp.h"
#include "nvrenderer.h"
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


}

