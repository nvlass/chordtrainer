(ns chordtrainer.i18n-test
  (:require [cljs.test :refer [deftest is]]
            [chordtrainer.i18n :as i18n]
            [chordtrainer.i18n.en :as en]
            [chordtrainer.i18n.el :as el]
            [chordtrainer.theory :as t]))

(defn- flat-keys [m prefix]
  (mapcat (fn [[k v]]
            (let [k (if prefix (keyword (str prefix "." (name k))) k)]
              (if (map? v) (flat-keys v (name k)) [k])))
          m))

(deftest same-keys
  (is (= (set (flat-keys en/dict nil)) (set (flat-keys el/dict nil)))))

(deftest every-chord-group-and-preset-has-a-name
  (doseq [lang [:en :el]
          k (concat (for [c t/chords] (keyword "chord" (name (:id c))))
                    (for [g t/groups] (keyword "group" (name g)))
                    (for [[p] t/presets] (keyword "preset" (name p))))]
    (is (string? (i18n/t {:lang lang} k)) (str lang k))
    (is (not (re-find #"Missing key" (i18n/t {:lang lang} k))) (str lang k))))
