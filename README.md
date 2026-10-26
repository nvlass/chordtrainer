# Chord trainer

Ear-training for chord qualities beyond plain triads: 7ths, sus/add, extensions and altered dominants, with inversions. Static re-frame SPA, works offline (PWA), stats kept in the browser (IndexedDB).

## Modes

- **Train**: a chord plays, you get a thinking pause, then it is revealed (name, formula, keyboard) and replayed. Optionally self-grade with ✓ / ✗ to feed the stats.
- **Puzzle**: a chord plays and the app waits for your answer: quality, plus inversion when more than one is enabled. Answer by tapping, or by playing it on a MIDI keyboard. After answering you can play your answer and A/B compare it with the real one.

- **Imagine**: the reverse direction. You see a chord symbol (and optionally hear its root), imagine the sound, then play it and grade whether it matched. Tracked separately from hearing in stats and adaptive selection.
- **Sketch**: a progression sketchpad for arranging away from the instrument. Type `Em9 | A13 | Cmaj7#11 | B7alt` (bars with `|` or new lines, several chords per bar with spaces, `%` repeats a bar) and loop it. Options: comping (whole notes, Charleston, four-to-the-bar, arpeggio), tempo, 3 or 4 beats per bar, a 2-feel bass, and voice leading, which moves each chord to the nearest position. Tap a chord to hear it alone and see its voicing. Transpose by semitones and save sketches by name.

Settings cover the chord set (with presets), root mode (fixed / random naturals / random all 12), inversions, close or spread voicing, block / arpeggio / arp-then-block playback, optional root reference, and adaptive selection, which drills your weakest chords more often (based on smoothed recent error rate per chord and per chord + inversion).

Keyboard: `Enter` start/next/reveal, `Space` replay (play/stop in Sketch), `R` hear root, `C` compare, `Y`/`N` self-grade.

### Inversions on bigger chords

Triads, 7ths, 6ths and sus chords invert by rotation. Chords with 9ths and up are played as slash chords with the 3rd / 5th / 7th in the bass. A chord is skipped for an inversion it lacks (e.g. `13` omits the 5th, `11` omits the 3rd).

## Develop

```sh
npm install
npx shadow-cljs watch app     # http://localhost:8280
npx shadow-cljs compile test  # theory tests (node)
```

The service worker is only registered in release builds.

## Build & deploy

```sh
npm run dist        # release build into dist/
rsync -a --delete dist/ server:/var/www/chordtrainer/
```

`dist/` is plain static files. Example nginx config:

```nginx
server {
    server_name chords.example.com;
    root /var/www/chordtrainer;

    location = /sw.js        { add_header Cache-Control "no-cache"; }
    location = /index.html   { add_header Cache-Control "no-cache"; }
    location /js/            { add_header Cache-Control "no-cache"; }
    location /samples/       { add_header Cache-Control "public, max-age=31536000, immutable"; }
}
```

The service worker caches the app and samples on first visit (about 1 MB total), so it runs offline afterwards. It is served network-first, so new deploys show up on the next online load. HTTPS is required for the service worker and Web MIDI (localhost is exempt).

## Notes

- **iOS**: Web MIDI isn't supported in Safari. Audio uses the `playback` audio session where available, so it should play with the silent switch on.
- **Stats** live per browser. Use Stats → Export / Import to merge them between devices.
- **Piano samples**: FluidR3_GM acoustic grand (via [gleitz/midi-js-soundfonts](https://github.com/gleitz/midi-js-soundfonts), CC BY 3.0), one sample every minor third, pitch-shifted in between.

## License

MIT, see [LICENSE](LICENSE). The piano samples are CC BY 3.0 (see above).
