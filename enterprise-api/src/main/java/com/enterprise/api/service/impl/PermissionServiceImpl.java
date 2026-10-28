package com.enterprise.api.service.impl;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.enterprise.api.entity.Permission;
import com.enterprise.api.mapper.PermissionMapper;
import com.enterprise.api.model.response.PermissionGroupResponse;
import com.enterprise.api.repository.IPermissionRepository;
import com.enterprise.api.service.IPermissionService;
import org.springframework.stereotype.Service;

@Service
public class PermissionServiceImpl implements IPermissionService {

    private final IPermissionRepository permissionRepository;
    private final PermissionMapper permissionMapper;

    public PermissionServiceImpl(IPermissionRepository permissionRepository, PermissionMapper permissionMapper) {
        this.permissionRepository = permissionRepository;
        this.permissionMapper = permissionMapper;
    }

    @Override
    public List<PermissionGroupResponse> getGroupedByModule() {
        // The procedure orders by module, then code; LinkedHashMap keeps that order.
        Map<String, List<Permission>> byModule = permissionRepository.findAll().stream()
                .collect(Collectors.groupingBy(Permission::getModule, LinkedHashMap::new, Collectors.toList()));
        return byModule.entrySet().stream()
                .map(entry -> new PermissionGroupResponse(entry.getKey(), permissionMapper.toResponses(entry.getValue())))
                .toList();
    }
}
