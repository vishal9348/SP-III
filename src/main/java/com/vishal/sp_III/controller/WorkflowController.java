package com.vishal.sp_III.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.engine.RepositoryService;
//import org.camunda.bpm.engine.test.Deployment;
import org.camunda.bpm.engine.repository.Deployment;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@Slf4j
@RequestMapping("/workflow")
@RequiredArgsConstructor
public class WorkflowController {

    private final RepositoryService repositoryService;
//
//    @PostMapping("/upload")
//    public String upload(@RequestParam MultipartFile file) throws Exception {
//
//        repositoryService.createDeployment()
//                .addInputStream(file.getOriginalFilename(), file.getInputStream())
//                .deploy();
//
//        return "Workflow Uploaded";
//    }

    @PostMapping("/upload")
    public ResponseEntity<String> upload(@RequestParam("file") MultipartFile file) {
        try {
            // Validate file
            if (file.isEmpty()) {
                return ResponseEntity.badRequest().body("File is empty");
            }

            if (!file.getOriginalFilename().endsWith(".bpmn")) {
                return ResponseEntity.badRequest().body("Only .bpmn files are allowed");
            }

            // Deploy the workflow
            Deployment deployment = repositoryService.createDeployment()
                    .name(file.getOriginalFilename())
                    .addInputStream(file.getOriginalFilename(), file.getInputStream())
                    .deploy();

            log.info("Workflow deployed successfully: {}", deployment.getId());
            return ResponseEntity.ok("Workflow deployed successfully! Deployment ID: " + deployment.getId());

        } catch (Exception e) {
            log.error("Failed to deploy workflow: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                    .body("Failed to deploy workflow: " + e.getMessage());
        }
    }

    @GetMapping("/deployments")
    public ResponseEntity<?> getDeployments() {
        List<Deployment> deployments = repositoryService.createDeploymentQuery().list();

        // Convert to DTOs with only needed fields
        List<Map<String, Object>> deploymentInfo = deployments.stream()
                .map(d -> {
                    Map<String, Object> info = new HashMap<>();
                    info.put("id", d.getId());
                    info.put("name", d.getName());
                    info.put("deploymentTime", d.getDeploymentTime());
                    info.put("source", d.getSource());
                    return info;
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(deploymentInfo);
    }
}
