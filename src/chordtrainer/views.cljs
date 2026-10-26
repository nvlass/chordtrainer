(ns chordtrainer.views
  (:require [re-frame.core :as rf]
            [reagent.core :as r]
            [clojure.string :as str]
            [chordtrainer.theory :as t]
            [chordtrainer.i18n :refer [tr tr-msg]]))

(defn <sub [q] @(rf/subscribe q))
(defn >evt [e] (rf/dispatch e))

(defn sym-label [c] (if (= "" (:sym c)) "maj" (:sym c)))

(defn pct [x] (if x (str (Math/round (* 100 x)) "%") "–"))

(defn chord-desc [c] (tr (keyword "chord" (name (:id c)))))

(defn inv-short [i] (tr :inv/short i))

(defn inversion-label [chord inv]
  (if (zero? inv)
    (tr :inv/long 0)
    (str (if (t/simple? chord) (tr :inv/long inv) (tr :inv/slash)) " · "
         (tr :inv/in-bass (t/bass-degree chord inv)))))

(defn formula [chord]
  (let [opt (:optional chord)]
    (str (str/join " " (:degrees chord))
         (when (seq opt) (str "  (" (tr :inv/optional) " " (str/join " " opt) ")")))))

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
                 :aria-label (tr :ui/notes (str/join " " (map t/note-name notes)))}
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
     [:div.chord-sub (chord-desc c) " · " (inversion-label c inv)]
     [:div.formula (formula c)]
     [piano {:notes notes :bass (first notes) :played (:last (<sub [:midi]))}]
     extra]))

;; ---------------------------------------------------------------- shared bits

(defn midi-badge []
  (let [{:keys [state inputs held]} (<sub [:midi])]
    (when (:midi (<sub [:settings]))
      [:span.badge {:class (when (seq inputs) "live")
                    :title (str/join ", " inputs)}
       (case state
         :ready (if (seq inputs) (tr :midi/ready (count inputs) (seq held)) (tr :midi/no-device))
         :unsupported (tr :midi/unsupported)
         :denied (tr :midi/denied)
         (tr :midi/connecting))])))

(defn error-banner []
  (when-let [e (<sub [:error])] [:div.banner.warn (tr-msg e)]))

