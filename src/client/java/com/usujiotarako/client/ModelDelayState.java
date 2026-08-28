package com.usujiotarako.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.WeakHashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

public final class ModelDelayState {

    /*
     * ================================================================
     * STATE KEY
     * ================================================================
     *
     * Normal stack-sensitive properties continue to use:
     *
     *     ownerId + slot
     *
     *
     * behavior=held instead uses:
     *
     *     ownerId + stackIdentity
     *
     * with slot deliberately set to -1.
     *
     * This prevents different inventory slots from storing independent
     * stale HELD timers for the same physical ItemStack.
     */
    private record StateKey(
            Identifier item,
            String property,
            int ownerId,
            int slot,
            int worldSeed,
            @Nullable ItemDisplayContext displayContext,
            long stackIdentity
    ) {
    }


    /*
     * ================================================================
     * EVOLVING SELECTION
     * ================================================================
     */
    private record EvolvingSelectionKey(
            int ownerId,
            Identifier item,
            int slot
    ) {
    }


    private static final Map<StateKey, Boolean> CURRENT_VALUE =
            new HashMap<>();


    private static final Map<StateKey, Boolean> TARGET_VALUE =
            new HashMap<>();


    private static final Map<StateKey, Integer> REMAINING =
            new HashMap<>();


    private static final Map<StateKey, Boolean> TRANSITIONING =
            new HashMap<>();


    private static final Map<StateKey, Integer> KEYBIND_RELEASE_ELAPSED =
            new HashMap<>();


    /*
     * ================================================================
     * HELD STACK IDENTITIES
     * ================================================================
     *
     * WeakHashMap allows ItemStack objects which no longer exist anywhere
     * else to be garbage-collected instead of retaining every stack object
     * seen for the lifetime of the client session.
     *
     * ItemStack uses object identity for equality in this Minecraft version,
     * so the lifecycle behavior remains the same as the previous
     * IdentityHashMap while allowing dead keys to disappear.
     *
     * If a physical stack moves:
     *
     *     slot 1 -> cursor -> slot 5
     *
     * and Minecraft keeps the same ItemStack object, its HELD lifecycle
     * ID follows it automatically.
     *
     *
     * If Minecraft renders a temporary copy, resolveHeldStackIdentity()
     * attempts to associate that copy with the actual inventory stack
     * before assigning a new identity.
     *
     * This identity system is ONLY used by property behavior=held.
     */
    private static final WeakHashMap<ItemStack, Long> HELD_STACK_IDENTITIES =
            new WeakHashMap<>();


    private static final AtomicLong NEXT_HELD_STACK_ID =
            new AtomicLong(1L);


    /*
     * ================================================================
     * STALE STATE CLEANUP
     * ================================================================
     *
     * Completed state which has not been evaluated for ten minutes is
     * discarded. Active delayed transitions are never removed by this
     * cleanup.
     */
    private static final long STALE_STATE_TICKS =
            20L * 60L * 10L;


    private static final Map<StateKey, Long> LAST_SEEN =
            new HashMap<>();


    private static long CLIENT_TICK =
            0L;


    /*
     * ================================================================
     * EVOLVING TRACKING
     * ================================================================
     */
    private static int LAST_SELECTED_OWNER =
            Integer.MIN_VALUE;


    private static int LAST_SELECTED_SLOT =
            Integer.MIN_VALUE;


    private static @Nullable Identifier LAST_SELECTED_ITEM =
            null;


    private static @Nullable EvolvingSelectionKey ACTIVE_EVOLVING_SELECTION =
            null;


    private ModelDelayState() {
    }


    /*
     * ================================================================
     * PROPERTY TYPES
     * ================================================================
     */
    private static boolean isStackSensitiveProperty(
            String property
    ) {

        return "damaged".equals(property)
                || "broken".equals(property)
                || "bundle/has_selected_item".equals(property)
                || property.startsWith("has_component[")
                || property.startsWith("component[")

                /*
                 * Boolean custom-model-data conditions inspect data belonging
                 * to an individual ItemStack.
                 *
                 * Different physical items must therefore not share their
                 * delayed state.
                 */
                || property.startsWith("custom_model_data[");
    }


    private static boolean isContextSensitiveProperty(
            String property
    ) {

        return "extended_view".equals(property);
    }


    private static boolean isEvolvingProperty(
            String property
    ) {

        return property.startsWith(
                "has_component["
        );
    }


    private static boolean usesElapsedRelease(
            StateKey key
    ) {

        return "keybind_down".equals(
                key.property()
        );
    }


    /*
     * ================================================================
     * DISPLAY CONTEXT
     * ================================================================
     */
    private static boolean isHandContext(
            ItemDisplayContext displayContext
    ) {

        return displayContext
                == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND

                || displayContext
                == ItemDisplayContext.FIRST_PERSON_LEFT_HAND

                || displayContext
                == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND

                || displayContext
                == ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
    }


    /*
     * ================================================================
     * TRANSITION EVENT OWNERSHIP
     * ================================================================
     *
     * keybind_down is a global input property. Minecraft may evaluate the
     * same configured item while it is sitting in the inventory, hotbar,
     * GUI, etc.
     *
     * The delayed MODEL state is still allowed to follow vanilla's global
     * keybind_down behavior, but transition EVENTS (sound now, and other
     * non-render-bound effects later) must only be emitted for an item the
     * player is actually holding.
     *
     * This is enforced here in the transition-state layer so every future
     * listener to ModelTransitionEvent receives correctly-owned events.
     */
    private static boolean shouldDispatchTransitionEvent(
            StateKey key
    ) {

        Minecraft minecraft =
                Minecraft.getInstance();


        LocalPlayer player =
                minecraft.player;


        if (player == null) {

            return false;
        }


        ItemStack mainHand =
                player.getMainHandItem();


        ItemStack offHand =
                player.getOffhandItem();


        /*
         * Stack-sensitive properties must belong to the player's actual
         * selected/offhand stack. Ownerless menu, dropped-item, fixed-item,
         * and other render states must never emit hand transition effects.
         */
        if (isStackSensitiveProperty(key.property())) {

            if (key.ownerId() != player.getId()) {

                return false;
            }


            if (key.slot() >= 0) {

                return key.slot()
                        == player.getInventory().getSelectedSlot()

                        || key.slot()
                        == Inventory.SLOT_OFFHAND;
            }


            if (key.stackIdentity() != 0L) {

                if (
                        !mainHand.isEmpty()

                                && getOrCreateHeldStackIdentity(mainHand)
                                == key.stackIdentity()
                ) {

                    return true;
                }


                return !offHand.isEmpty()

                        && getOrCreateHeldStackIdentity(offHand)
                        == key.stackIdentity();
            }


            return false;
        }


        /*
         * Non-stack-sensitive properties (keybind_down, using_item, etc.)
         * intentionally share logical model state. Their transition effect
         * is still a hand effect, so require the configured item to actually
         * be in one of the local player's hands.
         */
        if (
                !mainHand.isEmpty()

                        && BuiltInRegistries.ITEM.getKey(
                        mainHand.getItem()
                ).equals(
                        key.item()
                )
        ) {

            return true;
        }


        return !offHand.isEmpty()

                && BuiltInRegistries.ITEM.getKey(
                offHand.getItem()
        ).equals(
                key.item()
        );
    }


