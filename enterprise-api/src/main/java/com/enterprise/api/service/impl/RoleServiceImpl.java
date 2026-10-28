package com.enterprise.api.service.impl;

import java.util.Base64;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.enterprise.api.entity.RoleDetail;
import com.enterprise.api.entity.RoleListItem;
import com.enterprise.api.entity.RolePermissionAssignment;
import com.enterprise.api.entity.RoleSessionRevocation;
import com.enterprise.api.mapper.RoleMapper;
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
import com.enterprise.api.service.IRoleService;
import com.enterprise.api.service.ISessionValidationService;
import com.enterprise.api.util.TransactionHooks;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Role administration. Whenever a role stops granting something (permissions removed,
 * role deactivated or deleted), the sessions of its users are revoked in the same
 * transaction, because their access tokens still carry the old permissions.
 */
@Service
public class RoleServiceImpl implements IRoleService {

    static final String REASON_PERMISSIONS_CHANGED = "ROLE_PERMISSIONS_CHANGED";
    static final String REASON_DEACTIVATED = "ROLE_DEACTIVATED";
    static final String REASON_DELETED = "ROLE_DELETED";

    private final IRoleRepository roleRepository;
    private final ISessionRepository sessionRepository;
    private final ISessionValidationService sessionValidationService;
    private final RoleMapper roleMapper;

    public RoleServiceImpl(IRoleRepository roleRepository, ISessionRepository sessionRepository,
                           ISessionValidationService sessionValidationService, RoleMapper roleMapper) {
        this.roleRepository = roleRepository;
        this.sessionRepository = sessionRepository;
        this.sessionValidationService = sessionValidationService;
        this.roleMapper = roleMapper;
    }

    @Override
    public List<RoleResponse> getAll(boolean includeInactive) {
        return roleMapper.toResponses(roleRepository.findAll(includeInactive));
    }

    @Override
    public PagedResponse<RoleListItemResponse> getPaged(RolePageRequest request) {
        PageQuery query = new PageQuery(request.page(), request.pageSize(), request.sortColumn(),
                request.sortDirection(), request.searchText());
        PagedResult<RoleListItem> result = roleRepository.findPaged(query, request.isActive());
        return new PagedResponse<>(roleMapper.toListItemResponses(result.rows()), result.totalCount(),
                request.page(), request.pageSize());
    }

    @Override
    public RoleDetailResponse getById(long id) {
        return roleMapper.toDetailResponse(roleRepository.findById(id));
    }

    @Override
    @Transactional
    public RoleDetailResponse create(CreateRoleRequest request, CurrentUser actor, ClientInfo client) {
        long id = roleRepository.create(request.name(), request.description(), request.isActive(),
                request.permissionIds().stream().distinct().toList(), actor.userId(), client.ipAddress());
        return getById(id);
    }

    @Override
    @Transactional
    public RoleDetailResponse update(long id, UpdateRoleRequest request, CurrentUser actor, ClientInfo client) {
        boolean wasActive = roleRepository.findById(id).isActive();
        roleRepository.update(id, request.name(), request.description(), request.isActive(),
                decodeRowVersion(request.rowVersion()), actor.userId(), client.ipAddress());
        if (wasActive && !request.isActive()) {
            revokeSessionsOfRole(id, REASON_DEACTIVATED);
        }
        return getById(id);
    }

    @Override
    @Transactional
    public RoleDetailResponse updatePermissions(long id, UpdateRolePermissionsRequest request, CurrentUser actor,
                                                ClientInfo client) {
        RoleDetail role = roleRepository.findById(id);
        Set<Long> current = role.getPermissions().stream()
                .map(RolePermissionAssignment::getPermissionId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<Long> desired = new LinkedHashSet<>(request.permissionIds());

        Set<Long> added = new LinkedHashSet<>(desired);
        added.removeAll(current);
        Set<Long> removed = new LinkedHashSet<>(current);
        removed.removeAll(desired);

        for (Long permissionId : added) {
            roleRepository.assignPermission(id, permissionId, actor.userId(), client.ipAddress());
        }
        for (Long permissionId : removed) {
            roleRepository.revokePermission(id, permissionId, actor.userId(), client.ipAddress());
        }
        if (!removed.isEmpty()) {
            revokeSessionsOfRole(id, REASON_PERMISSIONS_CHANGED);
        }
        return getById(id);
    }

    @Override
    @Transactional
    public void delete(long id, String rowVersion, CurrentUser actor, ClientInfo client) {
        // First: usp_Role_SoftDelete removes the user links, after which the users can't be found.
        // A refusal (system role, stale row version) rolls the revocation back.
        revokeSessionsOfRole(id, REASON_DELETED);
        roleRepository.softDelete(id, decodeRowVersion(rowVersion), actor.userId(), client.ipAddress());
    }

    private void revokeSessionsOfRole(long roleId, String reason) {
        RoleSessionRevocation revocation = sessionRepository.revokeAllForRole(roleId, reason);
        evictUsers(revocation.getUserIds());
    }

    /** Now, and again after the commit (see {@link TransactionHooks#afterCommit}). */
    private void evictUsers(Collection<Long> userIds) {
        List<Long> ids = List.copyOf(userIds);
        ids.forEach(sessionValidationService::evictUser);
        TransactionHooks.afterCommit(() -> ids.forEach(sessionValidationService::evictUser));
    }

    /** The request records validate the shape (Base64 of 8 bytes). */
    private static byte[] decodeRowVersion(String rowVersion) {
        return rowVersion == null ? null : Base64.getDecoder().decode(rowVersion);
    }
}
