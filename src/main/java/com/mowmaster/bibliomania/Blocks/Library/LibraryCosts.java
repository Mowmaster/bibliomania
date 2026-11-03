package com.mowmaster.bibliomania.Blocks.Library;

import com.mowmaster.bibliomania.BibliomaniaReferences;
import com.mowmaster.bibliomania.ServerConfig;
import com.mowmaster.mowlib.transport.TransportType;
import com.mowmaster.mowlib.transport.TransportTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.resource.Resource;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

// Knowledge Points (KP): what library transfers cost, when the server config ([library]) sets a cost. They're only a
// number: a controller keeps a small balance (LibraryControllerBlockEntity), and when a transfer costs more than it
// has, it turns just enough "fuel" from its own storage into points (the config's fuel list: experience, energy,
// fluids; never items). Whole numbers throughout: "1 experience point -> 100 KP" needs no decimals.
// - Each insert or extract by players or automation pays (PER_TRANSFER: once; PER_UNIT: per item, bucket, 1000 FE,
//   experience point), plus distancePercentPerBlock % for every block it happens beyond distanceFree from the
//   controller, as the crow flies. Fuel going in is always free.
// - A library with no points and no fuel left is "out of knowledge": paid transfers fail, its Card Catalogs show every
//   drawer as missing, and players can't use its blocks (other than Bookcases) until fuel is put in.
// A library's members are positions in the controller's own level, so nothing can reach one from another dimension.
public final class LibraryCosts
{
    private LibraryCosts() {}

    public enum Operation { INSERT, EXTRACT }

    // One fuel: `amount` of `type` (only `resource` if set: a fluid) makes `points` KP
    public record Fuel(TransportType<?> type, @Nullable Resource resource, int amount, int points)
    {
        public boolean matches(TransportType<?> otherType, Resource other)
        {
            return otherType == type && !other.isEmpty() && (resource == null || resource.equals(other));
        }
    }

    // The parsed fuel list, re-parsed only when the config's list changes
    private static List<? extends String> parsedFrom = List.of();
    private static List<Fuel> fuels = List.of();

    public static List<Fuel> fuels()
    {
        List<? extends String> config = ServerConfig.libraryFuel();
        if (!config.equals(parsedFrom))
        {
            List<Fuel> parsed = new ArrayList<>();
            for (String entry : config)
            {
                Fuel fuel = parse(entry);
                if (fuel != null) parsed.add(fuel);
            }
            fuels = List.copyOf(parsed);
            parsedFrom = List.copyOf(config);
        }
        return fuels;
    }

    // "mowlib:experience=1:100" or "mowlib:fluids/minecraft:lava=100:20" (bad entries are skipped)
    private static @Nullable Fuel parse(String entry)
    {
        try
        {
            String[] typeAndRest = entry.trim().split("=", 2);
            String[] amountAndPoints = typeAndRest[1].split(":", 2);
            String[] typeAndResource = typeAndRest[0].split("/", 2);
            Identifier typeId = Identifier.tryParse(typeAndResource[0]);
            TransportType<?> type = typeId == null ? null : TransportTypes.REGISTRY.getValue(typeId);
            if (type == null || type == TransportTypes.ITEMS.get()) return null;
            Resource resource = null;
            if (typeAndResource.length > 1)
            {
                if (type != TransportTypes.FLUIDS.get()) return null;
                Identifier fluidId = Identifier.tryParse(typeAndResource[1]);
                Fluid fluid = fluidId == null ? Fluids.EMPTY : BuiltInRegistries.FLUID.getValue(fluidId);
                if (fluid == Fluids.EMPTY) return null;
                resource = FluidResource.of(fluid);
            }
            int amount = Integer.parseInt(amountAndPoints[0].trim());
            int points = Integer.parseInt(amountAndPoints[1].trim());
            return amount > 0 && points > 0 ? new Fuel(type, resource, amount, points) : null;
        }
        catch (RuntimeException badEntry)
        {
            return null;
        }
    }

    // Whether costs are on at all (with none, libraries never need fuel)
    public static boolean enabled()
    {
        return ServerConfig.libraryInsertCost() > 0 || ServerConfig.libraryExtractCost() > 0;
    }

    public static boolean isFuel(TransportType<?> type, Resource resource)
    {
        for (Fuel fuel : fuels()) if (fuel.matches(type, resource)) return true;
        return false;
    }

    // What moving `amount` of `type` costs, from `at`, for a controller at `controller`
    public static long cost(Operation operation, TransportType<?> type, int amount, BlockPos controller, BlockPos at)
    {
        long base = operation == Operation.INSERT ? ServerConfig.libraryInsertCost() : ServerConfig.libraryExtractCost();
        if (base <= 0 || amount <= 0) return 0;
        long units = ServerConfig.libraryCostMode() == ServerConfig.CostMode.PER_UNIT ? ceilDiv(amount, unitSize(type)) : 1;
        long cost = base * units;
        // As the crow flies: the straight-line distance between the two blocks
        long blocks = (long) Math.ceil(Math.sqrt(controller.distSqr(at)));
        long beyond = Math.max(0, blocks - ServerConfig.libraryDistanceFree());
        return cost + cost * beyond * ServerConfig.libraryDistancePercent() / 100;
    }

    // How much of a type is one unit: a bucket of fluid, 1000 FE; items and experience one each
    private static int unitSize(TransportType<?> type)
    {
        if (type == TransportTypes.FLUIDS.get() || type == TransportTypes.ENERGY.get()) return 1000;
        return 1;
    }

    private static long ceilDiv(long value, long by)
    {
        return (value + by - 1) / by;
    }

    // Players can't use a library's blocks (other than Bookcases) while it's out of knowledge: true (and tells them)
    // if the block at pos is in such a library
    public static boolean blocked(@Nullable Level level, LibraryMember member, BlockPos pos, @Nullable Player player)
    {
        LibraryControllerBlockEntity library = member.library(level, pos);
        if (library == null || library.isFueled()) return false;
        if (player != null && level != null && !level.isClientSide())
        {
            player.sendOverlayMessage(Component.translatable("message." + BibliomaniaReferences.MODID + ".library.no_knowledge"));
        }
        return true;
    }
}
