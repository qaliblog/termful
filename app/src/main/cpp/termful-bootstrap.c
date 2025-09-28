#include <jni.h>
#include <string.h>

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

JNIEXPORT jlong JNICALL Java_com_termful_app_TermuxInstaller_getZipSize(__attribute__((__unused__)) JNIEnv *env, __attribute__((__unused__)) jobject This)
{
    // Return the size of the embedded zip blob
    return (jlong) blob_size;
}

JNIEXPORT jint JNICALL Java_com_termful_app_TermuxInstaller_getZipChunk(JNIEnv *env, __attribute__((__unused__)) jobject This, jlong offset, jbyteArray buffer, jint maxBytes)
{
    // Validate parameters
    if (offset < 0 || offset >= blob_size) {
        return 0; // No more data
    }
    
    if (buffer == NULL || maxBytes <= 0) {
        return 0; // Invalid parameters
    }
    
    // Calculate how many bytes we can actually read
    jlong remainingBytes = blob_size - offset;
    jint bytesToRead = (jint) ((remainingBytes < maxBytes) ? remainingBytes : maxBytes);
    
    if (bytesToRead <= 0) {
        return 0; // No more data
    }
    
    // Get the buffer array and copy data
    jbyte* bufferPtr = (*env)->GetByteArrayElements(env, buffer, NULL);
    if (bufferPtr == NULL) {
        return 0; // Failed to get buffer
    }
    
    // Copy the chunk of data from the blob
    memcpy(bufferPtr, blob + offset, bytesToRead);
    
    // Release the buffer
    (*env)->ReleaseByteArrayElements(env, buffer, bufferPtr, 0);
    
    return bytesToRead;
}
