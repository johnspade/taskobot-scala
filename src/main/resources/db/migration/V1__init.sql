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
        constraint fkod4nsd69oox6xvdej27il8gmn
            references users,
    sender_id integer not null
        constraint fkkgw5el7l450v0mwaehks6xp1c
            references users
);
