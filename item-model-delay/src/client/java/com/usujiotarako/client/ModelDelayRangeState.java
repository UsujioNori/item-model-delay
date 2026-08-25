package com.usujiotarako.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.WeakHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

public final class ModelDelayRangeState {

    /*
     * ================================================================
     * STATE KEY
     * ================================================================
     */
    private record StateKey(
            Identifier item,
            String property,
            int ownerId,
            int context,
            long stackIdentity
    ) {
    }


    /*
     * ================================================================
     * PLAYER LOCATION
     * ================================================================
     */
    private record LocationKey(
            int ownerId,
            int slot
    ) {
    }


    private record TrackedLocation(
            ItemStack object,
            ItemStack snapshot,
            long identity
    ) {
    }


    private static final int CURSOR_SLOT =
            Integer.MIN_VALUE;


    /*
     * Ownerless NeedleDirectionHelper renders deliberately return 0.0.
     *
     * They must not share state with an owner-driven Time or Compass
     * property.
     */
    private static final int OWNERLESS_NEEDLE_CONTEXT =
            Integer.MIN_VALUE + 1;


    /*
     * ================================================================
     * RANGE STATE
     * ================================================================
     */
    private static final Map<StateKey, Float> CURRENT_VALUE =
            new HashMap<>();


    private static final Map<StateKey, Float> TARGET_VALUE =
            new HashMap<>();


    private static final Map<StateKey, Integer> CURRENT_RANGE =
            new HashMap<>();


    private static final Map<StateKey, Integer> TARGET_RANGE =
            new HashMap<>();


    private static final Map<StateKey, Integer> REMAINING =
            new HashMap<>();


    private static final Map<StateKey, Boolean> TRANSITIONING =
            new HashMap<>();


    /*
     * ================================================================
     * STACK LIFECYCLE IDENTITIES
     * ================================================================
     */
    private static final WeakHashMap<ItemStack, Long> STACK_IDENTITIES =
            new WeakHashMap<>();


    private static final AtomicLong NEXT_STACK_ID =
            new AtomicLong(1L);


    /*
     * ================================================================
     * STALE STATE CLEANUP
     * ================================================================
     *
     * Completed range state which has not been evaluated for ten minutes
     * is discarded. Active delayed transitions are never removed.
     */
    private static final long STALE_STATE_TICKS =
            20L * 60L * 10L;


    private static final Map<StateKey, Long> LAST_SEEN =
            new HashMap<>();


    private static long CLIENT_TICK =
            0L;


    /*
     * ================================================================
     * PLAYER LOCATION SNAPSHOTS
     * ================================================================
     */
    private static final Map<LocationKey, TrackedLocation> PLAYER_LOCATIONS =
            new HashMap<>();


    private ModelDelayRangeState() {
    }


    /*
     * ================================================================
     * CLIENT TICK
     * ================================================================
     */
    public static void tick() {

        CLIENT_TICK++;


        Minecraft minecraft =
                Minecraft.getInstance();


        LocalPlayer localPlayer =
                minecraft.player;


        if (localPlayer != null) {

            synchronizePlayerLocations(
                    localPlayer
            );
        }


        Iterator<
                Map.Entry<StateKey, Integer>
                > iterator =
                REMAINING.entrySet().iterator();


        while (iterator.hasNext()) {

            Map.Entry<StateKey, Integer> entry =
                    iterator.next();


            StateKey key =
                    entry.getKey();


            int remaining =
                    entry.getValue() - 1;


            if (remaining > 0) {

                entry.setValue(
                        remaining
                );


                continue;
            }


            float targetValue =
                    TARGET_VALUE.getOrDefault(
                            key,
                            CURRENT_VALUE.getOrDefault(
                                    key,
                                    0.0F
                            )
                    );


            int targetRange =
                    TARGET_RANGE.getOrDefault(
                            key,
                            CURRENT_RANGE.getOrDefault(
                                    key,
                                    -1
                            )
                    );


            CURRENT_VALUE.put(
                    key,
                    targetValue
            );


            CURRENT_RANGE.put(
                    key,
                    targetRange
            );


            TARGET_VALUE.put(
                    key,
                    targetValue
            );


            TARGET_RANGE.put(
                    key,
                    targetRange
            );


            TRANSITIONING.put(
                    key,
                    false
            );


            iterator.remove();
        }


        pruneStaleState();
    }


