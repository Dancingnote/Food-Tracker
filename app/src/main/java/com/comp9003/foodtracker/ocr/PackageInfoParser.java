package com.comp9003.foodtracker.ocr;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns OCR text from a food package into form values (name, dates, price,
 * category, storage). Pure Java so it can be unit-tested without Android.
 */
public final class PackageInfoParser {

    /** One recognised line of text plus its on-image height (bigger = larger font). */
    public static final class Line {
        public final String text;
        public final int height;

        public Line(String text, int height) {
            this.text = text == null ? "" : text.trim();
            this.height = height;
        }
    }

    public static final class Result {
        public String name;
        public LocalDate productionDate;
        public LocalDate expireDate;
        public Double price;
        public String category;
        public String storage;

        public boolean isEmpty() {
            return name == null && productionDate == null && expireDate == null
                    && price == null && category == null && storage == null;
        }
    }

    public static final String STORAGE_ROOM = "Room temp";
    public static final String STORAGE_CHILLED = "Chilled";
    public static final String STORAGE_FROZEN = "Frozen";

    private PackageInfoParser() {}

    // ── Dates ────────────────────────────────────────────────────────────────

    // 2026-11-21, 2026/11/21, 2026.11.21, 2026年11月21日, 2026 11 21
    private static final Pattern YMD = Pattern.compile(
            "(20\\d{2})\\s*[-/.年\\s]\\s*(\\d{1,2})\\s*[-/.月\\s]\\s*(\\d{1,2})\\s*日?");
    // 21/11/2026, 21.11.2026, 21-11-2026
    private static final Pattern DMY = Pattern.compile(
            "(?<!\\d)(\\d{1,2})\\s*[-/.]\\s*(\\d{1,2})\\s*[-/.]\\s*(20\\d{2})");
    // 20261121
    private static final Pattern COMPACT = Pattern.compile("(?<!\\d)(20\\d{2})(\\d{2})(\\d{2})(?!\\d)");
    // 21 NOV 2026 / 21NOV26
    private static final Pattern D_MON_Y = Pattern.compile(
            "(?i)(?<!\\d)(\\d{1,2})\\s*(JAN|FEB|MAR|APR|MAY|JUN|JUL|AUG|SEP|OCT|NOV|DEC)[A-Z]*\\.?\\s*(20\\d{2}|\\d{2})(?!\\d)");

    private static final Pattern PRODUCTION_KEY = Pattern.compile(
            "(?i)(生产日期|生产|制造日期|包装日期|出厂|MFG|MFD|PROD|PRODUCED|MANUFACTURED|PACKED|PKD|\\bP\\s*[:：])");
    private static final Pattern EXPIRY_KEY = Pattern.compile(
            "(?i)(到期|有效期至|保质期至|失效|截止|EXP|EXPIRY|EXPIRES|BEST\\s*BEFORE|BEST\\s*BY|USE\\s*BY|BBE|\\bBB\\b|\\bE\\s*[:：])");

    // 保质期12个月 / 保质期：180天 / shelf life 18 months
    private static final Pattern SHELF_LIFE = Pattern.compile(
            "(?i)(保质期|SHELF\\s*LIFE)[^0-9]{0,6}(\\d{1,4})\\s*(个月|月|天|日|年|MONTHS?|DAYS?|YEARS?)");

    // ── Price ────────────────────────────────────────────────────────────────

    private static final Pattern PRICE_SYMBOL = Pattern.compile("[¥￥$€£]\\s*(\\d{1,5}(?:[.,]\\d{1,2})?)");
    private static final Pattern PRICE_KEY = Pattern.compile(
            "(?i)(售价|价格|零售价|PRICE|RRP)[^0-9]{0,6}(\\d{1,5}(?:[.,]\\d{1,2})?)");

    // ── Lines that are never the product name ───────────────────────────────

