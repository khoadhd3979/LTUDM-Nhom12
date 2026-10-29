# Database Documentation & Migration Notes (SecureChat-Java)

## 1. Active Schema (Phase 1 & Phase 2)
The authoritative SQL Server schema is in `database/schema_mssql.sql`:
- `dbo.users`: stores user accounts, bcrypt password hashes, profile data, account status (`PENDING`, `ACTIVE`, `LOCKED`).
- `dbo.email_otp`: stores 6-digit email activation OTP records with bcrypt hash, expiration timestamp (10 mins), and max attempt tracking.

To initialize or reset the database:
```sql
-- Execute database/schema_mssql.sql in SQL Server Management Studio (SSMS) or sqlcmd
```

## 2. Phase 4 Planned Schema (Chat & Messaging)
Migrated from legacy queries (`_legacy/GroupDAO.java`, `_legacy/MessageDAO.java`):

### Groups Table
```sql
CREATE TABLE dbo.groups (
    id         BIGINT IDENTITY(1,1) PRIMARY KEY,
    name       NVARCHAR(100) NOT NULL,
    created_by BIGINT NOT NULL,
    created_at DATETIME2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
    CONSTRAINT fk_group_creator FOREIGN KEY (created_by) REFERENCES dbo.users(id)
);

CREATE TABLE dbo.group_members (
    id        BIGINT IDENTITY(1,1) PRIMARY KEY,
    group_id  BIGINT NOT NULL,
    user_id   BIGINT NOT NULL,
    role      NVARCHAR(20) NOT NULL DEFAULT 'MEMBER', -- 'ADMIN', 'MEMBER'
    joined_at DATETIME2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
    CONSTRAINT fk_gm_group FOREIGN KEY (group_id) REFERENCES dbo.groups(id) ON DELETE CASCADE,
    CONSTRAINT fk_gm_user  FOREIGN KEY (user_id)  REFERENCES dbo.users(id) ON DELETE CASCADE,
    CONSTRAINT uq_group_member UNIQUE (group_id, user_id)
);
```

### Messages Table
```sql
CREATE TABLE dbo.messages (
    id          BIGINT IDENTITY(1,1) PRIMARY KEY,
    sender_id   BIGINT NOT NULL,
    receiver_id BIGINT NULL,        -- NULL if group message
    group_id    BIGINT NULL,        -- NULL if 1-1 direct message
    content     NVARCHAR(MAX) NOT NULL,
    type        NVARCHAR(20) NOT NULL DEFAULT 'TEXT', -- 'TEXT', 'FILE'
    status      NVARCHAR(20) NOT NULL DEFAULT 'SENT', -- 'SENT', 'DELIVERED', 'READ'
    created_at  DATETIME2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
    CONSTRAINT fk_msg_sender   FOREIGN KEY (sender_id)   REFERENCES dbo.users(id),
    CONSTRAINT fk_msg_receiver FOREIGN KEY (receiver_id) REFERENCES dbo.users(id),
    CONSTRAINT fk_msg_group    FOREIGN KEY (group_id)    REFERENCES dbo.groups(id)
);

CREATE INDEX ix_msg_history ON dbo.messages (sender_id, receiver_id, created_at);
CREATE INDEX ix_msg_group   ON dbo.messages (group_id, created_at);
```

### Standard Chat History Query (Offset-Fetch Pagination)
```sql
SELECT id, sender_id, receiver_id, group_id, content, type, status, created_at
FROM dbo.messages
WHERE group_id IS NULL
  AND ((sender_id = @user1 AND receiver_id = @user2) OR (sender_id = @user2 AND receiver_id = @user1))
ORDER BY created_at ASC
OFFSET @offset ROWS FETCH NEXT @limit ROWS ONLY;
```

