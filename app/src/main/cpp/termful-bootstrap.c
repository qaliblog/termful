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

JNIEXPORT jbyteArray JNICALL Java_com_termful_app_TermuxInstaller_getZipWithMemoryCheck(JNIEnv *env, __attribute__((__unused__)) jobject This)
{
    // Check if blob_size is valid
    if (blob_size <= 0) {
        // Return null if no data - will be handled by Java code
        return NULL;
    }
    
    // Check available memory before allocation
    // Try to allocate a smaller chunk first to test memory availability
    const size_t test_size = 1024 * 1024; // 1MB test
    jbyteArray test_ret = (*env)->NewByteArray(env, test_size);
    if (test_ret == NULL) {
        // Not enough memory even for 1MB
        return NULL;
    }
    (*env)->DeleteLocalRef(env, test_ret);
    
    // If we have enough memory for the full blob, proceed
    if (blob_size <= 100 * 1024 * 1024) { // Only if blob is <= 100MB
        jbyteArray ret = (*env)->NewByteArray(env, blob_size);
        if (ret == NULL) {
            // NewByteArray failed (out of memory or invalid size)
            return NULL;
        }
        
        (*env)->SetByteArrayRegion(env, ret, 0, blob_size, blob);
        return ret;
    } else {
        // For very large blobs (>100MB), return NULL to indicate memory issue
        return NULL;
    }
}
