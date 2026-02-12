package com.mycelium.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Property;
import org.springframework.data.neo4j.core.schema.Relationship;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

@Node("User")
@Getter
@Setter
@NoArgsConstructor
public class UserNode {

    @Id
    private String username;

    @Property("isPrivate")
    private boolean isPrivate;

    @Property("isScanned")
    private boolean isScanned = false;

    @Property("lastScanned")
    private LocalDate lastScanned;

    @Relationship(type = "FOLLOWS", direction = Relationship.Direction.OUTGOING)
    private Set<FollowsRelationship> follows = new HashSet<>();

    @Relationship(type = "BELONGS_TO")
    private Set<ProjectNode> projects = new HashSet<>();


    public UserNode(String username) {
        this.username = username;
    }

    public void addProject(ProjectNode project) {
        this.projects.add(project);
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UserNode userNode = (UserNode) o;
        return Objects.equals(username, userNode.username);
    }

    @Override
    public int hashCode() {
        return Objects.hash(username);
    }

    @Override
    public String toString() {
        return "UserNode{" +
                "username='" + username + '\'' +
                ", isPrivate=" + isPrivate +
                ", isScanned=" + isScanned +
                '}';
    }
}