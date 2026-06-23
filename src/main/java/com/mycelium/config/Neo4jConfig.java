package com.mycelium.config;

import org.neo4j.driver.Driver;
import org.neo4j.driver.SessionConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

@Configuration
public class Neo4jConfig {

    private static final Logger log = LoggerFactory.getLogger(Neo4jConfig.class);

    private final Driver driver;

    public Neo4jConfig(Driver driver) {
        this.driver = driver;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void createIndexes() {
        try (var session = driver.session(SessionConfig.forDatabase("neo4j"))) {
            session.run("CREATE INDEX user_username IF NOT EXISTS FOR (u:User) ON (u.username)");
            session.run("CREATE INDEX project_name IF NOT EXISTS FOR (p:Project) ON (p.name)");
            log.info("Ensured Neo4j indexes on User.username and Project.name");
        }
    }
}
