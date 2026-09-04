package com.settlegraph.Artifacts.repository;

import com.settlegraph.Artifacts.entity.GroupMember;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface GroupMemberRepository extends JpaRepository<GroupMember, GroupMember.GroupMemberId> {
    List<GroupMember> findByIdGroupId(Long groupId);
    List<GroupMember> findByIdUserId(Long userId);
}
