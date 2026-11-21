package com.comp9003.foodtracker.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;
import androidx.room.Update;

import java.util.List;

/** DAO for CRUD operations on the products table. */
@Dao
public interface ProductDao {
    /** Returns all products owned by the given user, ordered by insertion time. */
    @Query("SELECT * FROM products WHERE username = :username ORDER BY id ASC")
    List<ProductEntity> getAllForUser(String username);

    @Query("DELETE FROM products WHERE username = :username")
    void deleteAllForUser(String username);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<ProductEntity> products);

    /** Updates a single product row, matched by its primary key. */
    @Update
    void update(ProductEntity entity);

    /**
     * Atomically replaces all products for a user with a fresh list.
     * The @Transaction annotation ensures the delete+insert pair is never partially visible.
     */
    @Transaction
    default void replaceAllForUser(String username, List<ProductEntity> products) {
        deleteAllForUser(username);
        insertAll(products);
    }
}
