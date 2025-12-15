package com.linuxdo.explorer.util;

import com.intellij.DynamicBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

/**
 * Internationalization resource bundle
 */
public final class LinuxDoBundle extends DynamicBundle {

    @NonNls
    private static final String BUNDLE = "messages.LinuxDoBundle";

    private static final LinuxDoBundle INSTANCE = new LinuxDoBundle();

    private LinuxDoBundle() {
        super(BUNDLE);
    }

    @NotNull
    public static String message(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key, Object... params) {
        return INSTANCE.getMessage(key, params);
    }
}
