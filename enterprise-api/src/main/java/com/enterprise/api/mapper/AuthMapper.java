package com.enterprise.api.mapper;

import com.enterprise.api.entity.LoginUser;
import com.enterprise.api.entity.UserDetail;
import com.enterprise.api.model.response.UserProfileResponse;
import org.mapstruct.Mapper;

@Mapper
public interface AuthMapper {

    UserProfileResponse toProfile(LoginUser user);

    UserProfileResponse toProfile(UserDetail user);
}
