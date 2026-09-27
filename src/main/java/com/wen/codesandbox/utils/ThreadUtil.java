package com.wen.codesandbox.utils;

public class ThreadUtil {

    private static final ThreadLocal<String> containerIdThreadLocal = new ThreadLocal<>();

    public static void setContainerId(String containerId) {
        containerIdThreadLocal.set(containerId);
    }

    public static String getContainerId() {
        return containerIdThreadLocal.get();
    }

    public static void removeContainerId() {
        containerIdThreadLocal.remove();
    }

}
