package com.mowmaster.bibliomania.Blocks.Library;

// A library block whose settings can be locked with the Librarian's Seal (LibrarySealItem): while locked, its settings
// (dyeing, its links, card catalog drawer assignments...) can't be changed. Using it as normal still works.
// Implemented on block entities.
public interface LibraryLockable
{
    boolean isLocked();

    void setLocked(boolean locked);
}
