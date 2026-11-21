package com.comp9003.foodtracker;

import com.comp9003.foodtracker.db.AppDatabase;
import com.comp9003.foodtracker.db.AuthStateEntity;
import com.comp9003.foodtracker.db.ProductEntity;
import com.comp9003.foodtracker.db.UserEntity;
import com.comp9003.foodtracker.db.UserProfileEntity;
import com.comp9003.foodtracker.model.Product;
import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Central repository for all user, product, and profile data.
 *
 * Storage strategy: Room database is the primary store. Every operation tries Room first
 * and falls back to SharedPreferences for legacy compatibility. A one-time migration
 * (ensureUserStoreMigrated) moves SharedPreferences data into Room on the first launch
 * after the DB was added.
 *
 * The in-memory list {@code products} always reflects the current user's active product set
 * after {@link #loadCurrentUserProducts(Context)} or {@link #ensureReady(Context)} is called.
 */
public class ProductRepository {
    /** In-memory product list for the current user. Mutated directly by the UI layer. */
    public static ArrayList<Product> products = new ArrayList<>();

    /** In-memory cache to avoid repeated Room/SharedPreferences reads per getCurrentUser call. */
    private static volatile String cachedCurrentUser = null;

    private static final String PREF_AUTH = "auth_prefs";
    private static final String KEY_CURRENT_USER = "current_user";
    private static final String PREF_USERS = "users_prefs";
    private static final String PREF_PRODUCTS = "products_prefs";
    private static final String PREF_PROFILES = "profiles_prefs";
    private static final String PREF_ROOM_MIGRATION = "room_migration_prefs";
    /** Flag written after the one-time migration from SharedPreferences to Room completes. */
    private static final String KEY_USERS_MIGRATED = "users_profiles_migrated_v1";

    /**
     * Initializes the repository for the current session.
     * Should be called on app startup and before any product access after login.
     */
    public static void ensureReady(Context context) {
        ensureUserStoreMigrated(context);
        if (isLoggedIn(context)) {
            ensureProfileExists(context);
            loadCurrentUserProducts(context);
        } else {
            products.clear();
        }
    }

    public static boolean isLoggedIn(Context context) {
        return getCurrentUser(context) != null;
    }

    /**
     * Returns the normalized username of the current session, or null if no one is logged in.
     * Read order: memory cache → Room auth_state → SharedPreferences (legacy fallback).
     * "guest" is treated as no session for backwards compatibility with an old guest mode.
     */
    public static String getCurrentUser(Context context) {
        if (cachedCurrentUser != null) return cachedCurrentUser;
        ensureUserStoreMigrated(context);
        try {
            String fromDb = AppDatabase.getInstance(context).authStateDao().getCurrentUser();
            if (fromDb != null) {
                String normalized = fromDb.trim().toLowerCase(Locale.US);
                if (!normalized.isEmpty() && !"guest".equals(normalized)) {
                    cachedCurrentUser = normalized;
                    return normalized;
                }
            }
        } catch (Exception ignored) {
        }

        // Legacy fallback: read from SharedPreferences and back-fill Room for next time
        SharedPreferences auth = context.getSharedPreferences(PREF_AUTH, Context.MODE_PRIVATE);
        String user = auth.getString(KEY_CURRENT_USER, null);
        if (user == null) return null;
        String normalized = user.trim().toLowerCase(Locale.US);
        if ("guest".equals(normalized)) return null;

        try {
            AuthStateEntity state = new AuthStateEntity();
            state.id = 1;
            state.currentUser = normalized;
            AppDatabase.getInstance(context).authStateDao().upsert(state);
        } catch (Exception ignored) {
        }
        cachedCurrentUser = normalized;
        return normalized;
    }

    public static void setCurrentUser(Context context, String username) {
        ensureUserStoreMigrated(context);
        String normalized = normalizeUsername(username);
        cachedCurrentUser = normalized;
        SharedPreferences auth = context.getSharedPreferences(PREF_AUTH, Context.MODE_PRIVATE);
        auth.edit().putString(KEY_CURRENT_USER, normalized).apply();

        try {
            AuthStateEntity state = new AuthStateEntity();
            state.id = 1;
            state.currentUser = normalized;
            AppDatabase.getInstance(context).authStateDao().upsert(state);
        } catch (Exception ignored) {
        }
    }

    public static void logout(Context context) {
        cachedCurrentUser = null;
        ensureUserStoreMigrated(context);
        try {
            AppDatabase.getInstance(context).authStateDao().clear();
        } catch (Exception ignored) {
        }
        SharedPreferences auth = context.getSharedPreferences(PREF_AUTH, Context.MODE_PRIVATE);
        auth.edit().remove(KEY_CURRENT_USER).apply();
        products.clear();
    }

    public static boolean userExists(Context context, String username) {
        ensureUserStoreMigrated(context);
        String normalized = normalizeUsername(username);
        if (normalized.isEmpty()) return false;
        try {
            return AppDatabase.getInstance(context).userDao().countByUsername(normalized) > 0;
        } catch (Exception ignored) {
        }

        SharedPreferences users = context.getSharedPreferences(PREF_USERS, Context.MODE_PRIVATE);
        return users.contains(userKey(normalized));
    }

    public static boolean signUp(Context context, String username, String password) {
        ensureUserStoreMigrated(context);
        String displayUsername = username == null ? "" : username.trim();
        String normalized = normalizeUsername(displayUsername);
        if (normalized.isEmpty() || password == null || password.trim().isEmpty()) return false;

        try {
            AppDatabase db = AppDatabase.getInstance(context);
            if (db.userDao().countByUsername(normalized) > 0) return false;
            String hash = hashPassword(normalized, password);

            UserEntity user = new UserEntity();
            user.username = normalized;
            user.passwordHash = hash;
            db.userDao().insert(user);

            setCurrentUser(context, normalized);
            initProfile(context, normalized, displayUsername);
            products.clear();
            saveCurrentUserProducts(context);
            return true;
        } catch (Exception ignored) {
        }

        SharedPreferences users = context.getSharedPreferences(PREF_USERS, Context.MODE_PRIVATE);
        String key = userKey(normalized);
        if (users.contains(key)) return false;

        String hash = hashPassword(normalized, password);
        users.edit().putString(key, hash).apply();
        setCurrentUser(context, normalized);
        initProfile(context, normalized, displayUsername);
        products.clear();
        saveCurrentUserProducts(context);
        return true;
    }

    public static boolean login(Context context, String username, String password) {
        ensureUserStoreMigrated(context);
        String normalized = normalizeUsername(username);
        if (normalized.isEmpty() || password == null) return false;

        try {
            AppDatabase db = AppDatabase.getInstance(context);
            UserEntity user = db.userDao().getByUsername(normalized);
            if (user != null) {
                String hash = hashPassword(normalized, password);
                if (!hash.equals(user.passwordHash)) return false;

                setCurrentUser(context, normalized);
                ensureProfileExists(context);
                loadCurrentUserProducts(context);
                return true;
            }
        } catch (Exception ignored) {
        }

        SharedPreferences users = context.getSharedPreferences(PREF_USERS, Context.MODE_PRIVATE);
        String stored = users.getString(userKey(normalized), null);
        if (stored == null) return false;

        String hash = hashPassword(normalized, password);
        if (!stored.equals(hash)) return false;

        setCurrentUser(context, normalized);
        ensureProfileExists(context);
        loadCurrentUserProducts(context);
        return true;
    }

    public static String getDisplayUsername(Context context) {
        ensureUserStoreMigrated(context);
        String user = getCurrentUser(context);
        if (user == null) return "";
        try {
            UserProfileEntity profile = AppDatabase.getInstance(context).userProfileDao().getByUsername(user);
            if (profile != null && profile.displayUsername != null && !profile.displayUsername.trim().isEmpty()) {
                return profile.displayUsername;
            }
        } catch (Exception ignored) {
        }

        SharedPreferences prefs = context.getSharedPreferences(PREF_PROFILES, Context.MODE_PRIVATE);
        String raw = prefs.getString(profileKey(user), null);
        if (raw == null) return user;
        try {
            JSONObject obj = new JSONObject(raw);
            return obj.optString("displayUsername", user);
        } catch (Exception ignored) {
            return user;
        }
    }

    public static String getNickname(Context context) {
        ensureUserStoreMigrated(context);
        String user = getCurrentUser(context);
        if (user == null) return "";
        try {
            UserProfileEntity profile = AppDatabase.getInstance(context).userProfileDao().getByUsername(user);
            if (profile != null) {
                String nick = profile.nickname;
                if (nick != null && !nick.trim().isEmpty()) return nick;
            }
        } catch (Exception ignored) {
        }

        SharedPreferences prefs = context.getSharedPreferences(PREF_PROFILES, Context.MODE_PRIVATE);
        String raw = prefs.getString(profileKey(user), null);
        if (raw == null) return getDisplayUsername(context);
        try {
            JSONObject obj = new JSONObject(raw);
            String nick = obj.optString("nickname", "");
            if (nick == null || nick.trim().isEmpty()) return getDisplayUsername(context);
            return nick;
        } catch (Exception ignored) {
            return getDisplayUsername(context);
        }
    }

    public static void setNickname(Context context, String nickname) {
        ensureUserStoreMigrated(context);
        String user = getCurrentUser(context);
        if (user == null) return;
        String value = nickname == null ? "" : nickname.trim();
        try {
            AppDatabase db = AppDatabase.getInstance(context);
            UserProfileEntity profile = db.userProfileDao().getByUsername(user);
            if (profile == null) {
                profile = new UserProfileEntity();
                profile.username = user;
                profile.displayUsername = user;
                profile.nickname = value;
                profile.avatarUri = null;
            } else {
                profile.nickname = value;
            }
            db.userProfileDao().upsert(profile);
            return;
        } catch (Exception ignored) {
        }
        updateProfileField(context, user, "nickname", value);
    }

    public static String getAvatarUri(Context context) {
        ensureUserStoreMigrated(context);
        String user = getCurrentUser(context);
        if (user == null) return null;
        try {
            UserProfileEntity profile = AppDatabase.getInstance(context).userProfileDao().getByUsername(user);
            if (profile != null) {
                String uri = profile.avatarUri;
                if (uri == null || uri.trim().isEmpty() || "null".equals(uri)) return null;
                return uri;
            }
        } catch (Exception ignored) {
        }

        SharedPreferences prefs = context.getSharedPreferences(PREF_PROFILES, Context.MODE_PRIVATE);
        String raw = prefs.getString(profileKey(user), null);
        if (raw == null) return null;
        try {
            JSONObject obj = new JSONObject(raw);
            String uri = obj.optString("avatarUri", null);
            if (uri == null || uri.trim().isEmpty() || "null".equals(uri)) return null;
            return uri;
        } catch (Exception ignored) {
            return null;
        }
    }

    public static void setAvatarUri(Context context, String avatarUri) {
        ensureUserStoreMigrated(context);
        String user = getCurrentUser(context);
        if (user == null) return;
        try {
            AppDatabase db = AppDatabase.getInstance(context);
            UserProfileEntity profile = db.userProfileDao().getByUsername(user);
            if (profile == null) {
                profile = new UserProfileEntity();
                profile.username = user;
                profile.displayUsername = user;
                profile.nickname = "";
            }
            profile.avatarUri = avatarUri;
            db.userProfileDao().upsert(profile);
            return;
        } catch (Exception ignored) {
        }
        updateProfileField(context, user, "avatarUri", avatarUri);
    }

    public static boolean changePassword(Context context, String oldPassword, String newPassword) {
        ensureUserStoreMigrated(context);
        String user = getCurrentUser(context);
        if (user == null) return false;
        if (oldPassword == null || newPassword == null) return false;

        try {
            AppDatabase db = AppDatabase.getInstance(context);
            UserEntity entity = db.userDao().getByUsername(user);
            if (entity == null) return false;
            String oldHash = hashPassword(user, oldPassword);
            if (!oldHash.equals(entity.passwordHash)) return false;
            String newHash = hashPassword(user, newPassword);
            return db.userDao().updatePassword(user, newHash) > 0;
        } catch (Exception ignored) {
        }

        SharedPreferences users = context.getSharedPreferences(PREF_USERS, Context.MODE_PRIVATE);
        String key = userKey(user);
        String stored = users.getString(key, null);
        if (stored == null) return false;

        String oldHash = hashPassword(user, oldPassword);
        if (!stored.equals(oldHash)) return false;

        String newHash = hashPassword(user, newPassword);
        users.edit().putString(key, newHash).apply();
        return true;
    }

    public static void loadCurrentUserProducts(Context context) {
        String user = getCurrentUser(context);
        if (user == null) {
            products.clear();
            return;
        }

        products.clear();
        try {
            List<ProductEntity> entities = AppDatabase.getInstance(context).productDao().getAllForUser(user);
            if (entities != null && !entities.isEmpty()) {
                for (ProductEntity entity : entities) {
                    products.add(entity.toProduct());
                }
                return;
            }
        } catch (Exception ignored) {
        }

        SharedPreferences prefs = context.getSharedPreferences(PREF_PRODUCTS, Context.MODE_PRIVATE);
        String raw = prefs.getString(productsKey(user), null);
        if (raw == null || raw.trim().isEmpty()) return;

        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                Product p = JsonProductCodec.fromJson(obj);
                if (p != null) products.add(p);
            }
        } catch (Exception ignored) {
        }

        try {
            saveCurrentUserProducts(context);
        } catch (Exception ignored) {
        }
    }

    /**
     * Returns all active (non-archived) products that other registered users have marked as shared.
     * Each product has sharedByUser set to the owner's raw username, and sharedByDisplayName
     * set to their nickname/displayUsername for UI rendering.
     */
    public static List<Product> loadSharedFromOtherUsers(Context context) {
        List<Product> result = new ArrayList<>();
        String currentUser = getCurrentUser(context);
        if (currentUser == null) return result;
        try {
            String familyCode = getActiveFamilyCode(context, currentUser);
            if (familyCode == null) return result;

            Set<String> familyMembers = getFamilyMembers(context, familyCode);
            if (familyMembers.isEmpty()) return result;

            AppDatabase db = AppDatabase.getInstance(context);
            for (String username : familyMembers) {
                if (username == null) continue;
                if (username.equalsIgnoreCase(currentUser)) continue;

                // Resolve display name
                String displayName = username;
                try {
                    UserProfileEntity profile = db.userProfileDao().getByUsername(username);
                    if (profile != null) {
                        if (profile.nickname != null && !profile.nickname.trim().isEmpty()) {
                            displayName = profile.nickname.trim();
                        } else if (profile.displayUsername != null && !profile.displayUsername.trim().isEmpty()) {
                            displayName = profile.displayUsername.trim();
                        }
                    }
                } catch (Exception ignored) {}

                // Load that user's products and keep only shared + active ones
                try {
                    List<ProductEntity> entities = db.productDao().getAllForUser(username);
                    for (ProductEntity entity : entities) {
                        if (entity.isShared && !entity.isArchived) {
                            Product p = entity.toProduct();
                            p.sharedByUser = username;
                            p.sharedByDisplayName = displayName;
                            result.add(p);
                        }
                    }
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
        return result;
    }

    public static boolean isCurrentUserInFamily(Context context) {
        String currentUser = getCurrentUser(context);
        if (currentUser == null) return false;
        return getActiveFamilyCode(context, currentUser) != null;
    }

    public static boolean areUsersInSameFamily(Context context, String userA, String userB) {
        if (userA == null || userB == null) return false;
        String a = getActiveFamilyCode(context, userA);
        if (a == null) return false;
        String b = getActiveFamilyCode(context, userB);
        if (b == null) return false;
        return a.equalsIgnoreCase(b);
    }

    private static String getActiveFamilyCode(Context context, String username) {
        if (context == null || username == null) return null;
        SharedPreferences prefs = context.getSharedPreferences(FamilyFragment.PREFS_FAMILY, Context.MODE_PRIVATE);
        String code = prefs.getString(FamilyFragment.KEY_FAMILY_CODE + username, null);
        if (code == null) code = prefs.getString(FamilyFragment.KEY_JOINED_CODE + username, null);
        if (code == null) return null;
        code = code.trim().toUpperCase(Locale.US);
        return code.isEmpty() ? null : code;
    }

    private static Set<String> getFamilyMembers(Context context, String familyCode) {
        Set<String> members = new HashSet<>();
        if (context == null || familyCode == null) return members;
        SharedPreferences prefs = context.getSharedPreferences(FamilyFragment.PREFS_FAMILY, Context.MODE_PRIVATE);
        try {
            String json = prefs.getString(FamilyFragment.KEY_MEMBERS + familyCode, "[]");
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                String u = arr.optString(i, null);
                if (u != null && !u.trim().isEmpty()) members.add(u.trim().toLowerCase(Locale.US));
            }
        } catch (Exception ignored) {
        }
        return members;
    }

    /**
     * Persists an edit made by a group member to a shared product owned by another user.
     * Uses the Room primary key (entityId) to update just the one row.
     */
    public static void saveSharedProductEdit(Context context, Product updatedProduct, String ownerUsername) {
        if (updatedProduct == null || ownerUsername == null) return;
        if (updatedProduct.entityId <= 0) {
            android.util.Log.w("ProductRepository", "saveSharedProductEdit: entityId is 0, cannot update");
            return;
        }
        String currentUser = getCurrentUser(context);
        if (currentUser == null || !areUsersInSameFamily(context, currentUser, ownerUsername)) {
            android.util.Log.w("ProductRepository", "saveSharedProductEdit: blocked (not in same family)");
            return;
        }
        try {
            ProductEntity entity = ProductEntity.fromProduct(ownerUsername, updatedProduct);
            entity.id = updatedProduct.entityId; // preserve the primary key for @Update
            AppDatabase.getInstance(context).productDao().update(entity);
        } catch (Exception e) {
            android.util.Log.e("ProductRepository", "saveSharedProductEdit failed", e);
        }
    }

    public static void saveCurrentUserProducts(Context context) {
        String user = getCurrentUser(context);
        if (user == null) return;

        try {
            ArrayList<ProductEntity> entities = new ArrayList<>();
            for (Product p : products) {
                if (p != null) entities.add(ProductEntity.fromProduct(user, p));
            }
            AppDatabase.getInstance(context).productDao().replaceAllForUser(user, entities);
            return;
        } catch (Exception ignored) {
        }

        JSONArray arr = new JSONArray();
        for (Product p : products) {
            JSONObject obj = JsonProductCodec.toJson(p);
            if (obj != null) arr.put(obj);
        }

        SharedPreferences prefs = context.getSharedPreferences(PREF_PRODUCTS, Context.MODE_PRIVATE);
        prefs.edit().putString(productsKey(user), arr.toString()).apply();
    }

    /**
     * Returns the expiry state of a product:
     *   0 = Fresh, 1 = Near expiry, 2 = Expired.
     * "Near expiry" uses a category-specific threshold (see thresholdDaysByCategory).
     */
    public static int getExpiryState(Context context, Product product) {
        if (product == null || product.expireDate == null) return 0;

        java.time.LocalDate today = java.time.LocalDate.now();
        if (product.expireDate.isBefore(today)) return 2;

        SharedPreferences prefs = context.getSharedPreferences("near_expiry_prefs", Context.MODE_PRIVATE);
        boolean enabled = prefs.getBoolean("enabled", true);
        if (!enabled) return 0;

        int daysToExpire = (int) java.time.temporal.ChronoUnit.DAYS.between(today, product.expireDate);
        int thresholdDays = thresholdDaysByCategory(product);

        return daysToExpire <= thresholdDays ? 1 : 0;
    }

    public static String getExpiryStateLabel(Context context, Product product) {
        int state = getExpiryState(context, product);
        if (state == 2) return "Expired";
        if (state == 1) return "Near expiry";
        return null;
    }

    /**
     * Returns the "near expiry" warning threshold in days for the product's primary category.
     * Items within this many days of expiry are shown with the "Near expiry" badge.
     */
    public static int thresholdDaysByCategory(Product product) {
        String tag = (product.tags != null && !product.tags.isEmpty()) ? product.tags.get(0) : "";
        if ("Fruit".equalsIgnoreCase(tag)) return 3;
        if ("Vegetable".equalsIgnoreCase(tag)) return 3;
        if ("Meat".equalsIgnoreCase(tag)) return 1;
        if ("Seafood".equalsIgnoreCase(tag)) return 1;
        if ("Dairy".equalsIgnoreCase(tag)) return 2;
        if ("Bakery".equalsIgnoreCase(tag)) return 1;
        if ("Snack".equalsIgnoreCase(tag)) return 30;
        if ("Frozen".equalsIgnoreCase(tag)) return 30;
        if ("Drink".equalsIgnoreCase(tag)) return 30;
        if ("Healthy".equalsIgnoreCase(tag)) return 90;
        if ("Sweet".equalsIgnoreCase(tag)) return 30;
        if ("Spicy".equalsIgnoreCase(tag)) return 60;
        return 15;
    }

    private static String normalizeUsername(String username) {
        if (username == null) return "";
        return username.trim().toLowerCase(Locale.US);
    }

    private static String userKey(String normalizedUsername) {
        return "u:" + normalizedUsername;
    }

    private static String productsKey(String normalizedUsername) {
        return "p:" + normalizedUsername;
    }

    private static String profileKey(String normalizedUsername) {
        return "profile:" + normalizedUsername;
    }

    private static void initProfile(Context context, String normalizedUsername, String displayUsername) {
        ensureUserStoreMigrated(context);
        try {
            UserProfileEntity profile = new UserProfileEntity();
            profile.username = normalizedUsername;
            profile.displayUsername = displayUsername == null || displayUsername.trim().isEmpty()
                    ? normalizedUsername
                    : displayUsername.trim();
            profile.nickname = displayUsername == null ? "" : displayUsername.trim();
            profile.avatarUri = null;
            AppDatabase.getInstance(context).userProfileDao().upsert(profile);
        } catch (Exception ignored) {
        }

        SharedPreferences prefs = context.getSharedPreferences(PREF_PROFILES, Context.MODE_PRIVATE);
        JSONObject obj = new JSONObject();
        try {
            obj.put("displayUsername", displayUsername == null || displayUsername.trim().isEmpty() ? normalizedUsername : displayUsername.trim());
            obj.put("nickname", displayUsername == null ? "" : displayUsername.trim());
            obj.put("avatarUri", JSONObject.NULL);
        } catch (Exception ignored) {
        }
        prefs.edit().putString(profileKey(normalizedUsername), obj.toString()).apply();
    }

    private static void ensureProfileExists(Context context) {
        ensureUserStoreMigrated(context);
        String user = getCurrentUser(context);
        if (user == null) return;

        try {
            UserProfileEntity profile = AppDatabase.getInstance(context).userProfileDao().getByUsername(user);
            if (profile != null) return;
            initProfile(context, user, user);
            return;
        } catch (Exception ignored) {
        }

        SharedPreferences prefs = context.getSharedPreferences(PREF_PROFILES, Context.MODE_PRIVATE);
        String key = profileKey(user);
        if (prefs.contains(key)) return;
        initProfile(context, user, user);
    }

    private static void updateProfileField(Context context, String normalizedUsername, String field, String value) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_PROFILES, Context.MODE_PRIVATE);
        String key = profileKey(normalizedUsername);
        String raw = prefs.getString(key, null);
        JSONObject obj;
        try {
            obj = raw == null ? new JSONObject() : new JSONObject(raw);
        } catch (Exception e) {
            obj = new JSONObject();
        }
        try {
            if (value == null) {
                obj.put(field, JSONObject.NULL);
            } else {
                obj.put(field, value);
            }
        } catch (Exception ignored) {
        }
        prefs.edit().putString(key, obj.toString()).apply();
    }

    /**
     * One-time migration from SharedPreferences to Room.
     * Copies user accounts, profiles, and the active session into Room, then writes a flag
     * so this method becomes a no-op on all subsequent calls.
     */
    private static void ensureUserStoreMigrated(Context context) {
        if (context == null) return;
        SharedPreferences flags = context.getSharedPreferences(PREF_ROOM_MIGRATION, Context.MODE_PRIVATE);
        if (flags.getBoolean(KEY_USERS_MIGRATED, false)) return;

        try {
            AppDatabase db = AppDatabase.getInstance(context);

            SharedPreferences users = context.getSharedPreferences(PREF_USERS, Context.MODE_PRIVATE);
            Map<String, ?> userAll = users.getAll();
            for (String key : userAll.keySet()) {
                if (key == null || !key.startsWith("u:")) continue;
                String username = key.substring(2);
                Object value = userAll.get(key);
                if (!(value instanceof String)) continue;
                UserEntity entity = new UserEntity();
                entity.username = username;
                entity.passwordHash = (String) value;
                db.userDao().insertIgnore(entity);
            }

            SharedPreferences profiles = context.getSharedPreferences(PREF_PROFILES, Context.MODE_PRIVATE);
            Map<String, ?> profileAll = profiles.getAll();
            for (String key : profileAll.keySet()) {
                if (key == null || !key.startsWith("profile:")) continue;
                String username = key.substring("profile:".length());
                Object value = profileAll.get(key);
                if (!(value instanceof String)) continue;
                try {
                    JSONObject obj = new JSONObject((String) value);
                    UserProfileEntity profile = new UserProfileEntity();
                    profile.username = username;
                    profile.displayUsername = obj.optString("displayUsername", username);
                    profile.nickname = obj.optString("nickname", "");
                    String avatar = obj.isNull("avatarUri") ? null : obj.optString("avatarUri", null);
                    if (avatar != null && ("null".equals(avatar) || avatar.trim().isEmpty())) avatar = null;
                    profile.avatarUri = avatar;
                    db.userProfileDao().upsert(profile);
                } catch (Exception ignored) {
                }
            }

            SharedPreferences auth = context.getSharedPreferences(PREF_AUTH, Context.MODE_PRIVATE);
            String current = auth.getString(KEY_CURRENT_USER, null);
            if (current != null) {
                String normalized = current.trim().toLowerCase(Locale.US);
                if ("guest".equals(normalized) || normalized.isEmpty()) {
                    normalized = null;
                }
                AuthStateEntity state = new AuthStateEntity();
                state.id = 1;
                state.currentUser = normalized;
                db.authStateDao().upsert(state);
            }

            flags.edit().putBoolean(KEY_USERS_MIGRATED, true).apply();
        } catch (Exception ignored) {
        }
    }

    /**
     * Computes the stored password hash as SHA-256("username:password").
     * The username is included as a salt to prevent cross-user hash collisions for the same password.
     * Falls back to a plaintext "username:password" string if SHA-256 is unavailable (should never happen on Android).
     */
    private static String hashPassword(String normalizedUsername, String password) {
        try {
            String input = normalizedUsername + ":" + password;
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format(Locale.US, "%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return normalizedUsername + ":" + password;
        }
    }

    /**
     * Normalizes legacy Chinese storage state strings to English.
     * Old versions of the app stored these values in Chinese; they are translated on read
     * so that filter/display logic only needs to handle the English canonical values.
     */
    private static String normalizeStorageState(String raw) {
        if (raw == null) return null;
        String value = raw.trim();
        if (value.isEmpty() || "null".equals(value)) return null;
        if ("\u5e38\u6e29".equals(value)) return "Room temp"; // \u5e38\u6e29
        if ("\u51b7\u85cf".equals(value)) return "Chilled";   // \u51b7\u85cf
        if ("\u51b7\u51bb".equals(value)) return "Frozen";    // \u51b7\u51bb
        return value;
    }

    private static final class JsonProductCodec {
        private static JSONObject toJson(Product product) {
            if (product == null) return null;
            try {
                JSONObject obj = new JSONObject();
                obj.put("name", product.name);
                obj.put("quantity", product.quantity);
                obj.put("price", product.price);
                obj.put("createdDate", product.createdDate == null ? JSONObject.NULL : product.createdDate.toString());
                obj.put("productionDate", product.productionDate == null ? JSONObject.NULL : product.productionDate.toString());
                obj.put("expireDate", product.expireDate == null ? JSONObject.NULL : product.expireDate.toString());
                obj.put("isArchived", product.isArchived);
                obj.put("isShared", product.isShared);
                obj.put("sharedByUser", product.sharedByUser == null ? JSONObject.NULL : product.sharedByUser);
                obj.put("imageUri", product.imageUri == null ? JSONObject.NULL : product.imageUri);
                obj.put("storageState", product.storageState == null ? JSONObject.NULL : product.storageState);
                obj.put("isOpened", product.isOpened == null ? JSONObject.NULL : product.isOpened);

                JSONArray tags = new JSONArray();
                if (product.tags != null) {
                    for (String t : product.tags) tags.put(t);
                }
                obj.put("tags", tags);
                return obj;
            } catch (Exception e) {
                return null;
            }
        }

        private static Product fromJson(JSONObject obj) {
            if (obj == null) return null;
            try {
                String name = obj.optString("name", "");
                int quantity = obj.optInt("quantity", 1);
                double price = obj.optDouble("price", 0.0);

                String created = obj.optString("createdDate", null);
                String production = obj.optString("productionDate", null);
                String expire = obj.optString("expireDate", null);
                String imageUri = obj.optString("imageUri", null);

                java.time.LocalDate createdDate = created == null || "null".equals(created) || created.isEmpty()
                        ? java.time.LocalDate.now()
                        : java.time.LocalDate.parse(created);
                java.time.LocalDate productionDate = production == null || "null".equals(production) || production.isEmpty()
                        ? null
                        : java.time.LocalDate.parse(production);
                java.time.LocalDate expireDate = expire == null || "null".equals(expire) || expire.isEmpty()
                        ? createdDate.plusDays(7)
                        : java.time.LocalDate.parse(expire);
                if (imageUri != null && ("null".equals(imageUri) || imageUri.isEmpty())) {
                    imageUri = null;
                }

                ArrayList<String> tags = new ArrayList<>();
                JSONArray arr = obj.optJSONArray("tags");
                if (arr != null) {
                    for (int i = 0; i < arr.length(); i++) {
                        tags.add(arr.optString(i));
                    }
                }
                if (tags.isEmpty()) tags.add("Fruit");

                Product p = new Product(name, quantity, price, createdDate, productionDate, expireDate, imageUri, tags);
                p.isArchived = obj.optBoolean("isArchived", false);
                p.isShared   = obj.optBoolean("isShared", false);
                String sbu = obj.isNull("sharedByUser") ? null : obj.optString("sharedByUser", null);
                p.sharedByUser = (sbu == null || sbu.isEmpty()) ? null : sbu;
                p.isSelected = false;
                p.storageState = normalizeStorageState(obj.isNull("storageState") ? null : obj.optString("storageState", null));
                if (!obj.has("isOpened") || obj.isNull("isOpened")) {
                    p.isOpened = null;
                } else {
                    p.isOpened = obj.optBoolean("isOpened", false);
                }
                return p;
            } catch (Exception e) {
                return null;
            }
        }
    }
}
