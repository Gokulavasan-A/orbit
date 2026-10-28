package com.enterprise.api.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.enterprise.api.entity.PasswordChangeResult;
import com.enterprise.api.entity.UserDetail;
import com.enterprise.api.entity.UserListItem;
import com.enterprise.api.entity.UserRoleAssignment;
import com.enterprise.api.exception.BusinessException;
import com.enterprise.api.exception.ErrorCode;
import com.enterprise.api.mapper.UserMapperImpl;
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
import com.enterprise.api.support.TestSecurityProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    private static final long ACTOR_ID = 1L;
    private static final long USER_ID = 42L;
    private static final UUID STAMP = UUID.fromString("0b9f3c1e-5d8a-4c2b-9e7f-1a2b3c4d5e6f");
    private static final CurrentUser ACTOR = new CurrentUser(ACTOR_ID, UUID.randomUUID(), List.of("Admin"),
            List.of("USER_EDIT"), false);
    private static final ClientInfo CLIENT = new ClientInfo("10.0.0.7", "JUnit");

    @Mock
    private IUserRepository userRepository;
    @Mock
    private ISessionRepository sessionRepository;
    @Mock
    private ISessionValidationService sessionValidationService;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);
    private final PasswordPolicyServiceImpl policyService =
            new PasswordPolicyServiceImpl(TestSecurityProperties.passwordPolicy());
    private UserServiceImpl userService;

    @BeforeEach
    void setUp() {
        userService = new UserServiceImpl(userRepository, sessionRepository, sessionValidationService,
                new TemporaryPasswordGeneratorImpl(TestSecurityProperties.passwordPolicy(), policyService),
                passwordEncoder, new UserMapperImpl());
    }

    @AfterEach
    void clearTransactionSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    // --- reads -------------------------------------------------------------

    @Test
    void getPagedMapsRowsAndPassesFilters() {
        UserListItem item = new UserListItem();
        item.setId(USER_ID);
        item.setUsername("jdoe");
        item.setActive(true);
        item.setLockedOut(true);
        item.setRoles("Admin, User");
        item.setConcurrencyStamp(STAMP);
        when(userRepository.findPaged(any(PageQuery.class), eq(true), eq(3L)))
                .thenReturn(new PagedResult<>(List.of(item), 7));

        PagedResponse<UserListItemResponse> page = userService.getPaged(
                new UserPageRequest(2, 10, "username", "ASC", " jd ", true, 3L));

        assertThat(page.totalCount()).isEqualTo(7);
        assertThat(page.rows()).singleElement().satisfies(row -> {
            assertThat(row.isActive()).isTrue();
            assertThat(row.isLockedOut()).isTrue();
            assertThat(row.roles()).containsExactly("Admin", "User");
            assertThat(row.concurrencyStamp()).isEqualTo(STAMP);
        });
        verify(userRepository).findPaged(new PageQuery(2, 10, "username", "ASC", "jd"), true, 3L);
    }

    @Test
    void getPagedDefaultsToNewestFirst() {
        when(userRepository.findPaged(any(PageQuery.class), isNull(), isNull())).thenReturn(new PagedResult<>(List.of(), 0));

        userService.getPaged(new UserPageRequest(null, null, null, null, null, null, null));

        verify(userRepository).findPaged(new PageQuery(1, UserPageRequest.DEFAULT_PAGE_SIZE, "createdOn", "DESC", null),
                null, null);
    }

    @Test
    void getByIdMapsRolesAndStamp() {
        when(userRepository.findById(USER_ID)).thenReturn(user(USER_ID, 2L, 3L));

        UserDetailResponse detail = userService.getById(USER_ID);

        assertThat(detail.username()).isEqualTo("jdoe");
        assertThat(detail.isActive()).isTrue();
        assertThat(detail.mustChangePassword()).isTrue();
        assertThat(detail.concurrencyStamp()).isEqualTo(STAMP);
        assertThat(detail.roles()).extracting("id").containsExactly(2L, 3L);
        assertThat(detail.roles().getFirst().name()).isEqualTo("Role 2");
        assertThat(detail.roles().getFirst().isActive()).isTrue();
    }

    // --- create / reset: temporary password ----------------------------------

    @Test
    void createStoresOnlyTheHashOfAPolicyCompliantTemporaryPassword() {
        when(userRepository.create(anyString(), anyString(), anyString(), anyString(), anyString(), any(), any(),
                anyLong(), any())).thenReturn(USER_ID);

        TemporaryPasswordResponse response = userService.create(
                new CreateUserRequest(" jdoe ", "jdoe@example.com", "Jane", "Doe", null, List.of(2L, 2L, 3L)),
                ACTOR, CLIENT);

        assertThat(response.userId()).isEqualTo(USER_ID);
        assertThat(response.username()).isEqualTo("jdoe");
        String temporary = response.temporaryPassword();
        assertThat(policyService.validate(temporary, "jdoe", null)).isEmpty();
        assertThat(response.toString()).doesNotContain(temporary);

        ArgumentCaptor<String> hash = ArgumentCaptor.forClass(String.class);
        verify(userRepository).create(eq("jdoe"), eq("jdoe@example.com"), hash.capture(), eq("Jane"), eq("Doe"),
                isNull(), eq(List.of(2L, 3L)), eq(ACTOR_ID), eq("10.0.0.7"));
        assertThat(hash.getValue()).isNotEqualTo(temporary).startsWith("$2");
        assertThat(passwordEncoder.matches(temporary, hash.getValue())).isTrue();
    }

    @Test
    void resetPasswordReturnsANewTemporaryPasswordAndEvictsTheUser() {
        when(userRepository.findById(USER_ID)).thenReturn(user(USER_ID));
        when(userRepository.resetPassword(eq(USER_ID), anyString(), eq(ACTOR_ID), eq("10.0.0.7")))
                .thenReturn(new PasswordChangeResult());

        TemporaryPasswordResponse response = userService.resetPassword(USER_ID, ACTOR, CLIENT);

        assertThat(response.username()).isEqualTo("jdoe");
        assertThat(policyService.validate(response.temporaryPassword(), "jdoe", null)).isEmpty();
        ArgumentCaptor<String> hash = ArgumentCaptor.forClass(String.class);
        verify(userRepository).resetPassword(eq(USER_ID), hash.capture(), eq(ACTOR_ID), eq("10.0.0.7"));
        assertThat(passwordEncoder.matches(response.temporaryPassword(), hash.getValue())).isTrue();
        verify(sessionValidationService, times(2)).evictUser(USER_ID);
    }

    // --- self-action guards ------------------------------------------------------

    @Test
    void cannotDeactivateOwnAccount() {
        UpdateUserRequest request = new UpdateUserRequest("a@b.cd", "A", "B", null, false, STAMP);

        assertThatThrownBy(() -> userService.update(ACTOR_ID, request, ACTOR, CLIENT))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.SELF_DEACTIVATE_NOT_ALLOWED));
        verifyNoInteractions(userRepository, sessionRepository, sessionValidationService);
    }

    @Test
    void canEditOwnProfileWhileStayingActive() {
        when(userRepository.findById(ACTOR_ID)).thenReturn(user(ACTOR_ID));

        userService.update(ACTOR_ID, new UpdateUserRequest("a@b.cd", "A", "B", null, true, STAMP), ACTOR, CLIENT);

        verify(userRepository).update(ACTOR_ID, "a@b.cd", "A", "B", null, true, STAMP, ACTOR_ID, "10.0.0.7");
        verifyNoInteractions(sessionValidationService);
    }

    @Test
    void cannotDeleteOwnAccount() {
        assertThatThrownBy(() -> userService.delete(ACTOR_ID, null, ACTOR, CLIENT))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.SELF_DELETE_NOT_ALLOWED));
        verifyNoInteractions(userRepository, sessionValidationService);
    }

    @Test
    void cannotResetOwnPassword() {
        assertThatThrownBy(() -> userService.resetPassword(ACTOR_ID, ACTOR, CLIENT))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.SELF_PASSWORD_RESET_NOT_ALLOWED));
        verifyNoInteractions(userRepository, sessionValidationService);
    }

    // --- update / delete / unlock --------------------------------------------

    @Test
    void deactivationPassesTheStampAndEvictsTheUser() {
        when(userRepository.findById(USER_ID)).thenReturn(user(USER_ID));

        userService.update(USER_ID, new UpdateUserRequest("a@b.cd", "A", "B", "+1 555", false, STAMP), ACTOR, CLIENT);

        verify(userRepository).update(USER_ID, "a@b.cd", "A", "B", "+1 555", false, STAMP, ACTOR_ID, "10.0.0.7");
        verify(sessionValidationService, times(2)).evictUser(USER_ID);
    }

    @Test
    void deleteEvictsTheUser() {
        userService.delete(USER_ID, STAMP, ACTOR, CLIENT);

        verify(userRepository).softDelete(USER_ID, STAMP, ACTOR_ID, "10.0.0.7");
        verify(sessionValidationService, times(2)).evictUser(USER_ID);
    }

    @Test
    void unlockReturnsTheReloadedUser() {
        when(userRepository.findById(USER_ID)).thenReturn(user(USER_ID));

        UserDetailResponse detail = userService.unlock(USER_ID, ACTOR, CLIENT);

        verify(userRepository).unlock(USER_ID, ACTOR_ID, "10.0.0.7");
        assertThat(detail.id()).isEqualTo(USER_ID);
        verifyNoInteractions(sessionValidationService);
    }

    // --- roles diff ------------------------------------------------------------

    @Test
    void updateRolesAssignsAndRevokesOnlyTheDifferenceAndEndsSessionsOnRemoval() {
        when(userRepository.findById(USER_ID)).thenReturn(user(USER_ID, 1L, 2L, 3L));

        userService.updateRoles(USER_ID, new UpdateUserRolesRequest(List.of(2L, 4L, 5L)), ACTOR, CLIENT);

        verify(userRepository).assignRole(USER_ID, 4L, ACTOR_ID, "10.0.0.7");
        verify(userRepository).assignRole(USER_ID, 5L, ACTOR_ID, "10.0.0.7");
        verify(userRepository).revokeRole(USER_ID, 1L, ACTOR_ID, "10.0.0.7");
        verify(userRepository).revokeRole(USER_ID, 3L, ACTOR_ID, "10.0.0.7");
        verify(userRepository, never()).assignRole(anyLong(), eq(2L), anyLong(), any());
        verify(userRepository, never()).revokeRole(anyLong(), eq(2L), anyLong(), any());

        InOrder order = inOrder(userRepository, sessionRepository, sessionValidationService);
        order.verify(userRepository).revokeRole(USER_ID, 3L, ACTOR_ID, "10.0.0.7");
        order.verify(sessionRepository).revokeAllForUser(USER_ID, UserServiceImpl.REASON_ROLES_CHANGED, null);
        order.verify(sessionValidationService, times(2)).evictUser(USER_ID);
    }

    @Test
    void updateRolesWithOnlyAdditionsKeepsTheSessions() {
        when(userRepository.findById(USER_ID)).thenReturn(user(USER_ID, 1L));

        userService.updateRoles(USER_ID, new UpdateUserRolesRequest(List.of(1L, 2L)), ACTOR, CLIENT);

        verify(userRepository).assignRole(USER_ID, 2L, ACTOR_ID, "10.0.0.7");
        verify(userRepository, never()).revokeRole(anyLong(), anyLong(), anyLong(), any());
        verifyNoInteractions(sessionRepository, sessionValidationService);
    }

    @Test
    void updateRolesWithTheSameListChangesNothing() {
        when(userRepository.findById(USER_ID)).thenReturn(user(USER_ID, 1L, 2L));

        userService.updateRoles(USER_ID, new UpdateUserRolesRequest(List.of(2L, 1L)), ACTOR, CLIENT);

        verify(userRepository, never()).assignRole(anyLong(), anyLong(), anyLong(), any());
        verify(userRepository, never()).revokeRole(anyLong(), anyLong(), anyLong(), any());
        verifyNoInteractions(sessionRepository, sessionValidationService);
    }

    @Test
    void updateRolesWithAnEmptyListRemovesEveryRole() {
        when(userRepository.findById(USER_ID)).thenReturn(user(USER_ID, 1L, 2L));

        userService.updateRoles(USER_ID, new UpdateUserRolesRequest(List.of()), ACTOR, CLIENT);

        verify(userRepository).revokeRole(USER_ID, 1L, ACTOR_ID, "10.0.0.7");
        verify(userRepository).revokeRole(USER_ID, 2L, ACTOR_ID, "10.0.0.7");
        verify(sessionRepository).revokeAllForUser(USER_ID, UserServiceImpl.REASON_ROLES_CHANGED, null);
    }

    // --- eviction after commit ---------------------------------------------------

    @Test
    void evictsImmediatelyAndAgainOnlyAfterTheCommit() {
        TransactionSynchronizationManager.initSynchronization();

        userService.delete(USER_ID, null, ACTOR, CLIENT);

        verify(sessionValidationService, times(1)).evictUser(USER_ID);
        List<TransactionSynchronization> synchronizations = TransactionSynchronizationManager.getSynchronizations();
        assertThat(synchronizations).hasSize(1);

        synchronizations.forEach(TransactionSynchronization::afterCommit);
        verify(sessionValidationService, times(2)).evictUser(USER_ID);
    }

    private static UserDetail user(long id, Long... roleIds) {
        UserDetail user = new UserDetail();
        user.setId(id);
        user.setUsername("jdoe");
        user.setEmail("jdoe@example.com");
        user.setFirstName("Jane");
        user.setLastName("Doe");
        user.setActive(true);
        user.setMustChangePassword(true);
        user.setCreatedOn(Instant.parse("2026-01-02T03:04:05Z"));
        user.setConcurrencyStamp(STAMP);
        user.setRoles(java.util.Arrays.stream(roleIds).map(roleId -> {
            UserRoleAssignment role = new UserRoleAssignment();
            role.setRoleId(roleId);
            role.setRoleName("Role " + roleId);
            role.setActive(true);
            return role;
        }).toList());
        return user;
    }
}
