package com.mowmaster.bibliomania.Registry;


import com.mowmaster.bibliomania.Blocks.Book.BaseBookBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import static com.mowmaster.bibliomania.Registry.BibliomaniaReferences.MODID;

public class DeferredBlockEntityTypes
{
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister
            .create(ForgeRegistries.BLOCK_ENTITY_TYPES, MODID);

    public static final RegistryObject<BlockEntityType<BaseBookBlockEntity>> BOOK_STARTER = BLOCK_ENTITIES.register(
            "block_entity_book_starter",
            () -> BlockEntityType.Builder.of(BaseBookBlockEntity::new, DeferredRegisterTileBlocks.TILE_BOOK_STARTER.get()).build(null));

    private DeferredBlockEntityTypes() {
    }
}
