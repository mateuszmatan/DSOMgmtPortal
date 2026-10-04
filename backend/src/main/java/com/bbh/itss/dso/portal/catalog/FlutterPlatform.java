package com.bbh.itss.dso.portal.catalog;

/**
 * What a Flutter build produces ({@code flutter.platform}).
 */
public enum FlutterPlatform {
    APK, APPBUNDLE, IOS, MACOS, LINUX, WINDOWS, WEB;

    public String configValue() {
        return name().toLowerCase();
    }
}