    /*
     * ================================================================
     * STALE STATE CLEANUP
     * ================================================================
     */
    private static void pruneStaleState() {

        Iterator<Map.Entry<StateKey, Long>> iterator =
                LAST_SEEN.entrySet().iterator();


        while (iterator.hasNext()) {

            Map.Entry<StateKey, Long> entry =
                    iterator.next();


            StateKey key =
                    entry.getKey();


            long age =
                    CLIENT_TICK - entry.getValue();


            if (
                    age < STALE_STATE_TICKS

                            || TRANSITIONING.getOrDefault(
                            key,
                            false
                    )
            ) {

                continue;
            }


            CURRENT_VALUE.remove(key);
            TARGET_VALUE.remove(key);
            CURRENT_RANGE.remove(key);
            TARGET_RANGE.remove(key);
            REMAINING.remove(key);
            TRANSITIONING.remove(key);


            iterator.remove();
        }
    }


    /*
     * ================================================================
     * INHERIT ACTIVE TRANSITION
     * ================================================================
     *
     * Used when Minecraft changes/replaces a stack identity during
     * inventory/cursor movement.
     */
    private static boolean inheritActiveTransition(
            StateKey newKey,
            int vanillaRange
    ) {

        StateKey candidate =
                null;


        for (
                Map.Entry<StateKey, Boolean> entry
                : TRANSITIONING.entrySet()
        ) {

            StateKey oldKey =
                    entry.getKey();


            if (!entry.getValue()) {
                continue;
            }


            if (oldKey.equals(newKey)) {
                continue;
            }


            if (
                    oldKey.ownerId()
                            != newKey.ownerId()
            ) {

                continue;
            }


            if (
                    !oldKey.item().equals(
                            newKey.item()
                    )
            ) {

                continue;
            }


            if (
                    !oldKey.property().equals(
                            newKey.property()
                    )
            ) {

                continue;
            }


            int oldTargetRange =
                    TARGET_RANGE.getOrDefault(
                            oldKey,
                            CURRENT_RANGE.getOrDefault(
                                    oldKey,
                                    -1
                            )
                    );


            if (oldTargetRange != vanillaRange) {
                continue;
            }


            if (candidate != null) {

                return false;
            }


            candidate =
                    oldKey;
        }


        if (candidate == null) {

            return false;
        }


        Float currentValue =
                CURRENT_VALUE.get(
                        candidate
                );


        Float targetValue =
                TARGET_VALUE.get(
                        candidate
                );


        Integer currentRange =
                CURRENT_RANGE.get(
                        candidate
                );


        Integer targetRange =
                TARGET_RANGE.get(
                        candidate
                );


        Integer remaining =
                REMAINING.get(
                        candidate
                );


        if (
                currentValue == null
                        || targetValue == null
                        || currentRange == null
                        || targetRange == null
                        || remaining == null
        ) {

            return false;
        }


        CURRENT_VALUE.put(
                newKey,
                currentValue
        );


        TARGET_VALUE.put(
                newKey,
                targetValue
        );


        CURRENT_RANGE.put(
                newKey,
                currentRange
        );


        TARGET_RANGE.put(
                newKey,
                targetRange
        );


        REMAINING.put(
                newKey,
                remaining
        );


        TRANSITIONING.put(
                newKey,
                true
        );


        CURRENT_VALUE.remove(
                candidate
        );


        TARGET_VALUE.remove(
                candidate
        );


        CURRENT_RANGE.remove(
                candidate
        );


        TARGET_RANGE.remove(
                candidate
        );


        REMAINING.remove(
                candidate
        );


        TRANSITIONING.remove(
                candidate
        );


        return true;
    }


