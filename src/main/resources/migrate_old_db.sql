alter table users
    drop column username;

alter table users
    drop column language_code;

update users
set language = 'ru'
where language = 'RUSSIAN';

update users
set language = 'en'
where language = 'ENGLISH';

update users
set language = 'it'
where language = 'ITALIAN';

update users
set language = 'tr'
where language = 'TURKISH';

update tasks
set receiver_id = sender_id
where receiver_id = 409157375;
