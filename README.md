# Tasker (version 0.1)

A personal assistant app for Android: tuitions, university classes, expenses, loans,
prayer, to-do and reminders, with a floating bubble for quick entry. Everything is
stored on the phone and works without internet.

## How to build and install

1. Install Android Studio (Ladybug 2024.2 or newer).
2. File > Open, and choose this `Tasker` folder.
3. Wait for "Gradle sync" to finish. The first time it downloads the Android tools
   and libraries, so this step needs internet.
4. Connect your phone with USB debugging on (or start an emulator) and press Run.

To get an APK file to share: Build > Build App Bundle(s) / APK(s) > Build APK(s).
It is saved in `app/build/outputs/apk/debug/`.

## Status

This code has NOT been compiled yet. It was written without access to the Android
build tools, so the first build in Android Studio may show a few errors to fix.

## What works in 0.1

- Tuition: weekly days and time, reminders that repeat until answered, mark each day
  done or missed, move a single day, extra days, day counter per payment set,
  notes per day and per set, payments that post to income.
- Classes: courses with weekly class times, reminders, attendance and notes,
  attendance percentage with a warning limit.
- Money: expenses by category and by date, loans given and taken with repayments,
  a repayment suggestion from a share of each income, income history.
- Prayer: five daily prayers with editable times, reminders, daily tracker, last 7 days.
- To-do: main categories and sub-categories, due date and time, repeat, reminders.
- Analytics for the last 7 days, this month or this year.
- Floating bubble over other apps with a quick panel (expense, to-do, next reminders).

## Not in 0.1 yet

- Sign-in, backup and sync.
- Automatic prayer-time calculation (times are typed by hand for now).
- Different class or tuition times on different days of one tuition.
- Semester dates and holidays, budgets, task steps, widgets, Bangla.
- Dates and times are typed as text (2026-11-15, 17:30) instead of pickers.

## Where things are

- `data/` database tables (`Entities.kt`), queries (`Db.kt`), all the rules (`Repo.kt`)
- `core/Reminders.kt` planner, alarms, notifications, boot restore
- `bubble/Bubble.kt` floating bubble service and quick panel
- `ui/` all screens
