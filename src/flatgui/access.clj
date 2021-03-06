; Copyright (c) 2015 Denys Lebediev and contributors. All rights reserved.
; The use and distribution terms for this software are covered by the
; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
; which can be found in the file LICENSE at the root of this distribution.
; By using this software in any fashion, you are agreeing to be bound by
; the terms of this license.
; You must not remove this notice, or any other, from this software.

(ns ^{:doc "Namespace that contains functions that help accessing
            components and their states by various criterias."
      :author "Denys Lebediev"}
  flatgui.access
  (:require [flatgui.util.matrix :as m]
            [flatgui.comlogic :as fgc]))


(defn get-any-component
  ([container pred-data pred-data-modifier pred result-postrocessor]
    (if (pred pred-data container)
      (result-postrocessor pred-data container)
      (let [children (fgc/get-children container)
            from-children (map (fn [child] (get-any-component child (pred-data-modifier pred-data container) pred-data-modifier pred result-postrocessor)) children)
            filtered (filter (fn [c] (not (nil? c))) from-children)]
        (first filtered))))
  ([container pred]
    "Returns first component found in given container
     (possibly container itself) for which (pred component)
     returns true."
    (get-any-component container nil (fn [d c] nil) (fn [d c] (pred c)) (fn [d c] c))))

;(defn get-components
;  ([container pred-data pred-data-modifier pred result-postrocessor]
;    (let [ from-children (mapcat (fn [c] (get-components c (pred-data-modifier pred-data container) pred-data-modifier pred result-postrocessor)) (get-children container))]
;      (if (pred pred-data container)
;        (conj from-children (result-postrocessor pred-data container))
;        from-children)))
;  ([container pred]
;  "Returns all found in given container (possibly including
;   container itself) components for which (pred component)
;   returns true."
;  (get-components container nil (fn [d c] nil) (fn [d c] (pred c)) (fn [d c] c))))
;
;
; @todo use below version specifically for getting components at mouse pointer. It is ~20 times faster
;
;
(defn get-components
  ([container pred-data pred-data-modifier pred result-postrocessor]
    (if (pred pred-data container)
      (conj
        (mapcat (fn [c] (get-components
                          c
                          (pred-data-modifier pred-data container) pred-data-modifier pred result-postrocessor))
                (fgc/get-children container))
        (result-postrocessor pred-data container))
      ))
  ([container pred]
    "Returns all found in given container (possibly including
     container itself) components for which (pred component)
     returns true."
    (get-components container nil (fn [d c] nil) (fn [d c] (pred c)) (fn [d c] c))))


(defn- get-top-left [d c]
  (m/mx*
    (m/mx* d (:position-matrix c))
    (m/defpoint 0 0 0)))

(defn- get-btm-right [d c]
  (m/mx*
    (m/mx* d (:position-matrix c))
    (m/defpoint (m/x (:clip-size c)) (m/y (:clip-size c)) 0)))

(defn- position-viewport-translator [d c] (m/mx* d (m/mx* (:position-matrix c) (:viewport-matrix c))))

(defn- mouse-predicate [d c mouse-x mouse-y]
  (let [ c-top-left (get-top-left d c)
         c-btm-right (get-btm-right d c)]
    (and
      (>= mouse-x (m/x c-top-left))
      (>= mouse-y (m/y c-top-left))
      (< mouse-x (m/x c-btm-right))
      (< mouse-y (m/y c-btm-right)))))

(defn- mouse-postprocessor [d c mouse-x mouse-y]
  (let [ c-top-left (get-top-left d c)
         vm (:viewport-matrix c)
         viewport-translate-x (m/mx-get vm 0 3)
         viewport-translate-y (m/mx-get vm 1 3)]
    (assoc
      c
      :mouse-x-relative (- mouse-x (m/x c-top-left) viewport-translate-x)
      :mouse-y-relative (- mouse-y (m/y c-top-left) viewport-translate-y))))

(defn get-components-at [container mouse-x mouse-y]
  (get-components
    container
    m/IDENTITY-MATRIX
    position-viewport-translator
    (fn [d c] (mouse-predicate d c mouse-x mouse-y))
    (fn [d c] (mouse-postprocessor d c mouse-x mouse-y))))

