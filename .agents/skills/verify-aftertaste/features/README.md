# AfterTaste feature map

One file per user-facing feature. Each answers: what it is, how to reach it, how to drive it, what proves it works. The harness is adb + uiautomator (see SKILL.md Path A); the JVM fallback (Path B) is noted per feature where it applies.

| Feature | Entry point | Primary proof | Status |
|---|---|---|---|
| [Now playing card](now-playing-card.md) | Top of main screen, always visible | Live session timer ticks; DB row appears after 5s flush | FULL positive path verified with real signed-in playback |
| [Daily tab](daily-tab.md) | Tab `content-desc` "Daily" | Today total, goal progress, track feed match DB | Verified incl. rehydration + goal persistence + live ticking |
| [Weekly tab](weekly-tab.md) | Tab `content-desc` "Weekly" | 7-day histogram sums match DB per-day minutes | Verified incl. today-uses-live-counter rule |
| [Yearly tab](yearly-tab.md) | Tab `content-desc` "Yearly" | Seed click creates rows; stats + milestones + month detail match DB | Verified; double-seed gated + month detail exact; `currentStreakDays` is a dead state field |
| [Genres tab](genres-tab.md) | Tab `content-desc` "Genres" | Pie slices match DB genre shares per scope | Verified Month/Year/All + drill-down |

Coverage rule: a proof that drives one tab is incomplete while the map lists others. When UI changes, update the matching file and the handles in SKILL.md's Drive table in the same commit.

Coordinate discipline: re-locate tabs from a fresh `uiautomator dump` before every tap. Layout shifts when the permission banner appears/disappears (~200px), and stale coordinates silently tap content instead of tabs (bit once on this setup).
