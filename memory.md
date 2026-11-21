# Project memory

Decisions and setup notes that aren't obvious from the code. Last updated 2026-11-21.

## Product decisions

### Recurring budgets = recurring transactions
- "Recurring budgets" means scheduled bills, subscriptions and income (e.g. Netflix ₹499 on the 15th every month), **not** spending limits. A spending-limit version was built first and replaced.
- They live on their own screen: **Profile → Recurring budgets**.
- An occurrence is added to Transactions only once its date arrives (on the due date itself). Past start dates add the missed occurrences immediately.
- Repeats: daily, weekly, monthly, quarterly, half-yearly, yearly. Fields: expense/income, title, amount, category, note, first date. No end date yet.
- Occurrence transactions use deterministic ids `rec_<recurringId>_<yyyy-mm-dd>`, so they are never added twice across devices.

### Accounts
- Opening screen: **Sign in**, **Create account**, **Continue as guest**.
- Sign-out always returns to that opening screen, never to guest Home.
- New passwords: 8+ characters with an uppercase letter, a lowercase letter and a number (live checklist); confirm password must match.
- Username-only accounts: a username maps to a hidden `<username>@users.example.com` email.

## Supabase
- Project ref `heemjnvmfkvwjyphuknf`; MCP server configured in `.mcp.json`.
- Auth **"Confirm email" must stay OFF**. The hidden addresses can't receive mail, so sign-up fails with `email_address_invalid` otherwise.
- The app reads `SUPABASE_URL` and `SUPABASE_ANON_KEY` from `local.properties` (git-ignored). Without them a build runs fully offline and never syncs.
- Synced tables: `categories`, `transactions`, `recurring_transactions`, plus `profiles`. The old `budgets` table (migration 0003) is unused.
- The MCP `apply_migration` tool silently returns "declined" for SQL it considers destructive (`drop ... if exists`, `revoke`). Apply the additive part first, then grants/revokes as a separate small migration. Keep the full re-runnable version in `supabase/migrations/`.

## Dev environment (Windows)
- Gradle and the `android` CLI fail with "Unable to establish loopback connection" (JDK 25). Workaround per command:
  `JAVA_TOOL_OPTIONS=-Djdk.net.unixdomain.tmpdir=E:\androidApps\financetracker\.gradle\udstmp`
- Android SDK: `C:\android\Sdk` (adb is not on PATH; the `android` CLI is in `cmdline-tools\latest\bin`).
- Emulators:
  - `Medium_Phone_API_36.1`: disk is full, don't install there.
  - `FT_Tablet`: holds guest test data, don't wipe.
  - `FT_SignupTest`: throwaway phone for testing.
- Test account credentials: `.claude/test-account.local.md` (git-ignored). Username `ft_signup_9684`.
- If Gboard's stylus tutorial swallows `adb shell input text`, use `adb shell input keyboard text`.
- To read and drive the UI, prefer `android layout --flat` and `android input tap "text=..."` over screenshots.

## Git
- Current feature work is on branch `feature/recurring-budgets`, not yet merged to `main`.
