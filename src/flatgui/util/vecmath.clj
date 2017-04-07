; Copyright (c) 2017 Denys Lebediev and contributors. All rights reserved.
; The use and distribution terms for this software are covered by the
; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
; which can be found in the file LICENSE at the root of this distribution.
; By using this software in any fashion, you are agreeing to be bound by
; the terms of this license.
; You must not remove this notice, or any other, from this software.

(ns ^{:doc    "Functions for vetor math operations"
      :author "Denys Lebediev"}
flatgui.util.vecmath
  (:require [flatgui.util.matrix :as m]))


(defn vec-op [op v1 v2]
  (mapv #(op (nth v1 %) (nth v2 %)) (range (count v1))))

(defn vec-comparenum [v1 v2]
  (vec-op
    (fn [a b] (if (> b a) 1 (if (< b a) -1 0)))
    v1
    v2))

(defn mxtransf->vec
  ([mx dim]
   (mapv #(m/mx-get mx % (dec (count mx))) (range dim)))
  ([mx] (mxtransf->vec mx (dec (count mx)))))

(defn -mxtransf+point->vec
  ([mx p dim]
   (mapv #(+ (* -1 (m/mx-get mx % (dec (count mx)))) (m/mx-get p % 0)) (range dim)))
  ([mx p] (-mxtransf+point->vec mx p (dec (count mx)))))

(defn find-subranges [v]
  (let [v-size (count v)]
    (loop [begin 0
           v-rest v
           result []]
      (if (= begin v-size)
        result
        (let [sub-range (take-while (fn [e] (= e (first v-rest))) v-rest)
              sub-range-size (count sub-range)]
          (recur
            (+ begin sub-range-size)
            (take-last (- (count v-rest) (count sub-range)) v-rest)
            (conj result [begin sub-range-size])))))))
