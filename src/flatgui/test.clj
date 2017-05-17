; Copyright (c) 2015 Denys Lebediev and contributors. All rights reserved.
; The use and distribution terms for this software are covered by the
; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
; which can be found in the file LICENSE at the root of this distribution.
; By using this software in any fashion, you are agreeing to be bound by
; the terms of this license.
; You must not remove this notice, or any other, from this software.

(ns flatgui.test
  (:require [flatgui.comlogic :as fgc]
            [flatgui.util.matrix :as m]
            [flatgui.awt :as awt]
            [flatgui.base :as fg]
            [clojure.test :as test])
  (:import (flatgui.core.engine ClojureContainerParser IResultCollector Container)
           (java.awt.event MouseEvent KeyEvent)
           (flatgui.core.engine.ui FGMouseEventParser FGTestAppContainer FGTestMouseEventParser)))

(def dummy-source (java.awt.Container.))

(def wait-attempts 5)

(def wait-interval-millis 1000)

(defn enable-traces-for-failed-tests []
  (defmethod clojure.test/report :fail [m]
    (clojure.test/with-test-out
      (clojure.test/inc-report-counter :fail)
      (println "\nFAIL in" (clojure.test/testing-vars-str m))
      (when (seq clojure.test/*testing-contexts*) (println (clojure.test/testing-contexts-str)))
      (when-let [message (:message m)] (println message))
      (println "expected:" (pr-str (:expected m)))
      (println "  actual:" (pr-str (:actual m)))
      (let [trace (.getStackTrace (Thread/currentThread))]
        (loop [i 0]
          (if (< i (alength trace))
            (do
              (println (.toString (aget trace i)))
              (recur (inc i)))))))))

(defn evolve
  ([container property reason target]
   (let [results (atom {})
         result-collector (reify IResultCollector
                            (appendResult [_this _parentComponentUid, _path, node, newValue]
                              (swap! results (fn [r] (if (= property (.getPropertyId node))
                                                       newValue
                                                       r))))
                            (componentAdded [_this _parentComponentUid _componentUid])
                            (componentRemoved [_this _componentUid])
                            (postProcessAfterEvolveCycle [_this _a _m]))
         container-engine (Container.
                            "flatgui.test"
                            (ClojureContainerParser.)
                            result-collector
                            container)
         _ (.evolve container-engine target reason)]
     @results))
  ([container property reason] (evolve container property reason [:main])))

(defn event-> [container target event] (.evolve container target event))

(defn create-container-from-file [c-path c-ns c-name]
  (FGTestAppContainer/loadSourceCreateAndInit c-path c-ns c-name))

(defn create-container [c-var]
  (if (coll? c-var)
    (let [c (FGTestAppContainer/createAndInit (first c-var))]
      ((second c-var) c)
      c)
    (FGTestAppContainer/createAndInit c-var)))

(defn init-container [c]
  (FGTestAppContainer/init c))

(defn get-property [container target property] (.getProperty container target property))

(defn wait-for-property-pred [container target property pred]
  (let [actual-value (loop [a 0
                            interval 5
                            v (.getProperty container target property)]
                       (if (and (not (pred v)) (< a wait-attempts))
                         (do
                           (fg/log-debug (str "Waiting " interval " millis, attempt " (inc a) " of " wait-attempts))
                           (Thread/sleep interval)
                           (recur
                             (inc a)
                             wait-interval-millis
                             (.getProperty container target property)))
                         v))]
    (test/is (pred actual-value) (str "Failed for actual value was " (if (coll? actual-value) (str "[coll count=" (count actual-value) "] ") "") actual-value))))

(defn wait-for-property [container target property expected-value]
  (wait-for-property-pred container target property (fn [v] (= expected-value v))))

;;
;; Combining test into scenarios
;;

(def standard-interstep-delay-millis 100)

(defn- gen-wait [clause] (list 'Thread/sleep (list '* clause standard-interstep-delay-millis)))

(defn- ensure-size [s size] (map #(if (< % (count s)) (nth s %)) (range size)))

(defn interleave-long [& colls]
  (let [max-size (count (apply max-key count colls))]
    (filter #(not (nil? %)) (apply interleave (map (fn [c] (ensure-size c max-size)) colls)))))

(declare clause-processor)

(defn clause-processor [clause]
  (cond

    (number? clause)
    (gen-wait clause)

    (and (seq? clause) (= 'inter (first clause)))
    (map clause-processor (apply interleave-long (rest clause)))

    :else clause))

(defmacro defscenario [scenario-name container-var-coll & clauses]
  (let [scenario (map clause-processor clauses)]
    (list 'clojure.test/deftest scenario-name
          (concat (list 'let ['containers (list 'mapv (list 'fn ['cv] (list 'flatgui.test/create-container 'cv)) container-var-coll)
                              'container (list 'nth 'containers 0)]) scenario))))

;;
;; Mouse
;;

(defn mouse-event
  ([id modifiers click-count button]
   (FGMouseEventParser/deriveFGEvent
     (MouseEvent. dummy-source id 0 modifiers 0 0 0 0 click-count false button)
     0 0))
  ([id modifiers click-count button x y]
   (let [xint (FGTestMouseEventParser/doubleToInt x)
         yint (FGTestMouseEventParser/doubleToInt y)]
     (MouseEvent. dummy-source id 0 modifiers xint yint xint yint click-count false button))))

(defn mouse-left
  ([id] (mouse-event id MouseEvent/BUTTON1_DOWN_MASK 1 MouseEvent/BUTTON1))
  ([id x y] (mouse-event id MouseEvent/BUTTON1_DOWN_MASK 1 MouseEvent/BUTTON1 x y)))

(def left-click-events
  [;TODO (move-mouse-to container target)
   (mouse-left MouseEvent/MOUSE_PRESSED)
   (mouse-left MouseEvent/MOUSE_RELEASED)
   (mouse-left MouseEvent/MOUSE_CLICKED)])

(defn create-left-click-events [x y]
  [;TODO (move-mouse-to container target)
   (mouse-left MouseEvent/MOUSE_PRESSED x y)
   (mouse-left MouseEvent/MOUSE_RELEASED x y)
   (mouse-left MouseEvent/MOUSE_CLICKED x y)])

(defn left-click
  ([container target] (.evolve container target left-click-events))
  ([container x y] (.evolve container (create-left-click-events x y))))

;;
;; Keyboard
;;

(defn key-event [id key-code char-code]
  (KeyEvent. dummy-source id 0 0 (if (= id KeyEvent/KEY_TYPED) KeyEvent/VK_UNDEFINED key-code) (char char-code)))

(defn create-key-type-events
  ([key-code char-code]
   [(key-event KeyEvent/KEY_PRESSED key-code char-code)
    (key-event KeyEvent/KEY_TYPED key-code char-code)
    (key-event KeyEvent/KEY_RELEASED key-code char-code)])
  ([key-code char-code cnt]
    (loop [i 0
           e []]
      (if (< i cnt)
        (recur
          (inc i)
          (vec (concat e (create-key-type-events key-code char-code))))
        e))))

(defn create-string-type-events [str]
  (let [len (.length str)]
    (loop [i 0
           events nil]
      (if (< i len)
        (recur
          (inc i)
          (concat events (create-key-type-events (int (.charAt str i)) (.charAt str i))))
        events))))

(defn type-string
  ([container target str] (.evolve container target (create-string-type-events str)))
  ([container str] (.evolve container (create-string-type-events str))))

(defn type-key
  ([container target key-code char-code cnt] (.evolve container target (create-key-type-events key-code char-code cnt)))
  ([container key-code char-code cnt] (.evolve container (create-key-type-events key-code char-code cnt))))


;;;
;;; Utilities for working with components
;;;

;;
;; Table (flatgui.widgets.table2.table)
;;

(defn wait-table-cell-id [container table-path coord]
  (wait-for-property-pred container table-path :in-use-model
                          (fn [v] (let [sc->id (:screen-coord->cell-id v)]
                                    (get sc->id coord)))))

(defn wait-table-model-coords-shown [container table-path coords]
  (wait-for-property-pred container table-path :in-use-model
                              (fn [v] (let [sc->id (:screen-coord->cell-id v)]
                                        (= (set coords) (set (map (fn [[k _cid]] k) sc->id)))))))

(defn wait-table-cell-property [container table-path coord property pred]
  (let [cid (wait-table-cell-id container table-path coord)]
    (wait-for-property-pred container (conj table-path cid) property pred)))