# AfterTaste feature map

One file per user-facing feature. Each answers: what it is, how to reach it, how to drive it, what proves it works. The harness is adb + uiautomator (see SKILL.md Path A); the JVM fallback (Path B) is noted per feature where it applies.

| Feature | Entry point | Primary proof | Status |
|---|---|---|---|
| [Now playing card](now-playing-card.md) | Top of main screen, always visible | Live session timer ticks; DB row appears after 5s flush | FULL positive path verified with real signed-in playback |
| [Daily tab](daily-tab.md) | Tab `content-desc` "Daily" | Today total, goal progress, track feed match DB | Verified incl. rehydration + goal persistence + live ticking |
| [Last seven-day record](weekly-tab.md) | Tab `content-desc` "Last seven-day record" | Scrollable seven-day histogram and drilldown match DB per-day minutes | Verify after the UI rename and cross-midnight fixes |
| [Yearly tab](yearly-tab.md) | Tab `content-desc` "Yearly" | Seed click creates rows; yearly stats + milestones match DB | Verify yearly summary and seed data; no month histogram is shown |
| [Genres tab](genres-tab.md) | Tab `content-desc` "Genres" | Pie slices match DB genre shares per scope | Verified Month/Year/All + drill-down |

Coverage rule: a proof that drives one tab is incomplete while the map lists others. When UI changes, update the matching file and the handles in SKILL.md's Drive table in the same commit.

Coordinate discipline: re-locate tabs from a fresh `uiautomator dump` before every tap. Layout shifts when the permission banner appears/disappears (~200px), and stale coordinates silently tap content instead of tabs (bit once on this setup).
