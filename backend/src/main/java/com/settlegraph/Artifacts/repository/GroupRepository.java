package com.settlegraph.Artifacts.repository;

import com.settlegraph.Artifacts.entity.Group;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface GroupRepository extends JpaRepository<Group, Long> {
    @Query("SELECT g FROM Group g JOIN GroupMember gm ON gm.id.groupId = g.id WHERE gm.id.userId = :userId")
    List<Group> findAllForUser(Long userId);
}
