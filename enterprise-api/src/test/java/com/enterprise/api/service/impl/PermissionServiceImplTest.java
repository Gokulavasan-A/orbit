package com.enterprise.api.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;

import com.enterprise.api.entity.Permission;
import com.enterprise.api.mapper.PermissionMapperImpl;
import com.enterprise.api.model.response.PermissionGroupResponse;
import com.enterprise.api.model.response.PermissionResponse;
import com.enterprise.api.repository.IPermissionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PermissionServiceImplTest {

    @Mock
    private IPermissionRepository permissionRepository;

    @Test
    void groupsByModuleKeepingTheProcedureOrder() {
        when(permissionRepository.findAll()).thenReturn(List.of(
                permission(5, "ROLE_MANAGE", "Roles"),
                permission(6, "ROLE_VIEW", "Roles"),
                permission(2, "USER_CREATE", "Users"),
                permission(1, "USER_VIEW", "Users")));
        PermissionServiceImpl service = new PermissionServiceImpl(permissionRepository, new PermissionMapperImpl());

        List<PermissionGroupResponse> groups = service.getGroupedByModule();

        assertThat(groups).extracting(PermissionGroupResponse::module).containsExactly("Roles", "Users");
        assertThat(groups.getFirst().permissions()).extracting(PermissionResponse::code)
                .containsExactly("ROLE_MANAGE", "ROLE_VIEW");
        assertThat(groups.get(1).permissions()).extracting(PermissionResponse::id).containsExactly(2L, 1L);
        PermissionResponse first = groups.getFirst().permissions().getFirst();
        assertThat(first.name()).isEqualTo("ROLE_MANAGE name");
        assertThat(first.description()).isEqualTo("ROLE_MANAGE description");
    }

    @Test
    void noPermissionsGiveNoGroups() {
        when(permissionRepository.findAll()).thenReturn(List.of());

        assertThat(new PermissionServiceImpl(permissionRepository, new PermissionMapperImpl()).getGroupedByModule())
                .isEmpty();
    }

    private static Permission permission(long id, String code, String module) {
        Permission permission = new Permission();
        permission.setId(id);
        permission.setCode(code);
        permission.setName(code + " name");
        permission.setModule(module);
        permission.setDescription(code + " description");
        return permission;
    }
}
