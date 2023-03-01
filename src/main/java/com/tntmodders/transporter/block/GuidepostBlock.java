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

@SuppressWarnings("deprecation")
public class GuidepostBlock extends Block {
    public GuidepostBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public PushReaction getPistonPushReaction(BlockState state) {
        return PushReaction.BLOCK;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity entity, ItemStack stack) {
        level.getCapability(Transporter.TRANSPORT).ifPresent(cap -> cap.addNode(BlockCoord.fromPos(pos), new Guidepost()));
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (player.getItemInHand(hand).getItem() instanceof RoadItem) return InteractionResult.PASS;
        if (!level.isClientSide)
            level.getCapability(Transporter.TRANSPORT).ifPresent(data -> data.removeFreight(BlockCoord.fromPos(pos)).forEach(freight -> Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), freight.stack)));
        return InteractionResult.SUCCESS;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        level.getCapability(Transporter.TRANSPORT).ifPresent(cap -> cap.removeNode(BlockCoord.fromPos(pos)).forEach(stack -> Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), stack)));
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos neighbor, boolean isMoving) {
        super.neighborChanged(state, level, pos, block, neighbor, isMoving);
        level.getCapability(Transporter.TRANSPORT).ifPresent(cap -> {
            var coord = BlockCoord.fromPos(pos);
            var neighbor_coord = BlockCoord.fromPos(neighbor);
            var blockEntity = level.getBlockEntity(neighbor);
            if (blockEntity == null || !blockEntity.getCapability(ForgeCapabilities.ITEM_HANDLER, neighbor_coord.getDirection(coord)).isPresent()) {
                // 隣接した座標が更新により対象外になっていたら削除する。
                cap.removeNode(neighbor_coord);
            }
        });
    }
}
