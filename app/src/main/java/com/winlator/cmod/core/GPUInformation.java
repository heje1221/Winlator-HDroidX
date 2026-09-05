package com.winlator.cmod.core;

import android.content.Context;

public abstract class GPUInformation {

    public static boolean isAdrenoGPU(Context context) {
        try {
            String renderer = getRenderer(null, context);
            return renderer != null && renderer.toLowerCase().contains("adreno");
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean isDriverSupported(String driverName, Context context) {
        if (!isAdrenoGPU(context) && !driverName.equals("System"))
            return false;

        try {
            String renderer = getRenderer(driverName, context);
            return renderer != null && !renderer.toLowerCase().contains("unknown");
        } catch (Throwable t) {
            return false;
        }
    }
    public native static String getVulkanVersion(String driverName, Context context);
    public native static String getRenderer(String driverName, Context context);
    public native static String[] enumerateExtensions(String driverName, Context context);

    static {
        System.loadLibrary("winlator");
    }
}
