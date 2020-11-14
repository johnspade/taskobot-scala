create table users
(
    id integer not null
        constraint users_pkey
            primary key,
    chat_id bigint,
    first_name varchar(255) not null,
    last_name varchar(255),
    language varchar(255) not null
);

create table tasks
(
    id bigserial not null
        constraint tasks_pkey
            primary key,
    created_at bigint not null,
    done boolean not null,
    done_at bigint,
    text varchar(4096) not null,
    receiver_id integer
        constraint tasks_users_receiver_fk
            references users,
    sender_id integer not null
        constraint tasks_users_sender_fk
            references users
);

create index tasks_receiver_id_index on tasks (receiver_id);

create index tasks_sender_id_index on tasks (sender_id);
