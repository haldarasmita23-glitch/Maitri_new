package com.maitri.dto.admin;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class VendorManagementRequest {

    private String shopName;
    private String ownerName;
    private String categoryId;
    private String description;
    private String address;
    private String area;
    private String phone;
    private String openingTime;
    private String closingTime;
    private Boolean active;
}