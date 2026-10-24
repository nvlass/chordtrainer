(ns chordtrainer.theory
  (:require [clojure.string :as str]))

(def note-names ["C" "Db" "D" "Eb" "E" "F" "F#" "G" "Ab" "A" "Bb" "B"])
(def natural-pcs [0 2 4 5 7 9 11])

(def degree-semis
  {"1" 0 "2" 2 "b3" 3 "3" 4 "4" 5 "b5" 6 "5" 7 "#5" 8 "6" 9 "bb7" 9 "b7" 10 "7" 11
   "b9" 13 "9" 14 "#9" 15 "11" 17 "#11" 18 "b13" 20 "13" 21})

;; Inversion slots: 0 root, 1 third (or sus 2/4), 2 fifth, 3 seventh (or 6th).
;; Extensions have no slot and can never be the bass note.
(defn degree-slot [d]
  (case d
    "1" 0
    ("2" "b3" "3" "4") 1
    ("b5" "5" "#5") 2
    ("6" "bb7" "b7" "7") 3
    nil))

(def groups
  [[:triads "Triads"]
   [:sevenths "7th chords"]
   [:sus-add "Sus & add"]
   [:extensions "Extensions"]
   [:altered "Altered dominants"]])

;; :degrees are the voiced tones; :optional tones may also be played on MIDI
;; (the 5th that is left out of a 13 chord, etc.).
(defn- chord [id sym nm group degrees & [optional]]
  {:id id :sym sym :name nm :group group :degrees degrees :optional (vec optional)})

(def chords
  [(chord :maj "" "major" :triads ["1" "3" "5"])
   (chord :min "m" "minor" :triads ["1" "b3" "5"])
   (chord :dim "dim" "diminished" :triads ["1" "b3" "b5"])
   (chord :aug "aug" "augmented" :triads ["1" "3" "#5"])

   (chord :maj7 "maj7" "major 7th" :sevenths ["1" "3" "5" "7"])
   (chord :dom7 "7" "dominant 7th" :sevenths ["1" "3" "5" "b7"])
   (chord :m7 "m7" "minor 7th" :sevenths ["1" "b3" "5" "b7"])
   (chord :m7b5 "m7b5" "half-diminished" :sevenths ["1" "b3" "b5" "b7"])
   (chord :dim7 "dim7" "diminished 7th" :sevenths ["1" "b3" "b5" "bb7"])
   (chord :mmaj7 "m(maj7)" "minor-major 7th" :sevenths ["1" "b3" "5" "7"])
   (chord :maj7s5 "maj7#5" "augmented major 7th" :sevenths ["1" "3" "#5" "7"])

   (chord :sus2 "sus2" "suspended 2nd" :sus-add ["1" "2" "5"])
   (chord :sus4 "sus4" "suspended 4th" :sus-add ["1" "4" "5"])
   (chord :dom7sus4 "7sus4" "dominant 7th sus4" :sus-add ["1" "4" "5" "b7"])
   (chord :add9 "add9" "added 9th" :sus-add ["1" "3" "5" "9"])
   (chord :madd9 "m(add9)" "minor added 9th" :sus-add ["1" "b3" "5" "9"])
   (chord :maj6 "6" "major 6th" :sus-add ["1" "3" "5" "6"])
   (chord :m6 "m6" "minor 6th" :sus-add ["1" "b3" "5" "6"])
   (chord :six9 "6/9" "six-nine" :sus-add ["1" "3" "5" "6" "9"])

   (chord :dom9 "9" "dominant 9th" :extensions ["1" "3" "5" "b7" "9"])
   (chord :maj9 "maj9" "major 9th" :extensions ["1" "3" "5" "7" "9"])
   (chord :m9 "m9" "minor 9th" :extensions ["1" "b3" "5" "b7" "9"])
   (chord :dom11 "11" "dominant 11th" :extensions ["1" "5" "b7" "9" "11"])
   (chord :m11 "m11" "minor 11th" :extensions ["1" "b3" "b7" "9" "11"] ["5"])
   (chord :maj7s11 "maj7#11" "lydian major 7th" :extensions ["1" "3" "5" "7" "#11"] ["9"])
   (chord :dom13 "13" "dominant 13th" :extensions ["1" "3" "b7" "9" "13"] ["5"])
   (chord :maj13 "maj13" "major 13th" :extensions ["1" "3" "7" "9" "13"] ["5"])
   (chord :m13 "m13" "minor 13th" :extensions ["1" "b3" "b7" "9" "13"] ["5" "11"])

   (chord :dom7b9 "7b9" "dominant 7th flat 9" :altered ["1" "3" "5" "b7" "b9"])
   (chord :dom7s9 "7#9" "dominant 7th sharp 9" :altered ["1" "3" "b7" "#9"] ["5"])
   (chord :dom7s11 "7#11" "dominant 7th sharp 11" :altered ["1" "3" "b7" "#11"] ["5" "9"])
   (chord :dom7b13 "7b13" "dominant 7th flat 13" :altered ["1" "3" "b7" "b13"] ["9"])
   (chord :dom7alt "7alt" "altered dominant" :altered ["1" "3" "b7" "#9" "b13"] ["b9"])
   (chord :dom7s5 "7#5" "augmented 7th" :altered ["1" "3" "#5" "b7"])
   (chord :dom7b5 "7b5" "dominant 7th flat 5" :altered ["1" "3" "b5" "b7"])])

