package com.mowmaster.bibliomania.Registry;

import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fluids.ForgeFlowingFluid;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class DeferredRegisterFluids
{
    public static final DeferredRegister<Fluid> FLUIDS =
            DeferredRegister.create(ForgeRegistries.FLUIDS, BibliomaniaReferences.MODID);

    public static final RegistryObject<FlowingFluid> SOURCE_COLORABLE_PAPER_PULP_FLUID = FLUIDS.register("colorable_paper_pulp_fluid",
            () -> new ForgeFlowingFluid.Source(DeferredRegisterFluids.COLORABLE_PAPER_PULP_FLUID_PROPERTIES));
    public static final RegistryObject<FlowingFluid> FLOWING_COLORABLE_PAPER_PULP_FLUID = FLUIDS.register("flowing_colorable_paper_pulp",
            () -> new ForgeFlowingFluid.Flowing(DeferredRegisterFluids.COLORABLE_PAPER_PULP_FLUID_PROPERTIES));


    public static final ForgeFlowingFluid.Properties COLORABLE_PAPER_PULP_FLUID_PROPERTIES = new ForgeFlowingFluid.Properties(
            DeferredRegisterFluidTypes.COLORABLE_PAPER_PULP_FLUIDTYPE, SOURCE_COLORABLE_PAPER_PULP_FLUID, FLOWING_COLORABLE_PAPER_PULP_FLUID)
            .slopeFindDistance(2).levelDecreasePerBlock(2).block(DeferredRegisterBlocks.COLORABLE_PAPER_PULP_FLUIDBLOCK)
            .bucket(DeferredRegisterItems.COLORABLE_PAPER_PULP_FLUID_BUCKET);


    public static void register(IEventBus eventBus) {
        FLUIDS.register(eventBus);
    }
}
