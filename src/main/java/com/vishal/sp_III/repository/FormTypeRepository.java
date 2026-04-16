package com.vishal.sp_III.repository;

import com.vishal.sp_III.entity.FormType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FormTypeRepository extends JpaRepository<FormType, Long> {
    List<FormType> findByDepartmentId(Long departmentId);
    boolean existsByName(String name);
}
