package com.enterprise.api.mapper;

import java.util.Arrays;
import java.util.List;

import com.enterprise.api.entity.UserDetail;
import com.enterprise.api.entity.UserListItem;
import com.enterprise.api.entity.UserRoleAssignment;
import com.enterprise.api.model.response.UserDetailResponse;
import com.enterprise.api.model.response.UserListItemResponse;
import com.enterprise.api.model.response.UserRoleResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper
public interface UserMapper {

    @Mapping(target = "isActive", source = "active")
    @Mapping(target = "isLockedOut", source = "lockedOut")
    UserListItemResponse toListItemResponse(UserListItem item);

    List<UserListItemResponse> toListItemResponses(List<UserListItem> items);

    @Mapping(target = "isActive", source = "active")
    @Mapping(target = "isLockedOut", source = "lockedOut")
    UserDetailResponse toDetailResponse(UserDetail user);

    @Mapping(target = "id", source = "roleId")
    @Mapping(target = "name", source = "roleName")
    @Mapping(target = "isActive", source = "active")
    UserRoleResponse toRoleResponse(UserRoleAssignment role);

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
