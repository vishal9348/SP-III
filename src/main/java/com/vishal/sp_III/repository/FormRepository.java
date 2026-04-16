package com.vishal.sp_III.repository;

import com.vishal.sp_III.entity.FormData;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FormRepository extends JpaRepository<FormData, Long> {
    Optional<FormData> findByProcessInstanceId(String processInstanceId);
    List<FormData> findByStatus(String status);
    List<FormData> findByFormTypeId(Long formTypeId);
}
