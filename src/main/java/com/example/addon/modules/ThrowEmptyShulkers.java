package com.example.addon.modules;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.SlotActionType;


public class ThrowEmptyShulkers extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("delay")
        .description("Delay in ticks between each shulker throw.")
        .defaultValue(5)
        .min(1)
        .max(40)
        .sliderMax(20)
        .build()
    );

    private final Setting<Boolean> hotbarOnly = sgGeneral.add(new BoolSetting.Builder()
        .name("hotbar-only")
        .description("Only throw shulkers from the hotbar (slots 0-8).")
        .defaultValue(false)
        .build()
    );

    private final Setting<Double> throwAngle = sgGeneral.add(new DoubleSetting.Builder()
        .name("throw-angle")
        .description("Rotates your yaw by this amount before throwing. 180 = throw behind you, 0 = throw in your current direction.")
        .defaultValue(0)
        .min(-180)
        .max(180)
        .sliderMin(-180)
        .sliderMax(180)
        .build()
    );

    private enum ThrowState { IDLE, ROTATING, THROWING, RESTORING }

    private ThrowState state = ThrowState.IDLE;
    private int tickTimer = 0;
    private float savedYaw = 0;
    private int pendingSlot = -1;

    public ThrowEmptyShulkers() {
        super(
            Categories.Player,
            "throw-empty-shulkers",
            "Automatically throws empty shulker boxes out of your inventory."
        );
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.interactionManager == null) return;

        switch (state) {
            case ROTATING -> {
                // Tick 1: rotate player, wait for next tick to throw
                mc.player.setYaw(savedYaw + throwAngle.get().floatValue());
                state = ThrowState.THROWING;
                return;
            }
            case THROWING -> {
                // Tick 2: now throw with the rotated yaw
                executeDrop(pendingSlot);
                state = ThrowState.RESTORING;
                return;
            }
            case RESTORING -> {
                // Tick 3: rotate back
                mc.player.setYaw(savedYaw);
                state = ThrowState.IDLE;
                tickTimer = delay.get();
                return;
            }
            case IDLE -> {
                if (tickTimer > 0) {
                    tickTimer--;
                    return;
                }

                int endSlot = hotbarOnly.get() ? 9 : mc.player.getInventory().size();
                for (int i = 0; i < endSlot; i++) {
                    ItemStack stack = mc.player.getInventory().getStack(i);
                    if (isEmptyShulker(stack)) {
                        float offset = throwAngle.get().floatValue();
                        if (offset != 0) {
                            savedYaw = mc.player.getYaw();
                            pendingSlot = i;
                            state = ThrowState.ROTATING;
                        } else {
                            executeDrop(i);
                            tickTimer = delay.get();
                        }
                        return;
                    }
                }
            }
        }
    }

    private boolean isEmptyShulker(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (!(stack.getItem() instanceof BlockItem blockItem)) return false;
        if (!(blockItem.getBlock() instanceof ShulkerBoxBlock)) return false;

        ContainerComponent container = stack.get(DataComponentTypes.CONTAINER);
        if (container == null) return true;

        for (ItemStack stored : container.iterateNonEmpty()) {
            if (!stored.isEmpty()) return false;
        }
        return true;
    }

    private void executeDrop(int invSlot) {
        int networkSlot = (invSlot < 9) ? 36 + invSlot : invSlot;
        mc.interactionManager.clickSlot(
            mc.player.playerScreenHandler.syncId,
            networkSlot,
            1,
            SlotActionType.THROW,
            mc.player
        );
    }
}
