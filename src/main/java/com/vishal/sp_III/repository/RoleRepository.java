package com.vishal.sp_III.repository;

import com.vishal.sp_III.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RoleRepository extends JpaRepository<Role, Long> {
    Role findByName(String names);
}