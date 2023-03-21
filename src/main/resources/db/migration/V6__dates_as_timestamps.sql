alter table tasks
    alter column created_at type timestamp
    using to_timestamp(created_at / 1000);

alter table tasks
    alter column done_at type timestamp
    using case
        when done_at is not null then to_timestamp(done_at / 1000)
        else null
    end;
