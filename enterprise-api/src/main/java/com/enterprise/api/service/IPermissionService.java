package com.enterprise.api.service;

import java.util.List;

import com.enterprise.api.model.response.PermissionGroupResponse;

public interface IPermissionService {

    /** Active permissions grouped by module (modules and codes in alphabetical order). */
    List<PermissionGroupResponse> getGroupedByModule();
}