    /*
     * ================================================================
     * GET
     * ================================================================
     */
    public static float get(
            ItemStack stack,
            @Nullable ItemOwner owner,
            Identifier itemId,
            String property,
            float vanillaValue,
            ModelDelayConfig.RangeDelayConfig config,
            int seed,
            float[] thresholds
    ) {

        StateKey key =
                createKey(
                        stack,
                        owner,
                        itemId,
                        property,
                        seed
                );


        LAST_SEEN.put(
                key,
                CLIENT_TICK
        );


        int vanillaRange =
                getRangeIndex(
                        thresholds,
                        vanillaValue
                );


        /*
         * ============================================================
         * FIRST EVALUATION
         * ============================================================
         */
        if (!CURRENT_VALUE.containsKey(key)) {

            boolean inherited =
                    inheritActiveTransition(
                            key,
                            vanillaRange
                    );


            if (!inherited) {

                CURRENT_VALUE.put(
                        key,
                        vanillaValue
                );


                TARGET_VALUE.put(
                        key,
                        vanillaValue
                );


                CURRENT_RANGE.put(
                        key,
                        vanillaRange
                );


                TARGET_RANGE.put(
                        key,
                        vanillaRange
                );


                TRANSITIONING.put(
                        key,
                        false
                );


                return vanillaValue;
            }
        }


        float currentValue =
                CURRENT_VALUE.get(
                        key
                );


        float targetValue =
                TARGET_VALUE.getOrDefault(
                        key,
                        currentValue
                );


        int currentRange =
                CURRENT_RANGE.getOrDefault(
                        key,
                        getRangeIndex(
                                thresholds,
                                currentValue
                        )
                );


        int targetRange =
                TARGET_RANGE.getOrDefault(
                        key,
                        currentRange
                );


        boolean transitioning =
                TRANSITIONING.getOrDefault(
                        key,
                        false
                );


        if (
                config.behavior()
                        == ModelDelayConfig.RangeBehavior.VALUE
        ) {

            return processValueBehavior(
                    key,
                    vanillaValue,
                    currentValue,
                    targetValue,
                    transitioning,
                    config
            );
        }


        return processThresholdBehavior(
                key,
                vanillaValue,
                vanillaRange,
                currentValue,
                currentRange,
                targetRange,
                transitioning,
                config
        );
    }


    /*
     * ================================================================
     * VALUE BEHAVIOR
     * ================================================================
     */
    private static float processValueBehavior(
            StateKey key,
            float vanillaValue,
            float currentValue,
            float targetValue,
            boolean transitioning,
            ModelDelayConfig.RangeDelayConfig config
    ) {

        if (transitioning) {

            if (
                    sameValue(
                            vanillaValue,
                            currentValue
                    )
            ) {

                TARGET_VALUE.put(
                        key,
                        currentValue
                );


                REMAINING.remove(
                        key
                );


                TRANSITIONING.put(
                        key,
                        false
                );


                return currentValue;
            }


            if (
                    !sameValue(
                            vanillaValue,
                            targetValue
                    )
            ) {

                TARGET_VALUE.put(
                        key,
                        vanillaValue
                );


                if (config.delay() <= 0) {

                    CURRENT_VALUE.put(
                            key,
                            vanillaValue
                    );


                    TARGET_VALUE.put(
                            key,
                            vanillaValue
                    );


                    REMAINING.remove(
                            key
                    );


                    TRANSITIONING.put(
                            key,
                            false
                    );


                    return vanillaValue;
                }


                REMAINING.put(
                        key,
                        config.delay()
                );


                return currentValue;
            }


            return currentValue;
        }


        if (
                sameValue(
                        vanillaValue,
                        currentValue
                )
        ) {

            TARGET_VALUE.put(
                    key,
                    currentValue
            );


            return currentValue;
        }


        TARGET_VALUE.put(
                key,
                vanillaValue
        );


        if (config.delay() <= 0) {

            CURRENT_VALUE.put(
                    key,
                    vanillaValue
            );


            TARGET_VALUE.put(
                    key,
                    vanillaValue
            );


            TRANSITIONING.put(
                    key,
                    false
            );


            REMAINING.remove(
                    key
            );


            return vanillaValue;
        }


        TRANSITIONING.put(
                key,
                true
        );


        REMAINING.put(
                key,
                config.delay()
        );


        return currentValue;
    }


