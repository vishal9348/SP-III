package com.vishal.sp_III.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class MultiDepartmentRequest {
    private Long masterFormTypeId;
    private Map<String, Object> masterData;
    private List<Map<String, Object>> subFormsData;
}
