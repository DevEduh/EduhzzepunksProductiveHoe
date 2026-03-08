package com.example.examplemod.event;

import com.example.examplemod.ProductiveHoeMod;
import com.mojang.logging.LogUtils;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

@Mod.EventBusSubscriber(modid = ProductiveHoeMod.MODID)
public final class WorldgenDebugEventHandler {
    private static final Logger LOGGER = LogUtils.getLogger();

    private WorldgenDebugEventHandler() {
    }

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        Registry<Structure> structures = event.getServer().registryAccess().registryOrThrow(Registries.STRUCTURE);
        Registry<StructureSet> structureSets = event.getServer().registryAccess().registryOrThrow(Registries.STRUCTURE_SET);

        ResourceLocation hutId = ResourceLocation.fromNamespaceAndPath(ProductiveHoeMod.MODID, "abandoned_hut");
        ResourceLocation gardenId = ResourceLocation.fromNamespaceAndPath(ProductiveHoeMod.MODID, "abandoned_garden");
        ResourceLocation farmSetId = ResourceLocation.fromNamespaceAndPath(ProductiveHoeMod.MODID, "abandoned_farm_set");

        LOGGER.info("[EPH] Structure registered {}: {}", hutId, structures.containsKey(hutId));
        LOGGER.info("[EPH] Structure registered {}: {}", gardenId, structures.containsKey(gardenId));
        LOGGER.info("[EPH] Structure set registered {}: {}", farmSetId, structureSets.containsKey(farmSetId));

        Holder<Structure> hutHolder = structures.getHolder(ResourceKey.create(Registries.STRUCTURE, hutId)).orElse(null);
        Holder<Structure> gardenHolder = structures.getHolder(ResourceKey.create(Registries.STRUCTURE, gardenId)).orElse(null);
        Holder<StructureSet> farmSetHolder = structureSets.getHolder(ResourceKey.create(Registries.STRUCTURE_SET, farmSetId)).orElse(null);

        for (ServerLevel level : event.getServer().getAllLevels()) {
            ChunkGeneratorStructureState structureState = level.getChunkSource().getGeneratorState();
            LOGGER.info("[EPH] Level {} possible structure sets: {}",
                    level.dimension().location(),
                    structureState.possibleStructureSets().size());

            if (hutHolder != null) {
                LOGGER.info("[EPH] Level {} placements for {}: {}",
                        level.dimension().location(),
                        hutId,
                        structureState.getPlacementsForStructure(hutHolder).size());
            }

            if (gardenHolder != null) {
                LOGGER.info("[EPH] Level {} placements for {}: {}",
                        level.dimension().location(),
                        gardenId,
                        structureState.getPlacementsForStructure(gardenHolder).size());
            }

            if (farmSetHolder != null) {
                LOGGER.info("[EPH] Level {} active set {}: {}",
                        level.dimension().location(),
                        farmSetId,
                        structureState.possibleStructureSets().contains(farmSetHolder));
            }
        }
    }
}
