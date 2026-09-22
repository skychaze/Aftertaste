# AfterTaste feature map

One file per user-facing feature. Each answers: what it is, how to reach it, how to drive it, what proves it works. The harness is adb + uiautomator (see SKILL.md Path A); the JVM fallback (Path B) is noted per feature where it applies.

| Feature | Entry point | Primary proof | Status |
|---|---|---|---|
| [Now playing card](now-playing-card.md) | Top of main screen, always visible | Live session timer ticks; DB row appears after 5s flush | Positive path verified with real signed-in playback; empty state, App Info, and YT Music launch rechecked |
| [Today tab](daily-tab.md) | Bottom navigation `content-desc` "Today" | Today total, goal progress, grouped track feed match DB | Goal selection and empty/live surface rechecked; feed count is grouped tracks, not session count |
| [History tab](weekly-tab.md) | Bottom navigation `content-desc` "History" | 7, 30, and 90 explicit calendar dates and day details match DB | Date-scoped list and selected-day detail rechecked |
| [Insights tab](yearly-tab.md) | Bottom navigation `content-desc` "Insights" | Yearly stats + milestones match DB; seeding only for an empty year | Summary and milestones rechecked; selected-year seed guard documented |
| [Genres tab](genres-tab.md) | Tab `content-desc` "Genres" | Donut slices and active-genre rows match DB shares per scope | Month/Year/All, automatic track panel, and Spotify dialog rechecked |

Coverage rule: a proof that drives one tab is incomplete while the map lists others. When UI changes, update the matching file and the handles in SKILL.md's Drive table in the same commit.

Coordinate discipline: re-locate tabs from a fresh `uiautomator dump` before every tap. Layout shifts when the permission banner appears/disappears (~200px), and stale coordinates silently tap content instead of tabs (bit once on this setup).
