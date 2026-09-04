package com.settlegraph.Artifacts.dto;

import java.util.List;

public class CreateGroupRequest {
    private String name;
    private List<Long> memberUserIds;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public List<Long> getMemberUserIds() { return memberUserIds; }
    public void setMemberUserIds(List<Long> memberUserIds) { this.memberUserIds = memberUserIds; }
}
