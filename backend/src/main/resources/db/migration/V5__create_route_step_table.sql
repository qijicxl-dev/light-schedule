create table route_step (
    id varchar(64) not null primary key,
    route_id varchar(64) not null,
    step_id varchar(64) not null,
    required_minutes int not null,
    dependency_step_ids nvarchar(max) null,
    created_at datetime2 not null default sysutcdatetime()
);

go

insert into route_step (id, route_id, step_id, required_minutes, dependency_step_ids) values
    ('rs-1', 'ROUTE-01', 'TASK-001', 120, '[]'),
    ('rs-2', 'ROUTE-01', 'TASK-002', 90, '["TASK-001"]');
