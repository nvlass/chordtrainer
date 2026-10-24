(ns chordtrainer.sketch
  (:require [clojure.string :as str]
            [chordtrainer.theory :as t]))

(def aliases
  {:maj ["M" "maj"]
   :min ["min" "-"]
   :dim ["°" "o"]
   :aug ["+"]
   :maj7 ["M7" "Δ7" "Δ" "ma7"]
   :m7 ["-7" "min7"]
   :m7b5 ["ø" "ø7" "-7b5" "min7b5"]
   :dim7 ["°7" "o7"]
   :mmaj7 ["mmaj7" "mMaj7" "mM7" "-Δ7" "-maj7" "m(Δ7)"]
   :maj7s5 ["Δ7#5" "+maj7" "maj7+5"]
   :sus4 ["sus"]
   :dom7sus4 ["7sus"]
   :add9 ["(add9)"]
   :madd9 ["madd9" "-add9"]
   :m6 ["-6"]
   :six9 ["69" "6add9"]
   :maj9 ["Δ9" "M9"]
   :m9 ["-9"]
   :m11 ["-11"]
   :maj7s11 ["Δ7#11" "Δ#11" "maj#11"]
   :maj13 ["Δ13" "M13"]
   :m13 ["-13"]
   :dom7b9 ["7(b9)"]
   :dom7s9 ["7(#9)"]
   :dom7s11 ["7(#11)"]
   :dom7b13 ["7(b13)"]
   :dom7alt ["alt"]
   :dom7s5 ["+7" "7+" "aug7"]
   :dom7b5 ["7(b5)"]})

(def quality-lookup
  (into {} (for [c t/chords a (cons (:sym c) (aliases (:id c)))] [a (:id c)])))

(def letter-pcs {"C" 0 "D" 2 "E" 4 "F" 5 "G" 7 "A" 9 "B" 11})

(defn parse-note [s]
  (when-let [[_ l acc] (re-matches #"^([A-G])([#b]?)$" s)]
    (mod (+ (letter-pcs l) (case acc "#" 1 "b" -1 0)) 12)))

(defn- normalize [text]
  (-> text (str/replace "♭" "b") (str/replace "♯" "#")))

(def chord-re #"^([A-G][#b]?)(.*?)(?:/([A-G][#b]?))?$")

(defn parse-chord [tok]
  (let [[_ root q bass] (re-matches chord-re (normalize tok))
        id (when root (quality-lookup q))]
    (if id
      {:token tok :root (parse-note root) :chord id :bass (some-> bass parse-note)}
      {:token tok :error true})))

(defn parse-progression
  "Bars split on | or newline, chords in a bar on whitespace; a bar of just % repeats the previous one."
  [text]
  (->> (str/split (normalize text) #"[|\n]")
       (map str/trim)
       (remove str/blank?)
       (reduce (fn [acc bar]
                 (if (= bar "%")
                   (if-let [p (peek acc)] (conj acc p) acc)
                   (conj acc (mapv parse-chord (str/split bar #"\s+")))))
               [])))

(defn transpose-text [text n]
  (str/replace (normalize text) #"[^\s|]+"
               (fn [tok]
                 (let [[_ root q bass] (re-matches chord-re tok)]
                   (if (and root (quality-lookup q))
                     (let [shift #(t/note-names (mod (+ (parse-note %) n) 12))]
                       (str (shift root) q (when bass (str "/" (shift bass)))))
                     tok)))))

;; ---------------------------------------------------------------- voicing

(defn- bass+upper
  "Splits a chord into a bass note (MIDI 36–47) and upper-structure notes."
  [{:keys [root chord bass]} style]
  (let [c (t/chord-by-id chord)
        inv (if bass (first (filter #(= bass (t/bass-pc c root %)) (t/inversions c))) 0)
        v (t/voicing c root (or inv 0) style)]
    (if inv
      {:bass (+ 36 (mod (first v) 12)) :upper (vec (rest v))}
      {:bass (+ 36 bass) :upper v})))

(defn- candidates [upper]
  (for [k (range (count upper))
        shift [-24 -12 0 12 24]
        :let [v (vec (sort (map #(+ shift %)
                                (concat (drop k upper) (map #(+ 12 %) (take k upper))))))]
        :when (and (>= (first v) 50) (<= (peek v) 86))]
    v))

(defn- motion [prev v]
  (let [near (fn [xs x] (apply min (map #(Math/abs (- x %)) xs)))]
    (+ (reduce + (map #(near prev %) v))
       (reduce + (map #(near v %) prev)))))

(defn- center-cost [v] (* (count v) (Math/abs (- (/ (reduce + v) (count v)) 64))))

(defn- place-upper [prev upper voice-lead?]
  (let [cs (candidates upper)]
    (cond
      (empty? cs) upper
      (and voice-lead? (seq prev)) (apply min-key #(+ (motion prev %) (* 0.1 (center-cost %))) cs)
      :else (apply min-key center-cost cs))))

(defn realize
  "Flat vector of slots: one per chord, with bar/beat position and its voicing."
  [{:keys [text beats voice-lead]} style]
  (let [bars (parse-progression text)]
    (:slots
     (reduce
      (fn [{:keys [prev slots]} [bar-i j n ch]]
        (let [len (/ beats n)
              base {:bar bar-i :j j :token (:token ch) :start (+ (* bar-i beats) (* j len)) :len len}]
          (if (:error ch)
            {:prev prev :slots (conj slots (assoc base :error true))}
            (let [{:keys [bass upper]} (bass+upper ch style)
                  upper (place-upper prev upper voice-lead)]
              {:prev upper
               :slots (conj slots (merge base (select-keys ch [:root :chord :bass])
                                         {:bass-note bass :upper upper}))}))))
      {:prev nil :slots []}
      (for [[bar-i bar] (map-indexed vector bars)
            [j ch] (map-indexed vector bar)]
        [bar-i j (count bar) ch])))))

;; ---------------------------------------------------------------- rhythm

(def swing (/ 2 3))

(defn- comp-hits
  "[offset duration] in beats for one chord of `len` beats."
  [style len]
  (case style
    :charleston (if (>= len 2) [[0 0.9] [(+ 1 swing) 0.7]] [[0 (* 0.9 len)]])
    :four (for [b (range (Math/ceil len))] [b 0.55])
    [[0 len]]))

(defn schedule
  "Audio events [midi seconds duration] for the realized slots, plus per-slot start times."
  [slots {:keys [tempo bass] comp-style :comp}]
  (let [spb (/ 60 tempo)
        total (* spb (+ (apply max 0 (map #(+ (:start %) (:len %)) slots))))
        events (for [{:keys [start len upper bass-note error]} slots
                     :when (not error)
                     ev (concat
                         (if (= comp-style :arp)
                           (let [step (/ len (max 1 (count upper)) 2)]
                             (map-indexed (fn [i m] [m (+ start (* i step)) (- len (* i step))]) upper))
                           (for [[off dur] (comp-hits comp-style len) m (if bass upper (cons bass-note upper))]
                             [m (+ start off) dur]))
                         (when bass
                           (if (>= len 4)
                             [[bass-note start 1.9] [(+ bass-note 7) (+ start 2) 1.9]]
                             [[bass-note start (* 0.95 len)]])))]
                 (let [[m off dur] ev] [m (* spb off) (* spb dur)]))]
    {:events (vec events)
     :length total
     :marks (mapv #(* spb (:start %)) slots)
     :voices (inc (apply max 1 (map (comp count :upper) slots)))}))
