LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)
LOCAL_MODULE := libtermful-bootstrap
LOCAL_SRC_FILES := termful-bootstrap-zip.S termful-bootstrap.c
include $(BUILD_SHARED_LIBRARY)
