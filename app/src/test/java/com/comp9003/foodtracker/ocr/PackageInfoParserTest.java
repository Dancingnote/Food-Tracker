package com.comp9003.foodtracker.ocr;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

public class PackageInfoParserTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 21);

    private static PackageInfoParser.Line l(String t, int h) {
        return new PackageInfoParser.Line(t, h);
    }

    @Test
    public void chineseLabel() {
        List<PackageInfoParser.Line> lines = Arrays.asList(
                l("纯牛奶", 90),
                l("配料：生牛乳", 20),
                l("净含量：250mL", 20),
                l("生产日期：2026年11月10日", 18),
                l("保质期：6个月", 18),
                l("贮存条件：2-6℃冷藏", 18),
                l("¥4.50", 30));
        PackageInfoParser.Result r = PackageInfoParser.parse(lines, TODAY);
        assertEquals("纯牛奶", r.name);
        assertEquals(LocalDate.of(2026, 11, 10), r.productionDate);
        assertEquals(LocalDate.of(2027, 5, 10), r.expireDate);
        assertEquals(4.5, r.price, 0.001);
        assertEquals("Dairy", r.category);
        assertEquals(PackageInfoParser.STORAGE_CHILLED, r.storage);
    }

    @Test
    public void englishLabelWithKeywordOnPreviousLine() {
        List<PackageInfoParser.Line> lines = Arrays.asList(
                l("Sea Salt Crisps", 80),
                l("Ingredients: potatoes, oil, salt", 15),
                l("BEST BEFORE", 15),
                l("21 MAR 2027", 15),
                l("Store in a cool, dry place", 15));
        PackageInfoParser.Result r = PackageInfoParser.parse(lines, TODAY);
        assertEquals("Sea Salt Crisps", r.name);
        assertNull(r.productionDate);
        assertEquals(LocalDate.of(2027, 3, 21), r.expireDate);
        assertEquals("Snack", r.category);
        assertEquals(PackageInfoParser.STORAGE_ROOM, r.storage);
    }

    @Test
    public void mfgAndExpOnSameLine() {
        List<PackageInfoParser.Line> lines = Arrays.asList(
                l("MFG 01/10/2026 EXP 01/04/2027", 15));
        PackageInfoParser.Result r = PackageInfoParser.parse(lines, TODAY);
        assertEquals(LocalDate.of(2026, 10, 1), r.productionDate);
        assertEquals(LocalDate.of(2027, 4, 1), r.expireDate);
    }

    @Test
    public void unlabeledDatesUseOrder() {
        List<PackageInfoParser.Line> lines = Arrays.asList(
                l("20261101", 15), l("2027.01.15", 15));
        PackageInfoParser.Result r = PackageInfoParser.parse(lines, TODAY);
        assertEquals(LocalDate.of(2026, 11, 1), r.productionDate);
        assertEquals(LocalDate.of(2027, 1, 15), r.expireDate);
    }

    @Test
    public void storeBelow25IsNotChilled() {
        assertNull(PackageInfoParser.guessStorage("请于25℃以下保存"));
    }
}
