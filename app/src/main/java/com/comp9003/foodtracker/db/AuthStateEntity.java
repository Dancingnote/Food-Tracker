package com.comp9003.foodtracker.db;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Room entity for the "auth_state" table.
 * This table always holds exactly one row (id = 1) that tracks the currently logged-in user.
 * It acts as a persistent alternative to SharedPreferences for session state.
 */
@Entity(tableName = "auth_state")
public class AuthStateEntity {
    /** Fixed primary key — always 1. Use REPLACE on insert to upsert the single row. */
    @PrimaryKey
    public int id = 1;

    /** Normalized username of the logged-in user, or null if no one is logged in. */
    public String currentUser;
}
