# Chord trainer

Static re-frame/shadow-cljs SPA, no backend. See README for modes and deploy.

## Commands

- `npx shadow-cljs watch app`: dev server on :8280
- `npx shadow-cljs compile test`: runs `test/` on node (theory tests)
- `npm run dist`: release build into `dist/`

## Conventions

- `dist/` is committed on purpose (server deploys with `git pull`). Run `npm run dist` before committing any change to `src/` or `resources/public/`, so `dist/` never goes stale.
- `theory.cljs` and `sketch.cljs` are pure; keep them that way. The test `every-voicing-is-in-range` checks every chord × root × inversion × voicing for MIDI range 36–96, the bass note and the pitch classes. Run it after touching chords or voicings.
- Inversions are slots, not rotation indices: 0 root, 1 third (or sus 2/4), 2 fifth, 3 seventh/6th (`degree-slot`). Simple chords (all tones < octave) invert by rotation; chords with extensions are played as slash chords with the slot tone in the bass. A chord without a tone for a slot (13 has no 5th) doesn't offer that inversion.
- Chord `:degrees` are the voiced tones; `:optional` tones are only for accepting MIDI answers.
- Answers are judged by `same-sound?` (same pitch classes + bass at the given root), so enharmonic duplicates like 7#5 / 7b13 both count as correct.
- Persistence: IndexedDB via idb-keyval, keys `settings`, `attempts`, `sketch` (current) and `sketches` (saved), stored as EDN strings. New setting keys need a default in `events/default-settings`, because loaded settings are merged over it.
- Attempt records are the stats source of truth; keep their shape (`:chord :inv :root :answer :answer-inv :correct :ms :mode :via :t`) backward compatible, since users have history stored.
- `resources/public/sw.js` lists the app-shell files and samples explicitly. Update it when adding static files, and bump `VERSION` when the cached set changes.
- Attempt `:mode` is `:puzzle`, `:training` or `:imagine`. Adaptive weights are computed per direction (hearing vs. imagining), so don't mix them.
- Timers in training carry a `:round` token (the sketchpad uses `[:sketch-play :token]`); any new delayed event must check it, so stale timeouts are ignored after stop/next.

## Roadmap

`TODO.md` holds the feature roadmap. The direction: move from recognizing chords on their own toward arranging by ear (imagining harmony, progressions, reharmonization, voicings). Prefer features that serve that goal.
