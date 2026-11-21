package com.comp9003.foodtracker.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

/** DAO for the users table. All usernames are expected to be normalized (lowercase) by the caller. */
@Dao
public interface UserDao {
    @Query("SELECT * FROM users WHERE username = :username LIMIT 1")
    UserEntity getByUsername(String username);

    /** Returns the number of accounts with this username (0 or 1). Used for existence checks. */
    @Query("SELECT COUNT(*) FROM users WHERE username = :username")
    int countByUsername(String username);

    /** Inserts a new user; throws on duplicate (used during sign-up to detect conflicts). */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    void insert(UserEntity user);

    /** Inserts a user only if not already present — used during migration from SharedPreferences. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void insertIgnore(UserEntity user);

    @Update
    void update(UserEntity user);

    /** Updates password hash for an existing user; returns the row count (0 if not found). */
    @Query("UPDATE users SET passwordHash = :passwordHash WHERE username = :username")
    int updatePassword(String username, String passwordHash);

    @Query("SELECT username FROM users ORDER BY username ASC")
    List<String> listUsernames();
}
