package com.usujiotarako.client;

import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public final class ModelDelayResourceLoader {

    /*
     * ================================================================
     * RESOURCE LOCATION
     * ================================================================
     *
     * .mdprop files are stored under:
     *
     *     assets/<namespace>/items/imd/
     *
     * Example:
     *
     *     assets/minecraft/items/imd/diamond_sword.mdprop
     *
     * maps to:
     *
     *     minecraft:diamond_sword
     */
    private static final String RESOURCE_DIRECTORY =
            "items/imd";


    private static final String RESOURCE_PATH_PREFIX =
            RESOURCE_DIRECTORY
                    + "/";


    private static final String EXTENSION =
            ".mdprop";


    private static final String RANGE_PREFIX =
            "range.";


    private static final String SELECT_PREFIX =
            "select.";


    private record LoadedItemConfig(
            ModelDelayConfig.Behavior behavior,
            Map<String, ModelDelayConfig.DelayConfig> properties,
            Map<String, ModelDelayConfig.RangeDelayConfig> rangeProperties,
            Map<String, ModelDelayConfig.SelectDelayConfig> selectProperties
    ) {
    }


    private ModelDelayResourceLoader() {
    }


    /*
     * ================================================================
     * INITIALIZE
     * ================================================================
     */
    public static void initialize() {

        ResourceLoader.get(
                        PackType.CLIENT_RESOURCES
                )
                .registerReloadListener(

                        Identifier.fromNamespaceAndPath(
                                "usus_item_model_delay",
                                "mdprop_loader"
                        ),

                        new SimplePreparableReloadListener<
                                Map<Identifier, LoadedItemConfig>
                                >() {

                            @Override
                            protected Map<Identifier, LoadedItemConfig> prepare(
                                    ResourceManager manager,
                                    ProfilerFiller profiler
                            ) {

                                return load(
                                        manager
                                );
                            }


                            @Override
                            protected void apply(
                                    Map<Identifier, LoadedItemConfig> configs,
                                    ResourceManager manager,
                                    ProfilerFiller profiler
                            ) {

                                ModelDelayConfig.clear();

                                /*
                                 * Conditional state.
                                 */
                                ModelDelayState.clear();

                                /*
                                 * Numeric range_dispatch state.
                                 */
                                ModelDelayRangeState.clear();

                                /*
                                 * Select state.
                                 */
                                ModelDelaySelectState.clear();

                                for (
                                        Map.Entry<
                                                Identifier,
                                                LoadedItemConfig
                                                > entry
                                        : configs.entrySet()
                                ) {

                                    LoadedItemConfig config =
                                            entry.getValue();

                                    ModelDelayConfig.registerItem(
                                            entry.getKey(),
                                            config.behavior(),
                                            config.properties(),
                                            config.rangeProperties(),
                                            config.selectProperties()
                                    );
                                }
                            }
                        }
                );
    }


    /*
     * ================================================================
     * LOAD ALL .MDPROP FILES
     * ================================================================
     */
    private static Map<
            Identifier,
            LoadedItemConfig
            > load(
            ResourceManager manager
    ) {

        Map<
                Identifier,
                LoadedItemConfig
                > result =
                new HashMap<>();

        Map<Identifier, Resource> resources =
                manager.listResources(
                        RESOURCE_DIRECTORY,
                        id ->
                                id.getPath()
                                        .endsWith(
                                                EXTENSION
                                        )
                );

        for (
                Map.Entry<Identifier, Resource> entry
                : resources.entrySet()
        ) {

            Identifier configId =
                    entry.getKey();

            String path =
                    configId.getPath();

            if (
                    !path.startsWith(
                            RESOURCE_PATH_PREFIX
                    )
            ) {

                continue;
            }

            String itemPathWithExtension =
                    path.substring(
                            RESOURCE_PATH_PREFIX.length()
                    );

            String itemPath =
                    itemPathWithExtension.substring(
                            0,
                            itemPathWithExtension.length()
                                    - EXTENSION.length()
                    );

            Identifier itemId =
                    Identifier.fromNamespaceAndPath(
                            configId.getNamespace(),
                            itemPath
                    );

            LoadedItemConfig config =
                    loadFile(
                            entry.getValue()
                    );

            if (
                    !config.properties().isEmpty()

                            || !config.rangeProperties().isEmpty()

                            || !config.selectProperties().isEmpty()
            ) {

                result.put(
                        itemId,
                        config
                );
            }
        }

        return result;
    }


    /*
     * ================================================================
     * LOAD ONE .MDPROP FILE
     * ================================================================
     */
    private static LoadedItemConfig loadFile(
            Resource resource
    ) {

        Map<
                String,
                ModelDelayConfig.DelayConfig
                > configs =
                new HashMap<>();

        Map<
                String,
                ModelDelayConfig.RangeDelayConfig
                > rangeConfigs =
                new HashMap<>();

        Map<
                String,
                ModelDelayConfig.SelectDelayConfig
                > selectConfigs =
                new HashMap<>();

        Properties properties =
                new Properties();

        ModelDelayConfig.Behavior itemBehavior =
                ModelDelayConfig.Behavior.NORMAL;

        try (
                InputStream stream =
                        resource.open();

                InputStreamReader reader =
                        new InputStreamReader(
                                stream,
                                StandardCharsets.UTF_8
                        )
        ) {

            properties.load(
                    reader
            );

            String itemBehaviorString =
                    properties.getProperty(
                                    "behavior",
                                    "normal"
                            )
                            .trim()
                            .toLowerCase();

            switch (itemBehaviorString) {

                case "normal" ->
                        itemBehavior =
                                ModelDelayConfig.Behavior.NORMAL;

                case "evolving" ->
                        itemBehavior =
                                ModelDelayConfig.Behavior.EVOLVING;

                default -> {

                    System.err.println(
                            "[Usu's Item Model Delay] Unknown behavior '"
                                    + itemBehaviorString
                                    + "'. Using normal."
                    );

                    itemBehavior =
                            ModelDelayConfig.Behavior.NORMAL;
                }
            }

            for (
                    String key
                    : properties.stringPropertyNames()
            ) {

                if (!key.endsWith(".delay")) {

                    continue;
                }

                String propertyName =
                        key.substring(
                                0,
                                key.length()
                                        - ".delay".length()
                        );

                int delay;

                try {

                    delay =
                            Integer.parseInt(
                                    properties
                                            .getProperty(key)
                                            .trim()
                            );

                } catch (NumberFormatException e) {

                    System.err.println(
                            "[Usu's Item Model Delay] Invalid delay for "
                                    + propertyName
                    );

                    continue;
                }

                if (
                        propertyName.startsWith(
                                SELECT_PREFIX
                        )
                ) {

                    selectConfigs.put(
                            propertyName,
                            new ModelDelayConfig.SelectDelayConfig(
                                    delay
                            )
                    );

                    continue;
                }

                if (
                        propertyName.startsWith(
                                RANGE_PREFIX
                        )
                ) {

                    String rangeBehaviorString =
                            properties.getProperty(
                                            propertyName + ".behavior",
                                            "threshold"
                                    )
                                    .trim()
                                    .toLowerCase();

                    ModelDelayConfig.RangeBehavior rangeBehavior;

                    switch (rangeBehaviorString) {

                        case "threshold" ->
                                rangeBehavior =
                                        ModelDelayConfig.RangeBehavior.THRESHOLD;

                        case "value" ->
                                rangeBehavior =
                                        ModelDelayConfig.RangeBehavior.VALUE;

                        default -> {

                            System.err.println(
                                    "[Usu's Item Model Delay] Unknown range behavior '"
                                            + rangeBehaviorString
                                            + "' for "
                                            + propertyName
                                            + ". Using threshold."
                            );

                            rangeBehavior =
                                    ModelDelayConfig.RangeBehavior.THRESHOLD;
                        }
                    }

                    rangeConfigs.put(
                            propertyName,
                            new ModelDelayConfig.RangeDelayConfig(
                                    delay,
                                    rangeBehavior
                            )
                    );

                    continue;
                }

                String modeString =
                        properties.getProperty(
                                        propertyName + ".mode",
                                        "release"
                                )
                                .trim()
                                .toLowerCase();

                ModelDelayConfig.Mode mode;

                switch (modeString) {

                    case "hold" ->
                            mode =
                                    ModelDelayConfig.Mode.HOLD;

                    case "release" ->
                            mode =
                                    ModelDelayConfig.Mode.RELEASE;

                    case "both" ->
                            mode =
                                    ModelDelayConfig.Mode.BOTH;

                    default -> {

                        System.err.println(
                                "[Usu's Item Model Delay] Unknown mode '"
                                        + modeString
                                        + "' for "
                                        + propertyName
                        );

                        mode =
                                ModelDelayConfig.Mode.RELEASE;
                    }
                }

                String propertyBehaviorString =
                        properties.getProperty(
                                        propertyName + ".behavior",
                                        "normal"
                                )
                                .trim()
                                .toLowerCase();

                ModelDelayConfig.PropertyBehavior propertyBehavior;

                switch (propertyBehaviorString) {

                    case "normal" ->
                            propertyBehavior =
                                    ModelDelayConfig.PropertyBehavior.NORMAL;

                    case "held" ->
                            propertyBehavior =
                                    ModelDelayConfig.PropertyBehavior.HELD;

                    default -> {

                        System.err.println(
                                "[Usu's Item Model Delay] Unknown property behavior '"
                                        + propertyBehaviorString
                                        + "' for "
                                        + propertyName
                                        + ". Using normal."
                        );

                        propertyBehavior =
                                ModelDelayConfig.PropertyBehavior.NORMAL;
                    }
                }

                configs.put(
                        propertyName,
                        new ModelDelayConfig.DelayConfig(
                                delay,
                                mode,
                                propertyBehavior
                        )
                );
            }

        } catch (IOException e) {

            e.printStackTrace();
        }

        return new LoadedItemConfig(
                itemBehavior,
                configs,
                rangeConfigs,
                selectConfigs
        );
    }
}
