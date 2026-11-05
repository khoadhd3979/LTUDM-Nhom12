package com.securechat.dto;

import java.time.LocalDateTime;

public class GroupDTO {
    private long groupId;
    private String groupName;
    private long createdBy;
    private LocalDateTime createdAt;
    private String role;  // role của current user trong group; null nếu query không tính

    public long getGroupId() { return groupId; }
    public void setGroupId(long groupId) { this.groupId = groupId; }
    public String getGroupName() { return groupName; }
    public void setGroupName(String groupName) { this.groupName = groupName; }
    public long getCreatedBy() { return createdBy; }
    public void setCreatedBy(long createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
}