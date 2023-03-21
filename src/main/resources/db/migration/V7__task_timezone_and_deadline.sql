alter table tasks
    add column timezone varchar(255) default 'UTC';

alter table tasks
    add column deadline timestamp;
