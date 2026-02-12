package com.mycelium.controller;

import com.mycelium.model.ProjectNode;
import com.mycelium.repository.ProjectRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectRepository projectRepository;

    public ProjectController(ProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
    }

    @GetMapping
    public List<String> getAllProjectNames() {
        return projectRepository.findAll().stream()
                .map(ProjectNode::getName)
                .collect(Collectors.toList());
    }

    @PostMapping
    public ResponseEntity<String> createProject(@RequestParam String name) {
        if (name == null || name.trim().isEmpty()) {
            return ResponseEntity.badRequest().body("project name must not be empty");
        }

        Optional<ProjectNode> existingProject = projectRepository.findByName(name);
        if (existingProject.isPresent()) {
            return ResponseEntity.status(409).body("a project with this name already exists");
        }

        ProjectNode newProject = new ProjectNode(name);
        projectRepository.save(newProject);
        return ResponseEntity.ok("project '" + name + "' has been successfully created");
    }

    @DeleteMapping("/{projectName}")
    public ResponseEntity<String> deleteProjectByName(@PathVariable String projectName) {
        if (projectName == null || projectName.trim().isEmpty()) {
            return ResponseEntity.badRequest().body("project name must not be empty");
        }

        Optional<ProjectNode> existingProject = projectRepository.findByName(projectName);
        if (existingProject.isEmpty()) {
            return ResponseEntity.status(404).body("a project with this name was not found");
        }

        projectRepository.delete(existingProject.get());
        return ResponseEntity.ok("project '" + projectName + "' has been successfully deleted");
    }
}