    /*
     * ================================================================
     * TRANSITION EFFECT RENDER IDENTITY
     * ================================================================
     *
     * Normal stack-sensitive conditional properties such as "damaged"
     * keep their MODEL state by player + inventory slot, so their StateKey
     * intentionally has stackIdentity == 0.
     *
     * Particle attachment is different: ItemModelResolver looks active
     * effects up using the physical held-stack identity. Resolve that
     * identity only when emitting the particle effect.
     */
    private static long resolveTransitionEffectIdentity(
            StateKey key
    ) {

        if (
                key.stackIdentity()
                        != 0L
        ) {

            return key.stackIdentity();
        }


        if (
                key.ownerId()
                        < 0

                        || key.slot()
                        < 0
        ) {

            return key.stackIdentity();
        }


        Minecraft minecraft =
                Minecraft.getInstance();


        LocalPlayer player =
                minecraft.player;


        if (
                player == null

                        || player.getId()
                        != key.ownerId()
        ) {

            return key.stackIdentity();
        }


        ItemStack stack =
                player.getInventory().getItem(
                        key.slot()
                );


        if (
                stack.isEmpty()

                        || !BuiltInRegistries.ITEM.getKey(
                        stack.getItem()
                ).equals(
                        key.item()
                )
        ) {

            return key.stackIdentity();
        }


        return getOrCreateHeldStackIdentity(
                stack
        );
    }


    /*
     * ================================================================
     * VISIBLE CHANGE EVENT
     * ================================================================
     *
     * CHANGE means:
     *
     *     the value actually exposed to the item model changed.
     *
     * It does not matter whether that change was immediate or happened
     * after a delay.
     *
     * First evaluation is deliberately NOT sent through this helper, so
     * loading/rendering an item for the first time does not create a fake
     * transition sound.
     */
    private static void dispatchVisibleChange(
            StateKey key,
            boolean previous,
            boolean current
    ) {

        if (
                previous
                        == current
        ) {

            return;
        }


        if (
                !shouldDispatchTransitionEvent(
                        key
                )
        ) {

            return;
        }


        ModelTransitionEvent.fireChange(
                key.item(),
                key.property(),
                key.ownerId(),
                key.stackIdentity(),
                previous,
                current
        );


        ModelDelayConfig.DelayConfig config =
                ModelDelayConfig.get(
                        key.item(),
                        key.property()
                );


        if (
                config != null

                        && config.effect() != null

                        && config.effect().enabled()

                        && config.effect().trigger()
                        == ModelDelayConfig.TransitionEffectTrigger.CHANGE
        ) {

            ModelTransitionEffects.trigger(
                    key.item(),
                    key.property(),
                    key.ownerId(),
                    resolveTransitionEffectIdentity(
                            key
                    ),
                    config.effect()
            );
        }
    }


    /*
     * ================================================================
     * EVOLVING SELECTION TRACKING
     * ================================================================
     */
    private static void updateEvolvingSelection() {

        Minecraft minecraft =
                Minecraft.getInstance();


        LocalPlayer player =
                minecraft.player;


        if (player == null) {

            LAST_SELECTED_OWNER =
                    Integer.MIN_VALUE;


            LAST_SELECTED_SLOT =
                    Integer.MIN_VALUE;


            LAST_SELECTED_ITEM =
                    null;


            ACTIVE_EVOLVING_SELECTION =
                    null;


            return;
        }


        Inventory inventory =
                player.getInventory();


        int ownerId =
                player.getId();


        int selectedSlot =
                inventory.getSelectedSlot();


        ItemStack selectedStack =
                inventory.getSelectedItem();


        Identifier selectedItemId =
                selectedStack.isEmpty()
                        ? null
                        : BuiltInRegistries.ITEM.getKey(
                        selectedStack.getItem()
                );


        boolean changed =
                ownerId != LAST_SELECTED_OWNER

                        || selectedSlot
                        != LAST_SELECTED_SLOT

                        || !Objects.equals(
                        selectedItemId,
                        LAST_SELECTED_ITEM
                );


        if (!changed) {
            return;
        }


        LAST_SELECTED_OWNER =
                ownerId;


        LAST_SELECTED_SLOT =
                selectedSlot;


        LAST_SELECTED_ITEM =
                selectedItemId;


        ACTIVE_EVOLVING_SELECTION =
                null;


        if (
                selectedItemId == null

                        || !ModelDelayConfig.isEvolving(
                        selectedItemId
                )
        ) {

            return;
        }


        EvolvingSelectionKey selection =
                new EvolvingSelectionKey(
                        ownerId,
                        selectedItemId,
                        selectedSlot
                );


        ACTIVE_EVOLVING_SELECTION =
                selection;


        clearEvolvingStates(
                selection
        );
    }


    private static void clearEvolvingStates(
            EvolvingSelectionKey selection
    ) {

        CURRENT_VALUE.keySet().removeIf(
                key ->
                        matchesEvolvingSelection(
                                key,
                                selection
                        )
        );


        TARGET_VALUE.keySet().removeIf(
                key ->
                        matchesEvolvingSelection(
                                key,
                                selection
                        )
        );


        REMAINING.keySet().removeIf(
                key ->
                        matchesEvolvingSelection(
                                key,
                                selection
                        )
        );


        TRANSITIONING.keySet().removeIf(
                key ->
                        matchesEvolvingSelection(
                                key,
                                selection
                        )
        );


        KEYBIND_RELEASE_ELAPSED.keySet().removeIf(
                key ->
                        matchesEvolvingSelection(
                                key,
                                selection
                        )
        );
    }


    private static boolean matchesEvolvingSelection(
            StateKey key,
            EvolvingSelectionKey selection
    ) {

        return key.ownerId()
                == selection.ownerId()

                && key.slot()
                == selection.slot()

                && key.item().equals(
                selection.item()
        )

                && isEvolvingProperty(
                key.property()
        );
    }


    private static boolean isActiveEvolvingState(
            StateKey key
    ) {

        EvolvingSelectionKey selection =
                ACTIVE_EVOLVING_SELECTION;


        if (selection == null) {
            return false;
        }


        return matchesEvolvingSelection(
                key,
                selection
        );
    }


