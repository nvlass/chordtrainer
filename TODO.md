# TODO

- [ ] Import should also restore settings from an export, not only merge attempts.
- [ ] Call `navigator.storage.persist()` so the browser is less likely to evict IndexedDB (Android/desktop).

## Roadmap: toward arranging by ear

Goal: hear harmony in your head and work with it before touching the instrument.

- [x] **Name → sound ("Imagine") mode**: show a chord symbol, imagine it, then hear it and self-grade.
- [x] **Progression sketchpad**: type `Em9 | A13 | Cmaj7#11 | B7alt`, loop it with comping style, tempo and voice leading; save sketches.
- [ ] **Melody note over chord**: hold one melody note and change the chord under it (note as 3, 7, 9, #11, 13…); identify the note's role. The core reharmonization skill.
- [ ] **Functional progressions**: ii–V–I, minor ii–V, tritone subs, backdoor ii–V…; identify as Roman numerals in a key.
- [ ] **Jazz piano voicings**: rootless A/B left-hand forms and drop-2 as voicing options.
- [ ] **Guide-tone lines**: two chords play; identify how the 3rd and 7th move.

## Sketchpad follow-ups

- [ ] Timing uses `setTimeout` for the bar highlight and loop restart; schedule against the AudioContext clock if gaps at the loop point become noticeable.
- [ ] Melody line input (a note per beat) on top of the chords, which would feed into the "melody note over chord" idea.
- [ ] Export or share a sketch as text or a link.
