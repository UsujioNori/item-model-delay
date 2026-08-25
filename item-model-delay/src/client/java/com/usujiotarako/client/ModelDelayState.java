package com.usujiotarako.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
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


        int selectedSlot =
                inventory.getSelectedSlot();


        ItemStack selected =
                inventory.getSelectedItem();


        if (
                !selected.isEmpty()

                        && ItemStack.isSameItemSameComponents(
                        stack,
                        selected
                )
        ) {

            return selectedSlot;
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

            return Inventory.SLOT_OFFHAND;
        }


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
                    !inventoryStack.isEmpty()

                            && ItemStack.isSameItemSameComponents(
                            stack,
                            inventoryStack
                    )
            ) {

                return slot;
            }
        }


        return -1;
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
         * Equivalent selected stack.
         */
        ItemStack selected =
                inventory.getSelectedItem();


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
         * Equivalent offhand.
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
         * Equivalent inventory render copy.
         */
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
                    !inventoryStack.isEmpty()

                            && ItemStack.isSameItemSameComponents(
                            stack,
                            inventoryStack
                    )
            ) {

                return slot;
            }
        }


        return -1;
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
         */
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


            boolean target =
                    TARGET_VALUE.getOrDefault(
                            key,
                            CURRENT_VALUE.getOrDefault(
                                    key,
                                    false
                            )
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
            KEYBIND_RELEASE_ELAPSED.remove(key);


            iterator.remove();
        }
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