package com.enterprise.api.mapper;

import java.util.Arrays;
import java.util.Base64;
import java.util.List;

import com.enterprise.api.entity.Role;
import com.enterprise.api.entity.RoleDetail;
import com.enterprise.api.entity.RoleListItem;
import com.enterprise.api.entity.RolePermissionAssignment;
import com.enterprise.api.model.response.RoleDetailResponse;
import com.enterprise.api.model.response.RoleListItemResponse;
import com.enterprise.api.model.response.RoleResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

@Mapper
public interface RoleMapper {

    @Mapping(target = "isSystem", source = "system")
    @Mapping(target = "isActive", source = "active")
    RoleResponse toResponse(Role role);

    List<RoleResponse> toResponses(List<Role> roles);

    @Mapping(target = "isSystem", source = "system")
    @Mapping(target = "isActive", source = "active")
    RoleListItemResponse toListItemResponse(RoleListItem item);

    List<RoleListItemResponse> toListItemResponses(List<RoleListItem> items);

    @Mapping(target = "isSystem", source = "system")
    @Mapping(target = "isActive", source = "active")
    @Mapping(target = "permissionIds", source = "permissions", qualifiedByName = "permissionIds")
    @Mapping(target = "permissionCodes", source = "permissions", qualifiedByName = "permissionCodes")
    RoleDetailResponse toDetailResponse(RoleDetail role);

    @Named("permissionIds")
    default List<Long> permissionIds(List<RolePermissionAssignment> permissions) {
        return permissions == null ? List.of()
                : permissions.stream().map(RolePermissionAssignment::getPermissionId).toList();
    }

    @Named("permissionCodes")
    default List<String> permissionCodes(List<RolePermissionAssignment> permissions) {
        return permissions == null ? List.of()
                : permissions.stream().map(RolePermissionAssignment::getPermissionCode).toList();
    }

    default String rowVersionToBase64(byte[] rowVersion) {
        return rowVersion == null ? null : Base64.getEncoder().encodeToString(rowVersion);
    }

    default List<String> csvToList(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        return Arrays.stream(csv.split(","))
                .map(String::strip)
                .filter(value -> !value.isEmpty())
                .toList();
    }
}
