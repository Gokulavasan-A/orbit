package com.enterprise.api.mapper;

import java.util.List;

import com.enterprise.api.entity.Permission;
import com.enterprise.api.model.response.PermissionResponse;
import org.mapstruct.Mapper;

@Mapper
public interface PermissionMapper {

    PermissionResponse toResponse(Permission permission);

    List<PermissionResponse> toResponses(List<Permission> permissions);
}
