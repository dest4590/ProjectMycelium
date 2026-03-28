package com.mycelium.repository;

import com.mycelium.model.UserNode;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface UserRepository extends Neo4jRepository<UserNode, String> {

    @Query("""
            MATCH (u:User {username: $username})
            OPTIONAL MATCH (u)-[r:FOLLOWS]->(following:User)
            WITH u, collect(r) as follows, collect(following) as followingUsers
            OPTIONAL MATCH (u)-[p_rel:BELONGS_TO]->(p:Project)
            RETURN u, follows, followingUsers, collect(p_rel), collect(p)
            """)
    Optional<UserNode> findByUsername(String username);

    @Query("""
            MATCH (targetUser:User {username: $username})
            OPTIONAL MATCH (follower:User)-[:FOLLOWS]->(targetUser)
            WITH targetUser, follower
            WITH targetUser, follower, size([(follower)--() WHERE () <> targetUser | 1]) as otherRelationshipsCount
            FOREACH (f IN CASE WHEN follower IS NOT NULL AND otherRelationshipsCount = 0 THEN [follower] ELSE [] END |
                DETACH DELETE f
            )
            
            WITH targetUser
            DETACH DELETE targetUser
            """)
    void detachDeleteUserByUsername(String username);

    @Query("""
            MATCH (proj:Project {name: $projectName})<-[:BELONGS_TO]-(u:User)
            WITH u, proj
            OPTIONAL MATCH (follower:User)-[:FOLLOWS]->(u)
            WHERE (follower)-[:BELONGS_TO]->(proj)
            WITH u, proj, count(follower) as followerCount
            ORDER BY followerCount DESC
            LIMIT 30
            OPTIONAL MATCH (u)-[r:FOLLOWS]->(f:User)
            WHERE (f)-[:BELONGS_TO]->(proj)
            WITH u, proj, collect(DISTINCT r) as follows, collect(DISTINCT f) as followingUsers
            OPTIONAL MATCH (u)-[p_rel:BELONGS_TO]->(proj)
            RETURN u, follows, followingUsers, collect(p_rel) as projectRelationships, collect(proj) as projects
            """)
    List<UserNode> findTop30ByProject(String projectName);

    @Query("""
            MATCH (proj:Project {name: $projectName})<-[:BELONGS_TO]-(u:User {username: $username})
            OPTIONAL MATCH (u)--(neighbor:User)-[:BELONGS_TO]->(proj)
            WITH collect(u) + collect(neighbor) AS userNodes, proj
            UNWIND userNodes AS singleUser
            WITH DISTINCT singleUser, proj
            OPTIONAL MATCH (singleUser)-[r:FOLLOWS]->(f:User)
            WHERE (f)-[:BELONGS_TO]->(proj)
            WITH singleUser, proj, collect(DISTINCT r) as follows, collect(DISTINCT f) as followingUsers
            OPTIONAL MATCH (singleUser)-[p_rel:BELONGS_TO]->(proj)
            RETURN singleUser, follows, followingUsers, collect(p_rel) as projectRelationships, collect(proj) as projects
            """)
    List<UserNode> findUserAndNeighborsInProject(String username, String projectName);


    @Query("""
            MATCH (proj:Project {name: $projectName})<-[p_rel:BELONGS_TO]-(u:User)
            OPTIONAL MATCH (u)-[r:FOLLOWS]->(neighbor:User)-[:BELONGS_TO]->(proj)
            RETURN u, collect(r) as follows, collect(neighbor) as followingUsers, collect(p_rel) as projectRelationships, collect(proj) as projects
            """)
    List<UserNode> findAllByProject(String projectName);

    @Query("MATCH (p:Project {name: $projectName}) " +
            "MATCH (startNode:User {username: $startUsername})-[:BELONGS_TO]->(p), " +
            "(endNode:User {username: $endUsername})-[:BELONGS_TO]->(p) " +
            "MATCH path = shortestPath((startNode)-[:FOLLOWS*..15]-(endNode)) " +
            "UNWIND nodes(path) AS user " +
            "RETURN user.username")
    List<String> findShortestPathInProject(String startUsername, String endUsername, String projectName);

    @Query("""
            MATCH (follower:User)-[:FOLLOWS]->(u:User {username: $username})
            WHERE (follower)-[:BELONGS_TO]->(:Project {name: $projectName})
            RETURN follower
            """)
    List<UserNode> findFollowersOf(String username, String projectName);


    @Query("""
            MATCH (u:User {username: $username})
            
            OPTIONAL MATCH (u)-[r:FOLLOWS]->(oldFollowing:User)
            WHERE NOT oldFollowing.username IN $newFollowingUsernames
            SET r.isActive = false, r.endDate = date()
            WITH u
            
            UNWIND $newFollowingUsernames AS newFollowingUsername
            
            MERGE (p:Project {name: $projectName})
            MERGE (newFollowing:User {username: newFollowingUsername})
            ON CREATE SET newFollowing.isScanned = false
            MERGE (newFollowing)-[:BELONGS_TO]->(p)
            
            MERGE (u)-[r:FOLLOWS]->(newFollowing)
            ON CREATE SET r.startDate = date(), r.isActive = true
            ON MATCH SET r.isActive = true, r.endDate = null
            """)
    void updateFollowingRelationships(String username, Set<String> newFollowingUsernames, String projectName);


    @Query("""
            MATCH (u:User {username: $username})
            
            OPTIONAL MATCH (oldFollower:User)-[r:FOLLOWS]->(u)
            WHERE NOT oldFollower.username IN $newFollowerUsernames
            SET r.isActive = false, r.endDate = date()
            WITH u
            
            UNWIND $newFollowerUsernames AS newFollowerUsername
            
            MERGE (p:Project {name: $projectName})
            MERGE (newFollower:User {username: newFollowerUsername})
            ON CREATE SET newFollower.isScanned = false
            MERGE (newFollower)-[:BELONGS_TO]->(p)
            
            MERGE (newFollower)-[r:FOLLOWS]->(u)
            ON CREATE SET r.startDate = date(), r.isActive = true
            ON MATCH SET r.isActive = true, r.endDate = null
            """)
    void updateFollowerRelationships(String username, Set<String> newFollowerUsernames, String projectName);
}