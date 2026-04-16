package com.vishal.sp_III.repository;

import com.vishal.sp_III.entity.WorkflowMapping;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkflowMappingRepository extends JpaRepository<WorkflowMapping, Long> {
    WorkflowMapping findByFormTypeIdAndActiveTrue(Long formTypeId);
}