    private static boolean shouldForceEvolvingBase(
            Identifier itemId,
            StateKey key,
            ItemDisplayContext displayContext
    ) {

        if (!ModelDelayConfig.isEvolving(itemId)) {
            return false;
        }


        if (!isEvolvingProperty(key.property())) {
            return false;
        }


        boolean playerPresentationContext =
                isHandContext(displayContext)

                        || displayContext
                        == ItemDisplayContext.GUI;


        if (!playerPresentationContext) {
            return false;
        }


        return !isActiveEvolvingState(
                key
        );
    }


    private static void resetStateToBase(
            StateKey key
    ) {

        CURRENT_VALUE.put(
                key,
                false
        );


        TARGET_VALUE.put(
                key,
                false
        );


        REMAINING.remove(
                key
        );


        TRANSITIONING.put(
                key,
                false
        );


        KEYBIND_RELEASE_ELAPSED.remove(
                key
        );
    }


    /*
     * ================================================================
     * PLAYER SLOT RESOLUTION
     * ================================================================
     */
    private static int resolvePlayerSlot(
            ItemStack stack,
            Player player,
            ItemDisplayContext displayContext
    ) {

        Inventory inventory =
                player.getInventory();


        /*
         * ============================================================
         * HAND RENDERING
         * ============================================================
         */
        if (isHandContext(displayContext)) {

            ItemStack offhand =
                    inventory.getItem(
                            Inventory.SLOT_OFFHAND
                    );


            /*
             * Exact offhand.
             */
            if (offhand == stack) {

                return Inventory.SLOT_OFFHAND;
            }


            ItemStack selected =
                    inventory.getSelectedItem();


            /*
             * Exact selected stack.
             */
            if (selected == stack) {

                return inventory.getSelectedSlot();
            }


            /*
             * Equivalent offhand render copy.
             */
            if (
                    !offhand.isEmpty()

                            && ItemStack.isSameItemSameComponents(
                            stack,
                            offhand
                    )
            ) {

                return Inventory.SLOT_OFFHAND;
            }


            /*
             * Equivalent selected render copy.
             */
            if (
                    !selected.isEmpty()

                            && ItemStack.isSameItemSameComponents(
                            stack,
                            selected
                    )
            ) {

                return inventory.getSelectedSlot();
            }


            /*
             * Exact previous held stack still stored somewhere else.
             */
            for (
                    int slot = 0;
                    slot < Inventory.INVENTORY_SIZE;
                    slot++
            ) {

                if (inventory.getItem(slot) == stack) {

                    return slot;
                }
            }


            if (
                    inventory.getItem(
                            Inventory.SLOT_OFFHAND
                    ) == stack
            ) {

                return Inventory.SLOT_OFFHAND;
            }


            /*
             * Equivalent old render copy belonging to another slot.
             */
            for (
                    int slot = 0;
                    slot < Inventory.INVENTORY_SIZE;
                    slot++
            ) {

                if (slot == inventory.getSelectedSlot()) {
                    continue;
                }


                ItemStack inventoryStack =
                        inventory.getItem(
                                slot
                        );


                if (
                        !inventoryStack.isEmpty()

                                && ItemStack.isSameItemSameComponents(
                                stack,
                                inventoryStack
                        )
                ) {

                    return slot;
                }
            }


            /*
             * Stale render copy of the selected stack after components
             * changed.
             *
             * damage 10 -> damage 11
             *
             * etc.
             */
            if (
                    !selected.isEmpty()

                            && stack.getItem()
                            == selected.getItem()
            ) {

                return inventory.getSelectedSlot();
            }


            return -1;
        }


        /*
         * ============================================================
         * NORMAL PLAYER INVENTORY
         * ============================================================
         */
        for (
                int slot = 0;
                slot < Inventory.INVENTORY_SIZE;
                slot++
        ) {

            if (inventory.getItem(slot) == stack) {

                return slot;
            }
        }


        if (
                inventory.getItem(
                        Inventory.SLOT_OFFHAND
                ) == stack
        ) {

            return Inventory.SLOT_OFFHAND;
        }


        /*
         * Render copies outside a hand context must only be associated
         * with an inventory slot when that association is unambiguous.
         *
         * Previously we preferred the selected slot, then returned the
         * first equivalent inventory stack. With two otherwise-identical
         * sticks this allowed a menu/anvil render copy of stick B to borrow
         * stick A's StateKey. A completed custom_name=true state could then
         * leak directly into the fresh stick.
         */
        return resolveUniqueEquivalentInventorySlot(
                stack,
                inventory
        );
    }


    /*
     * ================================================================
     * LOCAL GUI INVENTORY SLOT RESOLUTION
     * ================================================================
     */
    private static int resolveLocalInventorySlot(
            ItemStack stack
    ) {

        Minecraft minecraft =
                Minecraft.getInstance();


        LocalPlayer player =
                minecraft.player;


        if (player == null) {
            return -1;
        }


        Inventory inventory =
                player.getInventory();


        /*
         * Exact inventory identity.
         */
        for (
                int slot = 0;
                slot < Inventory.INVENTORY_SIZE;
                slot++
        ) {

            if (inventory.getItem(slot) == stack) {

                return slot;
            }
        }


        ItemStack offhand =
                inventory.getItem(
                        Inventory.SLOT_OFFHAND
                );


        if (offhand == stack) {

            return Inventory.SLOT_OFFHAND;
        }


        /*
         * For GUI copies, an equivalent stack is useful only when exactly
         * one player inventory location matches it. If several identical
         * stacks exist, guessing the selected/first slot merges independent
         * delayed states. Leave it unresolved instead; createKey() will give
         * that menu-stack lifecycle its own identity.
         */
        return resolveUniqueEquivalentInventorySlot(
                stack,
                inventory
        );
    }


    private static int resolveUniqueEquivalentInventorySlot(
            ItemStack stack,
            Inventory inventory
    ) {

        int match = -1;


        for (
                int slot = 0;
                slot < Inventory.INVENTORY_SIZE;
                slot++
        ) {

            ItemStack inventoryStack =
                    inventory.getItem(
                            slot
                    );


            if (
                    inventoryStack.isEmpty()

                            || !ItemStack.isSameItemSameComponents(
                            stack,
                            inventoryStack
                    )
            ) {

                continue;
            }


            if (match != -1) {
                return -1;
            }


            match = slot;
        }


        ItemStack offhand =
                inventory.getItem(
                        Inventory.SLOT_OFFHAND
                );


        if (
                !offhand.isEmpty()

                        && ItemStack.isSameItemSameComponents(
                        stack,
                        offhand
                )
        ) {

            if (match != -1) {
                return -1;
            }


            match = Inventory.SLOT_OFFHAND;
        }


        return match;
    }


    /*
     * ================================================================
     * HELD STACK IDENTITY
     * ================================================================
     */

    private static long getOrCreateHeldStackIdentity(
            ItemStack stack
    ) {

        Long existing =
                HELD_STACK_IDENTITIES.get(
                        stack
                );


        if (existing != null) {

            return existing;
        }


        long created =
                NEXT_HELD_STACK_ID.getAndIncrement();


        HELD_STACK_IDENTITIES.put(
                stack,
                created
        );


        return created;
    }


