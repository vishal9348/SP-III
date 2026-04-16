package com.vishal.sp_III.dto;

import lombok.Data;

import java.util.Map;

@Data
public class ActionRequest {
    private String action;           // approve, reject, send_back_to_maker, send_back_to_checker
    private String actor;            // maker, checker, cochecker, admin
    private String comments;         // Optional comments
    private Map<String, Object> additionalData;  // Any extra data
}
