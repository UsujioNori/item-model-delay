package com.usujiotarako.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.WeakHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

public final class ModelDelaySelectState {

    /*
     * ================================================================
     * STATE KEY
     * ================================================================
     *
     * Most select properties belong to a particular physical/logical
     * ItemStack.
     *
     * Examples:
     *
     *     charge_type
     *     trim_material
     *     block_state
     *
     *
     * main_hand is different:
     *
     *     it belongs to the owning entity.
     *
     * For main_hand:
     *
     *     stackIdentity = 0
     *
     * so all renders for that owner deliberately share one state.
     */
    private record StateKey(
            Identifier item,
            String property,
            int ownerId,
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
     * ================================================================
     * SELECT STATE
     * ================================================================
     */
    private static final Map<StateKey, Object> CURRENT_VALUE =
            new HashMap<>();


    private static final Map<StateKey, Object> TARGET_VALUE =
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
            new AtomicLong(
                    1L
            );


    /*
     * ================================================================
     * STALE STATE CLEANUP
     * ================================================================
     *
     * Completed select state which has not been evaluated for ten minutes
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


    private ModelDelaySelectState() {
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


        /*
         * Keep physical/logical stack identities synchronized before
         * advancing select timers.
         */
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


            /*
             * Delay completed.
             *
             * Promote the pending target to the currently exposed
             * select value.
             */
            Object target =
                    TARGET_VALUE.get(
                            key
                    );


            CURRENT_VALUE.put(
                    key,
                    target
            );


            TARGET_VALUE.put(
                    key,
                    target
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
     * Minecraft can replace an ItemStack Java object while that stack is
     * moved through inventory slots or the cursor.
     *
     * synchronizePlayerLocations normally keeps the logical identity
     * intact.
     *
     * This is an additional safety net for a render which encounters the
     * replacement identity before that relationship has been observed.
     *
     * Only inherit when exactly ONE active transition for:
     *
     *     same owner
     *     same item
     *     same property
     *     same target select value
     *
     * exists.
     *
     * Multiple possible candidates are deliberately rejected.
     */
    private static <T> boolean inheritActiveTransition(
            StateKey newKey,
            @Nullable T vanillaValue
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


            Object oldTarget =
                    TARGET_VALUE.get(
                            oldKey
                    );


            if (
                    !Objects.equals(
                            oldTarget,
                            vanillaValue
                    )
            ) {

                continue;
            }


            /*
             * Ambiguous.
             *
             * More than one previous stack could represent this new
             * render, so do not guess.
             */
            if (candidate != null) {

                return false;
            }


            candidate =
                    oldKey;
        }


        if (candidate == null) {

            return false;
        }


        Object currentValue =
                CURRENT_VALUE.get(
                        candidate
                );


        Object targetValue =
                TARGET_VALUE.get(
                        candidate
                );


        Integer remaining =
                REMAINING.get(
                        candidate
                );


        if (
                remaining == null
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


        REMAINING.put(
                newKey,
                remaining
        );


        TRANSITIONING.put(
                newKey,
                true
        );


        /*
         * Move rather than duplicate the state.
         */
        CURRENT_VALUE.remove(
                candidate
        );


        TARGET_VALUE.remove(
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
     * GET DELAYED SELECT VALUE
     * ================================================================
     */
    @SuppressWarnings("unchecked")
    public static <T> @Nullable T get(
            ItemStack stack,
            @Nullable LivingEntity owner,
            Identifier itemId,
            String property,
            @Nullable T vanillaValue,
            ModelDelayConfig.SelectDelayConfig config
    ) {

        StateKey key =
                createKey(
                        stack,
                        owner,
                        itemId,
                        property
                );


        LAST_SEEN.put(
                key,
                CLIENT_TICK
        );


        /*
         * ============================================================
         * FIRST EVALUATION
         * ============================================================
         *
         * A genuinely new stack/value should appear immediately.
         *
         * However, before treating it as new, check whether Minecraft
         * replaced the ItemStack object during an already-active delayed
         * transition.
         */
        if (!CURRENT_VALUE.containsKey(key)) {

            boolean inherited =
                    inheritActiveTransition(
                            key,
                            vanillaValue
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


                TRANSITIONING.put(
                        key,
                        false
                );


                return vanillaValue;
            }
        }


        T currentValue =
                (T) CURRENT_VALUE.get(
                        key
                );


        T targetValue =
                (T) TARGET_VALUE.get(
                        key
                );


        boolean transitioning =
                TRANSITIONING.getOrDefault(
                        key,
                        false
                );


        /*
         * ============================================================
         * ALREADY TRANSITIONING
         * ============================================================
         */
        if (transitioning) {

            /*
             * Vanilla returned to the value currently being displayed.
             *
             * Cancel the pending transition.
             */
            if (
                    Objects.equals(
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


            /*
             * Still heading toward exactly the same select value.
             *
             * Keep the existing timer.
             */
            if (
                    Objects.equals(
                            vanillaValue,
                            targetValue
                    )
            ) {

                return currentValue;
            }


            /*
             * Vanilla moved to a third value before the old transition
             * completed.
             *
             * Restart toward the newest target.
             *
             * Example:
             *
             *     none
             *         -> arrow
             *         -> rocket
             *
             * before the arrow delay finishes.
             */
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


        /*
         * ============================================================
         * NO VALUE CHANGE
         * ============================================================
         */
        if (
                Objects.equals(
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


        /*
         * ============================================================
         * BEGIN SELECT TRANSITION
         * ============================================================
         */
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


        /*
         * Main inventory + hotbar.
         */
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


        /*
         * Offhand.
         */
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


        /*
         * Inventory cursor.
         */
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
     *
     * This mirrors the proven range-state stack lifecycle logic.
     *
     * Pass order:
     *
     *     1. exact known object
     *     2. exact item/components movement
     *     3. same-location component mutation
     *     4. same item + count movement fallback
     *     5. genuinely new stack
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
         * ============================================================
         * PASS 1 — EXACT KNOWN STACK OBJECT
         * ============================================================
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
         * ============================================================
         * PASS 2 — MOVEMENT WITH EXACT ITEM / COMPONENTS
         * ============================================================
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


                    /*
                     * Prefer an actual movement over accidentally pairing
                     * with the same location.
                     */
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
         * ============================================================
         * PASS 3 — SAME-LOCATION COMPONENT MUTATION
         * ============================================================
         *
         * Important for select properties such as:
         *
         *     charge_type
         *     trim_material
         *     block_state
         *
         * Their components may change while the logical stack remains in
         * the same slot.
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


            /*
             * Same item in same location, but components changed.
             *
             * Preserve the logical identity.
             */
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
         * ============================================================
         * PASS 4 — MOVEMENT FALLBACK BY SAME ITEM + COUNT
         * ============================================================
         *
         * Useful where Minecraft recreated the stack while moving it and
         * components changed during the same operation.
         *
         * Only use this fallback if exactly one old candidate exists.
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
         * ============================================================
         * PASS 5 — GENUINELY NEW STACKS
         * ============================================================
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


        /*
         * Exact real inventory object.
         */
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


        /*
         * Already-known render/object identity.
         */
        Long known =
                STACK_IDENTITIES.get(
                        stack
                );


        if (known != null) {

            return known;
        }


        /*
         * ============================================================
         * UNIQUE COMPONENT-EQUIVALENT REAL STACK
         * ============================================================
         *
         * Important for renderer-created copies.
         *
         * Only accept when exactly one player's real stack matches.
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
         * ============================================================
         * SELECTED STALE RENDER COPY
         * ============================================================
         *
         * A stale rendered copy may contain old components while the real
         * selected stack has already changed.
         *
         * If it is the same item type as the selected stack, tie it to
         * that selected logical stack.
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
         * ============================================================
         * CURSOR STALE RENDER COPY
         * ============================================================
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


        /*
         * Nothing safely matched.
         *
         * Give this stack/render its own identity rather than guessing.
         */
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
            @Nullable LivingEntity owner,
            Identifier itemId,
            String property
    ) {

        /*
         * ============================================================
         * MAIN HAND
         * ============================================================
         *
         * minecraft:main_hand describes:
         *
         *     owner.getMainArm()
         *
         * It belongs to the entity, not a particular ItemStack.
         *
         * Therefore every stack/render owned by the same entity shares
         * one main_hand state.
         */
        if (
                "select.main_hand".equals(
                        property
                )
        ) {

            if (owner != null) {

                return new StateKey(
                        itemId,
                        property,
                        owner.getId(),
                        0L
                );
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
                        0L
                );
            }


            return new StateKey(
                    itemId,
                    property,
                    -1,
                    0L
            );
        }


        /*
         * ============================================================
         * NORMAL STACK-SENSITIVE SELECT PROPERTY
         * ============================================================
         *
         * Includes:
         *
         *     select.charge_type
         *     select.trim_material
         *     select.block_state[...]
         *
         * and future stack/component-based select properties.
         */
        if (owner instanceof Player player) {

            long identity =
                    resolvePlayerStackIdentity(
                            stack,
                            player
                    );


            return new StateKey(
                    itemId,
                    property,
                    player.getId(),
                    identity
            );
        }


        /*
         * Non-player LivingEntity.
         *
         * There is no player inventory lifecycle to resolve, so preserve
         * Java object identity for the stack.
         */
        if (owner != null) {

            return new StateKey(
                    itemId,
                    property,
                    owner.getId(),
                    getOrCreateStackIdentity(
                            stack
                    )
            );
        }


        /*
         * ============================================================
         * OWNERLESS LOCAL RENDER
         * ============================================================
         *
         * Inventory/GUI rendering frequently has owner == null.
         *
         * Resolve against the local player's physical/logical stack
         * identity so the same real stack does not suddenly acquire an
         * independent select timer merely because it is being rendered in
         * a different context.
         */
        Minecraft minecraft =
                Minecraft.getInstance();


        LocalPlayer localPlayer =
                minecraft.player;


        if (localPlayer != null) {

            long identity =
                    resolvePlayerStackIdentity(
                            stack,
                            localPlayer
                    );


            return new StateKey(
                    itemId,
                    property,
                    localPlayer.getId(),
                    identity
            );
        }


        /*
         * No player exists.
         */
        return new StateKey(
                itemId,
                property,
                -1,
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