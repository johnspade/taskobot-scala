create table reminders
(
    id bigserial not null
        primary key,
    task_id bigint not null 
        constraint reminders_tasks_fk
            references tasks,
    user_id bigint not null
        constraint reminders_users_fk
            references users,
    offset_minutes integer not null,
    status varchar(255) not null
);

alter table users
    add column blocked_bot boolean;
