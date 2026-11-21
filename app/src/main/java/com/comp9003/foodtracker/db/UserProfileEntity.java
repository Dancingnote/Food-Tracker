package com.comp9003.foodtracker.db;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Room entity for the "user_profiles" table.
 * Stores display metadata for each user; keyed by the same normalized username as UserEntity.
 */
@Entity(tableName = "user_profiles")
public class UserProfileEntity {
    /** Normalized username, matching UserEntity.username. */
    @PrimaryKey
    @NonNull
    public String username = "";

    /** Original-case username as entered at sign-up (used for display, not auth). */
    @NonNull
    public String displayUsername = "";

    /** Optional friendly nickname shown in greetings and shared-product tags. */
    @NonNull
    public String nickname = "";

    /** URI string of the user's avatar image, or null if no avatar is set. */
    public String avatarUri;
}
