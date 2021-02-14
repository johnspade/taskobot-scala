alter table tasks
    add column forward_from_id integer;

alter table tasks
    add column forward_sender_name varchar(255);
