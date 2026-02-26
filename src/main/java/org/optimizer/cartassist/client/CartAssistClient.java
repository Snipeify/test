package org.optimizer.cartassist.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.item.BowItem;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.item.RangedWeaponItem;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class CartAssistClient implements ClientModInitializer {
    private static final double MAX_PLACE_RANGE = 4.5D;
    private static final int MAX_SIM_TICKS = 250;

    private final Set<UUID> trackedArrows = new HashSet<>();

    private BlockHitResult currentTarget;
    private boolean aiming;
    private boolean railPlaced;
    private boolean tntCartPlaced;

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(this::onEndTick);
    }

    private void onEndTick(MinecraftClient client) {
        if (client.world == null || client.player == null || client.interactionManager == null) {
            reset();
            return;
        }

        ClientPlayerEntity player = client.player;

        if (player.isUsingItem() && player.getActiveItem().getItem() instanceof BowItem) {
            currentTarget = predictFromPlayerBow(player);
            aiming = currentTarget != null;
        } else {
            BlockHitResult predicted = predictFromActiveArrow(client, player);
            if (predicted != null) {
                currentTarget = predicted;
                aiming = true;
            }
        }

        if (!aiming || currentTarget == null) {
            return;
        }

        rotateToTarget(player, currentTarget);

        if (!railPlaced) {
            railPlaced = tryPlaceRail(client, player, currentTarget);
        }

        if (railPlaced && !tntCartPlaced) {
            tntCartPlaced = tryPlaceTntCart(client, player, currentTarget.getBlockPos());
        }

        if (railPlaced && tntCartPlaced) {
            reset();
        }
    }

    private BlockHitResult predictFromPlayerBow(ClientPlayerEntity player) {
        if (!(player.getActiveItem().getItem() instanceof RangedWeaponItem)) {
            return null;
        }

        int useTicks = player.getItemUseTime();
        int maxUse = player.getActiveItem().getMaxUseTime(player);
        float pullProgress = BowItem.getPullProgress(maxUse - useTicks);
        if (pullProgress < 0.1F) {
            return null;
        }

        Vec3d start = player.getEyePos();
        Vec3d velocity = player.getRotationVec(1.0F).multiply(pullProgress * 3.0F);
        return simulate(player, start, velocity);
    }

    private BlockHitResult predictFromActiveArrow(MinecraftClient client, ClientPlayerEntity player) {
        for (ArrowEntity arrow : client.world.getEntitiesByClass(ArrowEntity.class, player.getBoundingBox().expand(128.0D), a -> true)) {
            if (arrow.getOwner() == null || !arrow.getOwner().getUuid().equals(player.getUuid())) {
                continue;
            }

            if (trackedArrows.add(arrow.getUuid()) && !arrow.isInGround()) {
                return simulate(player, arrow.getPos(), arrow.getVelocity());
            }
        }

        return null;
    }

    private BlockHitResult simulate(ClientPlayerEntity player, Vec3d start, Vec3d velocityIn) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) {
            return null;
        }

        Vec3d position = start;
        Vec3d velocity = velocityIn;

        for (int i = 0; i < MAX_SIM_TICKS; i++) {
            Vec3d next = position.add(velocity);
            BlockHitResult hit = client.world.raycast(new RaycastContext(
                position,
                next,
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                player
            ));

            if (hit.getType() == HitResult.Type.BLOCK) {
                return hit;
            }

            position = next;
            velocity = velocity.multiply(0.99D).add(0.0D, -0.05D, 0.0D);
        }

        return null;
    }

    private void rotateToTarget(ClientPlayerEntity player, BlockHitResult hit) {
        Vec3d target = Vec3d.ofCenter(hit.getBlockPos())
            .add(Vec3d.of(hit.getSide().getVector()).multiply(0.5D));

        Vec3d delta = target.subtract(player.getEyePos());
        double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);

        float yaw = (float) (Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90.0D);
        float pitch = (float) (-Math.toDegrees(Math.atan2(delta.y, horizontal)));

        player.setYaw(MathHelper.wrapDegrees(yaw));
        player.setPitch(MathHelper.clamp(pitch, -90.0F, 90.0F));
        player.setHeadYaw(player.getYaw());
        player.setBodyYaw(player.getYaw());
    }

    private boolean tryPlaceRail(MinecraftClient client, ClientPlayerEntity player, BlockHitResult predictedHit) {
        BlockPos placePos = resolveRailPlacementPos(client, predictedHit);
        if (placePos == null) {
            return false;
        }

        if (player.getEyePos().distanceTo(Vec3d.ofCenter(placePos)) > MAX_PLACE_RANGE) {
            return false;
        }

        int railSlot = findHotbarSlot(Items.RAIL, Items.ACTIVATOR_RAIL, Items.DETECTOR_RAIL, Items.POWERED_RAIL);
        if (railSlot < 0) {
            return false;
        }

        int oldSlot = player.getInventory().selectedSlot;
        player.getInventory().selectedSlot = railSlot;

        BlockHitResult placeHit = new BlockHitResult(
            Vec3d.ofCenter(placePos),
            Direction.UP,
            placePos,
            false
        );

        client.interactionManager.interactBlock(player, Hand.MAIN_HAND, placeHit);
        player.swingHand(Hand.MAIN_HAND);
        player.getInventory().selectedSlot = oldSlot;

        return isRail(client.world.getBlockState(placePos).getBlock());
    }

    private BlockPos resolveRailPlacementPos(MinecraftClient client, BlockHitResult predictedHit) {
        BlockPos hitPos = predictedHit.getBlockPos();
        BlockState hitState = client.world.getBlockState(hitPos);

        if (isRail(hitState.getBlock()) || hitState.isReplaceable()) {
            return hitPos;
        }

        BlockPos upPos = hitPos.up();
        if (client.world.getBlockState(upPos).isReplaceable()) {
            return upPos;
        }

        BlockPos sidePos = hitPos.offset(predictedHit.getSide());
        if (client.world.getBlockState(sidePos).isReplaceable()) {
            return sidePos;
        }

        return null;
    }

    private boolean tryPlaceTntCart(MinecraftClient client, ClientPlayerEntity player, BlockPos railPos) {
        if (!isRail(client.world.getBlockState(railPos).getBlock())) {
            return false;
        }

        int oldSlot = player.getInventory().selectedSlot;
        Hand hand = Hand.MAIN_HAND;

        if (!player.getOffHandStack().isOf(Items.TNT_MINECART)) {
            int tntMinecartSlot = findHotbarSlot(Items.TNT_MINECART);
            if (tntMinecartSlot < 0) {
                return false;
            }
            player.getInventory().selectedSlot = tntMinecartSlot;
        } else {
            hand = Hand.OFF_HAND;
        }

        BlockHitResult useHit = new BlockHitResult(Vec3d.ofCenter(railPos), Direction.UP, railPos, false);
        client.interactionManager.interactBlock(player, hand, useHit);
        player.swingHand(hand);
        player.getInventory().selectedSlot = oldSlot;

        return !client.world.getOtherEntities(
            player,
            new Box(railPos).expand(1.0D),
            entity -> entity.getType() == EntityType.TNT_MINECART
        ).isEmpty();
    }

    private int findHotbarSlot(Item... items) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) {
            return -1;
        }

        for (int i = 0; i < 9; i++) {
            Item slotItem = client.player.getInventory().getStack(i).getItem();
            for (Item wanted : items) {
                if (slotItem == wanted) {
                    return i;
                }
            }
        }

        return -1;
    }

    private static boolean isRail(Block block) {
        return block == Blocks.RAIL
            || block == Blocks.ACTIVATOR_RAIL
            || block == Blocks.DETECTOR_RAIL
            || block == Blocks.POWERED_RAIL;
    }

    private void reset() {
        currentTarget = null;
        aiming = false;
        railPlaced = false;
        tntCartPlaced = false;
    }
}
