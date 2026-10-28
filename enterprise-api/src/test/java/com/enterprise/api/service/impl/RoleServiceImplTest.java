package com.enterprise.api.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import com.enterprise.api.entity.Role;
import com.enterprise.api.entity.RoleDetail;
import com.enterprise.api.entity.RoleListItem;
import com.enterprise.api.entity.RolePermissionAssignment;
import com.enterprise.api.entity.RoleSessionRevocation;
import com.enterprise.api.mapper.RoleMapperImpl;
import com.enterprise.api.model.request.CreateRoleRequest;
import com.enterprise.api.model.request.RolePageRequest;
import com.enterprise.api.model.request.UpdateRolePermissionsRequest;
import com.enterprise.api.model.request.UpdateRoleRequest;
import com.enterprise.api.model.response.PagedResponse;
import com.enterprise.api.model.response.RoleDetailResponse;
import com.enterprise.api.model.response.RoleListItemResponse;
import com.enterprise.api.model.response.RoleResponse;
import com.enterprise.api.repository.IRoleRepository;
import com.enterprise.api.repository.ISessionRepository;
import com.enterprise.api.repository.support.PageQuery;
import com.enterprise.api.repository.support.PagedResult;
import com.enterprise.api.security.ClientInfo;
import com.enterprise.api.security.CurrentUser;
import com.enterprise.api.service.ISessionValidationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RoleServiceImplTest {

    private static final byte[] ROW_VERSION = {0, 0, 0, 0, 0, 0, 0x07, (byte) 0xD1};

    private static final CurrentUser ACTOR = new CurrentUser(1L, UUID.randomUUID(), List.of("Admin"),
            List.of("ROLE_MANAGE"), false);
    private static final ClientInfo CLIENT = new ClientInfo("10.0.0.8", "JUnit");
    private static final String ROW_VERSION_BASE64 = Base64.getEncoder().encodeToString(ROW_VERSION);

    @Mock
    private IRoleRepository roleRepository;
    @Mock
    private ISessionRepository sessionRepository;
    @Mock
    private ISessionValidationService sessionValidationService;

    private RoleServiceImpl roleService;

    @BeforeEach
    void setUp() {
        roleService = new RoleServiceImpl(roleRepository, sessionRepository, sessionValidationService,
                new RoleMapperImpl());
    }

    @Test
    void getAllMapsEntitiesToResponses() {
        when(roleRepository.findAll(true)).thenReturn(List.of(role(1L, "Admin", true, true), role(3L, "Auditor", false, false)));

        List<RoleResponse> roles = roleService.getAll(true);

        assertThat(roles).hasSize(2);
        RoleResponse admin = roles.getFirst();
        assertThat(admin.id()).isEqualTo(1L);
        assertThat(admin.name()).isEqualTo("Admin");
        assertThat(admin.description()).isEqualTo("Admin description");
        assertThat(admin.isSystem()).isTrue();
        assertThat(admin.isActive()).isTrue();
        assertThat(admin.rowVersion()).isEqualTo(Base64.getEncoder().encodeToString(ROW_VERSION));
        assertThat(roles.get(1).isSystem()).isFalse();
        assertThat(roles.get(1).isActive()).isFalse();
    }

    @Test
    void getAllPassesIncludeInactiveFlag() {
        when(roleRepository.findAll(false)).thenReturn(List.of());

        assertThat(roleService.getAll(false)).isEmpty();
        verify(roleRepository).findAll(false);
    }

    @Test
    void getPagedMapsRowsAndEchoesPaging() {
        RoleListItem item = listItem("ROLE_MANAGE, ROLE_VIEW,USER_VIEW");
        when(roleRepository.findPaged(any(PageQuery.class), isNull())).thenReturn(new PagedResult<>(List.of(item), 42));

        PagedResponse<RoleListItemResponse> page = roleService.getPaged(
                new RolePageRequest(2, 10, "userCount", "DESC", "  adm  ", null));

        assertThat(page.totalCount()).isEqualTo(42);
        assertThat(page.page()).isEqualTo(2);
        assertThat(page.pageSize()).isEqualTo(10);
        assertThat(page.rows()).singleElement().satisfies(row -> {
            assertThat(row.id()).isEqualTo(1L);
            assertThat(row.isSystem()).isTrue();
            assertThat(row.isActive()).isTrue();
            assertThat(row.userCount()).isEqualTo(3);
            assertThat(row.permissionCount()).isEqualTo(3);
            assertThat(row.permissions()).containsExactly("ROLE_MANAGE", "ROLE_VIEW", "USER_VIEW");
            assertThat(row.createdOn()).isEqualTo(Instant.parse("2026-01-02T03:04:05.678Z"));
            assertThat(row.modifiedOn()).isNull();
        });

        ArgumentCaptor<PageQuery> query = ArgumentCaptor.forClass(PageQuery.class);
        verify(roleRepository).findPaged(query.capture(), isNull());
        assertThat(query.getValue()).isEqualTo(new PageQuery(2, 10, "userCount", "DESC", "adm"));
    }

    @Test
    void getPagedAppliesDefaultsForMissingParameters() {
        when(roleRepository.findPaged(any(PageQuery.class), any())).thenReturn(new PagedResult<>(List.of(), 0));

        PagedResponse<RoleListItemResponse> page = roleService.getPaged(new RolePageRequest(null, null, null, null, " ", true));

        assertThat(page.rows()).isEmpty();
        assertThat(page.totalCount()).isZero();
        assertThat(page.page()).isEqualTo(1);
        assertThat(page.pageSize()).isEqualTo(RolePageRequest.DEFAULT_PAGE_SIZE);
        verify(roleRepository).findPaged(new PageQuery(1, RolePageRequest.DEFAULT_PAGE_SIZE, "name", "ASC", null), true);
    }

    @Test
    void getPagedMapsMissingPermissionsToEmptyList() {
        RoleListItem item = listItem(null);
        when(roleRepository.findPaged(any(PageQuery.class), any())).thenReturn(new PagedResult<>(List.of(item), 1));

        PagedResponse<RoleListItemResponse> page = roleService.getPaged(new RolePageRequest(1, 25, null, null, null, null));

        assertThat(page.rows().getFirst().permissions()).isEmpty();
    }

    @Test
    void getByIdMapsPermissionIdsAndCodes() {
        when(roleRepository.findById(7L)).thenReturn(detail(7L, true, 1L, 5L));

        RoleDetailResponse role = roleService.getById(7L);

        assertThat(role.permissionIds()).containsExactly(1L, 5L);
        assertThat(role.permissionCodes()).containsExactly("P1", "P5");
        assertThat(role.rowVersion()).isEqualTo(ROW_VERSION_BASE64);
        assertThat(role.isSystem()).isFalse();
        assertThat(role.userCount()).isEqualTo(4);
    }

    @Test
    void createPassesDistinctPermissionIdsAndReturnsTheNewRole() {
        when(roleRepository.create("Auditor", null, true, List.of(1L, 2L), 1L, "10.0.0.8")).thenReturn(9L);
        when(roleRepository.findById(9L)).thenReturn(detail(9L, true, 1L, 2L));

        RoleDetailResponse role = roleService.create(new CreateRoleRequest(" Auditor ", " ", null, List.of(1L, 2L, 1L)),
                ACTOR, CLIENT);

        assertThat(role.id()).isEqualTo(9L);
        verifyNoInteractions(sessionRepository, sessionValidationService);
    }

    @Test
    void updateDecodesTheRowVersionAndKeepsSessionsWhileActive() {
        when(roleRepository.findById(7L)).thenReturn(detail(7L, true));

        roleService.update(7L, new UpdateRoleRequest("Auditor", "d", true, ROW_VERSION_BASE64), ACTOR, CLIENT);

        verify(roleRepository).update(7L, "Auditor", "d", true, ROW_VERSION, 1L, "10.0.0.8");
        verifyNoInteractions(sessionRepository, sessionValidationService);
    }

    @Test
    void deactivatingARoleEndsTheSessionsOfItsUsers() {
        when(roleRepository.findById(7L)).thenReturn(detail(7L, true));
        when(sessionRepository.revokeAllForRole(7L, RoleServiceImpl.REASON_DEACTIVATED)).thenReturn(revocation(11L, 12L));

        roleService.update(7L, new UpdateRoleRequest("Auditor", null, false, ROW_VERSION_BASE64), ACTOR, CLIENT);

        InOrder order = inOrder(roleRepository, sessionRepository);
        order.verify(roleRepository).update(7L, "Auditor", null, false, ROW_VERSION, 1L, "10.0.0.8");
        order.verify(sessionRepository).revokeAllForRole(7L, RoleServiceImpl.REASON_DEACTIVATED);
        verify(sessionValidationService, times(2)).evictUser(11L);
        verify(sessionValidationService, times(2)).evictUser(12L);
    }

    @Test
    void updatingAnAlreadyInactiveRoleDoesNotRevoke() {
        when(roleRepository.findById(7L)).thenReturn(detail(7L, false));

        roleService.update(7L, new UpdateRoleRequest("Auditor", null, false, ROW_VERSION_BASE64), ACTOR, CLIENT);

        verifyNoInteractions(sessionRepository, sessionValidationService);
    }

    @Test
    void updatePermissionsAppliesTheDifferenceAndEndsSessionsOnRemoval() {
        when(roleRepository.findById(7L)).thenReturn(detail(7L, true, 1L, 2L, 3L));
        when(sessionRepository.revokeAllForRole(7L, RoleServiceImpl.REASON_PERMISSIONS_CHANGED))
                .thenReturn(revocation(11L));

        roleService.updatePermissions(7L, new UpdateRolePermissionsRequest(List.of(2L, 4L)), ACTOR, CLIENT);

        verify(roleRepository).assignPermission(7L, 4L, 1L, "10.0.0.8");
        verify(roleRepository).revokePermission(7L, 1L, 1L, "10.0.0.8");
        verify(roleRepository).revokePermission(7L, 3L, 1L, "10.0.0.8");
        verify(roleRepository, never()).assignPermission(anyLong(), eq(2L), anyLong(), any());
        verify(roleRepository, never()).revokePermission(anyLong(), eq(2L), anyLong(), any());
        verify(sessionRepository).revokeAllForRole(7L, RoleServiceImpl.REASON_PERMISSIONS_CHANGED);
        verify(sessionValidationService, times(2)).evictUser(11L);
    }

    @Test
    void updatePermissionsWithOnlyAdditionsKeepsTheSessions() {
        when(roleRepository.findById(7L)).thenReturn(detail(7L, true, 1L));

        roleService.updatePermissions(7L, new UpdateRolePermissionsRequest(List.of(1L, 2L)), ACTOR, CLIENT);

        verify(roleRepository).assignPermission(7L, 2L, 1L, "10.0.0.8");
        verify(roleRepository, never()).revokePermission(anyLong(), anyLong(), anyLong(), any());
        verifyNoInteractions(sessionRepository, sessionValidationService);
    }

    @Test
    void deleteRevokesSessionsBeforeTheSoftDelete() {
        when(sessionRepository.revokeAllForRole(7L, RoleServiceImpl.REASON_DELETED)).thenReturn(revocation(11L));

        roleService.delete(7L, ROW_VERSION_BASE64, ACTOR, CLIENT);

        InOrder order = inOrder(sessionRepository, roleRepository);
        order.verify(sessionRepository).revokeAllForRole(7L, RoleServiceImpl.REASON_DELETED);
        order.verify(roleRepository).softDelete(7L, ROW_VERSION, 1L, "10.0.0.8");
        verify(sessionValidationService, times(2)).evictUser(11L);
    }

    @Test
    void deleteWithoutRowVersionPassesNull() {
        when(sessionRepository.revokeAllForRole(7L, RoleServiceImpl.REASON_DELETED)).thenReturn(revocation());

        roleService.delete(7L, null, ACTOR, CLIENT);

        verify(roleRepository).softDelete(7L, null, 1L, "10.0.0.8");
        verifyNoInteractions(sessionValidationService);
    }

    private static RoleDetail detail(long id, boolean active, Long... permissionIds) {
        RoleDetail role = new RoleDetail();
        role.setId(id);
        role.setName("Auditor");
        role.setActive(active);
        role.setUserCount(4);
        role.setRowVersion(ROW_VERSION);
        role.setPermissions(Arrays.stream(permissionIds).map(permissionId -> {
            RolePermissionAssignment permission = new RolePermissionAssignment();
            permission.setPermissionId(permissionId);
            permission.setPermissionCode("P" + permissionId);
            return permission;
        }).toList());
        return role;
    }

    private static RoleSessionRevocation revocation(Long... userIds) {
        RoleSessionRevocation revocation = new RoleSessionRevocation();
        revocation.setUserIds(List.of(userIds));
        return revocation;
    }

    private static Role role(long id, String name, boolean system, boolean active) {
        Role role = new Role();
        role.setId(id);
        role.setName(name);
        role.setDescription(name + " description");
        role.setSystem(system);
        role.setActive(active);
        role.setRowVersion(ROW_VERSION);
        return role;
    }

    private static RoleListItem listItem(String permissions) {
        RoleListItem item = new RoleListItem();
        item.setId(1L);
        item.setName("Admin");
        item.setSystem(true);
        item.setActive(true);
        item.setCreatedOn(Instant.parse("2026-01-02T03:04:05.678Z"));
        item.setUserCount(3);
        item.setPermissionCount(3);
        item.setPermissions(permissions);
        return item;
    }
}
