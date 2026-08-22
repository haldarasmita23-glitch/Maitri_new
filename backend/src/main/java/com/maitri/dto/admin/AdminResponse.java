package com.maitri.dto.admin;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class AdminResponse {

    private Boolean success;
    private String message;
    private String action;
}