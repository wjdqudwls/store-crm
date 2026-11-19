package com.crmpos.customer.repository;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 고객 목록·검색 조회 (ADR-018). SQL은 mappers/customer/CustomerQueryMapper.xml, 고객 모듈 테이블만 쓴다. */
@Mapper
public interface CustomerQueryMapper {

    /** name은 LIKE 특수문자를 '!'로 막은 값이다. */
    List<CustomerListRow> searchByName(@Param("name") String name, @Param("limit") int limit,
            @Param("offset") int offset);

    long countByName(@Param("name") String name);
}
