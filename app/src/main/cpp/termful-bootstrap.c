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
    
    // blob is an array, so it can't be NULL, but check if blob_size indicates no data
    
    jbyteArray ret = (*env)->NewByteArray(env, blob_size);
    if (ret == NULL) {
        // NewByteArray failed (out of memory or invalid size)
        return NULL;
    }
    
    (*env)->SetByteArrayRegion(env, ret, 0, blob_size, blob);
    return ret;
}

JNIEXPORT jlong JNICALL Java_com_termful_app_TermuxInstaller_getZipSize(JNIEnv *env, __attribute__((__unused__)) jobject This)
{
    // Return the size of the embedded zip blob
    return (jlong) blob_size;
}
