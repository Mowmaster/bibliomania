package com.mowmaster.bibliomania.Blocks.MobBook;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.inventory.tooltip.TooltipComponent;

/* The data for a mob book's tooltip picture (the mob's spawn egg with a count; Bestiary and Book of Essence). MobBookItem#getTooltipImage creates it; on the client,
 ClientMobBookTooltip draws it (registered in BibliomaniaClient). Common code, because items create it.*/
public record MobBookTooltip(EntityType<?> type, int count) implements TooltipComponent
{

}
