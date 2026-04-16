package com.vishal.sp_III.controller;

import com.vishal.sp_III.entity.Department;
import com.vishal.sp_III.repository.DepartmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/departments")
@RequiredArgsConstructor
public class DepartmentController {

    private final DepartmentRepository repo;

    @PostMapping
    public Department create(@RequestBody Department d) {
        return repo.save(d);
    }

    @GetMapping
    public List<Department> getAll() {
        return repo.findAll();
    }
}
