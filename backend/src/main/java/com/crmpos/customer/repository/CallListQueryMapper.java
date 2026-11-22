package com.crmpos.customer.repository;

import com.crmpos.common.type.GradeCode;
import com.crmpos.common.type.ItemType;
import com.crmpos.common.type.Target;
import com.crmpos.customer.domain.MemberRelation;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 콜리스트 조회 (CAL-01, ADR-018). SQL은 mappers/customer/CallListQueryMapper.xml이고 고객 모듈 테이블만 쓴다.
 * 모든 조건은 선택이라 null이면 그 조건을 빼는 동적 SQL이다.
 */
@Mapper
public interface CallListQueryMapper {

    /**
     * @param storeId           매장 범위(STORE)일 때 그 매장 (MINE이면 null)
     * @param storeAccountIds   그 매장의 매장 계정 ID들 (매장 계정이 맡은 고객 판별용)
     * @param staffId           담당 직원 (없으면 null)
     * @param grades            허용하는 적용 중 등급들 (없으면 null)
     * @param inactiveBefore    이 시각 이전이 마지막 방문(없으면 가입일)이어야 한다 (없으면 null)
     */
    List<CallListRow> find(@Param("storeId") Long storeId, @Param("storeAccountIds") List<Long> storeAccountIds,
            @Param("staffId") Long staffId, @Param("promoOnly") boolean promoOnly,
            @Param("grades") List<GradeCode> grades, @Param("inactiveBefore") LocalDateTime inactiveBefore,
            @Param("target") Target target, @Param("itemType") ItemType itemType,
            @Param("relation") MemberRelation relation, @Param("limit") int limit, @Param("offset") int offset);

    long count(@Param("storeId") Long storeId, @Param("storeAccountIds") List<Long> storeAccountIds,
            @Param("staffId") Long staffId, @Param("promoOnly") boolean promoOnly,
            @Param("grades") List<GradeCode> grades, @Param("inactiveBefore") LocalDateTime inactiveBefore,
            @Param("target") Target target, @Param("itemType") ItemType itemType,
            @Param("relation") MemberRelation relation);
}
