(ns chordtrainer.audio)

(def sample-notes (vec (range 36 97 3)))

(defonce ctx (atom nil))
(defonce out (atom nil))
(defonce buffers (atom {}))
(defonce voices (atom []))

(defn- context []
  (or @ctx
      (let [Ctor (or (.-AudioContext js/window) (.-webkitAudioContext js/window))
            c (Ctor.)
            comp (.createDynamicsCompressor c)
            master (.createGain c)]
        (set! (.. master -gain -value) 0.9)
        (.connect master comp)
        (.connect comp (.-destination c))
        (reset! out master)
        (reset! ctx c))))

(defn install-unlock!
  "iOS only resumes audio inside a user-gesture call stack, so do it synchronously."
  []
  (when-let [session (.-audioSession js/navigator)]
    (set! (.-type session) "playback"))
  (let [unlock (fn []
                 (let [c (context)]
                   (when (not= "running" (.-state c))
                     (.resume c)
                     (let [src (.createBufferSource c)]
                       (set! (.-buffer src) (.createBuffer c 1 1 22050))
                       (.connect src (.-destination c))
                       (.start src 0)))))]
    (doseq [ev ["pointerdown" "touchend" "keydown"]]
      (.addEventListener js/document ev unlock #js {:capture true}))))

(defn load!
  "Fetches and decodes all samples; calls `on-done` with the number loaded."
  [on-done]
  (let [c (context)]
    (-> (js/Promise.all
         (clj->js
          (for [n sample-notes]
            (-> (js/fetch (str "samples/" n ".mp3"))
                (.then #(.arrayBuffer %))
                (.then #(js/Promise. (fn [ok fail] (.decodeAudioData c % ok fail))))
                (.then #(swap! buffers assoc n %))
                (.catch #(js/console.warn "sample failed" n %))))))
        (.then #(on-done (count @buffers))))))

(defn- nearest-sample [midi]
  (apply min-key #(Math/abs (- % midi)) (keys @buffers)))

(defn- play-note! [midi start dur gain]
  (when (seq @buffers)
    (let [c (context)
          s (nearest-sample midi)
          src (.createBufferSource c)
          g (.createGain c)
          end (+ start dur)]
      (set! (.-buffer src) (@buffers s))
      (set! (.. src -playbackRate -value) (Math/pow 2 (/ (- midi s) 12)))
      (.setValueAtTime (.-gain g) gain start)
      (.setValueAtTime (.-gain g) gain end)
      (.exponentialRampToValueAtTime (.-gain g) 0.0001 (+ end 0.6))
      (.connect src g)
      (.connect g @out)
      (.start src start)
      (.stop src (+ end 0.7))
      (swap! voices conj [src g]))))

(defn stop-all! []
  (when-let [c @ctx]
    (let [now (.-currentTime c)]
      (doseq [[src g] @voices]
        (try
          (.cancelScheduledValues (.-gain g) now)
          (.setTargetAtTime (.-gain g) 0 now 0.03)
          (.stop src (+ now 0.2))
          (catch :default _)))))
  (reset! voices []))

(def block-dur 2.2)
(def root-ref-dur 1.2)

(defn- arp-step [tempo] (/ 60 tempo))

(defn timeline
  "Pure schedule of [midi offset-seconds duration] for a chord, and its total length."
  [notes {:keys [playback tempo root-ref]} root-note]
  (let [step (arp-step tempo)
        n (count notes)
        arp-len (* step n)
        lead (if root-ref (+ root-ref-dur 0.3) 0)
        ref-events (when root-ref [[root-note 0 root-ref-dur]])
        arp (fn [t0] (map-indexed (fn [i m] [m (+ t0 (* i step)) (- (+ arp-len 0.4) (* i step))]) notes))
        block (fn [t0] (map (fn [m] [m t0 block-dur]) notes))
        [events len] (case playback
                       :arp [(arp lead) (+ lead arp-len 0.4)]
                       :arp-block [(concat (arp lead) (block (+ lead arp-len 0.6)))
                                   (+ lead arp-len 0.6 block-dur)]
                       [(block lead) (+ lead block-dur)])]
    {:events (concat ref-events events) :length len}))

(defn chain
  "Joins timelines end to end with `gap` seconds between them."
  [timelines gap]
  (reduce (fn [acc {:keys [events length]}]
            (let [t (if (seq (:events acc)) (+ (:length acc) gap) 0)]
              {:events (concat (:events acc) (map (fn [[m off dur]] [m (+ off t) dur]) events))
               :length (+ t length)}))
          {:events [] :length 0}
          timelines))

(defn play-events!
  "`voices` is how many notes sound together; defaults to the distinct pitches in `events`."
  [events & [voices]]
  (let [c (context)
        t0 (+ (.-currentTime c) 0.05)
        gain (/ 0.6 (Math/sqrt (max 1 (or voices (count (distinct (map first events)))))))]
    (doseq [[m off dur] events]
      (play-note! m (+ t0 off) dur gain))))

(defn play-single! [midi dur]
  (play-events! [[midi 0 dur]]))
