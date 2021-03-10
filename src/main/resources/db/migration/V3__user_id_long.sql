alter table users
    alter column id type bigint;

alter table tasks
    alter column sender_id type bigint;

alter table tasks
    alter column receiver_id type bigint;
