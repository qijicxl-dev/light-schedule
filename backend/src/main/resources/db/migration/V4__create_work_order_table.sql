create table work_order (
    id varchar(64) not null primary key,
    work_order_code varchar(64) not null unique,
    status varchar(32) not null,
    quantity int not null,
    due_at varchar(32) not null,
    route_id varchar(64) not null,
    urgent bit not null default 0,
    parent_work_order_codes nvarchar(max) null,
    material_risk varchar(32) not null,
    created_at datetime2 not null default sysutcdatetime()
);

go

insert into work_order (id, work_order_code, status, quantity, due_at, route_id, urgent, parent_work_order_codes, material_risk) values
    ('wo-1', 'WO-001', 'released', 20, '2026-04-24T08:00:00Z', 'ROUTE-01', 0, '[]', 'low');
