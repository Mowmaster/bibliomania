package com.mowmaster.bibliomania.Recipes;

import com.mowmaster.bibliomania.Blocks.Book.TintableBookBlock;
import com.mowmaster.bibliomania.TintHandler.IColorableBlock;
import com.mowmaster.bibliomania.TintHandler.libTintBlockEntity;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

// One dyeable book (wool or leather) plus one or more dyes, anywhere in the grid -> the same book (contents kept)
// with the dyes mixed into its cover colour, the same way leather armour mixes.
public class BookDyeRecipe extends CustomRecipe
{
    public static final BookDyeRecipe INSTANCE = new BookDyeRecipe();

    @Override
    public boolean matches(CraftingInput input, Level level)
    {
        return findBook(input) != null;
    }

    @Override
    public ItemStack assemble(CraftingInput input)
    {
        ItemStack book = findBook(input);
        if (book == null) return ItemStack.EMPTY;

        DyedItemColor current = book.get(DataComponents.DYED_COLOR);
        int color = current != null ? current.rgb() : libTintBlockEntity.UNDYED;
        for (int slot = 0; slot < input.size(); slot++)
        {
            Integer dye = IColorableBlock.dyeColorOf(input.getItem(slot));
            if (dye != null) color = IColorableBlock.mixDye(color, dye);
        }

        ItemStack result = book.copyWithCount(1);
        result.set(DataComponents.DYED_COLOR, new DyedItemColor(color));
        return result;
    }

    @Override
    public RecipeSerializer<BookDyeRecipe> getSerializer()
    {
        return RecipeRegistry.BOOK_DYE.get();
    }

    // The single dyeable book, if the grid holds exactly one plus at least one dye and nothing else
    @Nullable
    private static ItemStack findBook(CraftingInput input)
    {
        ItemStack book = null;
        boolean hasDye = false;
        for (int slot = 0; slot < input.size(); slot++)
        {
            ItemStack stack = input.getItem(slot);
            if (stack.isEmpty()) continue;
            if (isDyeableBook(stack))
            {
                if (book != null) return null;
                book = stack;
            }
            else if (IColorableBlock.dyeColorOf(stack) != null)
            {
                hasDye = true;
            }
            else
            {
                return null;
            }
        }
        return hasDye ? book : null;
    }

    private static boolean isDyeableBook(ItemStack stack)
    {
        return stack.getItem() instanceof BlockItem blockItem && blockItem.getBlock() instanceof TintableBookBlock;
    }
}
