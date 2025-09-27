#include <jni.h>

extern jbyte blob[];
extern int blob_size;

JNIEXPORT jbyteArray JNICALL Java_com_termful_app_TermuxInstaller_getZip(JNIEnv *env, __attribute__((__unused__)) jobject This)
{
    // Check if blob_size is valid
    if (blob_size <= 0) {
        // Return null if no data - will be handled by Java code
        return NULL;
    }
    
    // Check if blob is valid
    if (blob == NULL) {
        // Return null if no blob data
        return NULL;
    }
    
    jbyteArray ret = (*env)->NewByteArray(env, blob_size);
    if (ret == NULL) {
        // NewByteArray failed (out of memory or invalid size)
        return NULL;
    }
    
    (*env)->SetByteArrayRegion(env, ret, 0, blob_size, blob);
    return ret;
}
