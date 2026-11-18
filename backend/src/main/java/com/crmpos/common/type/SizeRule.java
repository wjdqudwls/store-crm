package com.crmpos.common.type;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

/**
 * 품목·대상별 사이즈 허용값 (02-erd.md 3.2). 상품·고객·판매 직접 입력이 같이 쓴다.
 * 대상이 없으면 그 품목의 모든 허용값, 공용(UNISEX) 아우터·하의는 남성 목록, 아동 신발은 허용값이 없다.
 */
public final class SizeRule {

    private static final List<String> TOP = List.of("XS", "S", "M", "L", "XL", "XXL");
    private static final List<String> OUTER_WOMEN = List.of("85", "90", "95", "100", "105");
    private static final List<String> OUTER_MEN = List.of("95", "100", "105", "110", "115");
    private static final List<String> BOTTOM_WOMEN = List.of("24", "26", "28", "30", "32");
    private static final List<String> BOTTOM_MEN = List.of("28", "30", "32", "34", "36", "38");
    private static final List<String> KIDS = List.of("4", "6", "8", "10");
    private static final List<String> SHOES = IntStream.rangeClosed(0, 12).map(i -> 220 + i * 5)
            .mapToObj(String::valueOf).toList();
    private static final List<String> ACC = List.of("FREE");

    private SizeRule() {
    }

    /** GET /public/sizes의 한 묶음. target이 null이면 대상과 관계없는 목록이다. */
    public record SizeGroup(ItemType itemType, Target target, List<String> sizes) {
    }

    public static List<String> allowed(ItemType itemType, Target target) {
        return switch (itemType) {
            case TOP -> target == Target.KIDS ? KIDS : target == null ? union(TOP, KIDS) : TOP;
            case OUTER -> clothes(target, OUTER_WOMEN, OUTER_MEN);
            case BOTTOM -> clothes(target, BOTTOM_WOMEN, BOTTOM_MEN);
            case SHOES -> target == Target.KIDS ? List.of() : SHOES;
            case ACC -> ACC;
        };
    }

    public static boolean isAllowed(ItemType itemType, Target target, String size) {
        return size != null && allowed(itemType, target).contains(size);
    }

    /** 공개 사이즈 목록. 품목 순서대로 대상이 나뉘는 품목은 대상별로 담는다 (공용은 남성 목록을 쓰므로 뺀다). */
    public static List<SizeGroup> groups() {
        List<SizeGroup> groups = new ArrayList<>();
        groups.add(new SizeGroup(ItemType.OUTER, Target.WOMEN, OUTER_WOMEN));
        groups.add(new SizeGroup(ItemType.OUTER, Target.MEN, OUTER_MEN));
        groups.add(new SizeGroup(ItemType.OUTER, Target.KIDS, KIDS));
        groups.add(new SizeGroup(ItemType.TOP, null, TOP));
        groups.add(new SizeGroup(ItemType.TOP, Target.KIDS, KIDS));
        groups.add(new SizeGroup(ItemType.BOTTOM, Target.WOMEN, BOTTOM_WOMEN));
        groups.add(new SizeGroup(ItemType.BOTTOM, Target.MEN, BOTTOM_MEN));
        groups.add(new SizeGroup(ItemType.BOTTOM, Target.KIDS, KIDS));
        groups.add(new SizeGroup(ItemType.SHOES, null, SHOES));
        groups.add(new SizeGroup(ItemType.ACC, null, ACC));
        return groups;
    }

    private static List<String> clothes(Target target, List<String> women, List<String> men) {
        if (target == null) {
            return union(women, men, KIDS);
        }
        return switch (target) {
            case WOMEN -> women;
            case MEN, UNISEX -> men;
            case KIDS -> KIDS;
        };
    }

    @SafeVarargs
    private static List<String> union(List<String>... lists) {
        Set<String> merged = new LinkedHashSet<>();
        for (List<String> list : lists) {
            merged.addAll(list);
        }
        return List.copyOf(merged);
    }
}
