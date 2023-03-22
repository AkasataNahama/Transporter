package com.tntmodders.transporter;

import com.mojang.logging.LogUtils;
import com.tntmodders.transporter.block.GuidepostBlock;
import com.tntmodders.transporter.item.RoadItem;
import com.tntmodders.transporter.logic.TransportNet;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.Material;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.CreativeModeTabEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.slf4j.Logger;

@Mod(Transporter.MOD_ID)
public class Transporter {
    public static final String MOD_ID = "transporter";
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MOD_ID);
    public static final RegistryObject<Block> GUIDEPOST = BLOCKS.register("guidepost",
            () -> new GuidepostBlock(
                    BlockBehaviour.Properties.of(Material.METAL)
                            .sound(SoundType.METAL)
                            .strength(5.0f, 6.0f)
                            .requiresCorrectToolForDrops()
            ));
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MOD_ID);
    public static final RegistryObject<Item> GUIDEPOST_ITEM = ITEMS.register("guidepost",
            () -> new BlockItem(GUIDEPOST.get(), new Item.Properties()));
    public static final RegistryObject<Item> ROAD = ITEMS.register("road",
            () -> new RoadItem(new Item.Properties()));
    /**
     * 輸送網のCapability。
     */
    public static final Capability<TransportNet> TRANSPORT = CapabilityManager.get(new CapabilityToken<>() {
    });
    private static final Logger LOGGER = LogUtils.getLogger();

    public Transporter() {
        var modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        modEventBus.addListener(this::addCreative);
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        // このクラスの@SubscribeEventがついたメソッドを登録する。
        MinecraftForge.EVENT_BUS.register(this);
    }

    private void addCreative(CreativeModeTabEvent.BuildContents event) {
        if (event.getTab() == CreativeModeTabs.FUNCTIONAL_BLOCKS)
            event.accept(GUIDEPOST_ITEM);
        if (event.getTab() == CreativeModeTabs.TOOLS_AND_UTILITIES)
            event.accept(ROAD);
    }

    @SubscribeEvent
    public void attachCaps(AttachCapabilitiesEvent<Level> event) {
        // サーバー側のそれぞれのディメンションに輸送網の情報を付与する。
        if (!event.getObject().isClientSide) {
            event.addCapability(new ResourceLocation(MOD_ID, "transport"), new TransportNet());
            LOGGER.debug("Attached capability.");
        }
    }

    @SubscribeEvent
    public void levelTick(TickEvent.LevelTickEvent event) {
        // 各ディメンションの更新時に輸送網を更新する。
        if (event.phase == TickEvent.Phase.START) {
            event.level.getCapability(TRANSPORT).ifPresent(net -> net.update(event.level));
        }
    }
}
