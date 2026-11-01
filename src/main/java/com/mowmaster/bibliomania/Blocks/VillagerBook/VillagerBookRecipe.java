package com.mowmaster.bibliomania.Blocks.VillagerBook;

import com.mowmaster.bibliomania.Items.RecipeRegistry;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

// Two crafting-grid uses of the Trader's Ledger (serializer bibliomania:villager_book, one recipe file):
// - One Ledger holding a villager, alone: the same book with the villager reset (no profession, level 1, no trades)
// - Two Ledgers holding level 5 villagers plus one empty Ledger: the empty one gets a new villager (the first
//   parent's type, no profession). The two parents stay in the grid.
// (Like MowLib's ColorableItemDyeRecipe: a CustomRecipe works out its result from the grid in code.)
public class VillagerBookRecipe extends CustomRecipe
{
    public static final VillagerBookRecipe INSTANCE = new VillagerBookRecipe();

    // What's in the grid: the book to change, and (for a new villager) the two parents' grid slots
    private record Match(ItemStack target, @Nullable VillagerContents parent, int parentA, int parentB) {}

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
        ItemStack result = match.target().copyWithCount(1);
        VillagerContents source = match.parent() != null ? match.parent() : VillagerBookItem.contents(match.target());
        VillagerBookItem.setContents(result, VillagerBookActions.reset(source));
        return result;
    }

    // The two parents go back into the grid; everything else is used up
    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingInput input)
    {
        NonNullList<ItemStack> remaining = NonNullList.withSize(input.size(), ItemStack.EMPTY);
        Match match = match(input);
        if (match != null && match.parent() != null)
        {
            remaining.set(match.parentA(), input.getItem(match.parentA()).copyWithCount(1));
            remaining.set(match.parentB(), input.getItem(match.parentB()).copyWithCount(1));
        }
        return remaining;
    }

    @Override
    public RecipeSerializer<VillagerBookRecipe> getSerializer()
    {
        return RecipeRegistry.VILLAGER_BOOK.get();
    }

    // Only Ledgers in the grid: one holding a villager (reset), or two level 5 villagers and one empty book (new)
    private static @Nullable Match match(CraftingInput input)
    {
        int full = 0, maxed = 0, empty = 0;
        int firstFull = -1, secondFull = -1, emptySlot = -1;
        for (int slot = 0; slot < input.size(); slot++)
        {
            ItemStack stack = input.getItem(slot);
            if (stack.isEmpty()) continue;
            if (!(stack.getItem() instanceof VillagerBookItem)) return null;
            VillagerContents contents = VillagerBookItem.contents(stack);
            // Wandering traders have no level to reach and nothing to reset
            if (contents.wandering()) return null;
            if (contents.hasVillager())
            {
                full++;
                if (contents.level() >= VillagerBookActions.MAX_LEVEL) maxed++;
                if (firstFull < 0) firstFull = slot; else secondFull = slot;
            }
            else
            {
                empty++;
                emptySlot = slot;
            }
        }
        if (full == 1 && empty == 0) return new Match(input.getItem(firstFull), null, -1, -1);
        if (full == 2 && maxed == 2 && empty == 1)
        {
            return new Match(input.getItem(emptySlot), VillagerBookItem.contents(input.getItem(firstFull)), firstFull, secondFull);
        }
        return null;
    }
}
