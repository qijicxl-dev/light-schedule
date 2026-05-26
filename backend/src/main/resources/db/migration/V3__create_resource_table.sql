create table resource (
    id varchar(64) not null primary key,
    resource_id varchar(64) not null unique,
    group_name nvarchar(64) not null,
    default_planner bit not null default 0,
    created_at datetime2 not null default sysutcdatetime()
);

go

insert into resource (id, resource_id, group_name, default_planner) values
    ('res-1', 'LINE-A', '冲压组', 1),
    ('res-2', 'LINE-B', '冲压组', 1),
    ('res-3', 'LINE-C', '装配组', 0);
