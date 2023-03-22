package com.tntmodders.transporter.block;

import com.tntmodders.transporter.Transporter;
import com.tntmodders.transporter.item.RoadItem;
import com.tntmodders.transporter.logic.BlockCoord;
import com.tntmodders.transporter.logic.Guidepost;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.common.capabilities.ForgeCapabilities;

import javax.annotation.Nullable;

/**
 * 道標のブロック。
 */
@SuppressWarnings("deprecation")
public class GuidepostBlock extends Block {
    public GuidepostBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public PushReaction getPistonPushReaction(BlockState state) {
        // ピストンで押されると座標がずれてしまうので、押されないようにする。
        return PushReaction.BLOCK;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity entity, ItemStack stack) {
        // 設置されたとき、対応する道標を輸送網に追加する。
        level.getCapability(Transporter.TRANSPORT).ifPresent(cap -> cap.addNode(BlockCoord.fromPos(pos), new Guidepost()));
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        // 道以外で右クリックされたら、内部に保持しているアイテムをドロップする。
        if (player.getItemInHand(hand).getItem() instanceof RoadItem) return InteractionResult.PASS;
        if (!level.isClientSide) {
            level.getCapability(Transporter.TRANSPORT).ifPresent(net ->
                    net.removeFreight(BlockCoord.fromPos(pos)).forEach(freight ->
                            Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), freight.stack)
                    )
            );
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        // 破壊されたら対応する道標を輸送網から削除し、内部に保持していたアイテムをドロップする。
        level.getCapability(Transporter.TRANSPORT).ifPresent(cap ->
                cap.removeNode(BlockCoord.fromPos(pos)).forEach(stack ->
                        Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), stack)
                )
        );
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos neighbor, boolean isMoving) {
        super.neighborChanged(state, level, pos, block, neighbor, isMoving);
        // 隣接するブロックが更新されたら、外部接続を確認する。
        level.getCapability(Transporter.TRANSPORT).ifPresent(cap -> {
            var coord = BlockCoord.fromPos(pos);
            var neighbor_coord = BlockCoord.fromPos(neighbor);
            var blockEntity = level.getBlockEntity(neighbor);
            var side = neighbor_coord.getDirection(coord);
            if (blockEntity == null || !blockEntity.getCapability(ForgeCapabilities.ITEM_HANDLER, side).isPresent()) {
                // 外部接続が更新により対象外になっていたら削除する。
                cap.removeNode(neighbor_coord);
            }
        });
    }
}