    private static final Pattern NOT_NAME = Pattern.compile(
            "(?i)(生产|保质|日期|配料|成分|净含量|规格|贮存|储存|地址|电话|厂家|厂商|许可证|标准|营养|能量|蛋白质|脂肪|碳水|钠|"
                    + "INGREDIENT|NUTRITION|ENERGY|PROTEIN|FAT|CARBOHYDRATE|SODIUM|NET\\s*(WT|WEIGHT)|"
                    + "STORE|KEEP|BEST|EXP|MFG|BATCH|LOT|WWW\\.|HTTP|TEL|BARCODE|MADE IN|PRODUCT OF)");

    public static Result parse(List<Line> lines, LocalDate today) {
        Result r = new Result();
        if (lines == null || lines.isEmpty()) return r;

        StringBuilder all = new StringBuilder();
        for (Line l : lines) all.append(l.text).append('\n');
        String full = all.toString();

        parseDates(lines, today, r);
        parseShelfLife(full, r);
        r.price = parsePrice(full);
        r.name = pickName(lines);
        r.category = guessCategory(r.name, full);
        r.storage = guessStorage(full);
        return r;
    }

    private static void parseDates(List<Line> lines, LocalDate today, Result r) {
        List<LocalDate> unlabeled = new ArrayList<>();
        String pendingKey = null; // keyword on a line with no date applies to the next line

        for (Line line : lines) {
            String text = line.text;
            List<LocalDate> dates = findDates(text);
            boolean isProd = PRODUCTION_KEY.matcher(text).find();
            boolean isExp = EXPIRY_KEY.matcher(text).find();

            if (dates.isEmpty()) {
                if (isExp) pendingKey = "exp";
                else if (isProd) pendingKey = "prod";
                continue;
            }

            if (isProd && isExp && dates.size() >= 2) {
                // e.g. "MFG 2026-01-01 EXP 2026-12-31" — order of keywords decides
                int pIdx = indexOf(PRODUCTION_KEY, text);
                int eIdx = indexOf(EXPIRY_KEY, text);
                LocalDate first = dates.get(0), second = dates.get(1);
                if (pIdx <= eIdx) { setProd(r, first); setExp(r, second); }
                else { setExp(r, first); setProd(r, second); }
            } else if (isExp || (!isProd && "exp".equals(pendingKey))) {
                setExp(r, dates.get(0));
            } else if (isProd || "prod".equals(pendingKey)) {
                setProd(r, dates.get(0));
            } else {
                unlabeled.addAll(dates);
            }
            pendingKey = null;
        }

        // Fall back to unlabeled dates: earliest = production, latest = expiry
        if (!unlabeled.isEmpty()) {
            unlabeled.sort(null);
            if (r.productionDate == null && r.expireDate == null) {
                if (unlabeled.size() >= 2) {
                    r.productionDate = unlabeled.get(0);
                    r.expireDate = unlabeled.get(unlabeled.size() - 1);
                } else {
                    LocalDate only = unlabeled.get(0);
                    if (only.isAfter(today)) r.expireDate = only;
                    else r.productionDate = only;
                }
            } else if (r.expireDate == null) {
                LocalDate last = unlabeled.get(unlabeled.size() - 1);
                if (r.productionDate == null || last.isAfter(r.productionDate)) r.expireDate = last;
            } else if (r.productionDate == null) {
                LocalDate first = unlabeled.get(0);
                if (first.isBefore(r.expireDate)) r.productionDate = first;
            }
        }

        if (r.productionDate != null && r.productionDate.isAfter(today)) {
            // Production can't be in the future — most likely a misread
            r.productionDate = null;
        }
    }

    private static void setProd(Result r, LocalDate d) { if (r.productionDate == null) r.productionDate = d; }
    private static void setExp(Result r, LocalDate d) { if (r.expireDate == null) r.expireDate = d; }

    private static int indexOf(Pattern p, String s) {
        Matcher m = p.matcher(s);
        return m.find() ? m.start() : Integer.MAX_VALUE;
    }

