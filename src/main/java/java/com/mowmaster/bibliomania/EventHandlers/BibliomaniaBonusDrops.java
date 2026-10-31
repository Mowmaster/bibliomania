package java.com.mowmaster.bibliomania.EventHandlers;

import com.mowmaster.bibliomania.BibliomaniaTags;
import com.mowmaster.bibliomania.Items.ItemRegistry;
import com.mowmaster.mowlib.drops.BonusDrop;
import com.mowmaster.mowlib.drops.BonusDrops;
import net.minecraft.world.item.ItemStack;

// Bibliomania's bonus drops, rolled by MowLib's BonusDropHandler (replaces our old GrassFiberHandler):
// breaking anything in #bibliomania:grass_fiber_plants with a knife (#bibliomania:knives) has a 20% chance of
// dropping grass fibers. Call register() from Bibliomania#commonSetup (inside event.enqueueWork).
public final class BibliomaniaBonusDrops
{
    private BibliomaniaBonusDrops() {}

    public static void register()
    {
        BonusDrops.register(BonusDrop.of(BibliomaniaTags.KNIVES, BibliomaniaTags.GRASS_FIBER_PLANTS, 0.2f,
                () -> new ItemStack(ItemRegistry.FIBERS_GRASS.get())));
    }
}
