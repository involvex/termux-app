LOCAL_PATH:= $(call my-dir)

include $(CLEAR_VARS)
LOCAL_LDLIBS := -llog
LOCAL_MODULE := local-socket
LOCAL_SRC_FILES := local-socket.cpp
include $(BUILD_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE := invapp-redirector
LOCAL_SRC_FILES := redirector.c
LOCAL_LDLIBS := -ldl -llog
include $(BUILD_SHARED_LIBRARY)
