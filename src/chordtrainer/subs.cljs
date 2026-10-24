(ns chordtrainer.subs
  (:require [re-frame.core :as rf]
            [chordtrainer.events :as ev]
            [chordtrainer.theory :as t]
            [chordtrainer.stats :as stats]))

(doseq [k [:view :settings :attempts :phase :current :answer :pending :running :graded
           :countdown :samples :loaded? :midi :midi-msg :error :flash :session-start :round]]
  (rf/reg-sub k (fn [db _] (get db k))))

(rf/reg-sub :pool :<- [:settings] (fn [s _] (ev/pool s)))

(rf/reg-sub :inv-choices :<- [:settings] (fn [s _] (ev/inv-choices s)))

(rf/reg-sub :answer-groups
  :<- [:pool]
  (fn [pool _]
    (let [ids (set (map (comp :id first) pool))]
      (for [[g label] t/groups
            :let [cs (filter #(and (= g (:group %)) (ids (:id %))) t/chords)]
            :when (seq cs)]
        [label cs]))))

(rf/reg-sub :session
  :<- [:attempts] :<- [:session-start]
  (fn [[attempts start] _]
    (let [xs (filter #(and (>= (:t %) start) (= :puzzle (:mode %))) attempts)
          streak (count (take-while :correct (reverse xs)))]
      (assoc (stats/summarize xs) :streak streak))))

(rf/reg-sub :stats/overall :<- [:attempts] (fn [a _] (stats/summarize (stats/graded a))))
(rf/reg-sub :stats/by-chord :<- [:attempts] (fn [a _] (stats/by :chord a)))
(rf/reg-sub :stats/by-inv :<- [:attempts] (fn [a _] (sort-by :key (stats/by :inv a))))
(rf/reg-sub :stats/by-pair :<- [:attempts] (fn [a _] (take 15 (stats/by (juxt :chord :inv) a))))
(rf/reg-sub :stats/by-root :<- [:attempts] (fn [a _] (sort-by :key (stats/by :root a))))
(rf/reg-sub :stats/confusions :<- [:attempts] (fn [a _] (stats/confusions a)))
(rf/reg-sub :stats/daily :<- [:attempts] (fn [a _] (stats/daily a 14 (js/Date.now))))
