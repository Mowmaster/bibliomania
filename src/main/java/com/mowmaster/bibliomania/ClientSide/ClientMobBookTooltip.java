package com.mowmaster.bibliomania.ClientSide;

import com.mowmaster.bibliomania.Blocks.MobBook.MobBookContents;
import com.mowmaster.bibliomania.Blocks.MobBook.MobBookTooltip;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;

// Draws a Bestiary's tooltip picture: the stored mob's spawn egg with the adult count on it.
// The text lines (name, count, breeding) come from MobBookItem#appendHoverText.
// Registered in BibliomaniaClient. 26.x tooltips "extract" what to draw into a GuiGraphicsExtractor.
public class ClientMobBookTooltip implements ClientTooltipComponent
{
    private static final int ICON = 18; // the egg's square, like an inventory slot

    private final MobBookContents contents;
    // The mob's spawn egg, if it has one (a mod's mob might not): byId gives an Optional holder of the egg item
    private final ItemStack egg;

    public ClientMobBookTooltip(MobBookTooltip tooltip)
    {
        this.contents = tooltip.contents();
        this.egg = contents.type().flatMap(SpawnEggItem::byId).map(ItemStack::new).orElse(ItemStack.EMPTY);
    }

    @Override
    public int getHeight(Font font)
    {
        return egg.isEmpty() ? 0 : ICON + 2;
    }

    @Override
    public int getWidth(Font font)
    {
        return egg.isEmpty() ? 0 : ICON;
    }

    @Override
    public void extractImage(Font font, int x, int y, int width, int height, GuiGraphicsExtractor graphics)
    {
        if (egg.isEmpty()) return;
        graphics.item(egg, x + 1, y + 1);
        graphics.itemDecorations(font, egg, x + 1, y + 1, String.valueOf(contents.adults()));
    }
}
