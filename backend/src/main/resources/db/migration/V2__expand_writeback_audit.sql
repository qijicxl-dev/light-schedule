alter table writeback_audit add
    attempt_count int not null default 0,
    max_attempts int not null default 1,
    retryable bit not null default 0,
    external_request_id varchar(128) null,
    payload_json nvarchar(max) null,
    response_json nvarchar(max) null,
    next_retry_at datetime2 null,
    updated_at datetime2 not null default sysutcdatetime();

go

create index idx_writeback_audit_status_next_retry on writeback_audit(status, next_retry_at);
