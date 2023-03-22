package com.tntmodders.transporter.item;

import com.mojang.logging.LogUtils;
import com.tntmodders.transporter.Transporter;
import com.tntmodders.transporter.block.GuidepostBlock;
import com.tntmodders.transporter.logic.*;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 道のアイテム。
 */
public class RoadItem extends Item {
    private static final Logger LOGGER = LogUtils.getLogger();

    public RoadItem(Properties properties) {
        super(properties);
    }

    @Override
    public void onCraftedBy(ItemStack stack, Level level, Player player) {
        stack.getOrCreateTagElement("road");
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean isSelected) {
        // 更新時、座標が保存されていたらパーティクルを表示する。
        var tag = stack.getOrCreateTagElement("road");
        if (!level.isClientSide || !isSelected || !tag.contains("coord")) return;
        if (!level.dimension().location().toString().equals(tag.getString("dimension"))) return;
        // 基本的にパーティクルはプレイヤーに向ける。
        var targetPos = entity.position().add(0.0, entity.getEyeHeight() / 2.0, 0.0);
        var hitResult = Minecraft.getInstance().hitResult;
        if (hitResult instanceof BlockHitResult && hitResult.getType() == HitResult.Type.BLOCK) {
            // 視線の先に接続可能なブロックがあるなら、パーティクルをそこに向ける。
            var coord = BlockCoord.fromPos(((BlockHitResult) hitResult).getBlockPos());
            if (tryAddRoad(level, coord, tag, true) == Result.SUCCESS) {
                targetPos = coord.toBlockPos().getCenter();
            }
        }
        var savedPos = BlockCoord.fromNBT(tag.getCompound("coord")).toBlockPos().getCenter();
        spawnParticles(level, savedPos, targetPos, tag.getBoolean("reversed"));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        var stack = player.getItemInHand(hand);
        if (!level.isClientSide) {
            var tag = stack.getOrCreateTagElement("road");
            if (!player.isCrouching()) {
                // 空に向かって右クリックされたら接続方向を反転させる。
                if (tag.getBoolean("reversed")) {
                    tag.remove("reversed");
                } else {
                    tag.putBoolean("reversed", true);
                }
            } else {
                // 空に向かってしゃがみながら右クリックされたら保存を破棄する。
                tag.remove("coord");
                tag.remove("dimension");
                tag.remove("reversed");
            }
        }
        return InteractionResultHolder.success(stack);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!context.getLevel().isClientSide) {
            // 座標が保存されていなかったら保存し、保存されていたら接続する。
            var tag = context.getItemInHand().getOrCreateTagElement("road");
            var coord = BlockCoord.fromPos(context.getClickedPos());
            var result = tryAddRoad(context.getLevel(), coord, tag, false);
            // 接続に成功したらアイテムを消費する。
            if (result == Result.SUCCESS) context.getItemInHand().shrink(1);
            // 座標が保存されていなかったか、接続に成功したならこの座標を保存する。
            if (result.shouldSave()) {
                tag.put("coord", coord.toNBT());
                tag.putString("dimension", context.getLevel().dimension().location().toString());
            }
            LOGGER.info("Road: {}", result);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        // 座標が保存されていたらツールチップに表示する。
        var tag = stack.getTagElement("road");
        if (tag == null || !tag.contains("coord") || !tag.contains("dimension")) return;
        var coord = BlockCoord.fromNBT(tag.getCompound("coord"));
        var dimension = tag.getString("dimension");
        String message;
        if (tag.getBoolean("reversed")) {
            message = coord + " reversed";
        } else {
            message = coord.toString();
        }
        tooltip.add(Component.literal(message).withStyle(ChatFormatting.GRAY));
        // プレイヤーが保存された座標と異なるディメンションにいたらその旨を表示する。
        if (level == null || !level.dimension().location().toString().equals(dimension)) {
            tooltip.add(Component.literal("Different Dimension").withStyle(ChatFormatting.RED));
        }
    }

