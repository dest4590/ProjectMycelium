package com.mycelium.model;

import lombok.Getter;
import lombok.Setter;

import org.springframework.data.neo4j.core.schema.GeneratedValue;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Property;
import org.springframework.data.neo4j.core.schema.RelationshipProperties;
import org.springframework.data.neo4j.core.schema.TargetNode;

import java.time.LocalDate;
import java.util.Objects;

@RelationshipProperties
@Getter
@Setter
public class FollowsRelationship {
    @TargetNode
    private final UserNode targetUser;

    @Id
    @GeneratedValue
    private String id;

    @Property("startDate")
    private LocalDate startDate;

    @Property("endDate")
    private LocalDate endDate;

    @Property("isActive")
    private boolean isActive;

    public FollowsRelationship(UserNode targetUser) {
        this.targetUser = targetUser;
        this.startDate = LocalDate.now();
        this.isActive = true;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        FollowsRelationship that = (FollowsRelationship) o;
        return Objects.equals(targetUser, that.targetUser);
    }

    @Override
    public int hashCode() {
        return Objects.hash(targetUser);
    }
}