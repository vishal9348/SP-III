package com.vishal.sp_III.controller;

import com.vishal.sp_III.entity.Role;
import com.vishal.sp_III.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/roles")
@RequiredArgsConstructor
public class RoleController {

    private final RoleRepository repo;

    @PostMapping
    public Role create(@RequestBody Role r) {
        return repo.save(r);
    }
}