    /*
     * Resolve a HELD property's stable physical-stack identity.
     *
     * We first check whether we've already seen the exact rendered object.
     *
     * If not, attempt to associate render copies with the actual stack in
     * the player's inventory.
     */
    private static long resolveHeldStackIdentity(
            ItemStack stack,
            @Nullable LivingEntity owner,
            ItemDisplayContext displayContext
    ) {

        /*
         * Exact previously-known object.
         */
        Long existing =
                HELD_STACK_IDENTITIES.get(
                        stack
                );


        if (existing != null) {

            return existing;
        }


        /*
         * ============================================================
         * PLAYER-OWNED RENDER
         * ============================================================
         */
        if (owner instanceof Player player) {

            int slot =
                    resolvePlayerSlot(
                            stack,
                            player,
                            displayContext
                    );


            if (slot != -1) {

                ItemStack realStack =
                        player.getInventory().getItem(
                                slot
                        );


                if (!realStack.isEmpty()) {

                    long id =
                            getOrCreateHeldStackIdentity(
                                    realStack
                            );


                    /*
                     * Remember that this render copy represents the same
                     * physical stack.
                     */
                    HELD_STACK_IDENTITIES.put(
                            stack,
                            id
                    );


                    return id;
                }
            }


            /*
             * Unresolved player render.
             */
            return getOrCreateHeldStackIdentity(
                    stack
            );
        }


        /*
         * ============================================================
         * OWNERLESS GUI / HOTBAR RENDER
         * ============================================================
         */
        if (
                displayContext
                        == ItemDisplayContext.GUI
        ) {

            Minecraft minecraft =
                    Minecraft.getInstance();


            LocalPlayer player =
                    minecraft.player;


            if (player != null) {

                int slot =
                        resolveLocalInventorySlot(
                                stack
                        );


                if (slot != -1) {

                    ItemStack realStack =
                            player.getInventory().getItem(
                                    slot
                            );


                    if (!realStack.isEmpty()) {

                        long id =
                                getOrCreateHeldStackIdentity(
                                        realStack
                                );


                        HELD_STACK_IDENTITIES.put(
                                stack,
                                id
                        );


                        return id;
                    }
                }
            }
        }


        /*
         * Cursor / dropped / unidentified render.
         *
         * If this exact object was previously in inventory, the earlier
         * check at the top already found its existing ID.
         *
         * Otherwise, this really is a new lifecycle.
         */
        return getOrCreateHeldStackIdentity(
                stack
        );
    }

    /*
     * ================================================================
     * RENDER STACK IDENTITY
     * ================================================================
     *
     * Exposes the existing HELD physical-stack identity resolver to the
     * transition-effect renderer.
     *
     * This deliberately does NOT implement another identity system.
     */
    public static long resolveRenderStackIdentity(
            ItemStack stack,
            @Nullable LivingEntity owner,
            ItemDisplayContext displayContext
    ) {

        return resolveHeldStackIdentity(
                stack,
                owner,
                displayContext
        );
    }


    /*
     * ================================================================
     * ACTIVELY HELD
     * ================================================================
     *
     * HELD behavior is based on the CURRENT location of the physical
     * stack, not the slot stored in StateKey.
     */
    private static boolean isActivelyHeld(
            ItemStack stack,
            @Nullable LivingEntity owner,
            ItemDisplayContext displayContext
    ) {

        /*
         * Player-owned render.
         */
        if (owner instanceof Player player) {

            int slot =
                    resolvePlayerSlot(
                            stack,
                            player,
                            displayContext
                    );


            return slot
                    == player.getInventory().getSelectedSlot()

                    || slot
                    == Inventory.SLOT_OFFHAND;
        }


        /*
         * Ownerless GUI/hotbar render.
         */
        if (
                displayContext
                        == ItemDisplayContext.GUI
        ) {

            Minecraft minecraft =
                    Minecraft.getInstance();


            LocalPlayer player =
                    minecraft.player;


            if (player == null) {
                return false;
            }


            int slot =
                    resolveLocalInventorySlot(
                            stack
                    );


            return slot
                    == player.getInventory().getSelectedSlot()

                    || slot
                    == Inventory.SLOT_OFFHAND;
        }


        /*
         * A non-player living entity evaluated in a hand context is also
         * genuinely holding the stack.
         */
        if (
                owner != null

                        && isHandContext(
                        displayContext
                )
        ) {

            return true;
        }


        return false;
    }


    private static boolean shouldInitializeHeldFromBase(
            ItemStack stack,
            @Nullable LivingEntity owner,
            ItemDisplayContext displayContext,
            ModelDelayConfig.DelayConfig config
    ) {

        return config.behavior()
                == ModelDelayConfig.PropertyBehavior.HELD

                && isActivelyHeld(
                stack,
                owner,
                displayContext
        );
    }