    static List<LocalDate> findDates(String text) {
        List<int[]> spans = new ArrayList<>();
        List<LocalDate> out = new ArrayList<>();
        // Ordered by specificity; skip matches overlapping an earlier hit
        collect(YMD, text, spans, out, m -> date(m.group(1), m.group(2), m.group(3)));
        collect(DMY, text, spans, out, m -> date(m.group(3), m.group(2), m.group(1)));
        collect(D_MON_Y, text, spans, out, m -> {
            String y = m.group(3);
            if (y.length() == 2) y = "20" + y;
            return date(y, String.valueOf(monthIndex(m.group(2))), m.group(1));
        });
        collect(COMPACT, text, spans, out, m -> date(m.group(1), m.group(2), m.group(3)));
        // Restore reading order
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < out.size(); i++) order.add(i);
        order.sort((a, b) -> Integer.compare(spans.get(a)[0], spans.get(b)[0]));
        List<LocalDate> sorted = new ArrayList<>();
        for (int i : order) sorted.add(out.get(i));
        return sorted;
    }

    private interface DateFn { LocalDate apply(Matcher m); }

    private static void collect(Pattern p, String text, List<int[]> spans, List<LocalDate> out, DateFn fn) {
        Matcher m = p.matcher(text);
        while (m.find()) {
            boolean overlaps = false;
            for (int[] s : spans) {
                if (m.start() < s[1] && m.end() > s[0]) { overlaps = true; break; }
            }
            if (overlaps) continue;
            LocalDate d = fn.apply(m);
            if (d == null) continue;
            spans.add(new int[]{m.start(), m.end()});
            out.add(d);
        }
    }

    private static int monthIndex(String mon) {
        String[] names = {"JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC"};
        String up = mon.substring(0, 3).toUpperCase(Locale.ROOT);
        for (int i = 0; i < names.length; i++) if (names[i].equals(up)) return i + 1;
        return 0;
    }

    private static LocalDate date(String y, String m, String d) {
        try {
            int yy = Integer.parseInt(y), mm = Integer.parseInt(m), dd = Integer.parseInt(d);
            if (yy < 2000 || yy > 2100 || mm < 1 || mm > 12 || dd < 1 || dd > 31) return null;
            return LocalDate.of(yy, mm, dd);
        } catch (Exception e) {
            return null;
        }
    }

    private static void parseShelfLife(String full, Result r) {
        if (r.expireDate != null || r.productionDate == null) return;
        Matcher m = SHELF_LIFE.matcher(full);
        if (!m.find()) return;
        int n;
        try { n = Integer.parseInt(m.group(2)); } catch (Exception e) { return; }
        String unit = m.group(3).toUpperCase(Locale.ROOT);
        if (unit.startsWith("年") || unit.startsWith("YEAR")) r.expireDate = r.productionDate.plusYears(n);
        else if (unit.contains("月") || unit.startsWith("MONTH")) r.expireDate = r.productionDate.plusMonths(n);
        else r.expireDate = r.productionDate.plusDays(n);
    }

    static Double parsePrice(String full) {
        Matcher m = PRICE_SYMBOL.matcher(full);
        String raw = m.find() ? m.group(1) : null;
        if (raw == null) {
            Matcher k = PRICE_KEY.matcher(full);
            if (k.find()) raw = k.group(2);
        }
        if (raw == null) return null;
        try {
            double v = Double.parseDouble(raw.replace(',', '.'));
            return v > 0 ? v : null;
        } catch (Exception e) {
            return null;
        }
    }

    /** The product name is usually the biggest text on the label. */
    static String pickName(List<Line> lines) {
        Line best = null;
        for (Line l : lines) {
            String t = l.text;
            if (t.length() < 2 || t.length() > 30) continue;
            if (NOT_NAME.matcher(t).find()) continue;
            if (!findDates(t).isEmpty()) continue;
            int letters = 0;
            for (int i = 0; i < t.length(); i++) {
                if (Character.isLetter(t.charAt(i))) letters++;
            }
            if (letters * 2 < t.length()) continue; // mostly digits / symbols
            if (best == null || l.height > best.height) best = l;
        }
        return best == null ? null : best.text;
    }

    // {category, Chinese keywords, English whole words}
    private static final String[][] CATEGORY_KEYWORDS = {
            {"Dairy", "牛奶|酸奶|奶酪|芝士|乳制品|乳饮|黄油|奶油", "MILK|YOGH?URT|CHEESE|BUTTER|CREAM|DAIRY"},
            {"Bakery", "面包|吐司|蛋糕|糕点|欧包", "BREAD|TOAST|CAKES?|BAGELS?|CROISSANTS?|MUFFINS?|BUNS?"},
            {"Drink", "饮料|饮品|果汁|矿泉水|纯净水|茶饮|咖啡|可乐|汽水", "JUICE|WATER|DRINKS?|BEVERAGE|TEA|COFFEE|COLA|SODA"},
            {"Seafood", "鱼|虾|蟹|贝|海鲜", "FISH|SHRIMPS?|PRAWNS?|SALMON|TUNA|CRAB|SEAFOOD"},
            {"Meat", "牛肉|猪肉|鸡肉|鸡胸|鸭|羊肉|火腿|香肠|培根", "BEEF|PORK|CHICKEN|LAMB|HAM|SAUSAGES?|BACON|MEAT"},
            {"Frozen", "速冻|冷冻食品|冰淇淋|雪糕|水饺|汤圆", "FROZEN|ICE CREAM|DUMPLINGS?"},
            {"Sweet", "巧克力|糖果|甜品", "CHOCOLATES?|CANDY|SWEETS?|DESSERTS?|COOKIES?"},
            {"Snack", "薯片|饼干|零食|坚果|膨化", "CHIPS|CRISPS|BISCUITS?|CRACKERS?|SNACKS?|NUTS"},
            {"Spicy", "麻辣|香辣|辣条|辣酱", "SPICY|CHILL?I|HOT SAUCE"},
            {"Fruit", "苹果|香蕉|橙|葡萄|草莓|水果", "APPLES?|BANANAS?|ORANGES?|GRAPES?|STRAWBERR(Y|IES)|FRUITS?"},
            {"Vegetable", "蔬菜|番茄|西红柿|土豆|胡萝卜|生菜", "VEGETABLES?|TOMATO(ES)?|POTATO(ES)?|CARROTS?|SALAD|LETTUCE"},
            {"Healthy", "有机|全麦|燕麦|低脂|无糖", "ORGANIC|WHOLEGRAIN|OATS?|LOW FAT|SUGAR FREE|PROTEIN BAR"},
    };

    /** Guess from the product name first; the full label (ingredients etc.) is noisier. */
    static String guessCategory(String name, String full) {
        String fromName = name == null ? null : matchCategory(name);
        return fromName != null ? fromName : matchCategory(full);
    }

    private static String matchCategory(String text) {
        String up = text.toUpperCase(Locale.ROOT);
        for (String[] row : CATEGORY_KEYWORDS) {
            if (Pattern.compile(row[1]).matcher(up).find()
                    || Pattern.compile("\\b(" + row[2] + ")\\b").matcher(up).find()) {
                return row[0];
            }
        }
        return null;
    }

    static String guessStorage(String full) {
        String up = full.toUpperCase(Locale.ROOT);
        if (Pattern.compile("冷冻|-18\\s*°?\\s*C|-18℃|KEEP FROZEN|STORE FROZEN").matcher(up).find()) {
            return STORAGE_FROZEN;
        }
        if (Pattern.compile("冷藏|(?<![\\d-])[02]\\s*[-~～]\\s*[4-8]\\s*(°\\s*C|℃)|REFRIGERAT|KEEP CHILLED|KEEP COLD")
                .matcher(up).find()) {
            return STORAGE_CHILLED;
        }
        if (Pattern.compile("常温|阴凉|干燥处|COOL,? DRY PLACE|ROOM TEMPERATURE|AMBIENT").matcher(up).find()) {
            return STORAGE_ROOM;
        }
        return null;
    }
}