    /*
     * ================================================================
     * THRESHOLD BEHAVIOR
     * ================================================================
     */
    private static float processThresholdBehavior(
            StateKey key,
            float vanillaValue,
            int vanillaRange,
            float currentValue,
            int currentRange,
            int targetRange,
            boolean transitioning,
            ModelDelayConfig.RangeDelayConfig config
    ) {

        if (transitioning) {

            /*
             * Property returned to currently-displayed range.
             */
            if (vanillaRange == currentRange) {

                CURRENT_VALUE.put(
                        key,
                        vanillaValue
                );


                TARGET_VALUE.put(
                        key,
                        vanillaValue
                );


                CURRENT_RANGE.put(
                        key,
                        vanillaRange
                );


                TARGET_RANGE.put(
                        key,
                        vanillaRange
                );


                REMAINING.remove(
                        key
                );


                TRANSITIONING.put(
                        key,
                        false
                );


                return vanillaValue;
            }


            /*
             * Still moving toward same target range.
             *
             * Raw value may continue changing without restarting timer.
             */
            if (vanillaRange == targetRange) {

                TARGET_VALUE.put(
                        key,
                        vanillaValue
                );


                return currentValue;
            }


            /*
             * New target range.
             */
            TARGET_VALUE.put(
                    key,
                    vanillaValue
            );


            TARGET_RANGE.put(
                    key,
                    vanillaRange
            );


            if (config.delay() <= 0) {

                CURRENT_VALUE.put(
                        key,
                        vanillaValue
                );


                CURRENT_RANGE.put(
                        key,
                        vanillaRange
                );


                TARGET_VALUE.put(
                        key,
                        vanillaValue
                );


                TARGET_RANGE.put(
                        key,
                        vanillaRange
                );


                REMAINING.remove(
                        key
                );


                TRANSITIONING.put(
                        key,
                        false
                );


                return vanillaValue;
            }


            REMAINING.put(
                    key,
                    config.delay()
            );


            return currentValue;
        }


        /*
         * Same threshold/model region.
         */
        if (vanillaRange == currentRange) {

            CURRENT_VALUE.put(
                    key,
                    vanillaValue
            );


            TARGET_VALUE.put(
                    key,
                    vanillaValue
            );


            CURRENT_RANGE.put(
                    key,
                    vanillaRange
            );


            TARGET_RANGE.put(
                    key,
                    vanillaRange
            );


            return vanillaValue;
        }


        /*
         * Begin threshold transition.
         */
        TARGET_VALUE.put(
                key,
                vanillaValue
        );


        TARGET_RANGE.put(
                key,
                vanillaRange
        );


        if (config.delay() <= 0) {

            CURRENT_VALUE.put(
                    key,
                    vanillaValue
            );


            CURRENT_RANGE.put(
                    key,
                    vanillaRange
            );


            TARGET_VALUE.put(
                    key,
                    vanillaValue
            );


            TARGET_RANGE.put(
                    key,
                    vanillaRange
            );


            TRANSITIONING.put(
                    key,
                    false
            );


            REMAINING.remove(
                    key
            );


            return vanillaValue;
        }


        TRANSITIONING.put(
                key,
                true
        );


        REMAINING.put(
                key,
                config.delay()
        );


        return currentValue;
    }


    /*
     * ================================================================
     * RANGE INDEX
     * ================================================================
     */
    private static int getRangeIndex(
            float[] thresholds,
            float value
    ) {

        if (Float.isNaN(value)) {

            return -1;
        }


        if (thresholds.length < 16) {

            for (
                    int i = 0;
                    i < thresholds.length;
                    i++
            ) {

                if (thresholds[i] > value) {

                    return i - 1;
                }
            }


            return thresholds.length - 1;
        }


        int index =
                Arrays.binarySearch(
                        thresholds,
                        value
                );


        if (index < 0) {

            int insertionPoint =
                    ~index;


            return insertionPoint - 1;
        }


        return index;
    }


    private static boolean sameValue(
            float a,
            float b
    ) {

        return Float.compare(
                a,
                b
        ) == 0;
    }


    /*
     * ================================================================
     * BASIC STACK IDENTITY
     * ================================================================
     */
    private static long getOrCreateStackIdentity(
            ItemStack stack
    ) {

        Long existing =
                STACK_IDENTITIES.get(
                        stack
                );


        if (existing != null) {

            return existing;
        }


        long created =
                NEXT_STACK_ID.getAndIncrement();


        STACK_IDENTITIES.put(
                stack,
                created
        );


        return created;
    }


