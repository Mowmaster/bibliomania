package com.mowmaster.bibliomania.Items;

import com.mowmaster.mowlib.reading.ReadingSight;
import com.mowmaster.bibliomania.BibliomaniaReferences;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Consumer;

// Reading Glasses: a gold-framed helmet that always counts as having the Reading Goggles enchantment.
// - Reading Goggles (data/bibliomania/enchantment/reading_goggles.json) is an ordinary data-driven enchantment for any
//   helmet.
// - Wearing either shows MowLib's reading HUD (client/reading/ReadingHud): the glasses are in the item tag and the
//   enchantment in the enchantment tag #mowlib:grants_reading. What Bibliomania's blocks show on it: Reading/
//   BibliomaniaReadings.
// - Worn, the glasses are drawn from assets/bibliomania/equipment/reading_glasses.json, whose texture is
//   textures/entity/equipment/humanoid/reading_glasses.png (the same layout as a player skin's head).
public class ReadingGlassesItem extends Item
{
    public static final ResourceKey<Enchantment> READING_GOGGLES = ResourceKey.create(Registries.ENCHANTMENT,
            Identifier.fromNamespaceAndPath(BibliomaniaReferences.MODID, "reading_goggles"));

    // Which equipment/*.json draws it when worn
    public static final ResourceKey<EquipmentAsset> ASSET = ResourceKey.create(EquipmentAssets.ROOT_ID,
            Identifier.fromNamespaceAndPath(BibliomaniaReferences.MODID, "reading_glasses"));

    // Gold helmet durability (7 x 11), 1 armour, very enchantable like gold, repaired with gold ingots.
    // ArmorMaterial(durability multiplier, defense per armour type, enchantability, equip sound, toughness,
    // knockback resistance, repair items tag, equipment asset). EnumMap: a Map keyed by an enum, stored as an array.
    public static final ArmorMaterial MATERIAL = new ArmorMaterial(7, defense(), 25, SoundEvents.ARMOR_EQUIP_GOLD,
            0f, 0f, ItemTags.REPAIRS_GOLD_ARMOR, ASSET);

    private static Map<ArmorType, Integer> defense()
    {
        Map<ArmorType, Integer> map = new EnumMap<>(ArmorType.class);
        for (ArmorType type : ArmorType.values()) map.put(type, 0);
        map.put(ArmorType.HELMET, 1);
        return map;
    }

    public ReadingGlassesItem(Properties properties)
    {
        super(properties);
    }

    // Whether a player can read (MowLib's ReadingSight: the glasses and Reading Goggles helmets are in its
    // #mowlib:grants_reading item and enchantment tags, data/mowlib/tags/...)
    public static boolean canRead(Player player)
    {
        return ReadingSight.canRead(player);
    }

    // "Reading Goggles (always)" in the enchantment grey, and how it works
    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag)
    {
        super.appendHoverText(stack, context, display, tooltip, flag);
        tooltip.accept(Component.translatable("tooltip." + BibliomaniaReferences.MODID + ".reading_glasses").withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable("tooltip." + BibliomaniaReferences.MODID + ".reading_glasses.hint").withStyle(ChatFormatting.DARK_GRAY));
    }
}
