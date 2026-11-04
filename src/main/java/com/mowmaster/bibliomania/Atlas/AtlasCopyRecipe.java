package com.mowmaster.bibliomania.Atlas;

import com.mowmaster.bibliomania.Items.DataComponentRegistry;
import com.mowmaster.bibliomania.Items.RecipeRegistry;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

// Copying an Atlas (serializer bibliomania:atlas_copy): a used Atlas plus a blank one, anywhere in the grid, makes the
// blank one a copy: the same zoom, markers and unlocks (bibliomania:atlas), and the same explored map. The original
// stays in the grid.
// A blank Atlas is one that's never been carried as the shown Atlas (it has no bibliomania:book_id yet, no markers or
// unlocks). The recipe can't reach the world's saved maps, so the copy only remembers which Atlas it came from
// (bibliomania:atlas_copy_of); the server copies the explored map the first time it's carried (AtlasItem.finishCopy).
// After that the two are separate: exploring with one doesn't uncover the other.
public class AtlasCopyRecipe extends CustomRecipe
{
    public static final AtlasCopyRecipe INSTANCE = new AtlasCopyRecipe();

    // The used Atlas's and the blank one's grid slots
    private record Match(int source, int blank) {}

    @Override
    public boolean matches(CraftingInput input, Level level)
    {
        return match(input) != null;
    }

    @Override
    public ItemStack assemble(CraftingInput input)
    {
        Match match = match(input);
        if (match == null) return ItemStack.EMPTY;
        ItemStack source = input.getItem(match.source());
        ItemStack result = input.getItem(match.blank()).copyWithCount(1);
        result.set(DataComponentRegistry.ATLAS.get(), AtlasItem.data(source));
        UUID from = mapOf(source);
        if (from != null) result.set(DataComponentRegistry.ATLAS_COPY_OF.get(), from);
        return result;
    }

    // The original goes back into the grid; the blank one is used up (it's the result)
    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingInput input)
    {
        NonNullList<ItemStack> remaining = NonNullList.withSize(input.size(), ItemStack.EMPTY);
        Match match = match(input);
        if (match != null) remaining.set(match.source(), input.getItem(match.source()).copyWithCount(1));
        return remaining;
    }

    @Override
    public RecipeSerializer<AtlasCopyRecipe> getSerializer()
    {
        return RecipeRegistry.ATLAS_COPY.get();
    }

    // Whose explored map an Atlas shows: its own id, or (a copy not carried yet) the one it was copied from
    private static @Nullable UUID mapOf(ItemStack atlas)
    {
        UUID id = atlas.get(DataComponentRegistry.BOOK_ID.get());
        return id != null ? id : atlas.get(DataComponentRegistry.ATLAS_COPY_OF.get());
    }

    private static boolean isBlank(ItemStack atlas)
    {
        return mapOf(atlas) == null && !atlas.has(DataComponentRegistry.ATLAS.get());
    }

    // Exactly two Atlases and nothing else: one blank, one not
    private static @Nullable Match match(CraftingInput input)
    {
        int source = -1, blank = -1;
        for (int slot = 0; slot < input.size(); slot++)
        {
            ItemStack stack = input.getItem(slot);
            if (stack.isEmpty()) continue;
            if (!(stack.getItem() instanceof AtlasItem)) return null;
            if (isBlank(stack))
            {
                if (blank >= 0) return null;
                blank = slot;
            }
            else
            {
                if (source >= 0) return null;
                source = slot;
            }
        }
        return source >= 0 && blank >= 0 ? new Match(source, blank) : null;
    }
}
