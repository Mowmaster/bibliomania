package com.mowmaster.bibliomania.EventHandlers;

import com.mojang.authlib.GameProfile;
import com.mowmaster.bibliomania.Blocks.Book.BaseBookBlock;
import com.mowmaster.bibliomania.Blocks.Book.BaseBookBlockItem;
import com.mowmaster.bibliomania.Blocks.Book.DeathBook.DeathBookBlockEntity;
import com.mowmaster.bibliomania.Blocks.Book.DeathBook.DeathBookBlockItem;
import com.mowmaster.bibliomania.Registry.DeferredRegisterBlocks;
import com.mowmaster.bibliomania.Registry.DeferredRegisterItems;
import com.mowmaster.bibliomania.Registry.DeferredRegisterTileBlocks;
import com.mowmaster.bibliomania.Utils.BibliomaniaItemUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.SkullBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.items.ItemStackHandler;

import java.util.*;

import static com.mowmaster.bibliomania.Blocks.Book.BaseBookBlock.*;
import static com.mowmaster.bibliomania.Registry.BibliomaniaReferences.MODID;

@Mod.EventBusSubscriber
public class DeathEventBookGrave {

    /*@SubscribeEvent(priority = EventPriority.LOWEST)
    public static void bookGraveOnDeathEvent(LivingDeathEvent deathEvent)
    {
        Entity entity = deathEvent.getEntity();
        if (!(entity instanceof ServerPlayer)) {
            return;
        }
        ServerPlayer player = (ServerPlayer) deathEvent.getEntity();
    }*/

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void bookGraveOnDeathEvent(LivingDropsEvent deathEvent)
    {
        Entity entity = deathEvent.getEntity();
        if (!(entity instanceof ServerPlayer)) {
            return;
        }
        ServerPlayer player = (ServerPlayer) deathEvent.getEntity();
        Collection<ItemEntity> drops = deathEvent.getDrops();

        boolean hasDeathBook = drops.stream()
                .map(ItemEntity::getItem)
                .anyMatch(s -> s.getItem() instanceof DeathBookBlockItem);

        if(hasDeathBook)
        {
            ItemStack deathBookStack = drops.stream()
                    .map(ItemEntity::getItem)
                    .filter(s -> s.getItem() instanceof DeathBookBlockItem).findFirst().get();

            ItemStack head = new ItemStack(Items.PLAYER_HEAD);
            CompoundTag owner = new CompoundTag();
            owner.putUUID("Id", player.getUUID());
            owner.putString("Name", player.getScoreboardName());
            head.getOrCreateTag().put("SkullOwner", owner);
            CompoundTag display = head.getOrCreateTagElement("display");
            ListTag lore = display.getList("Lore", Tag.TAG_STRING);
            lore.add(StringTag.valueOf(Component.Serializer.toJson(player.getCombatTracker().getDeathMessage().copy().withStyle(ChatFormatting.DARK_RED))));
            display.put("Lore", lore);

            List<ItemStack> stacks = drops.stream()
                    .map(ItemEntity::getItem)   // ItemStack inside the ItemEntity
                    .map(ItemStack::copy)       // copy if you plan to keep them
                    .toList();

            Level level = deathEvent.getEntity().level();
            BlockPos pos = new BlockPos((int)deathEvent.getEntity().getX(), (int)deathEvent.getEntity().getY()+1, (int)deathEvent.getEntity().getZ());
            BlockState block = DeferredRegisterTileBlocks.TILE_BOOK_DEATH.get().defaultBlockState();



            level.setBlockAndUpdate(pos,block.setValue(BOOK_THICKNESS,0).setValue(BOOK_COVER,0).setValue(FACING, Direction.NORTH));

            if(level.getBlockEntity(pos) instanceof DeathBookBlockEntity d)
            {
                d.load(deathBookStack.getOrCreateTag());
                d.addItem(head,false);


                int iterator = 0;
                for(ItemEntity item : drops)
                {
                    if(item.getItem().is(deathBookStack.getItem()))continue;

                    d.addItem(item.getItem(),false);
                    iterator++;



                    /*if(d.addItem(item.getItem(),false)){
                        drops.remove(item);
                    }*/
                }

                //minus 1 because we skip the death book
                if(iterator >= drops.size()-1)drops.clear();
                d.setCurrentSlot(0);
            }


        }

    }

}
