package com.comp9003.foodtracker.db;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/** Room entity for the "users" table. Stores login credentials; username is the primary key. */
@Entity(tableName = "users")
public class UserEntity {
    /** Normalized (lowercase) username — used as the primary key across all user-scoped tables. */
    @PrimaryKey
    @NonNull
    public String username = "";

    /** SHA-256 hex digest of "username:password". Never stores the plaintext password. */
    @NonNull
    public String passwordHash = "";
}
