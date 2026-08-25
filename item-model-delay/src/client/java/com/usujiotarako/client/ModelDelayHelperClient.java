package com.usujiotarako.client;

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
				}
		);


		/*
		 * ================================================================
		 * DISCONNECT CLEANUP
		 * ================================================================
		 *
		 * Runtime state can contain:
		 *
		 *     owner entity IDs
		 *     stack identities
		 *     inventory-location tracking
		 *     pending delayed transitions
		 *     currently exposed delayed values
		 *
		 * None of those should survive leaving the current world/server.
		 *
		 * The loaded .mdprop configuration is NOT cleared here because
		 * that belongs to the active resource packs rather than the
		 * current play session.
		 */
		ClientPlayConnectionEvents.DISCONNECT.register(
				(handler, client) -> {

					/*
					 * Conditional properties.
					 */
					ModelDelayState.clear();


					/*
					 * Numeric range_dispatch properties.
					 */
					ModelDelayRangeState.clear();


					/*
					 * Select properties.
					 */
					ModelDelaySelectState.clear();
				}
		);
	}
}