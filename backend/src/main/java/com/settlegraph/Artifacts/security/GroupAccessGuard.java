package com.settlegraph.Artifacts.security;

import com.settlegraph.Artifacts.entity.GroupMember;
import com.settlegraph.Artifacts.exception.ForbiddenException;
import com.settlegraph.Artifacts.repository.GroupMemberRepository;
import org.springframework.stereotype.Component;

/**
 * One place for the "is this user allowed to see/touch this group's data?" check.
 * Group data (expenses, balances, settlements, audit log) is only visible to
 * members of that group.
 */
@Component
public class GroupAccessGuard {

    private final GroupMemberRepository groupMemberRepository;

    public GroupAccessGuard(GroupMemberRepository groupMemberRepository) {
        this.groupMemberRepository = groupMemberRepository;
    }

    public boolean isMember(Long groupId, Long userId) {
        return groupMemberRepository.existsById(new GroupMember.GroupMemberId(userId, groupId));
    }

    /** Throws ForbiddenException (→ HTTP 403) if the user is not a member of the group. */
    public void requireMember(Long groupId, Long userId) {
        if (!isMember(groupId, userId)) {
            throw new ForbiddenException("You are not a member of this group");
        }
    }
}
