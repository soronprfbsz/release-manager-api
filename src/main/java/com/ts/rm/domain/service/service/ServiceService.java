package com.ts.rm.domain.service.service;

import com.ts.rm.domain.account.entity.Account;
import com.ts.rm.domain.common.entity.Code;
import com.ts.rm.domain.common.repository.CodeRepository;
import com.ts.rm.domain.service.dto.ServiceDto;
import com.ts.rm.domain.service.entity.Service;
import com.ts.rm.domain.service.entity.ServiceComponent;
import com.ts.rm.domain.service.enums.ComponentType;
import com.ts.rm.domain.service.mapper.ServiceDtoMapper;
import com.ts.rm.domain.service.repository.ServiceComponentRepository;
import com.ts.rm.domain.service.repository.ServiceRepository;
import com.ts.rm.global.account.AccountLookupService;
import com.ts.rm.global.exception.BusinessException;
import com.ts.rm.global.exception.ErrorCode;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

/**
 * ServiceService
 *
 * <p>서비스 관리 비즈니스 로직
 */
@Slf4j
@org.springframework.stereotype.Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ServiceService {

    private final ServiceRepository serviceRepository;
    private final ServiceComponentRepository componentRepository;
    private final CodeRepository codeRepository;
    private final ServiceDtoMapper mapper;
    private final AccountLookupService accountLookupService;

    /**
     * 서비스 생성
     */
    @Transactional
    public ServiceDto.DetailResponse createService(ServiceDto.CreateRequest request, String createdByEmail) {
        log.info("Creating service: {}", request.serviceName());

        // 생성자 Account 조회
        Account creator = accountLookupService.findByEmail(createdByEmail);

        // 서비스 타입 검증 및 다음 sortOrder 계산
        validateServiceType(request.serviceType());
        Integer sortOrder = getNextSortOrderForServiceType(request.serviceType());

        // 서비스 생성
        String glyphText = (request.glyphText() == null || request.glyphText().isBlank())
                ? null : request.glyphText();
        String glyphBackgroundColor = (request.glyphBackgroundColor() == null || request.glyphBackgroundColor().isBlank())
                ? null : request.glyphBackgroundColor();

        Service service = Service.builder()
                .serviceName(request.serviceName())
                .serviceType(request.serviceType())
                .description(request.description())
                .glyphText(glyphText)
                .glyphBackgroundColor(glyphBackgroundColor)
                .sortOrder(sortOrder)
                .creator(creator)
                .createdByEmail(creator.getEmail())
                .build();

        // 컴포넌트 추가
        if (request.components() != null && !request.components().isEmpty()) {
            for (ServiceDto.ComponentRequest compReq : request.components()) {
                validateComponentType(compReq.componentType());
                ServiceComponent component = createComponentFromRequest(compReq, creator);
                service.addComponent(component);
            }
        }

        Service savedService = serviceRepository.save(service);

        // 컴포넌트가 있으면 sortOrder 재계산
        if (savedService.getComponents() != null && !savedService.getComponents().isEmpty()) {
            recalculateComponentSortOrder(savedService.getServiceId());
        }

        log.info("Service created successfully: {}", savedService.getServiceId());

        return toDetailResponseWithNames(savedService);
    }

    /**
     * 서비스 상세 조회
     */
    public ServiceDto.DetailResponse getServiceById(Long serviceId) {
        Service service = serviceRepository.findByIdWithComponents(serviceId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DATA_NOT_FOUND,
                        "서비스를 찾을 수 없습니다: " + serviceId));
        return toDetailResponseWithNames(service);
    }

    /**
     * 서비스 목록 조회 (컴포넌트 포함)
     * QueryDSL을 사용한 다중 필드 키워드 검색 (서비스명, 서비스타입, 설명)
     *
     * @param serviceType 서비스 타입 필터 (null이면 전체)
     * @param keyword 검색 키워드 - 서비스명, 서비스타입, 설명 통합 검색 (null이면 전체)
     * @return 서비스 목록
     */
    public List<ServiceDto.DetailResponse> getServices(
            String serviceType, String keyword) {
        // QueryDSL 기반 다중 필드 검색
        List<Service> services = serviceRepository.findAllWithFilters(serviceType, keyword);

        return services.stream()
                .map(this::toDetailResponseWithNames)
                .collect(Collectors.toList());
    }

    /**
     * 서비스 수정
     */
    @Transactional
    public ServiceDto.DetailResponse updateService(Long serviceId, ServiceDto.UpdateRequest request, String updatedBy) {
        log.info("Updating service: {}", serviceId);

        // 수정자 Account 조회
        Account updater = accountLookupService.findByEmail(updatedBy);

        Service service = findServiceById(serviceId);

        // 서비스 타입 검증 및 sortOrder 업데이트
        if (request.serviceType() != null && !request.serviceType().isBlank()) {
            validateServiceType(request.serviceType());
            // 서비스 타입이 변경되면 새 타입의 다음 sortOrder로 업데이트
            if (!request.serviceType().equals(service.getServiceType())) {
                Integer sortOrder = getNextSortOrderForServiceType(request.serviceType());
                service.setSortOrder(sortOrder);
            }
        }

        service.update(request.serviceName(), request.serviceType(),
                request.description(), updater,
                request.glyphText(), request.glyphBackgroundColor());
        service.setUpdatedByEmail(updater.getEmail());

        log.info("Service updated successfully: {}", serviceId);
        return toDetailResponseWithNames(service);
    }

    /**
     * 서비스 삭제
     */
    @Transactional
    public void deleteService(Long serviceId) {
        log.info("Deleting service: {}", serviceId);
        Service service = findServiceById(serviceId);
        serviceRepository.delete(service);
        log.info("Service deleted successfully: {}", serviceId);
    }

    /**
     * 컴포넌트 추가
     */
    @Transactional
    public ServiceDto.ComponentResponse addComponent(Long serviceId, ServiceDto.ComponentRequest request, String createdByEmail) {
        log.info("Adding component to service: {}", serviceId);

        // 생성자 Account 조회
        Account creator = accountLookupService.findByEmail(createdByEmail);

        Service service = findServiceById(serviceId);
        validateComponentType(request.componentType());

        ServiceComponent component = createComponentFromRequest(request, creator);
        service.addComponent(component);

        ServiceComponent savedComponent = componentRepository.save(component);

        // 컴포넌트 추가 후 전체 sortOrder 재계산
        recalculateComponentSortOrder(serviceId);

        log.info("Component added successfully: {}", savedComponent.getComponentId());

        return toComponentResponseWithName(savedComponent);
    }

    /**
     * 컴포넌트 수정
     */
    @Transactional
    public ServiceDto.ComponentResponse updateComponent(Long serviceId, Long componentId,
                                                         ServiceDto.ComponentRequest request, String updatedBy) {
        log.info("Updating component: {} in service: {}", componentId, serviceId);

        // 수정자 Account 조회
        Account updater = accountLookupService.findByEmail(updatedBy);

        ServiceComponent component = findComponentById(componentId);

        if (!component.getService().getServiceId().equals(serviceId)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                    "컴포넌트가 해당 서비스에 속하지 않습니다");
        }

        if (request.componentType() != null && !request.componentType().isBlank()) {
            validateComponentType(request.componentType());
        }

        ComponentType componentType = request.componentType() != null ?
                ComponentType.fromCode(request.componentType()) : component.getComponentType();

        component.update(componentType, request.componentName(),
                request.host(), request.port(), request.url(),
                request.description(), request.sshPort(), updater);
        component.setUpdatedByEmail(updater.getEmail());

        log.info("Component updated successfully: {}", componentId);
        return toComponentResponseWithName(component);
    }

    /**
     * 컴포넌트 삭제
     */
    @Transactional
    public void deleteComponent(Long serviceId, Long componentId) {
        log.info("Deleting component: {} from service: {}", componentId, serviceId);

        ServiceComponent component = findComponentById(componentId);

        if (!component.getService().getServiceId().equals(serviceId)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                    "컴포넌트가 해당 서비스에 속하지 않습니다");
        }

        componentRepository.delete(component);

        // 컴포넌트 삭제 후 전체 sortOrder 재계산
        recalculateComponentSortOrder(serviceId);

        log.info("Component deleted successfully: {}", componentId);
    }

    /**
     * 서비스 순서 변경
     */
    @Transactional
    public void reorderServices(ServiceDto.ReorderServicesRequest request) {
        log.info("Reordering services - serviceType: {}, serviceIds: {}",
                request.serviceType(), request.serviceIds());

        // 서비스 타입 검증
        validateServiceType(request.serviceType());

        List<Long> serviceIds = request.serviceIds();
        if (serviceIds == null || serviceIds.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                    "서비스 ID 목록은 비어있을 수 없습니다");
        }

        // 모든 서비스가 존재하고 동일한 serviceType인지 확인
        for (Long serviceId : serviceIds) {
            Service service = findServiceById(serviceId);
            if (!request.serviceType().equals(service.getServiceType())) {
                throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                        "서비스 " + serviceId + "는 " + request.serviceType() + " 분류에 속하지 않습니다");
            }
        }

        // sortOrder 업데이트 (1부터 시작)
        int sortOrder = 1;
        for (Long serviceId : serviceIds) {
            Service service = findServiceById(serviceId);
            service.setSortOrder(sortOrder++);
        }

        log.info("Services reordered successfully for serviceType: {}", request.serviceType());
    }

    /**
     * 컴포넌트 순서 변경
     */
    @Transactional
    public void reorderComponents(Long serviceId, ServiceDto.ReorderComponentsRequest request) {
        log.info("Reordering components for service: {}", serviceId);

        Service service = findServiceById(serviceId);
        List<Long> componentIds = request.componentIds();

        if (componentIds == null || componentIds.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                    "컴포넌트 ID 목록은 비어있을 수 없습니다");
        }

        // 모든 컴포넌트가 해당 서비스에 속하는지 확인
        for (Long componentId : componentIds) {
            ServiceComponent component = findComponentById(componentId);
            if (!component.getService().getServiceId().equals(serviceId)) {
                throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                        "컴포넌트 " + componentId + "는 서비스 " + serviceId + "에 속하지 않습니다");
            }
        }

        // sortOrder 업데이트 (1부터 시작)
        int sortOrder = 1;
        for (Long componentId : componentIds) {
            ServiceComponent component = findComponentById(componentId);
            component.setSortOrder(sortOrder++);
        }

        log.info("Components reordered successfully for service: {}", serviceId);
    }

    // ========================================
    // Private Helper Methods
    // ========================================

    private Service findServiceById(Long serviceId) {
        return serviceRepository.findById(serviceId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DATA_NOT_FOUND,
                        "서비스를 찾을 수 없습니다: " + serviceId));
    }

    private ServiceComponent findComponentById(Long componentId) {
        return componentRepository.findById(componentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DATA_NOT_FOUND,
                        "컴포넌트를 찾을 수 없습니다: " + componentId));
    }

    private void validateServiceType(String serviceType) {
        boolean exists = codeRepository.existsByCodeTypeIdAndCodeId("SERVICE_TYPE", serviceType);
        if (!exists) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                    "유효하지 않은 서비스 분류입니다: " + serviceType);
        }
    }

    /**
     * 서비스 타입별 다음 sortOrder 계산
     * 해당 타입의 기존 서비스 중 최대 sortOrder + 1 반환
     *
     * @param serviceType 서비스 타입
     * @return 다음 sortOrder (기존 데이터가 없으면 1)
     */
    private Integer getNextSortOrderForServiceType(String serviceType) {
        Integer maxSortOrder = serviceRepository.findMaxSortOrderByServiceType(serviceType);
        return (maxSortOrder == null) ? 1 : maxSortOrder + 1;
    }

    private void validateComponentType(String componentType) {
        try {
            ComponentType.fromCode(componentType);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                    "유효하지 않은 컴포넌트 타입입니다: " + componentType);
        }
    }

    private ServiceComponent createComponentFromRequest(ServiceDto.ComponentRequest request, Account creator) {
        return ServiceComponent.builder()
                .componentType(ComponentType.fromCode(request.componentType()))
                .componentName(request.componentName())
                .host(request.host())
                .port(request.port())
                .url(request.url())
                .sshPort(request.sshPort())
                .description(request.description())
                .sortOrder(0) // 임시값, 나중에 재계산됨
                .creator(creator)
                .createdByEmail(creator.getEmail())
                .build();
    }

    /**
     * 서비스의 모든 컴포넌트 sortOrder 재계산
     * created_at 순서로 1부터 순차 부여
     *
     * @param serviceId 서비스 ID
     */
    private void recalculateComponentSortOrder(Long serviceId) {
        List<ServiceComponent> components = componentRepository
                .findByService_ServiceIdOrderByCreatedAtAsc(serviceId);

        int sortOrder = 1;
        for (ServiceComponent component : components) {
            component.setSortOrder(sortOrder++);
        }
    }

    private ServiceDto.DetailResponse toDetailResponseWithNames(Service service) {
        ServiceDto.DetailResponse response = mapper.toDetailResponse(service);

        String serviceTypeName = getCodeName("SERVICE_TYPE", service.getServiceType());

        List<ServiceDto.ComponentResponse> components = service.getComponents().stream()
                .map(this::toComponentResponseWithName)
                .collect(Collectors.toList());

        return new ServiceDto.DetailResponse(
                response.serviceId(), response.serviceName(),
                response.serviceType(), serviceTypeName,
                response.description(),
                response.glyphText(), response.glyphBackgroundColor(),
                components,
                response.createdAt(), response.createdByEmail(),
                response.createdByAvatarStyle(), response.createdByAvatarSeed(),
                response.isDeletedCreator(),
                response.updatedAt(), response.updatedByEmail(),
                response.updatedByAvatarStyle(), response.updatedByAvatarSeed(),
                response.isDeletedUpdater()
        );
    }

    private ServiceDto.SimpleResponse toSimpleResponseWithNames(Service service) {
        ServiceDto.SimpleResponse response = mapper.toSimpleResponse(service);

        String serviceTypeName = getCodeName("SERVICE_TYPE", service.getServiceType());
        int componentCount = service.getComponents().size();

        return new ServiceDto.SimpleResponse(
                response.serviceId(), response.serviceName(),
                response.serviceType(), serviceTypeName,
                response.glyphText(), response.glyphBackgroundColor(),
                componentCount, response.createdAt()
        );
    }

    private ServiceDto.ComponentResponse toComponentResponseWithName(ServiceComponent component) {
        ServiceDto.ComponentResponse response = mapper.toComponentResponse(component);
        String componentTypeName = component.getComponentType().getDisplayName();

        return new ServiceDto.ComponentResponse(
                response.componentId(),
                response.componentType(), componentTypeName,
                response.componentName(),
                response.host(), response.port(), response.url(),
                response.sshPort(),
                response.description(), response.sortOrder()
        );
    }

    private String getCodeName(String codeTypeId, String codeId) {
        return codeRepository.findByCodeTypeIdAndCodeId(codeTypeId, codeId)
                .map(Code::getCodeName)
                .orElse(codeId);
    }
}
