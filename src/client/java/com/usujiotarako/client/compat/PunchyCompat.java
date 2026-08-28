package com.usujiotarako.client.compat;

import net.fabricmc.loader.api.FabricLoader;

public final class PunchyCompat {

    private static final boolean LOADED =
            FabricLoader.getInstance()
                    .isModLoaded(
                            "punchy"
                    );


    private PunchyCompat() {
    }


    public static boolean isLoaded() {

        return LOADED;
    }
}