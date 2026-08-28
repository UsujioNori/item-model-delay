package com.usujiotarako.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
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
     * Synthetic location range used for non-player slots in the currently
     * open container menu (smithing input/result, anvil slots, etc.).
     *
     * Player inventory/hotbar/offhand keep their vanilla slot numbers and
     * the cursor keeps CURSOR_SLOT. Menu slots are deliberately placed in a
     * separate negative range so they can never collide with either.
     */
    private static final int MENU_SLOT_BASE =
            Integer.MIN_VALUE + 1024;


    private static int menuLocationSlot(
            int menuSlotIndex
    ) {

        return MENU_SLOT_BASE
                + menuSlotIndex;
    }


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


    /*
     * Container result stacks can exist at the same time as their source
     * inputs. They therefore need their own identity, but can still inherit
     * the source stack's currently exposed select value on first evaluation.
     */
    private static final Map<Long, Long> DERIVED_FROM_IDENTITY =
            new HashMap<>();


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
     * TRANSITION EVENT OWNERSHIP
     * ================================================================
     *
     * Select properties can also be evaluated for inventory/GUI copies.
     *
     * Emit sound-capable shared events only for the local player's held
     * main-hand/offhand item. Stack-sensitive properties match the stable
     * physical stack identity; owner-level main_hand uses identity 0.
     */
    private static boolean shouldDispatchTransitionEvent(
            StateKey key
    ) {

        Minecraft minecraft =
                Minecraft.getInstance();


        LocalPlayer player =
                minecraft.player;


        if (
                player == null
        ) {

            return false;
        }


        if (
                key.ownerId() != -1

                        && key.ownerId()
                        != player.getId()
        ) {

            return false;
        }


        Inventory inventory =
                player.getInventory();


        ItemStack mainHand =
                inventory.getSelectedItem();


        if (
                matchesHeldStack(
                        key,
                        mainHand,
                        new LocationKey(
                                player.getId(),
                                inventory.getSelectedSlot()
                        )
                )
        ) {

            return true;
        }


        ItemStack offhand =
                inventory.getItem(
                        Inventory.SLOT_OFFHAND
                );


        return matchesHeldStack(
                key,
                offhand,
                new LocationKey(
                        player.getId(),
                        Inventory.SLOT_OFFHAND
                )
        );
    }


    private static boolean matchesHeldStack(
            StateKey key,
            ItemStack stack,
            LocationKey location
    ) {

        if (
                stack.isEmpty()

                        || !key.item().equals(
                        net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(
                                stack.getItem()
                        )
                )
        ) {

            return false;
        }


        if (
                key.stackIdentity()
                        == 0L
        ) {

            return true;
        }


        TrackedLocation tracked =
                PLAYER_LOCATIONS.get(
                        location
                );


        return tracked != null

                && tracked.identity()
                == key.stackIdentity();
    }


    /*
     * ================================================================
     * TRANSITION EFFECT RENDER IDENTITY
     * ================================================================
     *
     * select.main_hand is entity-owned state and intentionally uses
     * stackIdentity == 0. A particle still has to attach to the concrete
     * held stack, so resolve the current matching hand stack only when
     * emitting the effect.
     */
    private static long resolveTransitionEffectIdentity(
            StateKey key
    ) {

        if (
                !(
                        "select.main_hand".equals(
                                configPropertyFromStateKey(
                                        key.property()
                                )
                        )

                                || "select.local_time".equals(
                                configPropertyFromStateKey(
                                        key.property()
                                )
                        )
                )

                        || key.stackIdentity()
                        != 0L
        ) {

            return key.stackIdentity();
        }


        Minecraft minecraft =
                Minecraft.getInstance();


        LocalPlayer player =
                minecraft.player;


        if (
                player == null

                        || (
                        key.ownerId() != -1

                                && key.ownerId()
                                != player.getId()
                )
        ) {

            return key.stackIdentity();
        }


        ItemStack mainHand =
                player.getInventory().getSelectedItem();


        if (
                !mainHand.isEmpty()

                        && key.item().equals(
                        net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(
                                mainHand.getItem()
                        )
                )
        ) {

            return resolvePlayerStackIdentity(
                    mainHand,
                    player
            );
        }


        ItemStack offhand =
                player.getInventory().getItem(
                        Inventory.SLOT_OFFHAND
                );


        if (
                !offhand.isEmpty()

                        && key.item().equals(
                        net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(
                                offhand.getItem()
                        )
                )
        ) {

            return resolvePlayerStackIdentity(
                    offhand,
                    player
            );
        }


        return key.stackIdentity();
    }


    /*
     * ================================================================
     * PUBLIC CONFIG PROPERTY FROM STATE KEY
     * ================================================================
     *
     * Some select properties need an internal per-node state suffix.
     * For example local_time is stored as:
     *
     *     select.local_time{node=3}
     *
     * while the resource-pack configuration remains:
     *
     *     select.local_time
     *
     * Keep the internal state isolated, but always use the public key for
     * config lookup and transition event/effect dispatch.
     */
    private static String configPropertyFromStateKey(
            String stateProperty
    ) {

        int parameterStart =
                stateProperty.indexOf(
                        '{'
                );


        String publicProperty;


        if (parameterStart < 0) {

            publicProperty =
                    stateProperty;

        } else {

            publicProperty =
                    stateProperty.substring(
                            0,
                            parameterStart
                    );
        }


        /*
         * ComponentContents keeps the complete component ID in the
         * internal state key so different namespaces can never collide:
         *
         *     select.component[minecraft:rarity]
         *
         * Vanilla minecraft components intentionally use only their path
         * in .mdprop:
         *
         *     select.component[rarity]
         *
         * Convert the internal key back to that public configuration form
         * whenever tick/event code needs to look the config up again.
         */
        String vanillaComponentPrefix =
                "select.component[minecraft:";


        if (
                publicProperty.startsWith(
                        vanillaComponentPrefix
                )

                        && publicProperty.endsWith(
                        "]"
                )
        ) {

            String componentPath =
                    publicProperty.substring(
                            vanillaComponentPrefix.length(),
                            publicProperty.length() - 1
                    );


            return "select.component["
                    + componentPath
                    + "]";
        }


        return publicProperty;
    }


    /*
     * ================================================================
     * SELECT TRANSITION EVENTS
     * ================================================================
     */
    private static void dispatchSelectChange(
            StateKey key,
            Object previousValue,
            Object newValue,
            ModelDelayConfig.SelectDelayConfig config
    ) {

        if (
                !shouldDispatchTransitionEvent(
                        key
                )
        ) {

            return;
        }


        String publicProperty =
                configPropertyFromStateKey(
                        key.property()
                );


        long transitionIdentity =
                resolveTransitionEffectIdentity(
                        key
                );


        ModelTransitionEvent.fireChange(
                key.item(),
                publicProperty,
                key.ownerId(),
                transitionIdentity,
                previousValue,
                newValue,
                config.sound()
        );


        if (
                config.effect() != null

                        && config.effect().enabled()

                        && config.effect().trigger()
                        == ModelDelayConfig.TransitionEffectTrigger.CHANGE
        ) {

            ModelTransitionEffects.trigger(
                    key.item(),
                    publicProperty,
                    key.ownerId(),
                    transitionIdentity,
                    config.effect()
            );
        }
    }


    private static void dispatchSelectDelayed(
            StateKey key,
            Object previousValue,
            Object newValue,
            ModelDelayConfig.SelectDelayConfig config
    ) {

        if (
                !shouldDispatchTransitionEvent(
                        key
                )
        ) {

            return;
        }


        String publicProperty =
                configPropertyFromStateKey(
                        key.property()
                );


        long transitionIdentity =
                resolveTransitionEffectIdentity(
                        key
                );


        ModelTransitionEvent.fireDelayed(
                key.item(),
                publicProperty,
                key.ownerId(),
                transitionIdentity,
                previousValue,
                newValue,
                config.sound()
        );


        if (
                config.effect() != null

                        && config.effect().enabled()

                        && config.effect().trigger()
                        == ModelDelayConfig.TransitionEffectTrigger.DELAYED
        ) {

            ModelTransitionEffects.trigger(
                    key.item(),
                    publicProperty,
                    key.ownerId(),
                    transitionIdentity,
                    config.effect()
            );
        }
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
            Object previous =
                    CURRENT_VALUE.get(
                            key
                    );


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


            ModelDelayConfig.SelectDelayConfig config =
                    ModelDelayConfig.getSelect(
                            key.item(),
                            configPropertyFromStateKey(
                                    key.property()
                            )
                    );


            if (
                    config != null
            ) {

                dispatchSelectChange(
                        key,
                        previous,
                        target,
                        config
                );


                dispatchSelectDelayed(
                        key,
                        previous,
                        target,
                        config
                );
            }


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
     * DERIVED CONTAINER RESULT INHERITANCE
     * ================================================================
     */
    private static boolean inheritDerivedComponentState(
            StateKey newKey
    ) {

        Long sourceIdentity =
                DERIVED_FROM_IDENTITY.get(
                        newKey.stackIdentity()
                );


        if (sourceIdentity == null) {

            return false;
        }


        StateKey sourceKey =
                new StateKey(
                        newKey.item(),
                        newKey.property(),
                        newKey.ownerId(),
                        sourceIdentity
                );


        if (!CURRENT_VALUE.containsKey(sourceKey)) {

            return false;
        }


        Object exposedValue =
                CURRENT_VALUE.get(
                        sourceKey
                );


        CURRENT_VALUE.put(
                newKey,
                exposedValue
        );


        TARGET_VALUE.put(
                newKey,
                exposedValue
        );


        REMAINING.remove(
                newKey
        );


        TRANSITIONING.put(
                newKey,
                false
        );


        /*
         * The derivation link is only needed to seed this result's first
         * select evaluation. Keeping it forever would retain dead logical
         * identities after repeated smithing/container operations.
         */
        DERIVED_FROM_IDENTITY.remove(
                newKey.stackIdentity()
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

                inherited =
                        inheritDerivedComponentState(
                                key
                        );
            }


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

                dispatchSelectChange(
                        key,
                        currentValue,
                        vanillaValue,
                        config
                );


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

            dispatchSelectChange(
                    key,
                    currentValue,
                    vanillaValue,
                    config
            );


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


        /*
         * ============================================================
         * ACTIVE MENU / CRAFTING LOCATIONS
         * ============================================================
         *
         * Select properties such as trim_material can change while the
         * logical item temporarily leaves the player inventory. A smithing
         * operation is the important example:
         *
         *     inventory chestplate
         *         -> smithing input
         *         -> trimmed smithing result
         *         -> inventory/cursor
         *
         * Older delay behavior relies on the physical identity surviving
         * that lifecycle. Tracking only inventory + cursor creates a hole
         * while the item is inside the menu; when the result returns it can
         * be mistaken for a genuinely new stack and trim_material therefore
         * initializes directly to the new value with no delay.
         *
         * Track ONLY slots whose backing container is not the player's own
         * Inventory. Player-inventory slots are already represented above;
         * adding their menu mirrors as well would create duplicate logical
         * locations for the exact same stack.
         */
        for (
                Slot menuSlot
                : player.containerMenu.slots
        ) {

            if (
                    menuSlot.container
                            == inventory
            ) {

                continue;
            }


            ItemStack menuStack =
                    menuSlot.getItem();


            if (menuStack.isEmpty()) {

                continue;
            }


            locations.put(
                    new LocationKey(
                            player.getId(),
                            menuLocationSlot(
                                    menuSlot.index
                            )
                    ),
                    menuStack
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


            /*
             * Detect a newly-created component-derived result inside the
             * active menu (for example a smithing result). The source input
             * and result coexist, so do not share identities. Instead, keep
             * a one-way link so the result's first select evaluation starts
             * from the source's currently exposed value.
             */
            if (
                    location.slot() >= MENU_SLOT_BASE
                            && location.slot() < MENU_SLOT_BASE + 16384
            ) {

                Long sourceIdentity =
                        null;


                int sourceCount =
                        0;


                for (
                        Map.Entry<LocationKey, TrackedLocation> candidateEntry
                        : newLocations.entrySet()
                ) {

                    LocationKey candidateLocation =
                            candidateEntry.getKey();


                    if (
                            candidateLocation.ownerId()
                                    != location.ownerId()

                                    || candidateLocation.slot() < MENU_SLOT_BASE

                                    || candidateLocation.slot()
                                    >= MENU_SLOT_BASE + 16384
                    ) {

                        continue;
                    }


                    ItemStack candidateStack =
                            candidateEntry.getValue().object();


                    if (
                            candidateStack.getItem()
                                    != currentStack.getItem()

                                    || candidateStack.getCount()
                                    != currentStack.getCount()

                                    || ItemStack.isSameItemSameComponents(
                                    candidateStack,
                                    currentStack
                            )
                    ) {

                        continue;
                    }


                    sourceIdentity =
                            candidateEntry.getValue().identity();


                    sourceCount++;
                }


                if (
                        sourceIdentity != null
                                && sourceCount == 1
                ) {

                    DERIVED_FROM_IDENTITY.put(
                            identity,
                            sourceIdentity
                    );
                }
            }


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


        /*
         * Drop unconsumed derivation links once their derived stack has
         * disappeared from all tracked player/menu locations.
         */
        DERIVED_FROM_IDENTITY.keySet().removeIf(
                derivedIdentity ->
                        newLocations
                                .values()
                                .stream()
                                .noneMatch(
                                        tracked ->
                                                tracked.identity()
                                                        == derivedIdentity
                                )
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


        /*
         * Non-player slots in the active container menu use the same
         * synthetic locations as collectPlayerLocations(). This lets an
         * exact smithing/anvil/result object resolve back to the identity
         * already being tracked for that temporary menu location.
         */
        for (
                Slot menuSlot
                : player.containerMenu.slots
        ) {

            if (
                    menuSlot.container
                            == inventory
            ) {

                continue;
            }


            if (
                    menuSlot.getItem()
                            == stack
            ) {

                return new LocationKey(
                        player.getId(),
                        menuLocationSlot(
                                menuSlot.index
                        )
                );
            }
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
     * RENDER STACK IDENTITY
     * ================================================================
     *
     * Select state has its own physical-stack identity tracker, separate
     * from conditional and range state. The renderer must use this same
     * identity domain for select effects such as charge_type.
     */
    public static long resolveRenderStackIdentity(
            ItemStack stack,
            @Nullable LivingEntity owner
    ) {

        if (
                owner instanceof Player player
        ) {

            return resolvePlayerStackIdentity(
                    stack,
                    player
            );
        }


        Minecraft minecraft =
                Minecraft.getInstance();


        LocalPlayer player =
                minecraft.player;


        if (
                player != null
        ) {

            return resolvePlayerStackIdentity(
                    stack,
                    player
            );
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
            @Nullable LivingEntity owner,
            Identifier itemId,
            String property
    ) {

        /*
         * ============================================================
         * LOCAL TIME
         * ============================================================
         *
         * minecraft:local_time is driven by the client clock, not by a
         * physical ItemStack. Keep one owner/client-level delayed state
         * (stackIdentity=0), then resolve the concrete held stack only
         * when a transition effect is emitted.
         */
        if (
                "select.local_time".equals(
                        configPropertyFromStateKey(
                                property
                        )
                )
        ) {

            Minecraft minecraft =
                    Minecraft.getInstance();


            LocalPlayer localPlayer =
                    minecraft.player;


            int ownerId =
                    owner != null
                            ? owner.getId()
                            : localPlayer != null
                            ? localPlayer.getId()
                            : -1;


            return new StateKey(
                    itemId,
                    property,
                    ownerId,
                    0L
            );
        }


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
        DERIVED_FROM_IDENTITY.clear();
        PLAYER_LOCATIONS.clear();


        NEXT_STACK_ID.set(
                1L
        );


        CLIENT_TICK =
                0L;
    }
}