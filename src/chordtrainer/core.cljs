(ns chordtrainer.core
  (:require [reagent.dom.client :as rdc]
            [re-frame.core :as rf]
            [clojure.string :as str]
            [chordtrainer.events]
            [chordtrainer.subs]
            [chordtrainer.views :as views]
            [chordtrainer.audio :as audio]
            [chordtrainer.storage :as storage]))

(defonce root (atom nil))

(defn ^:dev/after-load mount []
  (rf/clear-subscription-cache!)
  (when-not @root
    (reset! root (rdc/create-root (js/document.getElementById "app"))))
  (rdc/render @root [views/app]))

(defn- on-key [e]
  (let [tag (some-> (.-target e) .-tagName str/lower-case)]
    (when-not (or (#{"input" "select" "textarea"} tag) (.-metaKey e) (.-ctrlKey e) (.-altKey e))
      (when (#{" " "Enter" "r" "c" "y" "n"} (.-key e))
        (when (#{" " "Enter"} (.-key e)) (.preventDefault e))
        (rf/dispatch [:key (.-key e)])))))

(defn init []
  (rf/dispatch-sync [:init])
  (storage/load-all! [:settings :attempts :sketch :sketches] #(rf/dispatch [:storage/loaded %]))
  (audio/install-unlock!)
  (audio/load! #(rf/dispatch [:audio/loaded %]))
  (.addEventListener js/document "keydown" on-key)
  (when (and (not ^boolean goog.DEBUG) (.-serviceWorker js/navigator))
    (.register (.-serviceWorker js/navigator) "sw.js"))
  (mount))