    /*
     * ================================================================
     * STATE KEY CREATION
     * ================================================================
     */
    private static StateKey createKey(
            Identifier itemId,
            ItemStack stack,
            @Nullable LivingEntity owner,
            String property,
            int seed,
            ItemDisplayContext displayContext,
            ModelDelayConfig.DelayConfig config
    ) {

        /*
         * ============================================================
         * HELD PROPERTY
         * ============================================================
         *
         * HELD properties deliberately DO NOT use the current inventory
         * slot as state identity.
         *
         * Their state follows the physical/lifecycle ItemStack instead.
         */
        if (
                config.behavior()
                        == ModelDelayConfig.PropertyBehavior.HELD
        ) {

            long stackIdentity =
                    resolveHeldStackIdentity(
                            stack,
                            owner,
                            displayContext
                    );


            int ownerId =
                    -1;


            if (owner instanceof Player player) {

                ownerId =
                        player.getId();

            } else {

                Minecraft minecraft =
                        Minecraft.getInstance();


                LocalPlayer player =
                        minecraft.player;


                if (player != null) {

                    ownerId =
                            player.getId();
                }
            }


            return new StateKey(
                    itemId,
                    property,
                    ownerId,

                    /*
                     * HELD state intentionally does not belong to an
                     * inventory slot.
                     */
                    -1,

                    -1,
                    null,
                    stackIdentity
            );
        }


        /*
         * ============================================================
         * USING_ITEM
         * ============================================================
         *
         * using_item is entity/hand state, not a global item state.
         *
         * Vanilla can evaluate the same item model in several places during
         * the same frame.  The held render has an owning player and may
         * report using_item=true while GUI/inventory renders of that item
         * report false.  Sharing one StateKey between those evaluations lets
         * the ownerless false evaluation cancel the hand's pending delay.
         *
         * Keep the actual hand state on player + hand slot.  Non-hand /
         * ownerless evaluations get their own display-context state and
         * therefore cannot cancel the transition which is driving the item in
         * the player's hand.
         */
        if ("using_item".equals(property)) {

            /*
             * A hand render is authoritative even when Minecraft does not
             * provide the property owner on that particular item-model
             * evaluation. First-person rendering can otherwise alternate
             * between a player-owned key and an ownerless display-context
             * key, leaving the visible hand stuck on the old value while a
             * different key completes the delay and fires the effects.
             *
             * Resolve ownerless hand evaluations back to the local player so
             * every render of the player's selected/offhand stack consumes
             * the same delayed using_item state.
             */
            Player statePlayer =
                    owner instanceof Player player
                            ? player
                            : Minecraft.getInstance().player;


            if (statePlayer != null) {

                /*
                 * Hand renders and the matching GUI/hotbar render of the
                 * selected/offhand stack must share ONE using_item state.
                 *
                 * Keeping GUI on an ownerless display-context key creates a
                 * second independent timer. Both timers can then complete at
                 * slightly different moments and each dispatch CHANGE, which
                 * produces an early effect followed by duplicate effects on
                 * release.
                 */
                int slot = -1;

                if (isHandContext(displayContext)) {

                    slot =
                            resolvePlayerSlot(
                                    stack,
                                    statePlayer,
                                    displayContext
                            );

                } else if (
                        displayContext
                                == ItemDisplayContext.GUI
                ) {

                    slot =
                            resolveLocalInventorySlot(
                                    stack
                            );
                }


                if (
                        slot
                                == statePlayer.getInventory().getSelectedSlot()

                                || slot
                                == Inventory.SLOT_OFFHAND
                ) {

                    return new StateKey(
                            itemId,
                            property,
                            statePlayer.getId(),
                            slot,
                            -1,
                            null,
                            0L
                    );
                }
            }


            /*
             * Non-held inventory/menu/ground renders keep an isolated
             * context state. They must not be allowed to start/cancel the
             * local player's authoritative using_item transition.
             */
            return new StateKey(
                    itemId,
                    property,
                    -1,
                    -1,
                    -1,
                    displayContext,
                    0L
            );
        }


        /*
         * ============================================================
         * CONTEXT-SENSITIVE NORMAL PROPERTY
         * ============================================================
         */
        if (isContextSensitiveProperty(property)) {

            return new StateKey(
                    itemId,
                    property,
                    -1,
                    -1,
                    -1,
                    displayContext,
                    0L
            );
        }


        /*
         * ============================================================
         * NON-STACK-SENSITIVE NORMAL PROPERTY
         * ============================================================
         */
        if (!isStackSensitiveProperty(property)) {

            return new StateKey(
                    itemId,
                    property,
                    -1,
                    -1,
                    -1,
                    null,
                    0L
            );
        }


        /*
         * ============================================================
         * PLAYER-OWNED STACK-SENSITIVE NORMAL PROPERTY
         * ============================================================
         */
        if (owner instanceof Player player) {

            int slot =
                    resolvePlayerSlot(
                            stack,
                            player,
                            displayContext
                    );


            return new StateKey(
                    itemId,
                    property,
                    player.getId(),
                    slot,
                    -1,
                    null,
                    0L
            );
        }


        /*
         * ============================================================
         * OWNERLESS GUI / HOTBAR NORMAL PROPERTY
         * ============================================================
         */
        if (
                displayContext
                        == ItemDisplayContext.GUI
        ) {

            Minecraft minecraft =
                    Minecraft.getInstance();


            LocalPlayer player =
                    minecraft.player;


            if (player != null) {

                int slot =
                        resolveLocalInventorySlot(
                                stack
                        );


                if (slot != -1) {

                    return new StateKey(
                            itemId,
                            property,
                            player.getId(),
                            slot,
                            -1,
                            null,
                            0L
                    );
                }
            }
        }


        /*
         * ============================================================
         * DROPPED / FIXED ITEM
         * ============================================================
         */
        if (
                displayContext
                        == ItemDisplayContext.GROUND

                        || displayContext
                        == ItemDisplayContext.FIXED
        ) {

            return new StateKey(
                    itemId,
                    property,
                    -1,
                    -1,
                    seed,
                    null,
                    0L
            );
        }


        /*
         * Truly ownerless normal fallback.
         *
         * Stack-sensitive properties MUST NOT collapse every unresolved GUI
         * copy of the same item/property onto one shared StateKey. Doing so
         * lets one physical stack's completed state leak into another (most
         * visibly when multiple sticks pass through an anvil).
         *
         * Give unresolved stack-sensitive lifecycles their own identity. If
         * Minecraft keeps the same menu ItemStack object, that identity also
         * stays stable long enough for its delay to advance normally.
         */
        long fallbackStackIdentity =
                isStackSensitiveProperty(property)
                        ? getOrCreateHeldStackIdentity(stack)
                        : 0L;


        return new StateKey(
                itemId,
                property,
                -1,
                -1,
                -1,
                null,
                fallbackStackIdentity
        );
    }


