#include <jni.h>

extern jbyte bun_blob[];
extern int bun_blob_size;

JNIEXPORT jbyteArray JNICALL
Java_com_invapp_app_TermuxBunInstaller_getZip(JNIEnv *env,
        __attribute__((__unused__)) jobject This)
{
    if (bun_blob_size <= 0) {
        return (*env)->NewByteArray(env, 0);
    }
    jbyteArray ret = (*env)->NewByteArray(env, bun_blob_size);
    if (ret == NULL) {
        return NULL;
    }
    (*env)->SetByteArrayRegion(env, ret, 0, bun_blob_size, bun_blob);
    return ret;
}