    /*
     * ================================================================
     * CURRENT PLAYER LOCATIONS
     * ================================================================
     */
    private static Map<LocationKey, ItemStack> collectPlayerLocations(
            Player player
    ) {

        Map<LocationKey, ItemStack> locations =
                new HashMap<>();


        Inventory inventory =
                player.getInventory();


        for (
                int slot = 0;
                slot < Inventory.INVENTORY_SIZE;
                slot++
        ) {

            ItemStack stack =
                    inventory.getItem(
                            slot
                    );


            if (!stack.isEmpty()) {

                locations.put(
                        new LocationKey(
                                player.getId(),
                                slot
                        ),
                        stack
                );
            }
        }


        ItemStack offhand =
                inventory.getItem(
                        Inventory.SLOT_OFFHAND
                );


        if (!offhand.isEmpty()) {

            locations.put(
                    new LocationKey(
                            player.getId(),
                            Inventory.SLOT_OFFHAND
                    ),
                    offhand
            );
        }


        ItemStack carried =
                player.containerMenu.getCarried();


        if (!carried.isEmpty()) {

            locations.put(
                    new LocationKey(
                            player.getId(),
                            CURSOR_SLOT
                    ),
                    carried
            );
        }


        return locations;
    }


    /*
     * ================================================================
     * PLAYER MOVEMENT SYNCHRONIZATION
     * ================================================================
     */
    private static void synchronizePlayerLocations(
            Player player
    ) {

        int ownerId =
                player.getId();


        Map<LocationKey, ItemStack> currentLocations =
                collectPlayerLocations(
                        player
                );


        Map<LocationKey, TrackedLocation> previousLocations =
                new HashMap<>();


        for (
                Map.Entry<LocationKey, TrackedLocation> entry
                : PLAYER_LOCATIONS.entrySet()
        ) {

            if (
                    entry.getKey().ownerId()
                            == ownerId
            ) {

                previousLocations.put(
                        entry.getKey(),
                        entry.getValue()
                );
            }
        }


        Map<LocationKey, TrackedLocation> newLocations =
                new HashMap<>();


        List<LocationKey> usedPreviousLocations =
                new ArrayList<>();


        /*
         * PASS 1 — exact known stack object.
         */
        for (
                Map.Entry<LocationKey, ItemStack> entry
                : currentLocations.entrySet()
        ) {

            LocationKey location =
                    entry.getKey();


            ItemStack currentStack =
                    entry.getValue();


            Long knownIdentity =
                    STACK_IDENTITIES.get(
                            currentStack
                    );


            if (knownIdentity == null) {
                continue;
            }


            newLocations.put(
                    location,
                    new TrackedLocation(
                            currentStack,
                            currentStack.copy(),
                            knownIdentity
                    )
            );


            for (
                    Map.Entry<LocationKey, TrackedLocation> previousEntry
                    : previousLocations.entrySet()
            ) {

                if (
                        previousEntry.getValue().identity()
                                == knownIdentity
                ) {

                    usedPreviousLocations.add(
                            previousEntry.getKey()
                    );
                }
            }
        }


        /*
         * PASS 2 — movement with exact item/components.
         */
        for (
                Map.Entry<LocationKey, ItemStack> entry
                : currentLocations.entrySet()
        ) {

            LocationKey currentLocation =
                    entry.getKey();


            if (
                    newLocations.containsKey(
                            currentLocation
                    )
            ) {

                continue;
            }


            ItemStack currentStack =
                    entry.getValue();


            LocationKey matchedPreviousLocation =
                    null;


            TrackedLocation matchedPrevious =
                    null;


            for (
                    Map.Entry<LocationKey, TrackedLocation> previousEntry
                    : previousLocations.entrySet()
            ) {

                LocationKey previousLocation =
                        previousEntry.getKey();


                if (
                        usedPreviousLocations.contains(
                                previousLocation
                        )
                ) {

                    continue;
                }


                ItemStack previousSnapshot =
                        previousEntry.getValue().snapshot();


                if (
                        ItemStack.isSameItemSameComponents(
                                currentStack,
                                previousSnapshot
                        )
                ) {

                    matchedPreviousLocation =
                            previousLocation;


                    matchedPrevious =
                            previousEntry.getValue();


                    if (
                            !previousLocation.equals(
                                    currentLocation
                            )
                    ) {

                        break;
                    }
                }
            }


            if (matchedPrevious != null) {

                long identity =
                        matchedPrevious.identity();


                STACK_IDENTITIES.put(
                        currentStack,
                        identity
                );


                newLocations.put(
                        currentLocation,
                        new TrackedLocation(
                                currentStack,
                                currentStack.copy(),
                                identity
                        )
                );


                usedPreviousLocations.add(
                        matchedPreviousLocation
                );
            }
        }


        /*
         * PASS 3 — same-location component mutation.
         */
        for (
                Map.Entry<LocationKey, ItemStack> entry
                : currentLocations.entrySet()
        ) {

            LocationKey location =
                    entry.getKey();


            if (
                    newLocations.containsKey(
                            location
                    )
            ) {

                continue;
            }


            ItemStack currentStack =
                    entry.getValue();


            TrackedLocation previous =
                    previousLocations.get(
                            location
                    );


            if (
                    previous == null
                            || usedPreviousLocations.contains(
                            location
                    )
            ) {

                continue;
            }


            ItemStack previousSnapshot =
                    previous.snapshot();


            if (
                    !previousSnapshot.isEmpty()
                            && previousSnapshot.getItem()
                            == currentStack.getItem()
            ) {

                long identity =
                        previous.identity();


                STACK_IDENTITIES.put(
                        currentStack,
                        identity
                );


                newLocations.put(
                        location,
                        new TrackedLocation(
                                currentStack,
                                currentStack.copy(),
                                identity
                        )
                );


                usedPreviousLocations.add(
                        location
                );
            }
        }


        /*
         * PASS 4 — movement fallback by same item + count.
         */
        for (
                Map.Entry<LocationKey, ItemStack> entry
                : currentLocations.entrySet()
        ) {

            LocationKey currentLocation =
                    entry.getKey();


            if (
                    newLocations.containsKey(
                            currentLocation
                    )
            ) {

                continue;
            }


            ItemStack currentStack =
                    entry.getValue();


            LocationKey candidateLocation =
                    null;


            TrackedLocation candidate =
                    null;


            int candidateCount =
                    0;


            for (
                    Map.Entry<LocationKey, TrackedLocation> previousEntry
                    : previousLocations.entrySet()
            ) {

                LocationKey previousLocation =
                        previousEntry.getKey();


                if (
                        usedPreviousLocations.contains(
                                previousLocation
                        )
                ) {

                    continue;
                }


                ItemStack previousSnapshot =
                        previousEntry.getValue().snapshot();


                if (
                        previousSnapshot.getItem()
                                == currentStack.getItem()
                                && previousSnapshot.getCount()
                                == currentStack.getCount()
                ) {

                    candidateLocation =
                            previousLocation;


                    candidate =
                            previousEntry.getValue();


                    candidateCount++;
                }
            }


            if (
                    candidate != null
                            && candidateCount == 1
            ) {

                long identity =
                        candidate.identity();


                STACK_IDENTITIES.put(
                        currentStack,
                        identity
                );


                newLocations.put(
                        currentLocation,
                        new TrackedLocation(
                                currentStack,
                                currentStack.copy(),
                                identity
                        )
                );


                usedPreviousLocations.add(
                        candidateLocation
                );
            }
        }


        /*
         * PASS 5 — genuinely new stacks.
         */
        for (
                Map.Entry<LocationKey, ItemStack> entry
                : currentLocations.entrySet()
        ) {

            LocationKey location =
                    entry.getKey();


            if (
                    newLocations.containsKey(
                            location
                    )
            ) {

                continue;
            }


            ItemStack currentStack =
                    entry.getValue();


            long identity =
                    getOrCreateStackIdentity(
                            currentStack
                    );


            newLocations.put(
                    location,
                    new TrackedLocation(
                            currentStack,
                            currentStack.copy(),
                            identity
                    )
            );
        }


        PLAYER_LOCATIONS.keySet().removeIf(
                key ->
                        key.ownerId()
                                == ownerId
        );


        PLAYER_LOCATIONS.putAll(
                newLocations
        );
    }