    /*
     * ================================================================
     * CLIENT TICK
     * ================================================================
     */
    public static void tick() {

        CLIENT_TICK++;


        updateEvolvingSelection();


        /*
         * keybind_down elapsed RELEASE counters.
         */
        for (
                Map.Entry<StateKey, Integer> entry
                : KEYBIND_RELEASE_ELAPSED.entrySet()
        ) {

            int elapsed =
                    entry.getValue();


            if (elapsed < Integer.MAX_VALUE) {

                entry.setValue(
                        elapsed + 1
                );
            }
        }


        /*
         * Normal pending delay countdowns.
         */
        Iterator<Map.Entry<StateKey, Integer>> iterator =
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


            boolean previous =
                    CURRENT_VALUE.getOrDefault(
                            key,
                            false
                    );


            boolean target =
                    TARGET_VALUE.getOrDefault(
                            key,
                            previous
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


            /*
             * ================================================================
             * TRANSITION EFFECT
             * ================================================================
             *
             * Only fire when the exposed delayed value genuinely changed.
             *
             * A cancelled transition never reaches this point.
             */
            if (
                    previous
                            != target
            ) {

                /*
                 * ============================================================
                 * SHARED DELAYED TRANSITION EVENT
                 * ============================================================
                 *
                 * For now this dispatches to the new sound system.
                 *
                 * The existing particle trigger below is deliberately kept
                 * intact until both systems are fully migrated to the shared
                 * transition event.
                 */
                /*
                 * Every exposed value change emits CHANGE, including a
                 * change which happened at the end of a delay.
                 */
                dispatchVisibleChange(
                        key,
                        previous,
                        target
                );


                /*
                 * DELAYED is the return-only event.
                 *
                 * CHANGE fires for every exposed model-value change.
                 * DELAYED fires only when a delayed conditional transition
                 * returns from TRUE to FALSE (the original/fallback model).
                 *
                 * This intentionally does NOT fire on FALSE -> TRUE even
                 * when that activation itself was delayed.
                 */
                if (
                        previous
                                && !target

                                && shouldDispatchTransitionEvent(
                                key
                        )
                ) {

                    ModelTransitionEvent.fireDelayed(
                            key.item(),
                            key.property(),
                            key.ownerId(),
                            key.stackIdentity(),
                            true,
                            false
                    );
                }


                /*
                 * ============================================================
                 * EXISTING DELAYED PARTICLE EFFECT
                 * ============================================================
                 */
                ModelDelayConfig.DelayConfig config =
                        ModelDelayConfig.get(
                                key.item(),
                                key.property()
                        );


                if (
                        previous
                                && !target

                                && config != null

                                && shouldDispatchTransitionEvent(
                                key
                        )

                                && config.effect().enabled()

                                && config.effect().trigger()
                                == ModelDelayConfig.TransitionEffectTrigger.DELAYED
                ) {

                    ModelTransitionEffects.trigger(
                            key.item(),
                            key.property(),
                            key.ownerId(),
                            resolveTransitionEffectIdentity(
                                    key
                            ),
                            config.effect()
                    );
                }
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
            KEYBIND_RELEASE_ELAPSED.remove(key);


            iterator.remove();
        }
    }


    /*
     * ================================================================
     * COMPONENT FIRST-HELD BASELINE
     * ================================================================
     *
     * Anvils and other menus can replace an ItemStack object when a
     * component such as custom_name changes. When that newly-created stack
     * first reaches the selected hand, there may be no StateKey for its new
     * slot lifecycle yet. Initializing directly from vanilla=true would skip
     * HOLD/BOTH entirely and make the alternate model appear immediately.
     *
     * For a newly-observed, actively-held component condition whose true
     * direction is configured to be delayed, establish the normal false/base
     * value first and let the ordinary HOLD/BOTH code start the timer below.
     */
    private static boolean shouldInitializeHeldComponentFromBase(
            StateKey key,
            ItemStack stack,
            @Nullable LivingEntity owner,
            ItemDisplayContext displayContext,
            boolean delayInput,
            ModelDelayConfig.DelayConfig config
    ) {

        if (!delayInput) {
            return false;
        }


        if (
                config.mode() != ModelDelayConfig.Mode.HOLD

                        && config.mode() != ModelDelayConfig.Mode.BOTH
        ) {

            return false;
        }


        if (
                !key.property().startsWith(
                        "has_component["
                )

                        && !key.property().startsWith(
                        "component["
                )
        ) {

            return false;
        }


        return isActivelyHeld(
                stack,
                owner,
                displayContext
        );
    }


    /*
     * ================================================================
     * ANVIL RESULT PREVIEW
     * ================================================================
     *
     * The anvil constructs a temporary result ItemStack while its text
     * field is being edited. Component conditions (especially
     * has_component[custom_name]) can therefore become true on that
     * preview before the player has actually taken/committed the result.
     *
     * That temporary result must not establish or advance IMD's persistent
     * delayed state. The real stack will be evaluated normally after it is
     * taken from the result slot.
     */
    private static boolean isAnvilResultPreview(
            ItemStack stack,
            @Nullable LivingEntity owner,
            String property,
            ItemDisplayContext displayContext
    ) {

        if (owner != null) {
            return false;
        }


        if (displayContext != ItemDisplayContext.GUI) {
            return false;
        }


        if (
                !property.startsWith("has_component[")

                        && !property.startsWith("component[")
        ) {

            return false;
        }


        Minecraft minecraft =
                Minecraft.getInstance();


        LocalPlayer player =
                minecraft.player;


        if (
                player == null

                        || !(player.containerMenu instanceof AnvilMenu anvilMenu)
        ) {

            return false;
        }


        ItemStack result =
                anvilMenu.getSlot(
                        AnvilMenu.RESULT_SLOT
                ).getItem();


        /*
         * The item-model renderer is allowed to receive a copy of the menu
         * result stack rather than the exact ItemStack object stored in the
         * result slot. Comparing with `result == stack` therefore misses the
         * preview in some GUI render paths.
         *
         * Match the copied preview by item + components instead. Count is
         * checked as well so this remains as narrow as possible. The result
         * stack is temporary and is deliberately forced to the false/base
         * branch until the player actually takes it from the anvil.
         */
        return !result.isEmpty()
                && result.getCount() == stack.getCount()
                && ItemStack.isSameItemSameComponents(
                result,
                stack
        );
    }


    /*
     * ================================================================
     * USING_ITEM AUTHORITATIVE INPUT
     * ================================================================
     */
    private static boolean resolveUsingItemInput(
            ItemStack stack,
            @Nullable LivingEntity owner,
            ItemDisplayContext displayContext,
            StateKey key,
            boolean vanillaValue
    ) {

        Player player =
                owner instanceof Player ownerPlayer
                        ? ownerPlayer
                        : Minecraft.getInstance().player;


        if (player == null) {
            return vanillaValue;
        }


        if (!player.isUsingItem()) {
            return false;
        }


        int usedSlot =
                player.getUsedItemHand() == InteractionHand.OFF_HAND
                        ? Inventory.SLOT_OFFHAND
                        : player.getInventory().getSelectedSlot();


        /*
         * createKey() resolves hand-render copies onto player + slot.
         * Prefer that authoritative identity so two identical items in
         * different hands cannot both become using_item=true.
         */
        if (
                key.ownerId() == player.getId()

                        && key.slot() == usedSlot
        ) {

            return true;
        }


        /*
         * Some player-owned model evaluations can still arrive before a
         * concrete slot is available. Preserve vanilla's exact true result
         * in that case, and accept an equivalent hand render copy only for
         * an actual hand context.
         */
        if (vanillaValue) {
            return true;
        }


        if (!isHandContext(displayContext)) {
            return false;
        }


        ItemStack useStack =
                player.getUseItem();


        return !useStack.isEmpty()
                && ItemStack.isSameItemSameComponents(
                stack,
                useStack
        );
    }


    /*
     * ================================================================
     * GET
     * ================================================================
     */
    public static boolean get(
            ItemStack stack,
            @Nullable LivingEntity owner,
            String property,
            boolean vanillaValue,
            ModelDelayConfig.DelayConfig config,
            int seed,
            ItemDisplayContext displayContext
    ) {

        updateEvolvingSelection();


        Identifier itemId =
                BuiltInRegistries.ITEM.getKey(
                        stack.getItem()
                );


        /*
         * Do not let the anvil's temporary result preview create a fresh
         * component state or start a delay. For the configured base/false
         * branch, this keeps an unnamed stick visually unchanged until the
         * renamed result is actually taken.
         */
        if (
                isAnvilResultPreview(
                        stack,
                        owner,
                        property,
                        displayContext
                )
        ) {

            return false;
        }


        StateKey key =
                createKey(
                        itemId,
                        stack,
                        owner,
                        property,
                        seed,
                        displayContext,
                        config
                );


        LAST_SEEN.put(
                key,
                CLIENT_TICK
        );


        /*
         * ============================================================
         * DELAY INPUT
         * ============================================================
         *
         * NORMAL:
         *
         *     actual vanilla value.
         *
         *
         * HELD:
         *
         *     actively held
         *         -> actual vanilla value
         *
         *     cursor / backpack / inactive hotbar
         *         -> false
         *
         *
         * HOLD / RELEASE / BOTH remain responsible for how transitions
         * between these values are delayed.
         */
        boolean delayInput =
                vanillaValue;


        /*
         * minecraft:using_item is implemented by vanilla with an ItemStack
         * object-identity check:
         *
         *     owner.getUseItem() == itemStack
         *
         * Item-model rendering is allowed to evaluate an equivalent render
         * copy rather than that exact object. Once IMD delays the property,
         * those copy evaluations can report false even though the player is
         * still actively using the real selected/offhand stack.
         *
         * For configured using_item only, resolve the input from the
         * player's authoritative active hand/slot instead. Vanilla remains
         * completely untouched when no .mdprop exists because this method is
         * never entered without a config.
         */
        if ("using_item".equals(property)) {

            delayInput = resolveUsingItemInput(
                    stack,
                    owner,
                    displayContext,
                    key,
                    vanillaValue
            );
        }


        if (
                config.behavior()
                        == ModelDelayConfig.PropertyBehavior.HELD

                        && !isActivelyHeld(
                        stack,
                        owner,
                        displayContext
                )
        ) {

            delayInput =
                    false;
        }


        /*
         * Existing item-level behavior=evolving.
         *
         * HELD state uses slot=-1 intentionally, so it will not accidentally
         * participate in the slot-based evolving reset unless the property
         * itself belongs to the existing evolving system.
         */
        if (
                config.behavior()
                        != ModelDelayConfig.PropertyBehavior.HELD

                        && shouldForceEvolvingBase(
                        itemId,
                        key,
                        displayContext
                )
        ) {

            resetStateToBase(
                    key
            );


            return false;
        }


        /*
         * keybind elapsed-release tracking.
         */
        if (
                config.mode()
                        != ModelDelayConfig.Mode.RELEASE

                        || !usesElapsedRelease(
                        key
                )
        ) {

            KEYBIND_RELEASE_ELAPSED.remove(
                    key
            );
        }


        /*
         * ============================================================
         * FIRST EVALUATION
         * ============================================================
         */
        if (!CURRENT_VALUE.containsKey(key)) {

            boolean initializeFromBase =
                    (
                            config.behavior()
                                    != ModelDelayConfig.PropertyBehavior.HELD

                                    && isActiveEvolvingState(
                                    key
                            )
                    )

                            || shouldInitializeHeldFromBase(
                            stack,
                            owner,
                            displayContext,
                            config
                    )

                            || shouldInitializeHeldComponentFromBase(
                            key,
                            stack,
                            owner,
                            displayContext,
                            delayInput,
                            config
                    );


            if (initializeFromBase) {

                CURRENT_VALUE.put(
                        key,
                        false
                );


                TARGET_VALUE.put(
                        key,
                        false
                );


                TRANSITIONING.put(
                        key,
                        false
                );

            } else {

                CURRENT_VALUE.put(
                        key,
                        delayInput
                );


                TARGET_VALUE.put(
                        key,
                        delayInput
                );


                TRANSITIONING.put(
                        key,
                        false
                );


                if (
                        config.mode()
                                == ModelDelayConfig.Mode.RELEASE

                                && usesElapsedRelease(
                                key
                        )

                                && delayInput
                ) {

                    KEYBIND_RELEASE_ELAPSED.put(
                            key,
                            0
                    );
                }


                return delayInput;
            }
        }


        boolean current =
                CURRENT_VALUE.get(
                        key
                );


        boolean transitioning =
                TRANSITIONING.getOrDefault(
                        key,
                        false
                );


        boolean target =
                TARGET_VALUE.getOrDefault(
                        key,
                        current
                );


        /*
         * ============================================================
         * HOLD
         * ============================================================
         *
         * FALSE -> TRUE delayed
         * TRUE  -> FALSE immediate
         */
        if (
                config.mode()
                        == ModelDelayConfig.Mode.HOLD
        ) {

            if (!delayInput) {

                /*
                 * HOLD mode releases immediately.
                 */
                dispatchVisibleChange(
                        key,
                        current,
                        false
                );


                CURRENT_VALUE.put(
                        key,
                        false
                );


                TARGET_VALUE.put(
                        key,
                        false
                );


                REMAINING.remove(
                        key
                );


                TRANSITIONING.put(
                        key,
                        false
                );


                return false;
            }


            if (current) {

                REMAINING.remove(
                        key
                );


                TARGET_VALUE.put(
                        key,
                        true
                );


                TRANSITIONING.put(
                        key,
                        false
                );


                return true;
            }


            if (!transitioning) {

                TARGET_VALUE.put(
                        key,
                        true
                );


                TRANSITIONING.put(
                        key,
                        true
                );


                if (config.delay() <= 0) {

                    dispatchVisibleChange(
                            key,
                            current,
                            true
                    );


                    CURRENT_VALUE.put(
                            key,
                            true
                    );


                    TRANSITIONING.put(
                            key,
                            false
                    );


                    return true;
                }


                REMAINING.put(
                        key,
                        config.delay()
                );
            }


            return CURRENT_VALUE.get(
                    key
            );
        }


        /*
         * ============================================================
         * RELEASE
         * ============================================================
         *
         * FALSE -> TRUE immediate
         * TRUE  -> FALSE delayed
         */
        if (
                config.mode()
                        == ModelDelayConfig.Mode.RELEASE
        ) {

            boolean elapsedRelease =
                    usesElapsedRelease(
                            key
                    );


            /*
             * Property true.
             */
            if (delayInput) {

                /*
                 * ============================================================
                 * CANCEL PREVIOUS DELAYED RELEASE EFFECT
                 * ============================================================
                 *
                 * A new false -> true activation supersedes any still-running
                 * DELAYED particle effect from the previous release.
                 *
                 * CHANGE effects are intentionally left alone.
                 */
                if (
                        !current

                                && config.effect().enabled()

                                && config.effect().trigger()
                                == ModelDelayConfig.TransitionEffectTrigger.DELAYED
                ) {

                    ModelTransitionEffects.cancel(
                            key.item(),
                            key.property(),
                            key.ownerId(),
                            key.stackIdentity()
                    );
                }


                dispatchVisibleChange(
                        key,
                        current,
                        true
                );


                CURRENT_VALUE.put(
                        key,
                        true
                );


                TARGET_VALUE.put(
                        key,
                        true
                );


                REMAINING.remove(
                        key
                );


                TRANSITIONING.put(
                        key,
                        false
                );


                if (elapsedRelease) {

                    KEYBIND_RELEASE_ELAPSED.putIfAbsent(
                            key,
                            0
                    );
                }


                return true;
            }


            /*
             * --------------------------------------------------------
             * KEYBIND ELAPSED RELEASE
             * --------------------------------------------------------
             */
            if (elapsedRelease) {

                int elapsed =
                        KEYBIND_RELEASE_ELAPSED.getOrDefault(
                                key,
                                0
                        );


                KEYBIND_RELEASE_ELAPSED.remove(
                        key
                );


                if (!current) {

                    REMAINING.remove(
                            key
                    );


                    TARGET_VALUE.put(
                            key,
                            false
                    );


                    TRANSITIONING.put(
                            key,
                            false
                    );


                    return false;
                }


                int remainingDelay =
                        Math.max(
                                0,
                                config.delay()
                                        - elapsed
                        );


                if (remainingDelay <= 0) {

                    /*
                     * ========================================================
                     * COMPLETE ELAPSED RELEASE
                     * ========================================================
                     *
                     * keybind_down can consume its configured RELEASE delay
                     * while the key is being held. In that case the transition
                     * completes here instead of through the REMAINING timer.
                     */
                    boolean previous =
                            current;


                    CURRENT_VALUE.put(
                            key,
                            false
                    );


                    TARGET_VALUE.put(
                            key,
                            false
                    );


                    REMAINING.remove(
                            key
                    );


                    TRANSITIONING.put(
                            key,
                            false
                    );


                    /*
                     * ========================================================
                     * SHARED DELAYED TRANSITION EVENT
                     * ========================================================
                     *
                     * This is the special long-hold path which bypasses
                     * tick(), so sound must be dispatched here too.
                     */
                    if (previous) {

                        /*
                         * The exposed model value changed TRUE -> FALSE here,
                         * even though this special elapsed-release path bypasses
                         * the normal tick countdown.
                         */
                        dispatchVisibleChange(
                                key,
                                true,
                                false
                        );


                        if (
                                shouldDispatchTransitionEvent(
                                        key
                                )
                        ) {

                            ModelTransitionEvent.fireDelayed(
                                    key.item(),
                                    key.property(),
                                    key.ownerId(),
                                    key.stackIdentity(),
                                    true,
                                    false
                            );
                        }
                    }


                    /*
                     * ========================================================
                     * EXISTING DELAYED PARTICLE EFFECT
                     * ========================================================
                     */
                    if (
                            previous

                                    && shouldDispatchTransitionEvent(
                                    key
                            )

                                    && config.effect().enabled()

                                    && config.effect().trigger()
                                    == ModelDelayConfig.TransitionEffectTrigger.DELAYED
                    ) {

                        ModelTransitionEffects.trigger(
                                key.item(),
                                key.property(),
                                key.ownerId(),
                                resolveTransitionEffectIdentity(
                                        key
                                ),
                                config.effect()
                        );
                    }


                    return false;
                }


                if (!transitioning) {

                    TARGET_VALUE.put(
                            key,
                            false
                    );


                    TRANSITIONING.put(
                            key,
                            true
                    );


                    REMAINING.put(
                            key,
                            remainingDelay
                    );
                }


                return CURRENT_VALUE.get(
                        key
                );
            }


            /*
             * --------------------------------------------------------
             * NORMAL RELEASE
             * --------------------------------------------------------
             */
            if (!current) {

                REMAINING.remove(
                        key
                );


                TARGET_VALUE.put(
                        key,
                        false
                );


                TRANSITIONING.put(
                        key,
                        false
                );


                return false;
            }


            if (!transitioning) {

                TARGET_VALUE.put(
                        key,
                        false
                );


                TRANSITIONING.put(
                        key,
                        true
                );


                if (config.delay() <= 0) {

                    dispatchVisibleChange(
                            key,
                            current,
                            false
                    );


                    CURRENT_VALUE.put(
                            key,
                            false
                    );


                    TRANSITIONING.put(
                            key,
                            false
                    );


                    return false;
                }


                REMAINING.put(
                        key,
                        config.delay()
                );
            }


            return CURRENT_VALUE.get(
                    key
            );
        }


        /*
         * ============================================================
         * BOTH
         * ============================================================
         *
         * FALSE -> TRUE delayed
         * TRUE  -> FALSE delayed
         */
        if (
                config.mode()
                        == ModelDelayConfig.Mode.BOTH
        ) {

            if (transitioning) {

                /*
                 * Input returned to the currently displayed value.
                 *
                 * Cancel the pending transition.
                 */
                if (delayInput == current) {

                    REMAINING.remove(
                            key
                    );


                    TARGET_VALUE.put(
                            key,
                            current
                    );


                    TRANSITIONING.put(
                            key,
                            false
                    );


                    return current;
                }


                /*
                 * Input reversed direction while another transition was
                 * pending.
                 */
                if (delayInput != target) {

                    TARGET_VALUE.put(
                            key,
                            delayInput
                    );


                    if (config.delay() <= 0) {

                        dispatchVisibleChange(
                                key,
                                current,
                                delayInput
                        );


                        CURRENT_VALUE.put(
                                key,
                                delayInput
                        );


                        TARGET_VALUE.put(
                                key,
                                delayInput
                        );


                        TRANSITIONING.put(
                                key,
                                false
                        );


                        REMAINING.remove(
                                key
                        );


                        return delayInput;
                    }


                    REMAINING.put(
                            key,
                            config.delay()
                    );


                    return current;
                }


                return current;
            }


            /*
             * Already displaying the requested state.
             */
            if (delayInput == current) {

                TARGET_VALUE.put(
                        key,
                        current
                );


                return current;
            }


            /*
             * Start a new transition.
             */
            TARGET_VALUE.put(
                    key,
                    delayInput
            );


            if (config.delay() <= 0) {

                dispatchVisibleChange(
                        key,
                        current,
                        delayInput
                );


                CURRENT_VALUE.put(
                        key,
                        delayInput
                );


                TARGET_VALUE.put(
                        key,
                        delayInput
                );


                TRANSITIONING.put(
                        key,
                        false
                );


                REMAINING.remove(
                        key
                );


                return delayInput;
            }


            TRANSITIONING.put(
                    key,
                    true
            );


            REMAINING.put(
                    key,
                    config.delay()
            );


            return current;
        }


        return delayInput;
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
        KEYBIND_RELEASE_ELAPSED.clear();
        LAST_SEEN.clear();


        HELD_STACK_IDENTITIES.clear();


        NEXT_HELD_STACK_ID.set(
                1L
        );


        CLIENT_TICK =
                0L;


        LAST_SELECTED_OWNER =
                Integer.MIN_VALUE;


        LAST_SELECTED_SLOT =
                Integer.MIN_VALUE;


        LAST_SELECTED_ITEM =
                null;


        ACTIVE_EVOLVING_SELECTION =
                null;
    }
}