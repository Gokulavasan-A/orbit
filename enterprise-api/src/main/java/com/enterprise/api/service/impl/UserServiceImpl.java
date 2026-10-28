package com.enterprise.api.service.impl;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.enterprise.api.entity.UserDetail;
import com.enterprise.api.entity.UserListItem;
import com.enterprise.api.entity.UserRoleAssignment;
import com.enterprise.api.exception.BusinessException;
import com.enterprise.api.exception.ErrorCode;
import com.enterprise.api.mapper.UserMapper;
import com.enterprise.api.model.request.CreateUserRequest;
import com.enterprise.api.model.request.UpdateUserRequest;
import com.enterprise.api.model.request.UpdateUserRolesRequest;
import com.enterprise.api.model.request.UserPageRequest;
import com.enterprise.api.model.response.PagedResponse;
import com.enterprise.api.model.response.TemporaryPasswordResponse;
import com.enterprise.api.model.response.UserDetailResponse;
import com.enterprise.api.model.response.UserListItemResponse;
import com.enterprise.api.repository.ISessionRepository;
import com.enterprise.api.repository.IUserRepository;
import com.enterprise.api.repository.support.PageQuery;
import com.enterprise.api.repository.support.PagedResult;
import com.enterprise.api.security.ClientInfo;
import com.enterprise.api.security.CurrentUser;
import com.enterprise.api.service.ISessionValidationService;
import com.enterprise.api.service.ITemporaryPasswordGenerator;
import com.enterprise.api.service.IUserService;
import com.enterprise.api.util.TransactionHooks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * User administration. The self-action guards run before the database is called; the
 * procedures' own checks (own account 50304, last active admin 50004) stay authoritative.
 */
@Service
public class UserServiceImpl implements IUserService {

    private static final Logger log = LoggerFactory.getLogger(UserServiceImpl.class);

    static final String REASON_ROLES_CHANGED = "ROLES_CHANGED";

    private final IUserRepository userRepository;
    private final ISessionRepository sessionRepository;
    private final ISessionValidationService sessionValidationService;
    private final ITemporaryPasswordGenerator temporaryPasswordGenerator;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;

    public UserServiceImpl(IUserRepository userRepository, ISessionRepository sessionRepository,
                           ISessionValidationService sessionValidationService,
                           ITemporaryPasswordGenerator temporaryPasswordGenerator, PasswordEncoder passwordEncoder,
                           UserMapper userMapper) {
        this.userRepository = userRepository;
        this.sessionRepository = sessionRepository;
        this.sessionValidationService = sessionValidationService;
        this.temporaryPasswordGenerator = temporaryPasswordGenerator;
        this.passwordEncoder = passwordEncoder;
        this.userMapper = userMapper;
    }

    @Override
    public PagedResponse<UserListItemResponse> getPaged(UserPageRequest request) {
        PageQuery query = new PageQuery(request.page(), request.pageSize(), request.sortColumn(),
                request.sortDirection(), request.searchText());
        PagedResult<UserListItem> result = userRepository.findPaged(query, request.isActive(), request.roleId());
        return new PagedResponse<>(userMapper.toListItemResponses(result.rows()), result.totalCount(),
                request.page(), request.pageSize());
    }

    @Override
    public UserDetailResponse getById(long id) {
        return userMapper.toDetailResponse(userRepository.findById(id));
    }

    @Override
    @Transactional
    public TemporaryPasswordResponse create(CreateUserRequest request, CurrentUser actor, ClientInfo client) {
        String temporaryPassword = temporaryPasswordGenerator.generate(request.username());
        long id = userRepository.create(request.username(), request.email(), passwordEncoder.encode(temporaryPassword),
                request.firstName(), request.lastName(), request.phoneNumber(),
                request.roleIds().stream().distinct().toList(), actor.userId(), client.ipAddress());
        log.info("User {} created by user {}", id, actor.userId());
        return new TemporaryPasswordResponse(id, request.username(), temporaryPassword);
    }

    @Override
    @Transactional
    public UserDetailResponse update(long id, UpdateUserRequest request, CurrentUser actor, ClientInfo client) {
        if (id == actor.userId() && !request.isActive()) {
            throw new BusinessException(ErrorCode.SELF_DEACTIVATE_NOT_ALLOWED);
        }
        userRepository.update(id, request.email(), request.firstName(), request.lastName(), request.phoneNumber(),
                request.isActive(), request.concurrencyStamp(), actor.userId(), client.ipAddress());
        if (!request.isActive()) {
            // The procedure revoked the sessions when the user was deactivated.
            evictUser(id);
        }
        return getById(id);
    }

    @Override
    @Transactional
    public UserDetailResponse updateRoles(long id, UpdateUserRolesRequest request, CurrentUser actor,
                                          ClientInfo client) {
        Set<Long> current = userRepository.findById(id).getRoles().stream()
                .map(UserRoleAssignment::getRoleId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<Long> desired = new LinkedHashSet<>(request.roleIds());

        Set<Long> added = new LinkedHashSet<>(desired);
        added.removeAll(current);
        Set<Long> removed = new LinkedHashSet<>(current);
        removed.removeAll(desired);

        for (Long roleId : added) {
            userRepository.assignRole(id, roleId, actor.userId(), client.ipAddress());
        }
        for (Long roleId : removed) {
            userRepository.revokeRole(id, roleId, actor.userId(), client.ipAddress());
        }
        if (!removed.isEmpty()) {
            // Tokens still carry the removed role's permissions: end the user's sessions.
            sessionRepository.revokeAllForUser(id, REASON_ROLES_CHANGED, null);
            evictUser(id);
        }
        return getById(id);
    }

    @Override
    @Transactional
    public TemporaryPasswordResponse resetPassword(long id, CurrentUser actor, ClientInfo client) {
        if (id == actor.userId()) {
            throw new BusinessException(ErrorCode.SELF_PASSWORD_RESET_NOT_ALLOWED);
        }
        UserDetail user = userRepository.findById(id);
        String temporaryPassword = temporaryPasswordGenerator.generate(user.getUsername());
        userRepository.resetPassword(id, passwordEncoder.encode(temporaryPassword), actor.userId(), client.ipAddress());
        // The procedure revoked all sessions of the user.
        evictUser(id);
        log.info("Password of user {} reset by user {}", id, actor.userId());
        return new TemporaryPasswordResponse(id, user.getUsername(), temporaryPassword);
    }

    @Override
    @Transactional
    public UserDetailResponse unlock(long id, CurrentUser actor, ClientInfo client) {
        userRepository.unlock(id, actor.userId(), client.ipAddress());
        return getById(id);
    }

    @Override
    @Transactional
    public void delete(long id, UUID concurrencyStamp, CurrentUser actor, ClientInfo client) {
        if (id == actor.userId()) {
            throw new BusinessException(ErrorCode.SELF_DELETE_NOT_ALLOWED);
        }
        userRepository.softDelete(id, concurrencyStamp, actor.userId(), client.ipAddress());
        // The procedure revoked all sessions of the user.
        evictUser(id);
    }

    /** Now, and again after the commit (see {@link TransactionHooks#afterCommit}). */
    private void evictUser(long userId) {
        sessionValidationService.evictUser(userId);
        TransactionHooks.afterCommit(() -> sessionValidationService.evictUser(userId));
    }
}