(defn root-pill []
  (let [{:keys [root]} (<sub [:current])]
    (when root
      [:div.root-row
       [:span.pill (tr :ui/root) [:b (t/note-names root)]]
       [:button.ghost {:on-click #(>evt [:hear-root])} (tr :ui/hear-root)]
       [:button.ghost {:on-click #(>evt [:replay])} (tr :ui/replay)]])))

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
           (inv-short i)])])
     (for [[g cs] groups]
       ^{:key g}
       [:div.group
        [:div.group-label (tr (keyword "group" (name g)))]
        [:div.grid
         (for [c cs]
           ^{:key (:id c)}
           [:button.q {:class (when (= (:id c) (:chord pending)) "on")
                       :on-click #(>evt [:puzzle/pick-chord (:id c)])}
            [:b (sym-label c)] [:small (chord-desc c)]])]])
     (when (> (count choices) 1)
       [:p.hint (tr :ui/pick-both)])]))

(defn result []
  (let [{:keys [correct answer answer-inv ms via]} (<sub [:answer])
        cur (<sub [:current])
        a (t/chord-by-id answer)]
    [:div
     [:div.banner {:class (if correct "good" "bad")}
      (if correct (tr :ui/correct) (tr :ui/not-quite))
      (when-not correct
        (tr :ui/you-said (str (sym-label a)
                              (when (> (count (<sub [:inv-choices])) 1)
                                (str " · " (inv-short answer-inv))))))
      [:span.meta (secs ms) (when (= via :midi) (tr :ui/via-midi))]]
     (when-not correct
       (when-let [{r :root nm :name} (t/same-notes-elsewhere (t/chord-by-id (:chord cur)) (:root cur)
                                                             (:inv cur) a)]
         [:div.banner.insight
          (tr :ui/same-notes nm (t/note-names r) (t/note-names (:root cur)))]))
     [chord-card cur
      [:div.actions
       [:button {:on-click #(>evt [:replay])} (tr :ui/play-it)]
       (when-not correct [:button {:on-click #(>evt [:puzzle/play-answer])} (tr :ui/play-yours)])
       (when-not correct [:button {:on-click #(>evt [:puzzle/compare])} (tr :ui/compare)])
       [:button.primary {:on-click #(>evt [:puzzle/next])} (tr :ui/next)]]]]))

(defn puzzle-view []
  (let [phase (<sub [:phase])
        {:keys [n acc streak]} (<sub [:session])]
    [:section
     [error-banner]
     (if (= :idle phase)
       [:div.start
        [:h2 (tr :ui/puzzle-title)]
        [:p (tr :ui/puzzle-intro (> (count (<sub [:inv-choices])) 1))]
        [:button.primary.big {:on-click #(>evt [:puzzle/next])} (tr :ui/start)]
        [:p.hint (tr :ui/puzzle-keys)]]
       [:div
        [:div.topline
         [root-pill]
         [:span.score (when (pos? n) (str (:correct (<sub [:session])) "/" n " · " (pct acc)
                                          (when (> streak 1) (str " · 🔥" streak))))]]
        (when-let [m (<sub [:midi-msg])] [:div.banner.warn (tr-msg m)])
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
        [:h2 (tr :ui/training-title)]
        [:p (tr :ui/training-intro (:pause s))]
        [:button.primary.big {:on-click #(>evt [:training/start])} (tr :ui/start)]
        [:p.hint (tr :ui/training-keys)]]
       [:div
        [:div.topline [root-pill]]
        (if (= :listening phase)
          [:div.card.mystery
           [:div.chord-name "?"]
           [:div.chord-sub (tr :ui/listen)]
           ^{:key round} [:div.countdown [:div {:style {:animation-duration (str ms "ms")}}]]
           [:div.actions
            [:button {:on-click #(>evt [:training/reveal round])} (tr :ui/reveal)]]]
          [chord-card cur
           [:div.actions
            [:button.good {:disabled (some? graded) :class (when (true? graded) "on")
                           :on-click #(>evt [:training/grade true])} (tr :ui/heard-it)]
            [:button.bad {:disabled (some? graded) :class (when (false? graded) "on")
                          :on-click #(>evt [:training/grade false])} (tr :ui/missed-it)]
            [:button.primary {:on-click #(>evt [:training/next])} (tr :ui/next)]]])
        [:div.actions.center
         (if running
           [:button.ghost {:on-click #(>evt [:training/stop])} (tr :ui/stop)]
           [:button.ghost {:on-click #(>evt [:training/start])} (tr :ui/resume)])
         (when (and running (:auto-advance s)) [:span.hint (tr :ui/auto-advancing)])]])]))

;; ---------------------------------------------------------------- stats

(defn chord-label [id inv]
  (let [c (t/chord-by-id id)]
    (str (sym-label c) (when inv (str " · " (inv-short inv))))))

(defn meter [x]
  [:span.meter [:span {:style {:width (pct (or x 0))}}]])

(defn stat-table [title rows label-fn]
  (when (seq rows)
    [:div.card
     [:h3 title]
     [:table.stats
      [:thead [:tr [:th] [:th "n"] [:th (tr :ui/th-accuracy)] [:th.num (tr :ui/th-last20)] [:th.num (tr :ui/th-avg)]]]
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
     [:h3 (tr :ui/last-14)]
     [:div.daily
      (for [{:keys [day n acc]} days
            :let [d (js/Date. day)
                  label (str (.getDate d) "/" (inc (.getMonth d)))]]
        ^{:key day}
        [:div.day {:title (tr :ui/day-title label n (when acc (pct acc)))}
         [:div.bar-wrap
          (when (pos? n)
            [:div.bar {:style {:height (pct acc) :opacity (+ 0.35 (* 0.65 (/ n maxn)))}}])]
         [:small label]])]
     [:p.hint (tr :ui/daily-hint)]]))

(defn reset-button []
  (let [armed (r/atom false)]
    (fn []
      (if @armed
        [:span
         [:button.bad {:on-click #(do (reset! armed false) (>evt [:stats/reset]))} (tr :ui/really-erase)]
         [:button.ghost {:on-click #(reset! armed false)} (tr :ui/cancel)]]
        [:button.ghost {:on-click #(reset! armed true)} (tr :ui/reset-stats)]))))

(defn stats-view []
  (let [{:keys [n acc avg-ms]} (<sub [:stats/overall])
        conf (<sub [:stats/confusions])]
    [:section
     [:div.stats-filter
      [seg [[:all (tr :ui/filter-all)] [:recognition (tr :ui/filter-recognition)] [:imagine (tr :ui/filter-imagine)]]
       (<sub [:stats-filter]) #(>evt [:stats/filter %])]]
     [:div.tiles
      [:div.tile [:small (tr :ui/answers)] [:b n]]
      [:div.tile [:small (tr :ui/accuracy)] [:b (pct acc)]]
      [:div.tile [:small (tr :ui/avg-time)] [:b (secs avg-ms)]]]
     (if (zero? n)
       [:div.card [:p (tr :ui/no-stats)]]
       [:<>
        [daily-chart]
        [stat-table (tr :ui/by-chord) (<sub [:stats/by-chord]) #(chord-label % nil)]
        [stat-table (tr :ui/by-pair) (<sub [:stats/by-pair]) (fn [[id inv]] (chord-label id inv))]
        (when (seq conf)
          [:div.card
           [:h3 (tr :ui/confusions)]
           [:ul.conf
            (for [{:keys [chord inv answer answer-inv n]} conf]
              ^{:key (str chord inv answer answer-inv)}
              [:li [:b (chord-label chord inv)] (tr :ui/heard-as) [:b (chord-label answer answer-inv)]
               [:span.num (str n "×")]])]])
        [stat-table (tr :ui/by-inv) (<sub [:stats/by-inv]) inv-short]
        [stat-table (tr :ui/by-root) (<sub [:stats/by-root]) #(t/note-names %)]])
     [:div.card
      [:h3 (tr :ui/data)]
      [:p.hint (tr :ui/data-hint)]
      (when-let [f (<sub [:flash])] [:div.banner (tr-msg f)])
      [:div.actions
       [:button {:on-click #(>evt [:stats/export])} (tr :ui/export)]
       [:label.button (tr :ui/import)
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
     [:h3 (tr :ui/chords) [:small (tr :ui/enabled (count enabled))]]
     [:div.presets
      (for [[p ids] t/presets]
        ^{:key p}
        [:button.ghost {:class (when (= (set ids) enabled) "on")
                        :on-click #(>evt [:settings/set-enabled ids])} (tr (keyword "preset" (name p)))])]
     (for [g t/groups
           :let [cs (filter #(= g (:group %)) t/chords)
                 ids (set (map :id cs))
                 all? (every? enabled ids)]]
       ^{:key g}
       [:div.group
        [:div.group-label (tr (keyword "group" (name g)))
         [:button.link {:on-click #(>evt [:settings/set-enabled
                                          (if all? (remove ids enabled) (into enabled ids))])}
          (if all? (tr :ui/none) (tr :ui/all))]]
        [:div.chips
         (for [c cs]
           ^{:key (:id c)}
           [:button.chip {:class (when (enabled (:id c)) "on")
                          :title (str (chord-desc c) ": " (formula c))
                          :on-click #(>evt [:settings/toggle-chord (:id c)])}
            (sym-label c)])]])]))

(defn settings-view []
  (let [s (<sub [:settings])
        setter (fn [k] #(>evt [:settings/set k %]))]
    [:section
     [chord-settings]
     [:div.card
      [:h3 (tr :ui/roots)]
      [seg [[:fixed (tr :ui/root-fixed)] [:naturals (tr :ui/root-naturals)] [:all (tr :ui/root-all)]]
       (:root-mode s) (setter :root-mode)]
      (when (= :fixed (:root-mode s))
        [:div.chips
         (for [pc (range 12)]
           ^{:key pc}
           [:button.chip {:class (when (= pc (:fixed-root s)) "on")
                          :on-click #(>evt [:settings/set :fixed-root pc])}
            (t/note-names pc)])])]
     [:div.card
      [:h3 (tr :ui/inversions)]
      [:div.chips
       (for [i (range 4)]
         ^{:key i}
         [:button.chip {:class (when ((:inversions s) i) "on")
                        :on-click #(>evt [:settings/toggle-inversion i])}
          (inv-short i)])]
      [:p.hint (tr :ui/inv-hint)]]
     [:div.card
      [:h3 (tr :ui/sound)]
      [:div.field [:span (tr :ui/voicing)]
       [seg [[:close (tr :ui/close)] [:spread (tr :ui/spread)]] (:voicing s) (setter :voicing)]]
      [:div.field [:span (tr :ui/playback-style)]
       [seg [[:block (tr :ui/block)] [:arp (tr :ui/arp)] [:arp-block (tr :ui/arp-block)]] (:playback s) (setter :playback)]]
      (when (not= :block (:playback s))
        [slider (tr :ui/arp-speed) (:tempo s) 60 400 10 " bpm" (setter :tempo)])
      [toggle (tr :ui/root-ref) (:root-ref s) (setter :root-ref) (tr :ui/root-ref-hint)]]
     [:div.card
      [:h3 (tr :ui/training)]
      [slider (tr :ui/pause) (:pause s) 0 10 0.5 "s" (setter :pause)]
      [slider (tr :ui/hold) (:hold s) 1 15 0.5 "s" (setter :hold)]
      [toggle (tr :ui/replay-on-reveal) (:replay-on-reveal s) (setter :replay-on-reveal)]
      [toggle (tr :ui/auto-advance) (:auto-advance s) (setter :auto-advance)]]
     [:div.card
      [:h3 (tr :ui/selection)]
      [toggle (tr :ui/adaptive) (:adaptive s) (setter :adaptive) (tr :ui/adaptive-hint)]
      [toggle (tr :ui/imagine-root) (:imagine-root s) (setter :imagine-root) (tr :ui/imagine-root-hint)]]
     [:div.card
      [:h3 (tr :ui/midi-title)]
      [toggle (tr :ui/use-midi) (:midi s) (setter :midi) (tr :ui/use-midi-hint)]
      (when (:midi s)
        [:<>
         [:p.hint (let [{:keys [state inputs]} (<sub [:midi])]
                    (case state
                      :ready (if (seq inputs) (tr :midi/connected (str/join ", " inputs)) (tr :midi/none-long))
                      :unsupported (tr :midi/unsupported-long)
                      :denied (tr :midi/denied-long)
                      (tr :midi/connecting-long)))]
         [toggle (tr :ui/midi-echo) (:midi-echo s) (setter :midi-echo) (tr :ui/midi-echo-hint)]])]]))

;; ---------------------------------------------------------------- imagine

(defn imagine-view []
  (let [phase (<sub [:phase])
        {:keys [chord inv root notes] :as cur} (<sub [:current])
        graded (<sub [:graded])
        c (t/chord-by-id chord)]
    [:section
     [error-banner]
     (if (#{:imagine :imagine-heard} phase)
       [:div
        [:div.topline [:div.root-row
                       [:span.pill (tr :ui/root) [:b (t/note-names root)]]
                       [:button.ghost {:on-click #(>evt [:hear-root])} (tr :ui/hear-root)]]]
        (if (= :imagine phase)
          [:div.card.reveal
           [:div.chord-name (t/chord-name c root inv)]
           [:div.chord-sub (chord-desc c) " · " (inversion-label c inv)]
           [:div.formula (formula c)]
           [:p.imagine-prompt (tr :ui/imagine-prompt)]
           [:div.actions [:button.primary {:on-click #(>evt [:imagine/hear])} (tr :ui/hear-it)]]]
          [chord-card cur
           [:div.actions
            [:button {:on-click #(>evt [:imagine/hear])} (tr :ui/again)]
            [:button.good {:disabled (some? graded) :class (when (true? graded) "on")
                           :on-click #(>evt [:imagine/grade true])} (tr :ui/matched)]
            [:button.bad {:disabled (some? graded) :class (when (false? graded) "on")
                          :on-click #(>evt [:imagine/grade false])} (tr :ui/different)]
            [:button.primary {:on-click #(>evt [:imagine/next])} (tr :ui/next)]]])]
       [:div.start
        [:h2 (tr :ui/imagine-title)]
        [:p (tr :ui/imagine-intro)]
        [:button.primary.big {:on-click #(>evt [:imagine/next])} (tr :ui/start)]
        [:p.hint (tr :ui/imagine-keys)]])]))

;; ---------------------------------------------------------------- sketchpad

(def comp-styles [:whole :charleston :four :arp])

(defn sketch-file []
  (let [sk (<sub [:sketch])
        saved (<sub [:sketches])
        exists? (some #(= (:name sk) (:name %)) saved)]
    [:div.card
     [:div.file-row
      [:input.name {:value (:name sk) :aria-label (tr :ui/sketch-name)
                    :on-change #(>evt [:sketch/set :name (.. % -target -value)])}]
      [:button {:on-click #(>evt [:sketch/save])} (tr :ui/save)]
      [:button.ghost {:on-click #(>evt [:sketch/new])} (tr :ui/new)]
      (when exists? [:button.ghost {:on-click #(>evt [:sketch/delete])} (tr :ui/delete)])]
     (when (seq saved)
       [:div.chips
        (for [{nm :name} saved]
          ^{:key nm}
          [:button.chip {:class (when (= nm (:name sk)) "on") :on-click #(>evt [:sketch/load nm])} nm])])
     (when-let [f (<sub [:flash])] [:p.hint (tr-msg f)])]))

(defn sketch-bars []
  (let [slots (<sub [:sketch/slots])
        pos (:pos (<sub [:sketch-play]))
        sel (<sub [:sketch-sel])]
    (when (seq slots)
      [:div.bars
       (for [[bar items] (group-by :bar (map-indexed #(assoc %2 :i %1) slots))]
         ^{:key bar}
         [:div.bar-cell
          (for [{:keys [i token error]} items]
            ^{:key i}
            [:button.slot {:class [(when error "err") (when (= i pos) "now") (when (= i sel) "sel")]
                           :title (when error (tr :ui/unknown-symbol))
                           :on-click #(>evt [:sketch/select i])}
             token])])])))

(defn sketch-selected []
  (let [slots (<sub [:sketch/slots])
        sel (<sub [:sketch-sel])
        {:keys [chord root bass-note upper error token]} (when sel (get slots sel))]
    (when (and chord (not error))
      (let [c (t/chord-by-id chord)]
        [:div.card
         [:div.chord-name token]
         [:div.chord-sub (chord-desc c)]
         [:div.formula (formula c)]
         [piano {:notes (vec (cons bass-note upper)) :bass bass-note}]]))))

(defn sketch-view []
  (let [sk (<sub [:sketch])
        playing (:playing (<sub [:sketch-play]))
        errors (filter :error (<sub [:sketch/slots]))
        setter (fn [k] #(>evt [:sketch/set k %]))]
    [:section
     [error-banner]
     [sketch-file]
     [:div.card
      [:textarea.prog {:value (:text sk) :rows 4 :spell-check false :auto-capitalize "off"
                       :auto-correct "off" :placeholder "Dm9 | G13 | Cmaj9 | A7alt"
                       :on-change #(>evt [:sketch/set :text (.. % -target -value)])}]
      [:p.hint (tr :ui/sketch-hint)]
      (when (seq errors)
        [:p.hint.err-text (tr :ui/unknown) (str/join ", " (distinct (map :token errors)))])
      [sketch-bars]
      [:div.actions
       (if playing
         [:button.primary {:on-click #(>evt [:sketch/stop])} (tr :ui/stop)]
         [:button.primary {:on-click #(>evt [:sketch/play])} (tr :ui/play)])
       [:button.ghost {:on-click #(>evt [:sketch/transpose -1])} "♭ −1"]
       [:button.ghost {:on-click #(>evt [:sketch/transpose 1])} "♯ +1"]]]
     [sketch-selected]
     [:div.card
      [:h3 (tr :ui/playback)]
      [:div.field [:span (tr :ui/comping)]
       [seg (for [c comp-styles] [c (tr (keyword "ui" (str "comp-" (name c))))]) (:comp sk) (setter :comp)]]
      [slider (tr :ui/tempo) (:tempo sk) 40 220 2 " bpm" (setter :tempo)]
      [:div.field [:span (tr :ui/beats)] [seg [[3 "3"] [4 "4"]] (:beats sk) (setter :beats)]]
      [toggle (tr :ui/bass2) (:bass sk) (setter :bass)]
      [toggle (tr :ui/voice-lead) (:voice-lead sk) (setter :voice-lead) (tr :ui/voice-lead-hint)]
      [toggle (tr :ui/loop) (:loop sk) (setter :loop)]
      [:p.hint (tr :ui/voicing-from)]]]))

;; ---------------------------------------------------------------- app

(def tabs [:training :puzzle :imagine :sketch :stats :settings])

(defn app []
  (let [view (<sub [:view])
        samples (<sub [:samples])]
    [:div.app
     [:header
      [:h1 (tr :ui/title)]
      [:div.header-end
       [midi-badge]
       [:div.lang
        [seg [[:en "EN"] [:el "ΕΛ"]] (:lang (<sub [:settings])) #(>evt [:settings/set :lang %])]]]]
     [:nav
      (for [v tabs]
        ^{:key v}
        [:button {:class (when (= v view) "on") :aria-label (name v)
                  :on-click #(>evt [:set-view v])} (tr (keyword "nav" (name v)))])]
     (when (nil? samples) [:div.banner (tr :ui/loading-samples)])
     (when (zero? samples) [:div.banner.warn (tr :ui/samples-failed)])
     [:main
      (case view
        :training [training-view]
        :puzzle [puzzle-view]
        :imagine [imagine-view]
        :sketch [sketch-view]
        :stats [stats-view]
        :settings [settings-view])]]))
