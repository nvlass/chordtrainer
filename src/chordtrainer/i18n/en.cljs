(ns chordtrainer.i18n.en)

(def dict
  {:nav
   {:training "Train" :puzzle "Puzzle" :imagine "Imagine" :sketch "Sketch" :stats "Stats" :settings "⚙"}

   :chord
   {:maj "major" :min "minor" :dim "diminished" :aug "augmented"
    :maj7 "major 7th" :dom7 "dominant 7th" :m7 "minor 7th" :m7b5 "half-diminished"
    :dim7 "diminished 7th" :mmaj7 "minor-major 7th" :maj7s5 "augmented major 7th"
    :sus2 "suspended 2nd" :sus4 "suspended 4th" :dom7sus4 "dominant 7th sus4" :add9 "added 9th"
    :madd9 "minor added 9th" :maj6 "major 6th" :m6 "minor 6th" :six9 "six-nine"
    :dom9 "dominant 9th" :maj9 "major 9th" :m9 "minor 9th" :dom11 "dominant 11th" :m11 "minor 11th"
    :maj7s11 "lydian major 7th" :dom13 "dominant 13th" :maj13 "major 13th" :m13 "minor 13th"
    :dom7b9 "dominant 7th flat 9" :dom7s9 "dominant 7th sharp 9" :dom7s11 "dominant 7th sharp 11"
    :dom7b13 "dominant 7th flat 13" :dom7alt "altered dominant" :dom7s5 "augmented 7th"
    :dom7b5 "dominant 7th flat 5"}

   :group
   {:triads "Triads" :sevenths "7th chords" :sus-add "Sus & add" :extensions "Extensions"
    :altered "Altered dominants"}

   :preset
   {:everything "Everything" :sevenths "All 7ths" :basic "maj7 / 7 / m7" :dominant "Dominant family"
    :minor "Minor colors" :major "Major colors" :sus-add "Sus & add"}

   :inv
   {:short (fn [_ i] (["Root" "1st" "2nd" "3rd"] i))
    :long (fn [_ i] (["root position" "1st inversion" "2nd inversion" "3rd inversion"] i))
    :slash "slash chord"
    :in-bass (fn [_ d] (str d " in bass"))
    :optional "optional"}

   :msg
   {:no-chords "No chords match your settings — enable some chords or inversions."
    :nothing-to-play "Nothing to play — type some chords like  Dm9 | G13 | Cmaj9"
    :saved (fn [_ nm] (str "Saved “" nm "”."))
    :deleted (fn [_ nm] (str "Deleted “" nm "”."))
    :no-match (fn [_ notes root] (str "Couldn't match " notes " to a chord on " root " — try again."))
    :imported (fn [_ n] (str "Imported — " n " attempts total."))
    :bad-import "That file doesn't look like a chordtrainer export."}

   :midi
   {:ready (fn [_ n held] (str "MIDI · " n (when held " ●")))
    :no-device "MIDI · no device"
    :unsupported "MIDI unsupported"
    :denied "MIDI denied"
    :connecting "MIDI…"
    :connected (fn [_ inputs] (str "Connected: " inputs))
    :none-long "No MIDI devices found — plug one in."
    :unsupported-long "This browser doesn't support Web MIDI."
    :denied-long "MIDI access was denied."
    :connecting-long "Connecting…"}

   :ui
   {:title "Chord trainer"
    :notes (fn [_ ns] (str "Notes: " ns))
    :loading-samples "Loading piano samples…"
    :samples-failed "Couldn't load piano samples."
    :start "Start"
    :next "Next →"
    :stop "■ Stop"
    :root "Root "
    :hear-root "Hear root"
    :replay "▶ Replay"
    :untitled "Untitled"
    :example "Example"

    :puzzle-title "Puzzle"
    :puzzle-intro (fn [_ inv?] (str "A chord plays — name its quality" (when inv? " and inversion") "."))
    :puzzle-keys "Keys: Enter next · Space replay · R root · C compare"
    :pick-both "Pick an inversion and a chord, in either order."
    :correct "✓ Correct"
    :not-quite "✗ Not quite"
    :you-said (fn [_ x] [:span " — you said " [:b x]])
    :via-midi " · via MIDI"
    :same-notes (fn [_ nm heard root]
                  [:<> "Same notes as " [:b nm] " — you heard " [:b heard] " as the root. "
                   "The root here is " [:b root] "."])
    :play-it "▶ Play it"
    :play-yours "Play yours"
    :compare "Compare (C)"

    :training-title "Training"
    :training-intro (fn [_ p] (str "Chords play one after another. Name each one in your head before it's revealed ("
                                   p "s pause)."))
    :training-keys "Keys: Enter reveal / next · Space replay · R root"
    :listen "Listen. What is it?"
    :reveal "Reveal now"
    :heard-it "✓ Heard it"
    :missed-it "✗ Missed it"
    :resume "Resume"
    :auto-advancing "auto-advancing"

    :imagine-title "Imagine"
    :imagine-intro "The reverse direction: you see a chord symbol, imagine how it sounds, then check."
    :imagine-keys "Keys: Enter hear / next · Space again · R root · Y / N grade"
    :imagine-prompt "Hear it in your head — bass first, then the top note, then the colour in between. Sing or hum it if you can."
    :hear-it "Hear it (Enter)"
    :again "▶ Again"
    :matched "✓ Matched (Y)"
    :different "✗ Different (N)"

    :sketch-name "Sketch name"
    :save "Save"
    :new "New"
    :delete "Delete"
    :unknown-symbol "Unknown chord symbol"
    :sketch-hint "Bars with | or new lines, several chords per bar with spaces, % repeats a bar. Symbols like Cmaj7 CΔ7 Dm9 D-9 F#ø Bb13/D G7alt C6/9."
    :unknown "Unknown: "
    :play "▶ Play"
    :playback "Playback"
    :comping "Comping"
    :comp-whole "Whole" :comp-charleston "Charleston" :comp-four "Four" :comp-arp "Arpeggio"
    :tempo "Tempo"
    :beats "Beats per bar"
    :bass2 "Bass in 2 (root, then 5th)"
    :voice-lead "Voice leading"
    :voice-lead-hint "move each chord to the nearest position instead of jumping"
    :loop "Loop"
    :voicing-from "Close / spread voicing comes from Settings → Sound."

    :filter-all "All" :filter-recognition "Hearing" :filter-imagine "Imagining"
    :answers "Answers" :accuracy "Accuracy" :avg-time "Avg time"
    :no-stats "No stats yet. Answer some puzzles (or self-grade in training / imagine) and your weak spots will show up here. With “adaptive” on, the trainer plays your weak chords more often."
    :th-accuracy "accuracy" :th-last20 "last 20" :th-avg "avg"
    :last-14 "Last 14 days"
    :day-title (fn [_ label n acc] (str label ": " n " answers" (when acc (str ", " acc " correct"))))
    :daily-hint "Bar height = accuracy, opacity = volume. Hover for details."
    :by-chord "Chords — weakest first"
    :by-pair "Weakest chord + inversion"
    :confusions "Confusions"
    :heard-as " heard as "
    :by-inv "Inversions"
    :by-root "Roots"
    :data "Data"
    :data-hint "Stats live in this browser. Export from one device and import on another to merge them."
    :export "Export"
    :import "Import"
    :reset-stats "Reset stats…"
    :really-erase "Really erase all stats"
    :cancel "Cancel"

    :chords "Chords "
    :enabled (fn [_ n] (str n " enabled"))
    :none "none"
    :all "all"
    :roots "Roots"
    :root-fixed "Fixed" :root-naturals "Random, no accidentals" :root-all "Random, all 12"
    :inversions "Inversions"
    :inv-hint "1st = 3rd (or sus tone) in bass, 2nd = 5th, 3rd = 7th/6th. Chords with 9ths and up are played as slash chords with that tone in the bass; a chord is skipped for an inversion it lacks (e.g. 13 has no 5th)."
    :sound "Sound"
    :voicing "Voicing" :close "Close" :spread "Spread (bass + RH)"
    :playback-style "Playback" :block "Block" :arp "Arpeggio" :arp-block "Arp → block"
    :arp-speed "Arpeggio speed"
    :root-ref "Play root before each chord"
    :root-ref-hint "a reference so you can hear the chord against its root"
    :training "Training"
    :pause "Thinking pause"
    :hold "Show answer for"
    :replay-on-reveal "Replay chord on reveal"
    :auto-advance "Auto-advance"
    :selection "Selection"
    :adaptive "Adaptive — drill weak chords more"
    :adaptive-hint "weights chords by your recent error rate (hearing and imagining are tracked separately)"
    :imagine-root "Imagine: play the root when showing a chord"
    :imagine-root-hint "an anchor pitch to imagine the chord from"
    :midi-title "MIDI keyboard"
    :use-midi "Use MIDI input"
    :use-midi-hint "answer puzzles by playing the chord (Chrome/Edge/Firefox; not iOS Safari)"
    :midi-echo "Sound notes I play"
    :midi-echo-hint "for silent controllers"}})
