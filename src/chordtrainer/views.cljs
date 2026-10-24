(ns chordtrainer.views
  (:require [re-frame.core :as rf]
            [reagent.core :as r]
            [clojure.string :as str]
            [chordtrainer.theory :as t]))

(defn <sub [q] @(rf/subscribe q))
(defn >evt [e] (rf/dispatch e))

(defn sym-label [c] (if (= "" (:sym c)) "maj" (:sym c)))

(defn pct [x] (if x (str (Math/round (* 100 x)) "%") "–"))

(defn secs [ms] (if ms (str (.toFixed (/ ms 1000) 1) "s") "–"))

;; ---------------------------------------------------------------- generic controls

(defn seg [options value on-change]
  [:div.seg
   (for [[v label] options]
     ^{:key (str v)}
     [:button {:class (when (= v value) "on") :on-click #(on-change v)} label])])

(defn toggle [label value on-change & [hint]]
  [:label.toggle
   [:input {:type "checkbox" :checked (boolean value) :on-change #(on-change (not value))}]
   [:span label (when hint [:small hint])]])

(defn slider [label value lo hi step unit on-change]
  [:label.slider
   [:span label [:b value unit]]
   [:input {:type "range" :min lo :max hi :step step :value value
            :on-change #(on-change (js/parseFloat (.. % -target -value)))}]])

;; ---------------------------------------------------------------- piano

(def black-key? #{1 3 6 8 10})

(defn piano [{:keys [notes bass played]}]
  (let [all (concat notes played)
        lo (let [m (apply min (or (seq all) [60]))] (- m (mod m 12)))
        hi (let [m (apply max (or (seq all) [71]))] (+ m (- 11 (mod m 12))))
        hi (if (< (- hi lo) 23) (+ hi 12) hi)
        ks (range lo (inc hi))
        whites (vec (remove #(black-key? (mod % 12)) ks))
        wi (zipmap whites (range))
        w 22 h 86 bw 14 bh 54
        on (set notes)
        pl (set played)
        cls (fn [m] (cond (= m bass) "k bass" (on m) "k on" (pl m) "k played" :else "k"))]
    [:svg.piano {:viewBox (str "0 0 " (* w (count whites)) " " (inc h)) :role "img"
                 :aria-label (str "Notes: " (str/join " " (map t/note-name notes)))}
     (for [m whites :let [x (* w (wi m))]]
       ^{:key m}
       [:g {:class (cls m)}
        [:rect.white {:x x :y 0 :width w :height h :rx 3}]
        (when (or (on m) (pl m))
          [:text {:x (+ x (/ w 2)) :y (- h 8) :text-anchor "middle"} (t/note-names (mod m 12))])])
     (for [m ks :when (black-key? (mod m 12)) :let [x (- (* w (inc (wi (dec m)))) (/ bw 2))]]
       ^{:key m}
       [:g {:class (cls m)}
        [:rect.black {:x x :y 0 :width bw :height bh :rx 2}]
        (when (or (on m) (pl m))
          [:text {:x (+ x (/ bw 2)) :y (- bh 6) :text-anchor "middle"} (t/note-names (mod m 12))])])]))

(defn chord-card [{:keys [chord inv root notes]} & [extra]]
  (let [c (t/chord-by-id chord)]
    [:div.card.reveal
     [:div.chord-name (t/chord-name c root inv)]
     [:div.chord-sub (:name c) " · " (t/inversion-label c inv)]
     [:div.formula (t/formula c)]
     [piano {:notes notes :bass (first notes) :played (:last (<sub [:midi]))}]
     extra]))

;; ---------------------------------------------------------------- shared bits

(defn midi-badge []
  (let [{:keys [state inputs held]} (<sub [:midi])]
    (when (:midi (<sub [:settings]))
      [:span.badge {:class (when (seq inputs) "live")
                    :title (str/join ", " inputs)}
       (case state
         :ready (if (seq inputs) (str "MIDI · " (count inputs) (when (seq held) " ●")) "MIDI · no device")
         :unsupported "MIDI unsupported"
         :denied "MIDI denied"
         "MIDI…")])))

(defn error-banner []
  (when-let [e (<sub [:error])] [:div.banner.warn e]))

(defn root-pill []
  (let [{:keys [root]} (<sub [:current])]
    (when root
      [:div.root-row
       [:span.pill "Root " [:b (t/note-names root)]]
       [:button.ghost {:on-click #(>evt [:hear-root])} "Hear root"]
       [:button.ghost {:on-click #(>evt [:replay])} "▶ Replay"]])))

;; ---------------------------------------------------------------- puzzle

(defn answer-grid []
  (let [groups (<sub [:answer-groups])
        choices (<sub [:inv-choices])
        pending (<sub [:pending])]
    [:div.answers
     (when (> (count choices) 1)
       [:div.inv-row
        (for [i choices]
          ^{:key i}
          [:button.inv {:class (when (= i (:inv pending)) "on")
                        :on-click #(>evt [:puzzle/pick-inv i])}
           (t/inversion-short i)])])
     (for [[label cs] groups]
       ^{:key label}
       [:div.group
        [:div.group-label label]
        [:div.grid
         (for [c cs]
           ^{:key (:id c)}
           [:button.q {:class (when (= (:id c) (:chord pending)) "on")
                       :on-click #(>evt [:puzzle/pick-chord (:id c)])}
            [:b (sym-label c)] [:small (:name c)]])]])
     (when (> (count choices) 1)
       [:p.hint "Pick an inversion and a chord, in either order."])]))

(defn result []
  (let [{:keys [correct answer answer-inv ms via]} (<sub [:answer])
        cur (<sub [:current])
        a (t/chord-by-id answer)]
    [:div
     [:div.banner {:class (if correct "good" "bad")}
      (if correct "✓ Correct" "✗ Not quite")
      (when-not correct
        [:span " — you said " [:b (str (sym-label a)
                                        (when (> (count (<sub [:inv-choices])) 1)
                                          (str " · " (t/inversion-short answer-inv))))]])
      [:span.meta (secs ms) (when (= via :midi) " · via MIDI")]]
     [chord-card cur
      [:div.actions
       [:button {:on-click #(>evt [:replay])} "▶ Play it"]
       (when-not correct [:button {:on-click #(>evt [:puzzle/play-answer])} "Play yours"])
       (when-not correct [:button {:on-click #(>evt [:puzzle/compare])} "Compare (C)"])
       [:button.primary {:on-click #(>evt [:puzzle/next])} "Next →"]]]]))

(defn puzzle-view []
  (let [phase (<sub [:phase])
        {:keys [n acc streak]} (<sub [:session])]
    [:section
     [error-banner]
     (if (= :idle phase)
       [:div.start
        [:h2 "Puzzle"]
        [:p "A chord plays — name its quality" (when (> (count (<sub [:inv-choices])) 1) " and inversion") "."]
        [:button.primary.big {:on-click #(>evt [:puzzle/next])} "Start"]
        [:p.hint "Keys: Enter next · Space replay · R root · C compare"]]
       [:div
        [:div.topline
         [root-pill]
         [:span.score (when (pos? n) (str (:correct (<sub [:session])) "/" n " · " (pct acc)
                                          (when (> streak 1) (str " · 🔥" streak))))]]
        (when-let [m (<sub [:midi-msg])] [:div.banner.warn m])
        (case phase
          :question [answer-grid]
          :answered [result]
          nil)])]))

;; ---------------------------------------------------------------- training

(defn training-view []
  (let [phase (<sub [:phase])
        running (<sub [:running])
        cur (<sub [:current])
        {:keys [ms round]} (<sub [:countdown])
        graded (<sub [:graded])
        s (<sub [:settings])]
    [:section
     [error-banner]
     (if (= :idle phase)
       [:div.start
        [:h2 "Training"]
        [:p "Chords play one after another. Name each one in your head before it's revealed ("
         (:pause s) "s pause)."]
        [:button.primary.big {:on-click #(>evt [:training/start])} "Start"]
        [:p.hint "Keys: Enter reveal / next · Space replay · R root"]]
       [:div
        [:div.topline [root-pill]]
        (if (= :listening phase)
          [:div.card.mystery
           [:div.chord-name "?"]
           [:div.chord-sub "Listen. What is it?"]
           ^{:key round} [:div.countdown [:div {:style {:animation-duration (str ms "ms")}}]]
           [:div.actions
            [:button {:on-click #(>evt [:training/reveal round])} "Reveal now"]]]
          [chord-card cur
           [:div.actions
            [:button.good {:disabled (some? graded) :class (when (true? graded) "on")
                           :on-click #(>evt [:training/grade true])} "✓ Heard it"]
            [:button.bad {:disabled (some? graded) :class (when (false? graded) "on")
                          :on-click #(>evt [:training/grade false])} "✗ Missed it"]
            [:button.primary {:on-click #(>evt [:training/next])} "Next →"]]])
        [:div.actions.center
         (if running
           [:button.ghost {:on-click #(>evt [:training/stop])} "■ Stop"]
           [:button.ghost {:on-click #(>evt [:training/start])} "Resume"])
         (when (and running (:auto-advance s)) [:span.hint "auto-advancing"])]])]))

;; ---------------------------------------------------------------- stats

(defn chord-label [id inv]
  (let [c (t/chord-by-id id)]
    (str (sym-label c) (when inv (str " · " (t/inversion-short inv))))))

(defn meter [x]
  [:span.meter [:span {:style {:width (pct (or x 0))}}]])

(defn stat-table [title rows label-fn]
  (when (seq rows)
    [:div.card
     [:h3 title]
     [:table.stats
      [:thead [:tr [:th] [:th "n"] [:th "accuracy"] [:th.num "last 20"] [:th.num "avg"]]]
      [:tbody
       (for [{:keys [key n acc recent-acc avg-ms]} rows]
         ^{:key (str key)}
         [:tr
          [:td (label-fn key)]
          [:td.num n]
          [:td [meter acc] [:span.num (pct acc)]]
          [:td.num (pct recent-acc)]
          [:td.num (secs avg-ms)]])]]]))

(defn daily-chart []
  (let [days (<sub [:stats/daily])
        maxn (apply max 1 (map :n days))]
    [:div.card
     [:h3 "Last 14 days"]
     [:div.daily
      (for [{:keys [day n acc]} days
            :let [d (js/Date. day)
                  label (str (.getDate d) "/" (inc (.getMonth d)))]]
        ^{:key day}
        [:div.day {:title (str label ": " n " answers" (when acc (str ", " (pct acc) " correct")))}
         [:div.bar-wrap
          (when (pos? n)
            [:div.bar {:style {:height (pct acc) :opacity (+ 0.35 (* 0.65 (/ n maxn)))}}])]
         [:small label]])]
     [:p.hint "Bar height = accuracy, opacity = volume. Hover for details."]]))

(defn reset-button []
  (let [armed (r/atom false)]
    (fn []
      (if @armed
        [:span
         [:button.bad {:on-click #(do (reset! armed false) (>evt [:stats/reset]))} "Really erase all stats"]
         [:button.ghost {:on-click #(reset! armed false)} "Cancel"]]
        [:button.ghost {:on-click #(reset! armed true)} "Reset stats…"]))))

(defn stats-view []
  (let [{:keys [n acc avg-ms]} (<sub [:stats/overall])
        conf (<sub [:stats/confusions])]
    [:section
     [:div.tiles
      [:div.tile [:small "Answers"] [:b n]]
      [:div.tile [:small "Accuracy"] [:b (pct acc)]]
      [:div.tile [:small "Avg time"] [:b (secs avg-ms)]]]
     (if (zero? n)
       [:div.card [:p "No stats yet. Answer some puzzles (or self-grade in training) and your weak spots will show up here. "
                   "With “adaptive” on, the trainer plays your weak chords more often."]]
       [:<>
        [daily-chart]
        [stat-table "Chords — weakest first" (<sub [:stats/by-chord]) #(chord-label % nil)]
        [stat-table "Weakest chord + inversion" (<sub [:stats/by-pair]) (fn [[id inv]] (chord-label id inv))]
        (when (seq conf)
          [:div.card
           [:h3 "Confusions"]
           [:ul.conf
            (for [{:keys [chord inv answer answer-inv n]} conf]
              ^{:key (str chord inv answer answer-inv)}
              [:li [:b (chord-label chord inv)] " heard as " [:b (chord-label answer answer-inv)]
               [:span.num (str n "×")]])]])
        [stat-table "Inversions" (<sub [:stats/by-inv]) #(t/inversion-short %)]
        [stat-table "Roots" (<sub [:stats/by-root]) #(t/note-names %)]])
     [:div.card
      [:h3 "Data"]
      [:p.hint "Stats live in this browser. Export from one device and import on another to merge them."]
      (when-let [f (<sub [:flash])] [:div.banner f])
      [:div.actions
       [:button {:on-click #(>evt [:stats/export])} "Export"]
       [:label.button "Import"
        [:input {:type "file" :accept ".edn,text/plain" :hidden true
                 :on-change (fn [e]
                              (when-let [file (aget (.. e -target -files) 0)]
                                (-> (.text file) (.then #(>evt [:stats/import %]))))
                              (set! (.. e -target -value) ""))}]]
       [reset-button]]]]))

;; ---------------------------------------------------------------- settings

(defn chord-settings []
  (let [enabled (:enabled (<sub [:settings]))]
    [:div.card
     [:h3 "Chords " [:small (count enabled) " enabled"]]
     [:div.presets
      (for [[label ids] t/presets]
        ^{:key label}
        [:button.ghost {:class (when (= (set ids) enabled) "on")
                        :on-click #(>evt [:settings/set-enabled ids])} label])]
     (for [[g label] t/groups
           :let [cs (filter #(= g (:group %)) t/chords)
                 ids (set (map :id cs))
                 all? (every? enabled ids)]]
       ^{:key g}
       [:div.group
        [:div.group-label label
         [:button.link {:on-click #(>evt [:settings/set-enabled
                                          (if all? (remove ids enabled) (into enabled ids))])}
          (if all? "none" "all")]]
        [:div.chips
         (for [c cs]
           ^{:key (:id c)}
           [:button.chip {:class (when (enabled (:id c)) "on")
                          :title (str (:name c) ": " (t/formula c))
                          :on-click #(>evt [:settings/toggle-chord (:id c)])}
            (sym-label c)])]])]))

(defn settings-view []
  (let [s (<sub [:settings])
        setter (fn [k] #(>evt [:settings/set k %]))]
    [:section
     [chord-settings]
     [:div.card
      [:h3 "Roots"]
      [seg [[:fixed "Fixed"] [:naturals "Random, no accidentals"] [:all "Random, all 12"]]
       (:root-mode s) (setter :root-mode)]
      (when (= :fixed (:root-mode s))
        [:div.chips
         (for [pc (range 12)]
           ^{:key pc}
           [:button.chip {:class (when (= pc (:fixed-root s)) "on")
                          :on-click #(>evt [:settings/set :fixed-root pc])}
            (t/note-names pc)])])]
     [:div.card
      [:h3 "Inversions"]
      [:div.chips
       (for [i (range 4)]
         ^{:key i}
         [:button.chip {:class (when ((:inversions s) i) "on")
                        :on-click #(>evt [:settings/toggle-inversion i])}
          (t/inversion-short i)])]
      [:p.hint "1st = 3rd (or sus tone) in bass, 2nd = 5th, 3rd = 7th/6th. "
       "Chords with 9ths and up are played as slash chords with that tone in the bass; "
       "a chord is skipped for an inversion it lacks (e.g. 13 has no 5th)."]]
     [:div.card
      [:h3 "Sound"]
      [:div.field [:span "Voicing"]
       [seg [[:close "Close"] [:spread "Spread (bass + RH)"]] (:voicing s) (setter :voicing)]]
      [:div.field [:span "Playback"]
       [seg [[:block "Block"] [:arp "Arpeggio"] [:arp-block "Arp → block"]] (:playback s) (setter :playback)]]
      (when (not= :block (:playback s))
        [slider "Arpeggio speed" (:tempo s) 60 400 10 " bpm" (setter :tempo)])
      [toggle "Play root before each chord" (:root-ref s) (setter :root-ref)
       "a reference so you can hear the chord against its root"]]
     [:div.card
      [:h3 "Training"]
      [slider "Thinking pause" (:pause s) 0 10 0.5 "s" (setter :pause)]
      [slider "Show answer for" (:hold s) 1 15 0.5 "s" (setter :hold)]
      [toggle "Replay chord on reveal" (:replay-on-reveal s) (setter :replay-on-reveal)]
      [toggle "Auto-advance" (:auto-advance s) (setter :auto-advance)]]
     [:div.card
      [:h3 "Selection"]
      [toggle "Adaptive — drill weak chords more" (:adaptive s) (setter :adaptive)
       "weights chords by your recent error rate"]]
     [:div.card
      [:h3 "MIDI keyboard"]
      [toggle "Use MIDI input" (:midi s) (setter :midi)
       "answer puzzles by playing the chord (Chrome/Edge/Firefox; not iOS Safari)"]
      (when (:midi s)
        [:<>
         [:p.hint (let [{:keys [state inputs]} (<sub [:midi])]
                    (case state
                      :ready (if (seq inputs) (str "Connected: " (str/join ", " inputs)) "No MIDI devices found — plug one in.")
                      :unsupported "This browser doesn't support Web MIDI."
                      :denied "MIDI access was denied."
                      "Connecting…"))]
         [toggle "Sound notes I play" (:midi-echo s) (setter :midi-echo)
          "for silent controllers"]])]]))

;; ---------------------------------------------------------------- app

(def tabs [[:training "Train"] [:puzzle "Puzzle"] [:stats "Stats"] [:settings "Settings"]])

(defn app []
  (let [view (<sub [:view])
        samples (<sub [:samples])]
    [:div.app
     [:header
      [:h1 "Chord trainer"]
      [midi-badge]]
     [:nav
      (for [[v label] tabs]
        ^{:key v}
        [:button {:class (when (= v view) "on") :on-click #(>evt [:set-view v])} label])]
     (when (nil? samples) [:div.banner "Loading piano samples…"])
     (when (zero? samples) [:div.banner.warn "Couldn't load piano samples."])
     [:main
      (case view
        :training [training-view]
        :puzzle [puzzle-view]
        :stats [stats-view]
        :settings [settings-view])]]))
