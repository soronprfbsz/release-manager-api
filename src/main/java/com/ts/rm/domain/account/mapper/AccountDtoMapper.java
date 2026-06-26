package com.ts.rm.domain.account.mapper;

import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.ts.rm.domain.account.dto.AccountDto;
import com.ts.rm.domain.account.entity.Account;

/**
 * Account Entity ↔ DTO 변환 Mapper (MapStruct로 구현)
 */
@Mapper(componentModel = "spring")
public interface AccountDtoMapper {

    @Mapping(target = "accountId", ignore = true)
    @Mapping(target = "lastLoginAt", ignore = true)
    @Mapping(target = "loginAttemptCount", constant = "0")
    @Mapping(target = "lockedUntil", ignore = true)
    @Mapping(target = "mustChangePassword", ignore = true) // 엔티티 기본값(false)
    @Mapping(target = "lastPasswordChangedAt", ignore = true) // 생성 시 미설정
    @Mapping(target = "department", ignore = true) // Service에서 설정
    Account toEntity(AccountDto.CreateRequest request);

    @Mapping(target = "departmentId", source = "department.departmentId")
    @Mapping(target = "departmentName", source = "department.departmentName")
    @Mapping(target = "positionName", ignore = true) // Service에서 설정
    AccountDto.DetailResponse toDetailResponse(Account account);

    List<AccountDto.DetailResponse> toDetailResponseList(List<Account> accounts);

    @Mapping(target = "departmentName", source = "department.departmentName")
    AccountDto.SimpleResponse toSimpleResponse(Account account);

    List<AccountDto.SimpleResponse> toSimpleResponseList(List<Account> accounts);
}
