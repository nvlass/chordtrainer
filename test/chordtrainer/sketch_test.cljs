(ns chordtrainer.sketch-test
  (:require [cljs.test :refer [deftest is testing]]
            [chordtrainer.sketch :as s]
            [chordtrainer.theory :as t]))

(deftest parsing
  (is (= {:token "Ebm9/Gb" :root 3 :chord :m9 :bass 6} (s/parse-chord "Ebm9/Gb")))
  (is (= :maj (:chord (s/parse-chord "C"))))
  (is (= :six9 (:chord (s/parse-chord "C6/9"))))
  (is (= :m7b5 (:chord (s/parse-chord "F#ø"))))
  (is (= :maj7 (:chord (s/parse-chord "BbΔ7"))))
  (is (= 10 (:root (s/parse-chord "B♭7"))))
  (is (:error (s/parse-chord "Hm7")))
  (is (:error (s/parse-chord "Cfoo"))))

(deftest progressions
  (let [bars (s/parse-progression "| Em9 | A13 |\nDm7 G7 | % |")]
    (is (= 4 (count bars)))
    (is (= ["Dm7" "G7"] (map :token (nth bars 2))))
    (is (= (nth bars 2) (nth bars 3)))))

(deftest transposing
  (is (= "F#m9 | B13/Eb | Dmaj7#11" (s/transpose-text "Em9 | A13/C# | Cmaj7#11" 2)))
  (is (= "Dm7 foo" (s/transpose-text "Cm7 foo" 2))))

(deftest realizing
  (let [slots (s/realize {:text "Dm9 | G13 | Cmaj9 | C/D" :beats 4 :voice-lead true} :spread)]
    (is (= [0 4 8 12] (map :start slots)))
    (doseq [{:keys [root chord bass bass-note upper]} slots
            :let [c (t/chord-by-id chord)]]
      (is (<= 36 bass-note 47))
      (is (= (or bass root) (mod bass-note 12)))
      (is (every? #(<= 50 % 86) upper))
      (is (every? (t/pcs c root) (map #(mod % 12) upper))))
    (testing "slash chord on a chord tone uses that inversion, bass in octave 2"
      (let [[{:keys [bass-note upper]}] (s/realize {:text "C/E" :beats 4} :close)]
        (is (= 40 bass-note))
        (is (= #{0 7} (set (map #(mod % 12) upper))))))))

(deftest scheduling
  (let [slots (s/realize {:text "Dm7 G7 | Cmaj7" :beats 4 :voice-lead true} :close)
        {:keys [events length marks]} (s/schedule slots {:tempo 120 :comp :whole :bass true})]
    (is (= [0 1 2] marks))
    (is (= 4 length))
    (is (every? (fn [[m off dur]] (and (pos? m) (>= off 0) (pos? dur))) events))))

(deftest every-event-is-finite
  (doseq [comp [:whole :charleston :four :arp] bass [true false] style [:close :spread]
          :let [text "Em9 | Em9/D | Cmaj7#11 | B7alt\nAm9 | D13 | Gmaj7 | F#m7b5 B7b9 | C/D"
                slots (s/realize {:text text :beats 4 :voice-lead true} style)
                {:keys [events length marks voices]} (s/schedule slots {:tempo 96 :comp comp :bass bass})]]
    (is (js/isFinite length))
    (is (js/isFinite voices))
    (is (every? js/isFinite marks))
    (doseq [[m off dur :as ev] events]
      (is (and (js/isFinite m) (js/isFinite off) (js/isFinite dur) (pos? dur)) [comp bass style ev]))))
