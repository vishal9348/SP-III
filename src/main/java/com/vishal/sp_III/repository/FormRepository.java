package com.vishal.sp_III.repository;

import com.vishal.sp_III.entity.FormData;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FormRepository extends JpaRepository<FormData, Long> {
    Optional<FormData> findByProcessInstanceId(String processInstanceId);
    List<FormData> findByStatus(String status);
    List<FormData> findByFormTypeId(Long formTypeId);

    // Find all sub-forms for a master form
    List<FormData> findByParentFormDataId(Long parentFormDataId);

    // Find sub-form by department name
    Optional<FormData> findByParentFormDataIdAndDepartmentName(Long parentFormDataId, String departmentName);

    // Find all sub-forms with specific status
    List<FormData> findByParentFormDataIdAndStatus(Long parentFormDataId, String status);

    // Count sub-forms by status
    long countByParentFormDataIdAndStatus(Long parentFormDataId, String status);
}
