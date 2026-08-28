package com.usujiotarako.client;

import com.usujiotarako.client.compat.PunchyRenderTransformBridge;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

public class ModelDelayHelperClient
        implements ClientModInitializer {

    @Override
    public void onInitializeClient() {

        /*
         * ================================================================
         * RESOURCE CONFIGURATION
         * ================================================================
         */
        ModelDelayResourceLoader.initialize();


        /*
         * ================================================================
         * CLIENT TICK
         * ================================================================
         */
        ClientTickEvents.END_CLIENT_TICK.register(
                client -> {

                    /*
                     * Conditional properties.
                     */
                    ModelDelayState.tick();


                    /*
                     * Numeric range_dispatch properties.
                     */
                    ModelDelayRangeState.tick();


                    /*
                     * Select properties.
                     */
                    ModelDelaySelectState.tick();


                    /*
                     * Transition effects.
                     */
                    ModelTransitionEffects.tick();
                }
        );


        /*
         * ================================================================
         * CONNECTION LIFECYCLE CLEANUP
         * ================================================================
         *
         * DISCONNECT is the normal cleanup boundary when leaving a world or
         * server. JOIN is intentionally cleared as well so every connection
         * starts from a guaranteed fresh runtime state even if a previous
         * connection ended unusually. Resource-pack configuration itself is
         * kept; only per-world/per-stack runtime state is discarded.
         */
        ClientPlayConnectionEvents.JOIN.register(
                (handler, sender, client) ->
                        clearRuntimeState()
        );


        ClientPlayConnectionEvents.DISCONNECT.register(
                (handler, client) ->
                        clearRuntimeState()
        );
    }


    /*
     * ================================================================
     * RUNTIME STATE CLEANUP
     * ================================================================
     *
     * Kept in one place so resource reloads and connection lifecycle events
     * cannot accidentally clear different subsets of IMD runtime state.
     */
    static void clearRuntimeState() {

        ModelDelayState.clear();

        ModelDelayRangeState.clear();

        ModelDelaySelectState.clear();

        ModelTransitionEffects.clear();

        PunchyRenderTransformBridge.clear();
    }
}