    /*
     * ================================================================
     * EXACT PLAYER LOCATION
     * ================================================================
     */
    private static @Nullable LocationKey findExactLocation(
            ItemStack stack,
            Player player
    ) {

        Inventory inventory =
                player.getInventory();


        for (
                int slot = 0;
                slot < Inventory.INVENTORY_SIZE;
                slot++
        ) {

            if (
                    inventory.getItem(
                            slot
                    ) == stack
            ) {

                return new LocationKey(
                        player.getId(),
                        slot
                );
            }
        }


        ItemStack offhand =
                inventory.getItem(
                        Inventory.SLOT_OFFHAND
                );


        if (offhand == stack) {

            return new LocationKey(
                    player.getId(),
                    Inventory.SLOT_OFFHAND
            );
        }


        ItemStack carried =
                player.containerMenu.getCarried();


        if (carried == stack) {

            return new LocationKey(
                    player.getId(),
                    CURSOR_SLOT
            );
        }


        return null;
    }


    /*
     * ================================================================
     * RESOLVE PLAYER STACK IDENTITY
     * ================================================================
     */
    private static long resolvePlayerStackIdentity(
            ItemStack stack,
            Player player
    ) {

        synchronizePlayerLocations(
                player
        );


        LocationKey exactLocation =
                findExactLocation(
                        stack,
                        player
                );


        if (exactLocation != null) {

            TrackedLocation tracked =
                    PLAYER_LOCATIONS.get(
                            exactLocation
                    );


            if (tracked != null) {

                STACK_IDENTITIES.put(
                        stack,
                        tracked.identity()
                );


                return tracked.identity();
            }
        }


        Long known =
                STACK_IDENTITIES.get(
                        stack
                );


        if (known != null) {

            return known;
        }


        /*
         * Unique component-equivalent real stack.
         */
        TrackedLocation matchingTracked =
                null;


        int matches =
                0;


        for (
                Map.Entry<LocationKey, TrackedLocation> entry
                : PLAYER_LOCATIONS.entrySet()
        ) {

            if (
                    entry.getKey().ownerId()
                            != player.getId()
            ) {

                continue;
            }


            TrackedLocation tracked =
                    entry.getValue();


            if (
                    ItemStack.isSameItemSameComponents(
                            stack,
                            tracked.object()
                    )
            ) {

                matchingTracked =
                        tracked;


                matches++;
            }
        }


        if (
                matchingTracked != null
                        && matches == 1
        ) {

            STACK_IDENTITIES.put(
                    stack,
                    matchingTracked.identity()
            );


            return matchingTracked.identity();
        }


        /*
         * Selected stale render copy.
         */
        Inventory inventory =
                player.getInventory();


        ItemStack selected =
                inventory.getSelectedItem();


        if (
                !selected.isEmpty()
                        && selected.getItem()
                        == stack.getItem()
        ) {

            LocationKey selectedLocation =
                    new LocationKey(
                            player.getId(),
                            inventory.getSelectedSlot()
                    );


            TrackedLocation tracked =
                    PLAYER_LOCATIONS.get(
                            selectedLocation
                    );


            if (tracked != null) {

                STACK_IDENTITIES.put(
                        stack,
                        tracked.identity()
                );


                return tracked.identity();
            }
        }


        /*
         * Cursor stale render copy.
         */
        ItemStack carried =
                player.containerMenu.getCarried();


        if (
                !carried.isEmpty()
                        && carried.getItem()
                        == stack.getItem()
        ) {

            LocationKey cursorLocation =
                    new LocationKey(
                            player.getId(),
                            CURSOR_SLOT
                    );


            TrackedLocation tracked =
                    PLAYER_LOCATIONS.get(
                            cursorLocation
                    );


            if (tracked != null) {

                STACK_IDENTITIES.put(
                        stack,
                        tracked.identity()
                );


                return tracked.identity();
            }
        }


        return getOrCreateStackIdentity(
                stack
        );
    }


