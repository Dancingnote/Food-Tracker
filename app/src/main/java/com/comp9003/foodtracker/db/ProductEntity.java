package com.comp9003.foodtracker.db;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import com.comp9003.foodtracker.model.Product;

import org.json.JSONArray;

import java.time.LocalDate;
import java.util.ArrayList;

/**
 * Room entity for the "products" table.
 * Dates are stored as epoch-day longs (days since 1970-01-01) for reliable SQLite serialization.
 * Tags are stored as a JSON array string; isOpened uses Integer (1/0/null) to represent
 * a tri-state (opened / unopened / unknown) since Room cannot encode Boolean null.
 */
@Entity(
        tableName = "products",
        indices = {
                @Index(value = {"username"})  // speeds up per-user queries
        }
)
public class ProductEntity {
    @PrimaryKey(autoGenerate = true)
    public long id;

    /** Normalized username of the owner. */
    @NonNull
    public String username = "";

    @NonNull
    public String name = "";

    public int quantity;
    public double price;

    /** Days since epoch for createdDate. */
    public long createdDateEpochDay;
    /** Null when no production date is set (productionDate is optional). */
    public Long productionDateEpochDay;
    public long expireDateEpochDay;

    public String imageUri;
    /** Tags serialized as a JSON array, e.g. ["Fruit","Healthy"]. */
    public String tagsJson;

    public boolean isArchived;
    public String storageState;
    /** Tri-state opened flag: 1 = opened, 0 = unopened, null = not specified. */
    public Integer isOpened;
    /** True if the owner has marked this item visible to family members. */
    public boolean isShared;
    /** Normalized username of the original creator (set when item was shared). */
    public String sharedByUser;

    /** Converts a Product domain object to a Room entity bound to the given username. */
    @NonNull
    public static ProductEntity fromProduct(@NonNull String username, @NonNull Product product) {
        ProductEntity entity = new ProductEntity();
        entity.username = username;
        entity.name = product.name == null ? "" : product.name;
        entity.quantity = product.quantity;
        entity.price = product.price;

        LocalDate created = product.createdDate != null ? product.createdDate : LocalDate.now();
        entity.createdDateEpochDay = created.toEpochDay();
        entity.productionDateEpochDay = product.productionDate == null ? null : product.productionDate.toEpochDay();
        LocalDate expire = product.expireDate != null ? product.expireDate : created.plusDays(7);
        entity.expireDateEpochDay = expire.toEpochDay();

        entity.imageUri = product.imageUri;
        entity.tagsJson = tagsToJson(product.tags);

        entity.isArchived = product.isArchived;
        entity.storageState = product.storageState;
        if (product.isOpened == null) {
            entity.isOpened = null;
        } else {
            entity.isOpened = product.isOpened ? 1 : 0;
        }
        entity.isShared = product.isShared;
        entity.sharedByUser = product.sharedByUser;
        return entity;
    }

    /** Converts this Room entity back to a Product domain object. */
    @NonNull
    public Product toProduct() {
        LocalDate created = LocalDate.ofEpochDay(createdDateEpochDay);
        LocalDate production = productionDateEpochDay == null ? null : LocalDate.ofEpochDay(productionDateEpochDay);
        LocalDate expire = LocalDate.ofEpochDay(expireDateEpochDay);

        ArrayList<String> tags = tagsFromJson(tagsJson);
        // Guarantee at least one tag so the adapter never renders an empty category label.
        if (tags.isEmpty()) tags.add("Fruit");

        Product product = new Product(
                name,
                quantity,
                price,
                created,
                production,
                expire,
                imageUri,
                tags
        );
        product.isArchived = isArchived;
        product.isSelected = false;
        product.storageState = storageState;
        if (isOpened == null) {
            product.isOpened = null;
        } else {
            product.isOpened = isOpened == 1;
        }
        product.isShared = isShared;
        product.sharedByUser = sharedByUser;
        product.entityId = this.id;   // carry Room PK so callers can update by ID
        return product;
    }

    private static String tagsToJson(ArrayList<String> tags) {
        JSONArray arr = new JSONArray();
        if (tags != null) {
            for (String t : tags) arr.put(t);
        }
        return arr.toString();
    }

    @NonNull
    private static ArrayList<String> tagsFromJson(String json) {
        ArrayList<String> tags = new ArrayList<>();
        if (json == null || json.trim().isEmpty()) return tags;
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                String t = arr.optString(i, null);
                if (t != null && !t.trim().isEmpty()) tags.add(t);
            }
        } catch (Exception ignored) {
        }
        return tags;
    }
}
