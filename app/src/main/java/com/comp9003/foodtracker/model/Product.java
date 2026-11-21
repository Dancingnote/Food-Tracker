package com.comp9003.foodtracker.model;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;

/**
 * In-memory domain model for a tracked food item.
 * Implements Serializable so instances can be passed between activities via Intent extras.
 * This class is the canonical representation used throughout the UI layer; ProductEntity
 * is its Room-persistence counterpart.
 */
public class Product implements Serializable {

    public String name;
    public int quantity;
    public double price;

    /** The date this product was added to the tracker. */
    public LocalDate createdDate;
    /** Manufacturing / production date, optional. */
    public LocalDate productionDate;
    public LocalDate expireDate;
    public String imageUri;

    /** Category tags; the first tag drives category-specific expiry thresholds. */
    public ArrayList<String> tags;
    /** UI-only selection state for bulk actions. Not persisted. */
    public boolean isSelected = false;
    public boolean isArchived = false;
    /** True if the owner has opted to share this item with family members. */
    public boolean isShared = false;
    /** Room DB primary key — populated when loaded from Room, 0 otherwise. */
    public long entityId = 0;
    /** Raw username of the creator (persisted). Used for identity comparison. */
    public String sharedByUser = null;
    /** Display name shown in the "→ name" tag (transient, not persisted). */
    public String sharedByDisplayName = null;
    public String storageState = null;
    public Boolean isOpened = null;

    public Product(String name,
                   int quantity,
                   double price,
                   LocalDate createdDate,
                   LocalDate expireDate,
                   ArrayList<String> tags) {

        this(name, quantity, price, createdDate, null, expireDate, null, tags);
    }

    public Product(String name,
                   int quantity,
                   double price,
                   LocalDate createdDate,
                   LocalDate productionDate,
                   LocalDate expireDate,
                   String imageUri,
                   ArrayList<String> tags) {

        this.name = name;
        this.quantity = quantity;
        this.price = price;
        this.createdDate = createdDate;
        this.productionDate = productionDate;
        this.expireDate = expireDate;
        this.imageUri = imageUri;
        this.tags = tags;
    }

    // ================= LOGIC =================

    public boolean isExpired() {
        return expireDate.isBefore(LocalDate.now());
    }

    public int getDaysToExpire() {
        return (int) ChronoUnit.DAYS.between(LocalDate.now(), expireDate);
    }

    public int getDaysAfterExpire() {
        return (int) ChronoUnit.DAYS.between(expireDate, LocalDate.now());
    }
}
