package com.crmpos.customer.domain;

import com.crmpos.common.domain.StaffRole;
import com.crmpos.common.type.GradeCode;
import com.crmpos.common.type.ItemType;
import com.crmpos.common.type.Target;
import java.util.List;
import java.util.Map;

/**
 * 콜리스트 조건을 정하는 순수 규칙 (CAL-01, BR-09). 요청이 주지 않은 조건은 계정에 따라 기본값을 채운다.
 * <ul>
 *   <li>직원 개인: 범위 내 담당(MINE), 홍보 연락 가능 아님(false), 최소 등급 D, 마지막 방문 후 90일 이상</li>
 *   <li>매장 계정·본사: 범위 이 매장 고객 전체(STORE), 홍보 연락 가능만(true), 등급·방문 조건 없음. MINE을 고르면 매장 계정이 담당인 고객</li>
 * </ul>
 * 기본값을 끄려면 값을 명시한다(minGrade=NORMAL, inactiveDays=0, promoOnly=false).
 *
 * @param scope        범위
 * @param staffId      특정 계정 담당만 (MINE이고 지정이 없으면 로그인한 계정, 그 밖에는 null)
 * @param promoOnly    문자·카톡 중 하나라도 동의한 고객만
 * @param minGrade     이 등급 이상(적용 중 등급, 컷 점수 순서). null이면 조건 없음
 * @param inactiveDays 마지막 방문(없으면 가입일) 후 이 일수 이상. null이면 조건 없음
 */
public record CallListFilter(CallListScope scope, Long staffId, boolean promoOnly, GradeCode minGrade,
        Integer inactiveDays, Target target, ItemType itemType, MemberRelation memberRelation) {

    /** 요청 파라미터 (모두 선택). */
    public record Request(CallListScope scope, Long staffId, Boolean promoOnly, GradeCode minGrade,
            Integer inactiveDays, Target target, ItemType itemType, MemberRelation memberRelation) {
    }

    public static CallListFilter resolve(StaffRole role, Long accountId, Request request) {
        boolean employee = role != StaffRole.STORE && role != StaffRole.HQ;
        CallListScope scope = request.scope() != null ? request.scope()
                : employee ? CallListScope.MINE : CallListScope.STORE;
        // MINE은 내 담당이다. 매장 계정도 담당자가 될 수 있어(매장 계정이 맡은 고객) 로그인한 계정이 담당인 고객만 본다
        Long staffId = request.staffId() != null ? request.staffId()
                : scope == CallListScope.MINE ? accountId : null;
        boolean promoOnly = request.promoOnly() != null ? request.promoOnly() : !employee;
        GradeCode minGrade = request.minGrade() != null ? request.minGrade() : employee ? GradeCode.D : null;
        Integer inactiveDays = request.inactiveDays() != null ? request.inactiveDays() : employee ? 90 : null;
        return new CallListFilter(scope, staffId, promoOnly, minGrade, inactiveDays, request.target(),
                request.itemType(), request.memberRelation());
    }

    /**
     * 최소 등급 이상인 등급들. 높고 낮음은 등급 컷 점수로 정한다 (BR-04): 컷 점수가 최소 등급의 컷 이상인 등급이다.
     * 컷 정보가 없는 등급은 제외하고, 최소 등급이 null이면 조건이 없다는 뜻으로 null을 돌려준다.
     */
    public static List<GradeCode> gradesAtLeast(GradeCode minGrade, Map<GradeCode, Integer> cutScores) {
        if (minGrade == null) {
            return null;
        }
        Integer min = cutScores.get(minGrade);
        if (min == null) {
            return List.of(minGrade);
        }
        return cutScores.entrySet().stream().filter(e -> e.getValue() >= min).map(Map.Entry::getKey).toList();
    }
}
