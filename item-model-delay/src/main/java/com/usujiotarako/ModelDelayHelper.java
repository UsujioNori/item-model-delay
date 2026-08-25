package com.usujiotarako;

import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Shared constants and utilities for Usu's Item Model Delay.
 */
public final class ModelDelayHelper {

	public static final String MOD_ID = "usus_item_model_delay";

	public static final Logger LOGGER =
			LoggerFactory.getLogger(MOD_ID);


	private ModelDelayHelper() {
	}


	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(
				MOD_ID,
				path
		);
	}
}