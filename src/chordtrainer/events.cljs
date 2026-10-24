(ns chordtrainer.events
  (:require [re-frame.core :as rf]
            [cljs.reader :as reader]
            [clojure.string :as str]
            [chordtrainer.theory :as t]
            [chordtrainer.audio :as audio]
            [chordtrainer.stats :as stats]
            [chordtrainer.sketch :as sketch]
            [chordtrainer.storage :as storage]
            [chordtrainer.midi :as midi]))

(def default-settings
  {:enabled #{:maj7 :dom7 :m7 :m7b5 :dim7 :mmaj7 :maj7s5}
   :root-mode :all
   :fixed-root 0
   :inversions #{0}
   :voicing :close
   :playback :block
   :tempo 180
   :root-ref false
   :pause 3
   :hold 3
   :replay-on-reveal true
   :auto-advance true
   :adaptive true
   :midi false
   :midi-echo false
   :imagine-root true})

(def default-sketch
  {:name "Example"
   :text "Em9 | Em9/D | Cmaj7#11 | B7alt\nAm9 | D13 | Gmaj7 | F#m7b5 B7b9"
   :tempo 96
   :beats 4
   :comp :charleston
   :bass true
   :loop true
   :voice-lead true})

(def initial-db
  {:view :puzzle
   :settings default-settings
   :attempts []
   :loaded? false
   :samples nil
   :phase :idle
   :round 0
   :running false
   :session-start (js/Date.now)
   :sketch default-sketch
   :sketches []
   :sketch-play {:playing false :token 0 :pos nil}
   :stats-filter :all
   :midi {:state :off :inputs [] :held #{} :collected #{}}})

;; ---------------------------------------------------------------- effects

(rf/reg-fx :audio/play
  (fn [x]
    (let [{:keys [events voices]} (if (map? x) x {:events x})]
      (audio/stop-all!)
      (audio/play-events! events voices))))
(rf/reg-fx :audio/stop (fn [_] (audio/stop-all!)))
(rf/reg-fx :later
  (fn [x]
    (doseq [{:keys [ms event]} (if (map? x) [x] x)]
      (js/setTimeout #(rf/dispatch event) ms))))
(rf/reg-fx :save (fn [kvs] (doseq [[k v] kvs] (storage/save! k v))))
(rf/reg-fx :midi/connect (fn [on?] (if on? (midi/connect!) (midi/disconnect!))))

(rf/reg-fx :download
  (fn [{:keys [filename content]}]
    (let [url (js/URL.createObjectURL (js/Blob. #js [content] #js {:type "application/edn"}))
          a (js/document.createElement "a")]
      (set! (.-href a) url)
      (set! (.-download a) filename)
      (.click a)
      (js/setTimeout #(js/URL.revokeObjectURL url) 1000))))

(rf/reg-cofx :now (fn [cofx] (assoc cofx :now (js/Date.now))))

;; ---------------------------------------------------------------- helpers

(defn pool [{:keys [enabled inversions]}]
  (for [c t/chords
        :when (contains? enabled (:id c))
        :let [invs (filterv (set inversions) (t/inversions c))]
        :when (seq invs)]
    [c invs]))

(defn inv-choices [settings] (vec (sort (:inversions settings))))

(defn- pick-root [{:keys [root-mode fixed-root]}]
  (case root-mode
    :fixed fixed-root
    :naturals (rand-nth t/natural-pcs)
    (rand-int 12)))

(def recognition-modes #{:puzzle :training})

(defn- next-question
  "Weights come from attempts in `modes`, so imagining and recognizing track separately."
  [{:keys [settings attempts current]} modes]
  (let [p (pool settings)
        w (if (:adaptive settings)
            (stats/weight-fn (filter #(modes (:mode %)) attempts))
            (constantly 1))
        weighted (for [[c invs] p inv invs]
                   [[(:id c) inv] (/ (w (:id c) inv) (count invs))])
        draw (fn []
               (let [[id inv] (stats/weighted-rand weighted)
                     root (pick-root settings)]
                 {:chord id :inv inv :root root
                  :notes (t/voicing (t/chord-by-id id) root inv (:voicing settings))}))]
    (when (seq weighted)
      (loop [i 0]
        (let [q (draw)]
          (if (and (< i 8) (> (count weighted) 1)
                   (= [(:chord q) (:inv q)] [(:chord current) (:inv current)]))
            (recur (inc i))
            q))))))

(defn- timeline [db notes & [opts]]
  (audio/timeline notes (merge (:settings db) opts) (t/root-midi (get-in db [:current :root]))))

(defn- answer-notes [db {:keys [answer answer-inv]}]
  (let [c (t/chord-by-id answer)
        inv (if (some #{answer-inv} (t/inversions c)) answer-inv 0)]
    (t/voicing c (get-in db [:current :root]) inv (get-in db [:settings :voicing]))))

(defn- record [db attempt]
  (let [attempts (vec (take-last stats/max-attempts (conj (:attempts db) attempt)))]
    [(assoc db :attempts attempts) attempts]))

;; ---------------------------------------------------------------- lifecycle

(rf/reg-event-fx :init
  (fn [_ _]
    {:db initial-db}))

(rf/reg-event-fx :storage/loaded
  (fn [{:keys [db]} [_ {:keys [settings attempts sketch sketches]}]]
    (let [s (merge default-settings settings)]
      (cond-> {:db (assoc db :settings s :attempts (vec attempts) :loaded? true
                          :sketch (merge default-sketch sketch)
                          :sketches (vec sketches))}
        (:midi s) (assoc :midi/connect true)))))

(rf/reg-event-db :audio/loaded
  (fn [db [_ n]] (assoc db :samples n)))

(rf/reg-event-fx :set-view
  (fn [{:keys [db]} [_ v]]
    (if (= v (:view db))
      {:db db}
      {:db (-> db
               (assoc :view v :running false :phase :idle :current nil :answer nil
                      :error nil :flash nil :round (inc (:round db)))
               (update :sketch-play assoc :playing false :pos nil)
               (update-in [:sketch-play :token] inc))
       :audio/stop true})))

;; ---------------------------------------------------------------- settings

(defn- save-settings [db] {:db db :save {:settings (:settings db)}})

(rf/reg-event-fx :settings/set
  (fn [{:keys [db]} [_ k v]]
    (cond-> (save-settings (assoc-in db [:settings k] v))
      (= k :midi) (assoc :midi/connect v))))

(rf/reg-event-fx :settings/toggle-chord
  (fn [{:keys [db]} [_ id]]
    (save-settings (update-in db [:settings :enabled]
                              #(if (contains? % id) (disj % id) (conj % id))))))

(rf/reg-event-fx :settings/set-enabled
  (fn [{:keys [db]} [_ ids]]
    (save-settings (assoc-in db [:settings :enabled] (set ids)))))

(rf/reg-event-fx :settings/toggle-inversion
  (fn [{:keys [db]} [_ k]]
    (let [invs (get-in db [:settings :inversions])
          new (if (contains? invs k) (disj invs k) (conj invs k))]
      (if (empty? new) {:db db} (save-settings (assoc-in db [:settings :inversions] new))))))

;; ---------------------------------------------------------------- puzzle

(rf/reg-event-fx :puzzle/next
  [(rf/inject-cofx :now)]
  (fn [{:keys [db now]} _]
    (if-let [q (next-question db recognition-modes)]
      (let [tl (timeline (assoc db :current q) (:notes q))]
        {:db (assoc db
                    :current (assoc q :started (+ now (* 1000 (:length tl))))
                    :phase :question :answer nil :pending {} :midi-msg nil :error nil)
         :audio/play (:events tl)})
      {:db (assoc db :error "No chords match your settings — enable some chords or inversions.")})))

(rf/reg-event-fx :replay
  (fn [{:keys [db]} _]
    (when-let [notes (get-in db [:current :notes])]
      {:audio/play (:events (timeline db notes))})))

(rf/reg-event-fx :hear-root
  (fn [{:keys [db]} _]
    (when-let [root (get-in db [:current :root])]
      {:audio/play [[(t/root-midi root) 0 1.4]]})))

(defn- submit [{:keys [db now]} chord-id inv via]
  (let [{:keys [chord root] :as cur} (:current db)
        correct (t/same-sound? root
                               [(t/chord-by-id chord) (:inv cur)]
                               [(t/chord-by-id chord-id) inv])
        attempt {:t now :mode :puzzle :chord chord :inv (:inv cur) :root root
                 :answer chord-id :answer-inv inv :correct (boolean correct)
                 :ms (max 0 (- now (:started cur))) :via via}
        [db attempts] (record db attempt)]
    {:db (assoc db :phase :answered :answer attempt :pending {})
     :save {:attempts attempts}}))

(rf/reg-event-fx :puzzle/pick-chord
  [(rf/inject-cofx :now)]
  (fn [{:keys [db] :as cofx} [_ id]]
    (when (= :question (:phase db))
      (let [choices (inv-choices (:settings db))
            inv (get-in db [:pending :inv])]
        (cond
          (= 1 (count choices)) (submit cofx id (first choices) :touch)
          inv (submit cofx id inv :touch)
          :else {:db (assoc-in db [:pending :chord] id)})))))

(rf/reg-event-fx :puzzle/pick-inv
  [(rf/inject-cofx :now)]
  (fn [{:keys [db] :as cofx} [_ inv]]
    (when (= :question (:phase db))
      (if-let [id (get-in db [:pending :chord])]
        (submit cofx id inv :touch)
        {:db (assoc-in db [:pending :inv] inv)}))))

(rf/reg-event-fx :puzzle/play-answer
  (fn [{:keys [db]} _]
    (when-let [a (:answer db)]
      {:audio/play (:events (timeline db (answer-notes db a) {:root-ref false}))})))

(rf/reg-event-fx :puzzle/compare
  (fn [{:keys [db]} _]
    (when-let [a (:answer db)]
      {:audio/play (:events (audio/chain [(timeline db (get-in db [:current :notes]) {:root-ref false})
                                          (timeline db (answer-notes db a) {:root-ref false})]
                                         0.8))})))

;; ---------------------------------------------------------------- training

(rf/reg-event-fx :training/start
  (fn [{:keys [db]} _]
    {:db (assoc db :running true)
     :dispatch [:training/next]}))

(rf/reg-event-fx :training/stop
  (fn [{:keys [db]} _]
    {:db (assoc db :running false :phase :idle :round (inc (:round db)))
     :audio/stop true}))

(rf/reg-event-fx :training/next
  (fn [{:keys [db]} _]
    (if-let [q (next-question db recognition-modes)]
      (let [round (inc (:round db))
            tl (timeline (assoc db :current q) (:notes q))
            wait (* 1000 (+ (:length tl) (get-in db [:settings :pause])))]
        {:db (assoc db :current q :phase :listening :round round :graded nil :error nil
                    :running true :countdown {:ms wait :round round})
         :audio/play (:events tl)
         :later {:ms wait :event [:training/reveal round]}})
      {:db (assoc db :running false :phase :idle
                  :error "No chords match your settings — enable some chords or inversions.")})))

(rf/reg-event-fx :training/reveal
  (fn [{:keys [db]} [_ round]]
    (when (and (= round (:round db)) (= :listening (:phase db)))
      (let [s (:settings db)
            tl (when (:replay-on-reveal s)
                 (timeline db (get-in db [:current :notes]) {:root-ref false}))
            hold (* 1000 (+ (:length tl 0) (:hold s)))]
        (cond-> {:db (assoc db :phase :revealed)}
          tl (assoc :audio/play (:events tl))
          (and (:running db) (:auto-advance s))
          (assoc :later {:ms hold :event [:training/auto-next round]}))))))

(rf/reg-event-fx :training/auto-next
  (fn [{:keys [db]} [_ round]]
    (when (and (:running db) (= round (:round db)) (= :revealed (:phase db)))
      {:dispatch [:training/next]})))

(rf/reg-event-fx :training/grade
  [(rf/inject-cofx :now)]
  (fn [{:keys [db now]} [_ correct]]
    (when (and (= :revealed (:phase db)) (nil? (:graded db)))
      (let [{:keys [chord inv root]} (:current db)
            [db attempts] (record db {:t now :mode :training :chord chord :inv inv :root root
                                      :correct correct :via :self})]
        {:db (assoc db :graded correct)
         :save {:attempts attempts}}))))

;; ---------------------------------------------------------------- imagine

(rf/reg-event-fx :imagine/next
  (fn [{:keys [db]} _]
    (if-let [q (next-question db #{:imagine})]
      (cond-> {:db (assoc db :current q :phase :imagine :graded nil :error nil)}
        (get-in db [:settings :imagine-root])
        (assoc :audio/play [[(t/root-midi (:root q)) 0 1.4]]))
      {:db (assoc db :error "No chords match your settings — enable some chords or inversions.")})))

(rf/reg-event-fx :imagine/hear
  (fn [{:keys [db]} _]
    (when-let [notes (get-in db [:current :notes])]
      {:db (assoc db :phase :imagine-heard)
       :audio/play (:events (timeline db notes {:root-ref false}))})))

(rf/reg-event-fx :imagine/grade
  [(rf/inject-cofx :now)]
  (fn [{:keys [db now]} [_ correct]]
    (when (and (= :imagine-heard (:phase db)) (nil? (:graded db)))
      (let [{:keys [chord inv root]} (:current db)
            [db attempts] (record db {:t now :mode :imagine :chord chord :inv inv :root root
                                      :correct correct :via :self})]
        {:db (assoc db :graded correct)
         :save {:attempts attempts}}))))

;; ---------------------------------------------------------------- sketchpad

(defn- save-sketch [db] {:db db :save {:sketch (:sketch db)}})

(rf/reg-event-fx :sketch/set
  (fn [{:keys [db]} [_ k v]]
    (save-sketch (assoc-in db [:sketch k] v))))

(rf/reg-event-fx :sketch/transpose
  (fn [{:keys [db]} [_ n]]
    (save-sketch (update-in db [:sketch :text] sketch/transpose-text n))))

(defn- realize [db] (sketch/realize (:sketch db) (get-in db [:settings :voicing])))

(rf/reg-event-fx :sketch/play
  (fn [{:keys [db]} _]
    (let [slots (realize db)
          {:keys [events length marks voices]} (sketch/schedule slots (:sketch db))
          token (inc (get-in db [:sketch-play :token]))]
      (if (empty? events)
        {:db (assoc db :error "Nothing to play — type some chords like  Dm9 | G13 | Cmaj9")}
        {:db (assoc db :sketch-play {:playing true :token token :pos 0} :error nil)
         :audio/play {:events events :voices voices}
         :later (into [{:ms (* 1000 length) :event [:sketch/end token]}]
                      (map-indexed (fn [i t] {:ms (* 1000 t) :event [:sketch/at token i]}) marks))}))))

(rf/reg-event-db :sketch/at
  (fn [db [_ token i]]
    (if (= token (get-in db [:sketch-play :token]))
      (assoc-in db [:sketch-play :pos] i)
      db)))

(rf/reg-event-fx :sketch/end
  (fn [{:keys [db]} [_ token]]
    (when (and (= token (get-in db [:sketch-play :token])) (get-in db [:sketch-play :playing]))
      (if (get-in db [:sketch :loop])
        {:dispatch [:sketch/play]}
        {:db (update db :sketch-play assoc :playing false :pos nil)}))))

(rf/reg-event-fx :sketch/stop
  (fn [{:keys [db]} _]
    {:db (-> db
             (update :sketch-play assoc :playing false :pos nil)
             (update-in [:sketch-play :token] inc))
     :audio/stop true}))

(rf/reg-event-fx :sketch/select
  (fn [{:keys [db]} [_ i]]
    (let [{:keys [bass-note upper error]} (get (realize db) i)
          db (-> db
                 (assoc :sketch-sel i)
                 (update :sketch-play assoc :playing false :pos nil)
                 (update-in [:sketch-play :token] inc))]
      (if error
        {:db db}
        {:db db :audio/play (map (fn [m] [m 0 2.2]) (cons bass-note upper))}))))

(rf/reg-event-fx :sketch/save
  (fn [{:keys [db]} _]
    (let [sk (:sketch db)
          sketches (->> (:sketches db)
                        (remove #(= (:name %) (:name sk)))
                        (cons sk)
                        (sort-by :name)
                        vec)]
      {:db (assoc db :sketches sketches :flash (str "Saved “" (:name sk) "”."))
       :save {:sketches sketches}})))

(rf/reg-event-fx :sketch/load
  (fn [{:keys [db]} [_ nm]]
    (when-let [sk (first (filter #(= nm (:name %)) (:sketches db)))]
      (save-sketch (-> db
                       (assoc :sketch (merge default-sketch sk) :sketch-sel nil :flash nil)
                       (update :sketch-play assoc :playing false :pos nil)
                       (update-in [:sketch-play :token] inc))))))

(rf/reg-event-fx :sketch/delete
  (fn [{:keys [db]} _]
    (let [nm (get-in db [:sketch :name])
          sketches (vec (remove #(= nm (:name %)) (:sketches db)))]
      {:db (assoc db :sketches sketches :flash (str "Deleted “" nm "”."))
       :save {:sketches sketches}})))

(rf/reg-event-fx :sketch/new
  (fn [{:keys [db]} _]
    (save-sketch (assoc db :sketch (assoc default-sketch :name "Untitled" :text "") :sketch-sel nil :flash nil))))

(rf/reg-event-db :stats/filter
  (fn [db [_ f]] (assoc db :stats-filter f)))

;; ---------------------------------------------------------------- midi

(rf/reg-event-db :midi/status
  (fn [db [_ m]] (update db :midi merge m)))

(rf/reg-event-fx :midi/note-on
  (fn [{:keys [db]} [_ n]]
    (cond-> {:db (-> db
                     (update-in [:midi :held] conj n)
                     (update-in [:midi :collected] conj n))}
      (get-in db [:settings :midi-echo]) (assoc :audio/play [[n 0 1.5]]))))

(rf/reg-event-fx :midi/note-off
  (fn [{:keys [db]} [_ n]]
    (let [db (update-in db [:midi :held] disj n)]
      (if (empty? (get-in db [:midi :held]))
        {:db (assoc-in db [:midi :collected] #{})
         :dispatch [:midi/chord (vec (sort (get-in db [:midi :collected])))]}
        {:db db}))))

(rf/reg-event-fx :midi/chord
  [(rf/inject-cofx :now)]
  (fn [{:keys [db] :as cofx} [_ notes]]
    (let [db (assoc-in db [:midi :last] notes)
          cofx (assoc cofx :db db)]
      (if (and (= :puzzle (:view db)) (= :question (:phase db)) (> (count notes) 1))
        (let [{:keys [chord root]} (:current db)
              enabled (map first (pool (:settings db)))
              target (t/chord-by-id chord)
              [c inv] (or (t/identify root (cons target enabled) notes)
                          (t/identify root t/chords notes))]
          (if c
            (submit cofx (:id c) inv :midi)
            {:db (assoc db :midi-msg (str "Couldn't match "
                                          (str/join " " (map t/note-name notes))
                                          " to a chord on " (t/note-names root) " — try again."))}))
        {:db db}))))

;; ---------------------------------------------------------------- stats

(rf/reg-event-fx :stats/reset
  (fn [{:keys [db]} _]
    {:db (assoc db :attempts [])
     :save {:attempts []}}))

(rf/reg-event-fx :stats/export
  (fn [{:keys [db]} _]
    {:download {:filename (str "chordtrainer-" (.slice (.toISOString (js/Date.)) 0 10) ".edn")
                :content (pr-str {:attempts (:attempts db) :settings (:settings db)})}}))

(rf/reg-event-fx :stats/import
  (fn [{:keys [db]} [_ text]]
    (let [data (try (reader/read-string text) (catch :default _ nil))]
      (if-let [incoming (and (map? data) (seq (:attempts data)) (:attempts data))]
        (let [merged (->> (concat (:attempts db) incoming)
                          (reduce (fn [m a] (assoc m [(:t a) (:chord a)] a)) {})
                          vals
                          (sort-by :t)
                          (take-last stats/max-attempts)
                          vec)]
          {:db (assoc db :attempts merged :flash (str "Imported — " (count merged) " attempts total."))
           :save {:attempts merged}})
        {:db (assoc db :flash "That file doesn't look like a chordtrainer export.")}))))

;; ---------------------------------------------------------------- keyboard

(rf/reg-event-fx :key
  (fn [{:keys [db]} [_ k]]
    (let [{:keys [view phase running]} db]
      (case [view k]
        [:puzzle " "] (when (#{:question :answered} phase) {:dispatch [:replay]})
        [:puzzle "Enter"] (when (#{:idle :answered} phase) {:dispatch [:puzzle/next]})
        [:puzzle "r"] {:dispatch [:hear-root]}
        [:puzzle "c"] (when (= :answered phase) {:dispatch [:puzzle/compare]})
        [:training " "] (when running {:dispatch [:replay]})
        [:training "Enter"] (cond
                              (not running) {:dispatch [:training/start]}
                              (= :revealed phase) {:dispatch [:training/next]}
                              :else {:dispatch [:training/reveal (:round db)]})
        [:training "r"] {:dispatch [:hear-root]}
        [:training "y"] {:dispatch [:training/grade true]}
        [:training "n"] {:dispatch [:training/grade false]}
        [:imagine "Enter"] (case phase
                             :imagine {:dispatch [:imagine/hear]}
                             {:dispatch [:imagine/next]})
        [:imagine " "] (when (= :imagine-heard phase) {:dispatch [:imagine/hear]})
        [:imagine "r"] {:dispatch [:hear-root]}
        [:imagine "y"] {:dispatch [:imagine/grade true]}
        [:imagine "n"] {:dispatch [:imagine/grade false]}
        [:sketch " "] {:dispatch (if (get-in db [:sketch-play :playing]) [:sketch/stop] [:sketch/play])}
        nil))))
