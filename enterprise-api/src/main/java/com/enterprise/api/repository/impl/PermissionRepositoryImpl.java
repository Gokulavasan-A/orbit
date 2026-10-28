package com.enterprise.api.repository.impl;

import java.util.List;

import com.enterprise.api.entity.Permission;
import com.enterprise.api.repository.IPermissionRepository;
import com.enterprise.api.repository.support.StoredProcedureExecutor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class PermissionRepositoryImpl implements IPermissionRepository {

    private static final String ROWS = "rows";

    private static final RowMapper<Permission> PERMISSION_MAPPER = (rs, rowNum) -> {
        Permission permission = new Permission();
        permission.setId(rs.getLong("Id"));
        permission.setCode(rs.getString("Code"));
        permission.setName(rs.getString("Name"));
        permission.setModule(rs.getString("Module"));
        permission.setDescription(rs.getString("Description"));
        return permission;
    };

    private final StoredProcedureExecutor executor;

    public PermissionRepositoryImpl(StoredProcedureExecutor executor) {
        this.executor = executor;
    }

    @Override
    public List<Permission> findAll() {
        return executor.call("usp_Permission_GetAll")
                .resultSet(ROWS, PERMISSION_MAPPER)
                .execute()
                .rows(ROWS);
    }
}
