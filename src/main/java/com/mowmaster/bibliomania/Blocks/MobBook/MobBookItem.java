package com.mowmaster.bibliomania.Blocks.MobBook;

import com.mowmaster.bibliomania.Items.DataComponentRegistry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

import java.util.Optional;

// The Bestiary as an item.
// - Right-click an adult animal (not a pet, not named): it goes into the book. Same kind only; one kind per book.
// - In inventories, clicking the book with something on your cursor:
//     breeding food, left-click (insert): breeds the mobs, using only as much food as they can eat
//     a sword, right-click (extract):      kills one; the drops go into your inventory and the XP to you
//     anything else:                       the bundle "can't" sound, nothing happens
// - The tooltip shows the mob as its spawn egg with the count, babies growing and the breeding cooldown.
public class MobBookItem extends BlockItem
{
    public MobBookItem(Block block, Properties properties)
    {
        super(block, properties);
    }

    public static MobBookContents contents(ItemStack stack)
    {
        MobBookContents contents = stack.get(DataComponentRegistry.MOB_CONTENTS.get());
        return contents != null ? contents : MobBookContents.EMPTY;
    }

    // What it holds now (babies whose cooldown is over count as adults)
    private static MobBookContents settled(ItemStack stack, Level level)
    {
        return contents(stack).settle(level.getGameTime());
    }

    private static void setContents(ItemStack stack, MobBookContents contents)
    {
        stack.set(DataComponentRegistry.MOB_CONTENTS.get(), contents);
    }

    // ---- Capturing ----

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand)
    {
        Level level = player.level();
        MobBookContents contents = settled(stack, level);
        if (!MobBookActions.canCapture(target) || !contents.canCapture(target.getType()))
        {
            player.playSound(SoundEvents.BUNDLE_INSERT_FAIL, 1f, 1f);
            return InteractionResult.FAIL;
        }
        if (level instanceof ServerLevel serverLevel)
        {
            setContents(stack, contents.withCaptured(target.getType()));
            serverLevel.sendParticles(ParticleTypes.POOF, target.getX(), target.getY() + target.getBbHeight() / 2, target.getZ(),
                    10, 0.3, 0.3, 0.3, 0.02);
            level.playSound(null, target.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1f, 1f);
            target.discard(); // gone from the world, now a count in the book
        }
        return InteractionResult.SUCCESS;
    }

    // ---- Inventory clicks ----

    // Holding something (or nothing), clicking the book in a slot
    @Override
    public boolean overrideOtherStackedOnMe(ItemStack self, ItemStack other, Slot slot, ClickAction clickAction,
                                            Player player, SlotAccess carriedItem)
    {
        if (self.getCount() != 1) return false;
        // Left-click with an empty hand picks the book up as normal
        if (clickAction == ClickAction.PRIMARY && other.isEmpty()) return false;

        Level level = player.level();
        MobBookContents contents = settled(self, level);

        // Breeding food, left-click: breed
        if (clickAction == ClickAction.PRIMARY && slot.allowModification(player) && MobBookActions.isBreedingFood(level, contents, other))
        {
            int eaten = Math.min(other.getCount(), contents.maxFeed());
            if (eaten > 0)
            {
                // Changes are made on the server and sent to the client; the client only plays the sound
                if (level instanceof ServerLevel)
                {
                    setContents(self, contents.withFed(eaten, level.getGameTime()));
                    other.consume(eaten, player);
                    slotsChanged(player);
                }
                player.playSound(SoundEvents.GENERIC_EAT.value(), 0.6f, 1f);
                return true;
            }
        }

        // A sword, right-click: kill one, drops and XP to the player
        if (clickAction == ClickAction.SECONDARY && slot.allowModification(player) && MobBookActions.isKillWeapon(other)
                && contents.adults() > 0 && contents.type().isPresent())
        {
            if (level instanceof ServerLevel serverLevel)
            {
                int killed = MobBookActions.kill(serverLevel, contents.type().get(), 1, player, player.position(), true);
                setContents(self, contents.withKilled(killed));
                slotsChanged(player);
            }
            return true;
        }

        // Anything else isn't allowed
        player.playSound(SoundEvents.BUNDLE_INSERT_FAIL, 1f, 1f);
        return true;
    }

    // Tells the open container the book changed, so the change is sent to the client
    private static void slotsChanged(Player player)
    {
        player.containerMenu.slotsChanged(player.getInventory());
    }

    // ---- Tooltip: drawn by ClientMobBookTooltip ----

    @Override
    public Optional<TooltipComponent> getTooltipImage(ItemStack stack)
    {
        return Optional.of(new MobBookTooltip(contents(stack)));
    }
}