(def chord-by-id (into {} (map (juxt :id identity)) chords))

(def presets
  [["Everything" (map :id chords)]
   ["All 7ths" [:maj7 :dom7 :m7 :m7b5 :dim7 :mmaj7 :maj7s5]]
   ["maj7 / 7 / m7" [:maj7 :dom7 :m7]]
   ["Dominant family" [:dom7 :dom7sus4 :dom9 :dom11 :dom13 :dom7b9 :dom7s9 :dom7s11 :dom7b13 :dom7alt :dom7s5 :dom7b5]]
   ["Minor colors" [:min :m7 :m6 :mmaj7 :madd9 :m9 :m11 :m13 :m7b5]]
   ["Major colors" [:maj :maj7 :maj6 :add9 :six9 :maj9 :maj7s11 :maj13]]
   ["Sus & add" [:sus2 :sus4 :dom7sus4 :add9 :madd9 :maj6 :m6 :six9]]])

(def inversion-names ["root position" "1st inversion" "2nd inversion" "3rd inversion"])
(def inversion-short ["Root" "1st" "2nd" "3rd"])

(defn semis [chord] (mapv degree-semis (:degrees chord)))

(defn simple? [chord] (every? #(< % 12) (semis chord)))

(defn inversions [chord]
  (vec (sort (keep degree-slot (:degrees chord)))))

(defn bass-degree [chord inv]
  (first (filter #(= inv (degree-slot %)) (:degrees chord))))

(defn- offsets-close [chord inv]
  (let [s (semis chord)
        k (.indexOf (:degrees chord) (bass-degree chord inv))]
    (cond
      (zero? inv) s
      (simple? chord) (vec (concat (drop k s) (map #(+ 12 %) (take k s))))
      :else (let [b (mod (nth s k) 12)]
              (into [(- b 12)] (keep-indexed (fn [i x] (when (not= i k) x)) s))))))

(defn- offsets-spread [chord inv]
  (let [degs (:degrees chord)
        bdeg (bass-degree chord inv)
        upper (->> (cond-> (vec (remove #{bdeg} degs)) (< (count degs) 4) (conj "1"))
                   distinct
                   (map degree-semis)
                   sort)
        ;; stack in chord order, at least a minor 3rd apart, to avoid muddy clusters
        stack (reduce (fn [acc semi]
                        (let [floor (if (seq acc) (+ (peek acc) 3) 4)]
                          (conj acc (+ floor (mod (- semi floor) 12)))))
                      []
                      upper)]
    (into [(- (mod (degree-semis bdeg) 12) 12)] stack)))

(defn voicing
  "Ascending MIDI notes for the chord at root pitch class `root`."
  [chord root inv style]
  (let [offs (if (= style :spread) (offsets-spread chord inv) (offsets-close chord inv))
        lo (apply min offs)
        hi (apply max offs)
        center (/ (+ lo hi) 2)
        target (if (= style :spread) 54 62)
        fits (filter #(and (>= (+ % lo) 36) (<= (+ % hi) 96))
                     (map #(+ root (* 12 %)) (range 2 7)))
        r (apply min-key #(Math/abs (- (+ % center) target)) fits)]
    (vec (sort (map #(+ r %) offs)))))

(defn root-midi [root] (+ 48 root))

(defn pcs [chord root & [with-optional?]]
  (set (map #(mod (+ root (degree-semis %)) 12)
            (cond-> (:degrees chord) with-optional? (concat (:optional chord))))))

(defn bass-pc [chord root inv]
  (when-let [d (bass-degree chord inv)]
    (mod (+ root (degree-semis d)) 12)))

(defn chord-name [chord root inv]
  (str (note-names root) (:sym chord)
       (when (pos? inv) (str "/" (note-names (bass-pc chord root inv))))))

(defn inversion-label [chord inv]
  (let [d (bass-degree chord inv)]
    (if (zero? inv)
      "root position"
      (str (if (simple? chord) (inversion-names inv) "slash chord") " · " d " in bass"))))

(defn formula [chord]
  (let [opt (:optional chord)]
    (str (str/join " " (:degrees chord))
         (when (seq opt) (str "  (optional " (str/join " " opt) ")")))))

(defn same-sound?
  "True when two [chord inv] answers at the same root give the same pitch classes and bass."
  [root [c1 i1] [c2 i2]]
  (and (= (pcs c1 root) (pcs c2 root))
       (= (bass-pc c1 root i1) (bass-pc c2 root i2))))

(defn identify
  "Finds the first [chord inv] among `candidates` matching the played MIDI notes at `root`."
  [root candidates notes]
  (when (seq notes)
    (let [played (set (map #(mod % 12) notes))
          bass (mod (apply min notes) 12)]
      (first
       (for [c candidates
             :let [req (pcs c root) allowed (pcs c root true)]
             :when (and (every? played req) (every? allowed played))
             inv (inversions c)
             :when (= bass (bass-pc c root inv))]
         [c inv])))))

(defn note-name [midi]
  (str (note-names (mod midi 12)) (dec (quot midi 12))))