; @todo why is paths-having-visible-popups nil here?
;
;
(defn get-path-to-component [container path-to-target pred-data pred-data-modifier pred result-postrocessor paths-having-visible-popups]
  (let [ container-applies (pred pred-data container)
         target-id-path (fgc/conjv path-to-target (:id container))]
    (if (and (or container-applies (and paths-having-visible-popups (paths-having-visible-popups target-id-path))) (:visible container))
      (let [paths-from-children-all (map
                                      #(get-path-to-component %1 target-id-path (pred-data-modifier pred-data container) pred-data-modifier pred result-postrocessor paths-having-visible-popups)
                                      (fgc/get-children container))
            paths-from-children (filter #(and (not (nil? %1)) (not (empty? %1))) paths-from-children-all)
            path-to-topmost (if (not (empty? paths-from-children))
                               (reduce #(max-key (fn [p] (:z-position (last p))) %1 %2) paths-from-children))]
          (let [ container-postprocessed (result-postrocessor pred-data container)]
            (if path-to-topmost
              (cons container-postprocessed path-to-topmost)
              (if container-applies (list container-postprocessed))))))))

(defn get-mouse-pointed-path [container mouse-x mouse-y]
  (get-path-to-component
    container
    []
    m/IDENTITY-MATRIX
    position-viewport-translator
    (fn [d c] (mouse-predicate d c mouse-x mouse-y))
    (fn [d c] (mouse-postprocessor d c mouse-x mouse-y))
    (:paths-having-visible-popups container)))

; Does not seem to be needed
;(defn get-component-path-by-id-path
;  ([container-wrap path id-path]
;   (if (empty? id-path)
;     path
;     (let [c-id (first id-path)]
;       (get-component-path-by-id-path
;         (:children (c-id container-wrap))
;         (fgc/conjv path (c-id container-wrap))
;         (next id-path)))))
;  ([container id-path] (get-component-path-by-id-path {(:id container) container} [] id-path)))

; Does not seem to be needed
;(defn assign-mouse-rel-coords
;  ([component-path index target-rel-x target-rel-y]
;   (if (neg? index)
;     component-path
;     (let [c (nth component-path index)
;           cpm (:position-matrix c)]
;       (assign-mouse-rel-coords
;         (assoc component-path index (assoc c :mouse-x-relative target-rel-x :mouse-y-relative target-rel-y))
;         (dec index)
;         (+ target-rel-x (m/mx-x cpm))
;         (+ target-rel-y (m/mx-y cpm))))))
;  ([component-path target-rel-x target-rel-y]
;   (assign-mouse-rel-coords component-path (dec (count component-path)) target-rel-x target-rel-y)))

(defn get-ids-from-pointed-path [path]
  (if (nil? path)
    []
    (mapv #(:id %1) path)))

(defn get-mouse-rel-x-from-pointed-path [path]
  (if (nil? path)
    0.0
    (fgc/masknil (:mouse-x-relative (last path)))))

(defn get-mouse-rel-y-from-pointed-path [path]
  (if (nil? path)
    0.0
    (fgc/masknil (:mouse-y-relative (last path)))))

(defn get-mouse-rel-x-from-path [path]
  (if (nil? path)
    (vec (for [c path] 0.0))
    (mapv (fn [c] (fgc/masknil (:mouse-x-relative c))) path)))

(defn get-mouse-rel-y-from-path [path]
  (if (nil? path)
    (vec (for [c path] 0.0))
    (mapv (fn [c] (fgc/masknil (:mouse-y-relative c))) path)))

(defn get-pressed-coord-capture-from-path [path]
  (if (nil? path)
    true
    (if (:no-mouse-press-capturing (last path)) false true)))

;;
;; Keyboard focus
;;

(defn get-focused-path
  ([container path-to-target]
    (let [target-id-path (fgc/conjv path-to-target (:id container))
          focus-state (:focus-state container)
          focus-mode (:mode focus-state)]
      (case focus-mode
        :has-focus target-id-path
        :parent-of-focused (get-focused-path (get (:children container) (:focused-child focus-state)) target-id-path)
        nil)))
  ([container] (get-focused-path container [])))

;;
;; Functions generally related to input channels
;;

;(defn get-path-to-property-list-map-for-channel [container channel]
;  (let [subscribers (:input-channel-subscribers container)]
;    (into {} (filter
;               (fn [[_ v]] (not (nil? v)))
;               (map (fn [[k v]] [k (channel v)]) subscribers)))))