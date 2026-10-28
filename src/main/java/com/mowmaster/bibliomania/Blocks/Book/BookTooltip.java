package com.mowmaster.bibliomania.Blocks.Book;

import net.minecraft.world.inventory.tooltip.TooltipComponent;

// The data for a book's tooltip picture: every page (empty ones too), the selected one, and how much a page holds.
// BookItem#getTooltipImage returns this; on the client, ClientBookTooltip draws it (registered in
// BibliomaniaClient). This half lives in common code because items, which servers also load, create it.
public record BookTooltip(BookContents contents, int pageCapacity) implements TooltipComponent
{
}
