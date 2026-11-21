package com.comp9003.foodtracker.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

/** DAO for the single-row auth_state table that tracks the currently logged-in user. */
@Dao
public interface AuthStateDao {
    /** Returns the normalized username of the active session, or null if no one is logged in. */
    @Query("SELECT currentUser FROM auth_state WHERE id = 1")
    String getCurrentUser();

    /** Insert-or-replace the single session row (id is always 1). */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(AuthStateEntity state);

    /** Removes the session row — called on logout. */
    @Query("DELETE FROM auth_state")
    void clear();
}
