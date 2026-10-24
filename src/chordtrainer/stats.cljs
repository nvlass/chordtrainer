(ns chordtrainer.stats)

(def max-attempts 20000)
(def recent-window 20)

(defn graded [attempts] (filter #(boolean? (:correct %)) attempts))

(defn summarize [xs]
  (let [n (count xs)
        ok (count (filter :correct xs))
        timed (keep :ms (filter #(= :puzzle (:mode %)) xs))]
    {:n n
     :correct ok
     :acc (when (pos? n) (/ ok n))
     :avg-ms (when (seq timed) (/ (reduce + timed) (count timed)))
     :recent-acc (let [r (take-last recent-window xs)]
                   (when (seq r) (/ (count (filter :correct r)) (count r))))}))

(defn by [kfn attempts]
  (->> (graded attempts)
       (group-by kfn)
       (map (fn [[k xs]] (assoc (summarize xs) :key k)))
       (sort-by (juxt #(or (:acc %) 1) (comp - :n)))))

(defn confusions [attempts]
  (->> (graded attempts)
       (remove :correct)
       (filter :answer)
       (group-by (juxt :chord :inv :answer :answer-inv))
       (map (fn [[[c i a ai] xs]] {:chord c :inv i :answer a :answer-inv ai :n (count xs)}))
       (sort-by :n >)
       (take 12)))

(defn daily [attempts days now]
  (let [day-ms 86400000
        today (quot now day-ms)
        per-day (group-by #(quot (:t %) day-ms) (graded attempts))]
    (for [d (range (- today (dec days)) (inc today))]
      (assoc (summarize (get per-day d)) :day (* d day-ms)))))

(defn- error-rate [xs]
  (let [r (take-last recent-window xs)]
    (/ (inc (count (remove :correct r))) (+ 2 (count r)))))

(defn weight-fn
  "Weight of drilling [chord-id inv]: Laplace-smoothed recent error rate, so
   unseen chords sit at 0.5 and mastered ones fade towards ~0.05."
  [attempts]
  (let [g (graded attempts)
        per-chord (group-by :chord g)
        per-pair (group-by (juxt :chord :inv) g)]
    (fn [id inv]
      (* (error-rate (get per-chord id))
         (+ 0.5 (error-rate (get per-pair [id inv])))))))

(defn weighted-rand [pairs]
  (let [total (reduce + (map second pairs))
        r (* (rand) total)]
    (loop [[[x w] & more] pairs acc 0]
      (if (or (empty? more) (< r (+ acc w))) x (recur more (+ acc w))))))
