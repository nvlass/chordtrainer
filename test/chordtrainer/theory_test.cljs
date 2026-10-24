(ns chordtrainer.theory-test
  (:require [cljs.test :refer [deftest is testing]]
            [chordtrainer.theory :as t]))

(def c t/chord-by-id)

(deftest close-voicings
  (is (= [60 64 67 70] (t/voicing (c :dom7) 0 0 :close)))
  (testing "simple chords invert by rotation"
    (is (= [64 67 70 72] (t/voicing (c :dom7) 0 1 :close)))
    (is (= [58 60 64 67] (t/voicing (c :dom7) 0 3 :close))))
  (testing "extended chords put the slot tone in the bass"
    (is (= [52 60 67 70 74] (t/voicing (c :dom9) 0 1 :close)))))

(deftest inversions
  (is (= [0 1 2] (t/inversions (c :maj))))
  (is (= [0 1 3] (t/inversions (c :dom13))))
  (is (= [0 2 3] (t/inversions (c :dom11)))))

(deftest every-voicing-is-in-range
  (doseq [ch t/chords root (range 12) inv (t/inversions ch) style [:close :spread]
          :let [v (t/voicing ch root inv style)]]
    (is (<= 36 (first v)) [(:id ch) root inv style])
    (is (<= (last v) 96) [(:id ch) root inv style])
    (is (= (t/bass-pc ch root inv) (mod (first v) 12)) [(:id ch) root inv style])
    (is (= (t/pcs ch root) (set (map #(mod % 12) v))) [(:id ch) root inv style])))

(deftest naming
  (is (= "C7/E" (t/chord-name (c :dom7) 0 1)))
  (is (= "Ebm7b5" (t/chord-name (c :m7b5) 3 0))))

(deftest identify
  (is (= [(c :dom7) 1] (t/identify 0 t/chords [64 67 70 72])))
  (testing "optional tones are accepted"
    (is (= [(c :dom13) 0] (t/identify 0 t/chords [48 52 55 58 62 69]))))
  (testing "doublings and spread voicings"
    (is (= [(c :maj7) 0] (t/identify 0 t/chords [36 48 55 59 64]))))
  (is (nil? (t/identify 0 t/chords [60 61 62]))))

(deftest equivalence
  (is (t/same-sound? 0 [(c :dom7s5) 0] [(c :dom7b13) 0]))
  (is (not (t/same-sound? 0 [(c :dom7) 0] [(c :dom7) 1]))))
