#ifndef GLOBAL_INTERFACE_H_
#define GLOBAL_INTERFACE_H_
#include "jni.h"

extern "C"
{
    typedef union {
        JNIEnv  *env;
        void    *venv;
    }UnionJNIEnvToVoid;

    extern JavaVM *g_vm;
    extern JNIEnv *g_env;
    extern int     g_attached;
    extern UnionJNIEnvToVoid g_uenv;

    extern void android_app_update_tex_image();

#define ATTATCH_JNI_THREAD  g_attatched =  g_vm->AttachCurrentThread(&g_env, NULL);\
            if(g_attatched > 0)\
            {\
                g_vm->DetachCurrentThread();\
            }else{\
            }

#define DETATCH_JNI_THREAD if(g_attatched <=0)\
            {\
                g_vm->DetachCurrentThread();\
            }

}




#endif //GLOBAL_INTERFACE_H_