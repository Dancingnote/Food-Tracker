package com.comp9003.foodtracker.db;

import android.content.Context;

import androidx.room.Database;
import androidx.room.migration.Migration;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.sqlite.db.SupportSQLiteDatabase;

/**
 * Room database singleton for FoodTracker.
 * Version history:
 *   1 → 2 : added users, user_profiles, and auth_state tables
 *   2 → 3 : added isShared column to products
 *   3 → 4 : added sharedByUser column to products
 */
@Database(
        entities = {ProductEntity.class, UserEntity.class, UserProfileEntity.class, AuthStateEntity.class},
        version = 4,
        exportSchema = false
)
public abstract class AppDatabase extends RoomDatabase {
    /** Volatile ensures the singleton is safely published across threads. */
    private static volatile AppDatabase INSTANCE;

    public abstract ProductDao productDao();
    public abstract UserDao userDao();
    public abstract UserProfileDao userProfileDao();
    public abstract AuthStateDao authStateDao();

    static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(SupportSQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS users (username TEXT NOT NULL, passwordHash TEXT NOT NULL, PRIMARY KEY(username))");
            db.execSQL("CREATE TABLE IF NOT EXISTS user_profiles (username TEXT NOT NULL, displayUsername TEXT NOT NULL, nickname TEXT NOT NULL, avatarUri TEXT, PRIMARY KEY(username))");
            db.execSQL("CREATE TABLE IF NOT EXISTS auth_state (id INTEGER NOT NULL, currentUser TEXT, PRIMARY KEY(id))");
        }
    };

    static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE products ADD COLUMN isShared INTEGER NOT NULL DEFAULT 0");
        }
    };

    static final Migration MIGRATION_3_4 = new Migration(3, 4) {
        @Override
        public void migrate(SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE products ADD COLUMN sharedByUser TEXT");
        }
    };

    /**
     * Returns the singleton database instance, creating it if necessary.
     * Uses double-checked locking for thread safety.
     * Note: allowMainThreadQueries() is enabled for simplicity; all DB calls
     * in this app are kept short enough not to cause ANR issues.
     */
    public static AppDatabase getInstance(Context context) {
        if (INSTANCE != null) return INSTANCE;
        synchronized (AppDatabase.class) {
            if (INSTANCE == null) {
                INSTANCE = Room.databaseBuilder(
                                context.getApplicationContext(),
                                AppDatabase.class,
                                "foodtracker.db"
                        )
                        .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                        .fallbackToDestructiveMigration()
                        .allowMainThreadQueries()
                        .build();
            }
            return INSTANCE;
        }
    }
}
