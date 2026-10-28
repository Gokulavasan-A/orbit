package com.enterprise.api.repository;

import java.util.List;

import com.enterprise.api.entity.Permission;

public interface IPermissionRepository {

    /** {@code usp_Permission_GetAll}: active permissions ordered by module, then code. */
    List<Permission> findAll();
}
