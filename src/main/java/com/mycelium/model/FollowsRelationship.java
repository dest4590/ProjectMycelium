package com.mycelium.model;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.neo4j.core.schema.*;

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
    private Long id;
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
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FollowsRelationship that = (FollowsRelationship) o;
        return Objects.equals(targetUser, that.targetUser);
    }

    @Override
    public int hashCode() {
        return Objects.hash(targetUser);
    }
}