    /*
     * ================================================================
     * STATE KEY CREATION
     * ================================================================
     */
    private static StateKey createKey(
            ItemStack stack,
            @Nullable ItemOwner owner,
            Identifier itemId,
            String property,
            int seed
    ) {

        /*
         * ============================================================
         * COOLDOWN
         * ============================================================
         *
         * Cooldown belongs to the player's cooldown manager rather than
         * one physical ItemStack.
         */
        if (
                "range.cooldown".equals(
                        property
                )
        ) {

            if (owner != null) {

                LivingEntity livingEntity =
                        owner.asLivingEntity();


                if (livingEntity instanceof Player player) {

                    return new StateKey(
                            itemId,
                            property,
                            player.getId(),
                            -1,
                            0L
                    );
                }
            }


            Minecraft minecraft =
                    Minecraft.getInstance();


            LocalPlayer localPlayer =
                    minecraft.player;


            if (localPlayer != null) {

                return new StateKey(
                        itemId,
                        property,
                        localPlayer.getId(),
                        -1,
                        0L
                );
            }


            return new StateKey(
                    itemId,
                    property,
                    -1,
                    -1,
                    0L
            );
        }


        /*
         * ============================================================
         * TIME
         * ============================================================
         *
         * Time depends on owner/world state, not a physical ItemStack.
         *
         * Ownerless NeedleDirectionHelper rendering returns 0.0 and is
         * deliberately kept separate.
         */
        if (
                property.startsWith(
                        "range.time{"
                )
        ) {

            if (owner != null) {

                LivingEntity livingEntity =
                        owner.asLivingEntity();


                if (livingEntity != null) {

                    return new StateKey(
                            itemId,
                            property,
                            livingEntity.getId(),
                            -1,
                            0L
                    );
                }
            }


            Minecraft minecraft =
                    Minecraft.getInstance();


            LocalPlayer localPlayer =
                    minecraft.player;


            int localOwnerId =
                    localPlayer != null
                            ? localPlayer.getId()
                            : -1;


            return new StateKey(
                    itemId,
                    property,
                    localOwnerId,
                    OWNERLESS_NEEDLE_CONTEXT,
                    0L
            );
        }


        /*
         * ============================================================
         * COMPASS
         * ============================================================
         *
         * Compass combines BOTH:
         *
         *     owner/world state
         *
         * and potentially:
         *
         *     ItemStack state
         *
         * because target=lodestone reads:
         *
         *     DataComponents.LODESTONE_TRACKER
         *
         *
         * So an owner-driven compass retains physical stack identity.
         *
         * Ownerless NeedleDirectionHelper evaluation is deliberately
         * separate because vanilla returns 0.0 when owner == null.
         */
        if (
                property.startsWith(
                        "range.compass{"
                )
        ) {

            if (owner != null) {

                LivingEntity livingEntity =
                        owner.asLivingEntity();


                if (livingEntity instanceof Player player) {

                    long identity =
                            resolvePlayerStackIdentity(
                                    stack,
                                    player
                            );


                    return new StateKey(
                            itemId,
                            property,
                            player.getId(),
                            -1,
                            identity
                    );
                }


                if (livingEntity != null) {

                    return new StateKey(
                            itemId,
                            property,
                            livingEntity.getId(),
                            -1,
                            getOrCreateStackIdentity(
                                    stack
                            )
                    );
                }
            }


            Minecraft minecraft =
                    Minecraft.getInstance();


            LocalPlayer localPlayer =
                    minecraft.player;


            int localOwnerId =
                    localPlayer != null
                            ? localPlayer.getId()
                            : -1;


            /*
             * Keep ownerless GUI compass state separate from the
             * player-owned compass state.
             */
            return new StateKey(
                    itemId,
                    property,
                    localOwnerId,
                    OWNERLESS_NEEDLE_CONTEXT,
                    getOrCreateStackIdentity(
                            stack
                    )
            );
        }


        /*
         * ============================================================
         * NORMAL STACK-SENSITIVE RANGE PROPERTY
         * ============================================================
         */
        if (owner != null) {

            LivingEntity livingEntity =
                    owner.asLivingEntity();


            if (livingEntity instanceof Player player) {

                long identity =
                        resolvePlayerStackIdentity(
                                stack,
                                player
                        );


                return new StateKey(
                        itemId,
                        property,
                        player.getId(),
                        -1,
                        identity
                );
            }
        }


        Minecraft minecraft =
                Minecraft.getInstance();


        LocalPlayer player =
                minecraft.player;


        if (player != null) {

            long identity =
                    resolvePlayerStackIdentity(
                            stack,
                            player
                    );


            return new StateKey(
                    itemId,
                    property,
                    player.getId(),
                    -1,
                    identity
            );
        }


        return new StateKey(
                itemId,
                property,
                -1,
                seed,
                getOrCreateStackIdentity(
                        stack
                )
        );
    }


    /*
     * ================================================================
     * CLEAR
     * ================================================================
     */
    public static void clear() {

        CURRENT_VALUE.clear();
        TARGET_VALUE.clear();
        CURRENT_RANGE.clear();
        TARGET_RANGE.clear();
        REMAINING.clear();
        TRANSITIONING.clear();
        LAST_SEEN.clear();


        STACK_IDENTITIES.clear();
        PLAYER_LOCATIONS.clear();


        NEXT_STACK_ID.set(
                1L
        );


        CLIENT_TICK =
                0L;
    }
}