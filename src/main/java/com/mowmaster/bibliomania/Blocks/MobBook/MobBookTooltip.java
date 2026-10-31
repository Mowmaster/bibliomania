package com.mowmaster.bibliomania.Blocks.MobBook;

import net.minecraft.world.inventory.tooltip.TooltipComponent;

// The data for a Bestiary's tooltip picture. MobBookItem#getTooltipImage creates it; on the client,
// ClientMobBookTooltip draws it (registered in BibliomaniaClient). Common code, because items create it.
public record MobBookTooltip(MobBookContents contents) implements TooltipComponent
{
}
