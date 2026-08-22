package com.maitri.dto.admin;

import com.maitri.model.Role;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class UserManagementRequest {

    private String name;
    private String email;
    private Role role;
    private Boolean active;
}