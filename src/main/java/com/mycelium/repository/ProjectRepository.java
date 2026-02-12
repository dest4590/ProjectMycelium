package com.mycelium.repository;

import com.mycelium.model.ProjectNode;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProjectRepository extends Neo4jRepository<ProjectNode, String> {
    Optional<ProjectNode> findByName(String name);
}