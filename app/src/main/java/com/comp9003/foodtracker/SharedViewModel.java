package com.comp9003.foodtracker;

import androidx.lifecycle.ViewModel;
import java.util.HashSet;
import java.util.Set;

/**
 * ViewModel shared across MainActivity's fragments to preserve sort/filter/search state
 * across fragment transactions (e.g., Home ↔ Profile ↔ Stats navigation).
 */
public class SharedViewModel extends ViewModel {

    // ── Sort ─────────────────────────────────────────────────────────────────
    /** Active sort field: "recent" | "name" | "stock" | "expire" | "near_expire". */
    public String sortType = "recent";
    public boolean sortAsc = false;

    // ── Filter ───────────────────────────────────────────────────────────────
    public Set<String> selectedTags = new HashSet<>();
    /** Show only items expiring within this many days; 0 = disabled. */
    public int nearExpireThreshold = 0;
    /** Show only items that expired within this many days ago; 0 = disabled. */
    public int expiredThreshold = 0;

    /** Storage location filter keys: "room" | "chilled" | "frozen"; empty = no filter. */
    public Set<String> selectedStorages = new HashSet<>();
    /** Expiry status filter keys: "fresh" | "near_expiry" | "expired"; empty = no filter. */
    public Set<String> selectedStatuses = new HashSet<>();
    /** Tri-state opened filter: null = all, TRUE = opened only, FALSE = unopened only. */
    public Boolean filterOpened = null;
    /** Tri-state sharing filter: null = all, TRUE = shared only, FALSE = not-shared only. */
    public Boolean filterShared = null;
    /** When TRUE, show only items that belong to other family members (not the current user). */
    public Boolean filterFromOther = null;

    // ── Search ───────────────────────────────────────────────────────────────
    public String keyword = "";
}