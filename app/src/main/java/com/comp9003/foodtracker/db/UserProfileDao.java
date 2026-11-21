package com.comp9003.foodtracker.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

/** DAO for user profile data (display name, nickname, avatar). */
@Dao
public interface UserProfileDao {
    @Query("SELECT * FROM user_profiles WHERE username = :username LIMIT 1")
    UserProfileEntity getByUsername(String username);

    /** Insert-or-replace: creates the profile on first login and overwrites on subsequent updates. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(UserProfileEntity profile);
}
