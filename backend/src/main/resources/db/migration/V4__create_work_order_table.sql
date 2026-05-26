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
    ('wo-1', 'WO-001', 'released', 20, '2026-04-24T08:00:00Z', 'ROUTE-01', 0, '[]', 'low'),
    ('wo-2', 'WO-002', 'released', 15, '2026-04-24T20:00:00Z', 'ROUTE-02', 0, NULL, 'medium'),
    ('wo-3', 'WO-003', 'released', 30, '2026-04-25T08:00:00Z', 'ROUTE-03', 0, NULL, 'medium'),
    ('wo-4', 'WO-004', 'released', 25, '2026-04-25T12:00:00Z', 'ROUTE-03', 1, NULL, 'high'),
    ('wo-5', 'WO-005', 'released', 18, '2026-04-25T16:00:00Z', 'ROUTE-04', 0, NULL, 'low'),
    ('wo-6', 'WO-006', 'released', 22, '2026-04-26T08:00:00Z', 'ROUTE-04', 0, NULL, 'medium');
