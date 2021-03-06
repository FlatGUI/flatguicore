; Copyright (c) 2016 Denys Lebediev and contributors. All rights reserved.
; The use and distribution terms for this software are covered by the
; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
; which can be found in the file LICENSE at the root of this distribution.
; By using this software in any fashion, you are agreeing to be bound by
; the terms of this license.
; You must not remove this notice, or any other, from this software.

(ns flatgui.base
  (:require [clojure.algo.generic.functor :as functor]
            [flatgui.dependency]
            [flatgui.responsefeed])
  (:import (flatgui.core.engine GetPropertyStaticClojureFn GetDynPropertyDynPathClojureFn GetDynPropertyClojureFn GetPropertyDynPathClojureFn)))

(defn- path-argument? [e]
  (or (vector? e) (symbol? e)))

(defn- deprecated-component-sym? [s]
  (= 'component s) )

(defn get-property-call? [form]
  (and
    (= 'get-property (first form))
    (or
      (path-argument? (second form))
      (and (deprecated-component-sym? (second form)) (path-argument? (first (next (next form))))))))

(defn get-reason-call? [form]
  (or
    (= 'get-reason (first form))
    ;; 'fg/get-reason is here for backward compatibility
    (= 'fg/get-reason (first form))))

;;; TODO add get-full-reason: include property also; maybe also actual node instead of wildcard

(def old-val-prefix "old-")

(defn old-value-ref? [e property]
  (and
    (not (nil? property))
    (symbol? e)
    (.startsWith (name e) old-val-prefix)
    (.endsWith (name e) (name property))
    (= (.length (str e)) (+ (.length old-val-prefix) (dec (.length (str property)))))))

(declare replace-gp)
(declare replace-gpv)
(declare replace-gpmap)

(defn- gp-replacer [% property]
  (cond
    (and (seq? %) (get-property-call? %))
    (let [m (meta %)
          % (if (deprecated-component-sym? (second %)) (conj (drop 2 %) (first %)) %) ;backward compatibility
          % (replace-gp % property)
          path-&-prop (next %)
          path (flatgui.dependency/resolve-path-arg % (first path-&-prop))
          dyn-path (some #(not (keyword? %)) path)
          property (last path-&-prop)
          dyn-property (not (keyword? property))
          get-property-fn (cond
                            (and dyn-path dyn-property) (GetDynPropertyDynPathClojureFn.)
                            dyn-path (GetPropertyDynPathClojureFn.)
                            dyn-property (GetDynPropertyClojureFn.)
                            :else (GetPropertyStaticClojureFn.))]
      (with-meta (list get-property-fn path property) m))
    (and (seq? %) (get-reason-call? %))
    (with-meta (list '.getEvolveReason 'component) (meta %))
    (old-value-ref? % property)
    (with-meta (list (keyword (.substring (name %) (.length old-val-prefix))) 'component) (meta %))
    (seq? %) (replace-gp % property)
    (vector? %) (replace-gpv % property)
    (map? %) (replace-gpmap % property)
    :else %))

(defn replace-gp [form property]
  (let [m (meta form)]
    (with-meta (map #(gp-replacer % property) form) m)))

(defn replace-gpv [v property]
  (let [m (meta v)]
    (with-meta (mapv #(gp-replacer % property) v) m)))

(defn replace-gpmap [form property]
  (let [m (meta form)]
    (with-meta (functor/fmap #(gp-replacer % property) form) m)))

;(defn- gen-evolver [body property] (flatgui.core/replace-gp (gp-replacer body property) property))
(defn- gen-evolver [body property] (gp-replacer body property))

(defn with-all-meta [obj m]
  (let [orig-meta (meta obj)]
    (with-meta obj (merge orig-meta m))))

(defn- gen-evolver-decl
  ([fnname property body]
    (let [result (list 'def fnname
                       (list 'flatgui.base/with-all-meta
                             (list 'fn ['component] (gen-evolver body property))
                             (list 'hash-map
                                    :input-channel-dependencies (conj (flatgui.dependency/get-input-dependencies body) 'list)
                                    :relative-dependencies (conj (flatgui.dependency/get-all-dependencies body) 'list))))
          ;_ (println "Generated evolver:\n" result)
          _ (if (= fnname 'r-spinner-evolver)
              (println "Evolver dependencies:\n" (flatgui.dependency/get-all-dependencies body)))
          ]
      result))
  ([property body]
   (gen-evolver-decl (symbol (str (name property) "-evolver")) property body)))

(defmacro defevolverfn [& args] (apply gen-evolver-decl args))

(defmacro accessorfn [body]
  (list 'flatgui.base/with-all-meta
        (list 'fn ['component] (gen-evolver body nil))
        (list 'hash-map
              :input-channel-dependencies (conj (flatgui.dependency/get-input-dependencies body) 'list)
              :relative-dependencies (conj (flatgui.dependency/get-all-dependencies body) 'list))))

(defmacro defaccessorfn [fnname params body]
  (list 'def fnname (list 'flatgui.base/with-all-meta
                          (list 'fn params (gen-evolver body nil))
                          (list 'hash-map
                                :input-channel-dependencies (conj (flatgui.dependency/get-input-dependencies body) 'list)
                                :relative-dependencies (conj (flatgui.dependency/get-all-dependencies body) 'list)))))

;; TODO Deprecated
(defn defroot [container] container)

;;; TODO move below to dedicated namespace(s)

(defn properties-merger [a b]
  (if (and (map? a) (map? b))
    (merge-with properties-merger a b)
    ;; If the latter has no value associated with a key then a value from the former is taken.
    ;; However, the latter has chance to suppress values from former by having explicit nils
    b))

(defmacro defwidget [widget-type dflt-properties & base-widget-types]
  "Creates widget property map and associates it with a symbol."
  `(def
     ~(symbol widget-type)
     (merge-with properties-merger
                 ~@base-widget-types
                 ~dflt-properties
                 {:widget-type ~widget-type})))

;;
;; TODO   For component inheritance, merge-properties should throw exception in case same property found in more than one parent,
;; TODO   and inheriting component does not declare its own value
;;
(defn defcomponent [type id properties & children]
  "Defines component of speficied type, with
   specified id and optionally with child components"
  (let [c (merge-with
            properties-merger
            ;; Do not inherit :skin-key from parent type if :look is defined explicitly
            (if (:look properties) (dissoc type :skin-key) type)
            properties)]
    (merge-with
      properties-merger
      c
      {:children (into {} (for [c children] [(:id c) c]))
       :id id
       :look-vec nil})))


;;;;;
;;;;; borrowed from v0.1.0 flatgui.base
;;;;;

(def date-formatter (java.text.SimpleDateFormat. "MMM d HH:mm:ss.SSS Z"))
(defn- ts [] (.format date-formatter (java.util.Date. (java.lang.System/currentTimeMillis))))

(def log-info? true)
(defn log-info [& msg] (if log-info? (println (conj msg "[FlatGUI info] " (ts)))))

(def log-debug? true)
(defn log-debug [& msg] (if log-debug? (println (conj msg "[FlatGUI debug] " (ts)))))

(def log-warning? true)
(defn log-warning [& msg] (if log-warning? (println (conj msg "[FlatGUI warning] " (ts)))))

(def log-error? true)
(defn log-error [& msg] (if log-error? (println (conj msg "[FlatGUI ERROR] " (ts)))))

(defn log-timestamp [& msg] (log-debug msg " at " (str (java.lang.System/currentTimeMillis))))


(defn get-child-count [comp-property-map] (count (:children comp-property-map)))
