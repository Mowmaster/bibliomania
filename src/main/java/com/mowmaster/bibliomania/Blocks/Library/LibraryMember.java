package com.mowmaster.bibliomania.Blocks.Library;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

// A block that can be part of a library (Bookcases, Reading Lecterns, and later Card Catalogs): implemented on its
// block entity. Library Controllers find members by walking through touching members (and the ones linked with the
// Librarian's Quill), and tell each one which library it's in (setLibrary). Members don't look for controllers
// themselves; the controller re-checks every couple of seconds, so a member removed from a library is told too.
public interface LibraryMember extends LibraryLockable
{
    // The controller of the library it's in, or null
    @Nullable BlockPos getLibrary();

    void setLibrary(@Nullable BlockPos controller);

    // The controller block entity, if it's loaded and still counts this block as a member
    default @Nullable LibraryControllerBlockEntity library(@Nullable Level level, BlockPos self)
    {
        BlockPos controller = getLibrary();
        if (level == null || controller == null || !level.isLoaded(controller)) return null;
        if (level.getBlockEntity(controller) instanceof LibraryControllerBlockEntity library && library.isMember(self)) return library;
        return null;
    }
}
