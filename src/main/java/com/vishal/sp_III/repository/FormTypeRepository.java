package com.vishal.sp_III.repository;

import com.vishal.sp_III.entity.FormType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FormTypeRepository extends JpaRepository<FormType, Long> {
    List<FormType> findByDepartmentId(Long departmentId);
    boolean existsByName(String name);

    // New methods for multi-department support
    Optional<FormType> findByName(String name);

    // Find sub-form type by parent form type ID and department ID
    @Query("SELECT ft FROM FormType ft WHERE ft.parentFormTypeId = :parentFormTypeId AND ft.departmentId = :departmentId")
    Optional<FormType> findByParentFormTypeIdAndDepartmentId(@Param("parentFormTypeId") Long parentFormTypeId,
                                                             @Param("departmentId") Long departmentId);

    // Find all sub-form types for a master form
    @Query("SELECT ft FROM FormType ft WHERE ft.parentFormTypeId = :parentFormTypeId")
    List<FormType> findByParentFormTypeId(@Param("parentFormTypeId") Long parentFormTypeId);

    // Find all master forms (isMasterForm = true)
    @Query("SELECT ft FROM FormType ft WHERE ft.isMasterForm = true")
    List<FormType> findAllMasterForms();

    // Find all sub-forms for a master form with their department information
    @Query("SELECT ft FROM FormType ft WHERE ft.parentFormTypeId = :parentFormTypeId AND ft.isMasterForm = false")
    List<FormType> findSubFormsByParentId(@Param("parentFormTypeId") Long parentFormTypeId);

    // Find form type by workflow key
    @Query("SELECT ft FROM FormType ft WHERE ft.associatedWorkflowKey = :workflowKey")
    List<FormType> findByAssociatedWorkflowKey(@Param("workflowKey") String workflowKey);

    // Count sub-forms for a master form
    @Query("SELECT COUNT(ft) FROM FormType ft WHERE ft.parentFormTypeId = :parentFormTypeId")
    long countSubFormsByParentId(@Param("parentFormTypeId") Long parentFormTypeId);

}
