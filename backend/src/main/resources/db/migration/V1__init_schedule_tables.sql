create table schedule_draft (
    id varchar(64) not null primary key,
    version_no int not null,
    status varchar(32) not null,
    draft_payload nvarchar(max) not null,
    created_at datetime2 not null default sysutcdatetime()
);

go

create table writeback_audit (
    id varchar(64) not null primary key,
    draft_id varchar(64) not null,
    status varchar(32) not null,
    message nvarchar(255) not null,
    created_at datetime2 not null default sysutcdatetime()
);

go

create index idx_writeback_audit_draft_id on writeback_audit(draft_id);
