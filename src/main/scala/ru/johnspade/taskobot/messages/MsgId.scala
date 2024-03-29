package ru.johnspade.taskobot.messages

enum MsgId:
  case `chat`, `tasks-personal-new`, `help-task-new`, `buy-coffee`, `chats-list`, `tasks`,
    `tasks-collaborative-new`, `help`, `settings`, `help-description`, `support-creator`, `help-forward`,
    `tasks-personal-created`, `chats-tasks`, `tasks-personal`, `pages-previous`, `pages-next`, `languages-current`,
    `languages-switch`, `tasks-create`, `languages-changed`, `tasks-must-be-confirmed`, `tasks-completed`,
    `tasks-completed-by`, `timezone`, `tasks-due-date`, `tasks-created-at`, `cancel`, `remove`, `ok`, `hours`,
    `minutes`, `reminders-at-start`, `reminders-minutes-before`, `reminders-hours-before`,
    `reminders-days-before`, `reminders-minutes-short`, `reminders-hours-short`, `reminders-days-short`,
    `reminders-reminder`, `help-due-date`, `help-task-complete`