    /**
     * 保存された座標との接続を試みる。
     *
     * @param level       現在のディメンション
     * @param targetCoord 視線の先の座標
     * @param tag         座標が保存されているNBT
     * @param simulate    接続を実行しないならtrue
     * @return 接続を試みた結果
     */
    private Result tryAddRoad(Level level, BlockCoord targetCoord, CompoundTag tag, boolean simulate) {
        // 視線の先のブロックを取得する。
        var targetState = level.getBlockState(targetCoord.toBlockPos());
        var targetBlockEntity = level.getBlockEntity(targetCoord.toBlockPos());
        Node targetNode;
        if (targetState.getBlock() instanceof GuidepostBlock) {
            targetNode = new Guidepost();
        } else if (targetBlockEntity == null) {
            return Result.TARGET_NOT_SUPPORTED;
        } else {
            targetNode = new ExternalStorage();
        }
        // ディメンションを確認する。
        if (!tag.contains("dimension")) return Result.EMPTY_DIMENSION;
        var dimension = tag.getString("dimension");
        if (!level.dimension().location().toString().equals(dimension)) return Result.SAVED_DIFFERENT_DIMENSION;
        // 保存された座標のブロックを取得する。
        if (!tag.contains("coord")) return Result.EMPTY_COORD;
        var savedCoord = BlockCoord.fromNBT(tag.getCompound("coord"));
        if (savedCoord.equals(targetCoord)) return Result.EQUALS_COORD;
        if (!level.isLoaded(savedCoord.toBlockPos())) return Result.SAVED_NOT_LOADED;
        var savedState = level.getBlockState(savedCoord.toBlockPos());
        var savedBlockEntity = level.getBlockEntity(savedCoord.toBlockPos());
        Node savedNode;
        if (savedState.getBlock() instanceof GuidepostBlock) {
            savedNode = new Guidepost();
        } else if (savedBlockEntity == null) {
            return Result.SAVED_NOT_SUPPORTED;
        } else if (targetNode instanceof ExternalStorage) {
            return Result.NO_GUIDEPOST;
        } else {
            savedNode = new ExternalStorage();
        }
        // 向きを判定する。
        long distanceSq = savedCoord.distanceSq(targetCoord);
        // 視線の先のブロックが外部接続なら、接続可能か確認する。
        if (targetNode instanceof ExternalStorage) {
            var side = targetCoord.getDirection(savedCoord);
            if (distanceSq == 1 && side == null)
                LOGGER.warn("The length of the vector is 1 but the side is null.");
            if (distanceSq != 1 || side == null) return Result.TOO_DISTANT;
            if (!targetBlockEntity.getCapability(ForgeCapabilities.ITEM_HANDLER, side).isPresent())
                return Result.TARGET_NOT_SUPPORTED;
        }
        // 保存されたブロックが外部接続なら、接続可能か確認する。
        if (savedNode instanceof ExternalStorage) {
            var side = savedCoord.getDirection(targetCoord);
            if (distanceSq == 1 && side == null)
                LOGGER.warn("The length of the vector is 1 but the side is null.");
            if (distanceSq != 1 || side == null) return Result.TOO_DISTANT;
            if (!savedBlockEntity.getCapability(ForgeCapabilities.ITEM_HANDLER, side).isPresent())
                return Result.TARGET_NOT_SUPPORTED;
        }
        if (simulate) return Result.SUCCESS;
        // 接続方向を考慮して接続を追加する。
        return level.getCapability(Transporter.TRANSPORT).map(net -> {
            boolean result;
            if (!tag.getBoolean("reversed")) {
                var road = new Road(savedCoord, targetCoord);
                result = net.addRoad(road, savedNode, targetNode);
            } else {
                var road = new Road(targetCoord, savedCoord);
                result = net.addRoad(road, targetNode, savedNode);
            }
            if (result) {
                return Result.SUCCESS;
            } else {
                return Result.ALREADY_CONNECTED;
            }
        }).orElse(Result.SUCCESS);
    }

    /**
     * 2つの座標の間にパーティクルを表示する。
     *
     * @param level      パーティクルを出すディメンション
     * @param savedPos   保存された座標で、通常は接続元
     * @param targetPos  視線の先の座標で、通常は接続先
     * @param isReversed 接続方向を反転するか
     */
    private void spawnParticles(Level level, Vec3 savedPos, Vec3 targetPos, boolean isReversed) {
        // レッドストーンのパーティクルを流用しているので、移動速度は反映されない。
        var vector = targetPos.subtract(savedPos);
        var length = vector.length();
        var speedFactorLimit = 2.0 / length;
        var random = level.random;
        for (int i = 0; i < Math.max(1.0, Math.round(length / 5.0)); i++) {
            var options = new DustParticleOptions(new Vec3(0.0, 0.8, 0.0).toVector3f(), 1.0f);
            var factor = random.nextDouble();
            if (!isReversed) {
                // savedPosとtargetPosを結ぶ線分上に等頻度で発生させる。
                var pos = randomizePos(savedPos.add(vector.multiply(factor, factor, factor)), random);
                // targetPosに着いて消滅するような速度にするが、上限をかける。
                var speed_factor = Math.min(1.0 - factor, speedFactorLimit) / 20.0;
                var speed = randomizeSpeed(vector.multiply(speed_factor, speed_factor, speed_factor), random);
                level.addParticle(options, pos.x, pos.y, pos.z, speed.x, speed.y, speed.z);
            } else {
                var limitedFactor = Math.min(factor, speedFactorLimit);
                // savedPosとtargetPosを結ぶ線分上に等頻度で消滅するよう発生させる。
                var pos_factor = 1.0 - factor + limitedFactor;
                var pos = randomizePos(savedPos.add(vector.multiply(pos_factor, pos_factor, pos_factor)), random);
                // savedPos + vector * (1.0 - factor)の座標で消滅するように速度を設定する。
                var speed_factor = -limitedFactor / 20.0;
                var speed = randomizeSpeed(vector.multiply(speed_factor, speed_factor, speed_factor), random);
                level.addParticle(options, pos.x, pos.y, pos.z, speed.x, speed.y, speed.z);
            }
        }
    }

    private Vec3 randomizePos(Vec3 pos, RandomSource random) {
        return pos.add((random.nextDouble() - 0.5) / 10.0, (random.nextDouble() - 0.5) / 10.0, (random.nextDouble() - 0.5) / 10.0);
    }

    private Vec3 randomizeSpeed(Vec3 pos, RandomSource random) {
        return pos.add((random.nextDouble() - 0.5) / 40.0, (random.nextDouble() - 0.5) / 40.0, (random.nextDouble() - 0.5) / 40.0);
    }

    private enum Result {
        SUCCESS, ALREADY_CONNECTED, TOO_DISTANT, NO_GUIDEPOST, EMPTY_DIMENSION, EMPTY_COORD, EQUALS_COORD, TARGET_NOT_SUPPORTED, SAVED_DIFFERENT_DIMENSION, SAVED_NOT_LOADED, SAVED_NOT_SUPPORTED;

        private boolean shouldSave() {
            return this == SUCCESS || this == EMPTY_DIMENSION || this == EMPTY_COORD;
        }
    }
}
