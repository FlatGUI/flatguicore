; Copyright (c) 2015 Denys Lebediev and contributors. All rights reserved.
; The use and distribution terms for this software are covered by the
; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
; which can be found in the file LICENSE at the root of this distribution.
; By using this software in any fashion, you are agreeing to be bound by
; the terms of this license.
; You must not remove this notice, or any other, from this software.

(ns flatgui.core-test
  (:require [clojure.test :as test]
            [flatgui.base :as core]
            [flatgui.dependency]
            [flatgui.paint :as fgp]
            [flatgui.awt :as awt]
            [flatgui.util.matrix :as m]
            [flatgui.inputchannels.mouse :as mouse]
            [flatgui.inputchannels.mousewheel :as mousewheel]
            [flatgui.inputchannels.keyboard :as keyboard]
            [flatgui.inputchannels.host :as host]
            [flatgui.inputchannels.clipboard :as clipboard]
            [flatgui.inputchannels.timer :as timer])
  (:import (flatgui.core.engine IResultCollector Container ClojureContainerParser)
           (flatgui.core.engine.ui FGAppContainer FGAWTAppContainer)
           (flatgui.core.awt FGAWTInteropUtil)
           (java.util ArrayList)
           (java.util.function Consumer)
           (java.awt.geom AffineTransform)
           (flatgui.core IFGEvolveConsumer)
           (java.util.concurrent.locks ReentrantLock)
           (java.util.concurrent TimeUnit)))

(test/deftest get-property-call?-test
  (test/is (true? (core/get-property-call? (list 'get-property [:a :b] :c))))
  (test/is (true? (core/get-property-call? (list 'get-property 'component [:a :b] :c))))
  (test/is (false? (core/get-property-call? (list 'component [:a :b] :c)))))

(test/deftest init-&-evolve-test
  (let [_ (core/defevolverfn evolver-c1-a :a (let [src (get-property [] :src)] (inc src)))
        _ (core/defevolverfn evolver-c2-b :b (+
                                            (:d component)
                                            (get-property [:this] :c)
                                            (get-property [] :src)))
        _ (core/defevolverfn evolver-res :res (*
                                           (get-property [:this :c1] :a)
                                           (get-property [:this :c2] :b)))
        _ (core/defevolverfn evolver-c2-d :d (if (not (nil? (get-reason)))
                                            (+ (:d component) (:x (get-reason)))
                                            (:d component)))
        container (core/defroot
                    {:id :main
                     :src 1
                     :res nil
                     :evolvers {:res evolver-res} ; (* 2 8)
                     :children {:c1 {:id :c1
                                     :a 0
                                     :evolvers {:a evolver-c1-a}} ;2
                                :c2 {:id :c2
                                     :b 0
                                     :c 2
                                     :d 5
                                     :evolvers {:b evolver-c2-b ; (+ 5 2 1)
                                                :d evolver-c2-d}}}})
        results (atom {})
        result-collector (proxy [IResultCollector] []
                           (appendResult [_parentComponentUid, path, node, newValue]
                             (swap! results (fn [r]
                                              (if (not (or (= :children (.getPropertyId node)) (= :evolvers (.getPropertyId node))))
                                                (assoc r [path (.getPropertyId node)] newValue)
                                                r)))
                             )
                           (componentAdded [_parentComponentUid _componentUid])
                           (postProcessAfterEvolveCycle [_a _m]))
        container-engine (Container.
                           "init-&-evolve-test"
                           (ClojureContainerParser.)
                           result-collector
                           container)
        init-res (get @results [[:main] :res])
        init-a (get @results [[:main :c1] :a])
        init-b (get @results [[:main :c2] :b])
        _ (.evolve container-engine [:main :c2] {:x 10})]
    (test/is (= 16 init-res))
    (test/is (= 2 init-a))
    (test/is (= 8 init-b))
    (test/is (= 36 (get @results [[:main] :res])))
    (test/is (= 2 (get @results [[:main :c1] :a])))
    (test/is (= 18 (get @results [[:main :c2] :b])))
    (test/is (= 15 (get @results [[:main :c2] :d])))))

(test/deftest init-&-evolve-test2
  (let [_ (core/defevolverfn :z-position
                             (let [pz {:a (get-property component [] :z-position)}]
                               (+ (:a pz) 1)))
        container (core/defroot
                    {:id :main
                     :z-position 1
                     :children {:c1 {:id :c1
                                     :popup false
                                     :z-position nil
                                     :evolvers {:z-position z-position-evolver}}}})
        results (atom {})
        result-collector (proxy [IResultCollector] []
                           (appendResult [_parentComponentUid, path, node, newValue]
                             (swap! results (fn [r]
                                              (if (not (or (= :children (.getPropertyId node)) (= :evolvers (.getPropertyId node))))
                                                (assoc r [path (.getPropertyId node)] newValue)
                                                r)))
                             )
                           (componentAdded [_parentComponentUid _componentUid])
                           (postProcessAfterEvolveCycle [_a _m]))
        _container-engine (Container.
                            "init-&-evolve-test2"
                            (ClojureContainerParser.)
                            result-collector
                            container)
        z-res (get @results [[:main :c1] :z-position])]
    (test/is (= 2 z-res))))

(test/deftest init-&-evolve-test3
  (let [_ (core/defevolverfn evolver-res :res (if (= [:this :c2] (get-reason))
                                                (get-property [:this :c2] :b)
                                                -1))
        _ (core/defevolverfn evolver-c2-b :b (* 3 old-b))
        container (core/defroot
                    {:id :main
                     :res nil
                     :evolvers {:res evolver-res}
                     :children {:c1 {:id :c1
                                     :a 5}
                                :c2 {:id :c2
                                     :b 3
                                     :evolvers {:b evolver-c2-b}}}})
        results (atom {})
        result-collector (proxy [IResultCollector] []
                           (appendResult [_parentComponentUid, _path, node, newValue]
                             (swap! results (fn [r]
                                              (if (not (or (= :children (.getPropertyId node)) (= :evolvers (.getPropertyId node))))
                                                (assoc r (.getPropertyId node) newValue)
                                                r)))
                             )
                           (componentAdded [_parentComponentUid _componentUid])
                           (postProcessAfterEvolveCycle [_a _m]))
        container-engine (Container.
                           "init-&-evolve-test3"
                           (ClojureContainerParser.)
                           result-collector
                           container)
        _ (.evolve container-engine [:main :c2] {})]
    (test/is (= 27 (get @results :res)))))

(test/deftest init-&-evolve-test4
  (let [_ (core/defevolverfn evolver-res :res (+
                                                (get-property [:main :c1] :a)
                                                (get-property [:this :c2] :b))) ; Both :this and actual id (:main) work
        container (core/defroot
                    {:id :main
                     :res nil
                     :evolvers {:res evolver-res}
                     :children {:c1 {:id :c1
                                     :a 5}
                                :c2 {:id :c2
                                     :b 3}}})
        results (atom {})
        result-collector (proxy [IResultCollector] []
                           (appendResult [_parentComponentUid, _path, node, newValue]
                             (swap! results (fn [r]
                                              (if (not (or (= :children (.getPropertyId node)) (= :evolvers (.getPropertyId node))))
                                                (assoc r (.getPropertyId node) newValue)
                                                r)))
                             )
                           (componentAdded [_parentComponentUid _componentUid])
                           (postProcessAfterEvolveCycle [_a _m]))
        container-engine (Container.
                           "init-&-evolve-test4"
                           (ClojureContainerParser.)
                           result-collector
                           container)
        _ (.evolve container-engine [:main] {})]
    (test/is (= 8 (get @results :res)))))

(defn- get-cmpnd-key [m k]
  (second (first (filter (fn [[mk _mv]] (= mk k)) m))))

(test/deftest init-&-evolve-test-non-const-path
  (let [_ (core/defevolverfn evolver-a :a (+ 1 (get-property [:this] :b) (if (map? (get-reason)) (:x (get-reason)) 0)))
        _ (core/defevolverfn evolver-res :res (if (map? (get-reason))
                                                (get-property [:this (:x (get-reason))] :a)
                                                old-res))
        _ (core/defevolverfn :dep-test (let [;synthetically create dependency on both [:main :c1 :a] and [:main :c2 :a]
                                             _a :c1
                                             _b (get-property [:this _a] :a)]
                                         (conj old-dep-test (get-reason))))
        container (core/defroot
                    {:id :main
                     :src 1
                     :res 0
                     :dep-test #{}
                     :evolvers {:res evolver-res
                                :dep-test dep-test-evolver
                                }
                     :children {:c1 {:id :c1
                                     :a 0
                                     :b 1
                                     :evolvers {:a evolver-a}}
                                :c2 {:id :c2
                                     :a 0
                                     :b 2
                                     :evolvers {:a evolver-a}}}})
        results (atom {})
        result-collector (proxy [IResultCollector] []
                           (appendResult [_parentComponentUid, path, node, newValue]
                             (swap! results (fn [r]
                                              (if (not (or (= :children (.getPropertyId node)) (= :evolvers (.getPropertyId node))))
                                                (assoc r [path (.getPropertyId node)] newValue)
                                                r)))
                             )
                           (componentAdded [_parentComponentUid _componentUid])
                           (postProcessAfterEvolveCycle [_a _m]))
        container-engine (Container.
                           "init-&-evolve-test-non-const-path"
                           (ClojureContainerParser.)
                           result-collector
                           container)
        _ (.evolve container-engine [:main :c1] {:x 1})
        _ (.evolve container-engine [:main :c2] {:x 2})
        _ (.evolve container-engine [:main] {:x :c2})
        result-map @results
        result-fn (fn [e] (if (nil? e) e (str e)))
        dep-test-results (set (map result-fn (get-cmpnd-key result-map [[:main] :dep-test])))]
    (test/is (= 5 (get-cmpnd-key result-map [[:main] :res])))
    (test/is (= 3 (get-cmpnd-key result-map [[:main :c1] :a])))
    (test/is (= 5 (get-cmpnd-key result-map [[:main :c2] :a])))
    (test/is (= 4 (count dep-test-results)))
    (test/is (= true (contains? dep-test-results "[:this :c1]")))
    (test/is (= true (contains? dep-test-results "[:this :c2]")))
    (test/is (= true (contains? dep-test-results "{:x :c2}")))
    (test/is (= true (contains? dep-test-results nil)))
    ))

(test/deftest init-&-evolve-test-non-const-path2
  (let [_ (core/defevolverfn evolver-res :res (let [child-list (list :c1 :c2)
                                                    propetry-list-1 (list :x :y)]
                                                (+
                                                  (apply + (map #(get-property [:this %] :a) child-list))
                                                  (apply + (map #(get-property [:this] %) propetry-list-1))
                                                  (apply + (map #(get-property [:this %] %) child-list)))))
        container (core/defroot
                    {:id :main
                     :src 1
                     :res nil
                     :x 11
                     :y 22
                     :evolvers {:res evolver-res}
                     :children {:c1 {:id :c1
                                     :a 5
                                     :c1 100}
                                :c2 {:id :c2
                                     :a 3
                                     :c2 200}}})
        results (atom {})
        result-collector (proxy [IResultCollector] []
                           (appendResult [_parentComponentUid, _path, node, newValue]
                             (swap! results (fn [r]
                                              (if (not (or (= :children (.getPropertyId node)) (= :evolvers (.getPropertyId node))))
                                                (assoc r (.getPropertyId node) newValue)
                                                r)))
                             )
                           (componentAdded [_parentComponentUid _componentUid])
                           (postProcessAfterEvolveCycle [_a _m]))
        container-engine (Container.
                           "init-&-evolve-test-non-const-path2"
                           (ClojureContainerParser.)
                           result-collector
                           container)
        _ (.evolve container-engine [:main] {})]
    (test/is (= (+ 5 3 11 22 100 200) (get @results :res)))))

(def init-&-evolve-test-non-vec-path-p1 [:this])
(test/deftest init-&-evolve-test-non-vec-path
  (let [p2 [:this :c1]
        p3 [init-&-evolve-test-non-vec-path-p1 :c1]  ;Here nested vector in vector will be flattened
        _ (core/defevolverfn evolver-res :res
                             (+ (get-property init-&-evolve-test-non-vec-path-p1 :x)
                                (get-property p2 :a)
                                (get-property p3 :b)))
        _ (core/defevolverfn :a (if-let [a (:a (get-reason))] a old-a))
        container (core/defroot
                    {:id :main
                     :res nil
                     :x 11
                     :evolvers {:res evolver-res}
                     :children {:c1 {:id :c1
                                     :a 1
                                     :b 2
                                     :evolvers {:a a-evolver}}}})
        results (atom {})
        result-collector (proxy [IResultCollector] []
                           (appendResult [_parentComponentUid, _path, node, newValue]
                             (swap! results (fn [r]
                                              (if (not (or (= :children (.getPropertyId node)) (= :evolvers (.getPropertyId node))))
                                                (assoc r (.getPropertyId node) newValue)
                                                r)))
                             )
                           (componentAdded [_parentComponentUid _componentUid])
                           (postProcessAfterEvolveCycle [_a _m]))
        container-engine (Container.
                           "init-&-evolve-test-non-vec-path"
                           (ClojureContainerParser.)
                           result-collector
                           container)
        _ (.evolve container-engine [:main :c1] {:a 5})]
    (test/is (= (+ 11 5 2) (get @results :res)))))

(test/deftest accessor-call-test
  (let [_ (core/defaccessorfn tfn [x] (+ x 2 (:a {:a (first [x 1 2])})))
        _ (core/defevolverfn :a (if (not (nil? (get-reason)))
                                  (+ (:x (get-reason)) (tfn 3))
                                  old-a))
        container (core/defroot
                    {:id :main
                     :a 0
                     :evolvers {:a a-evolver}})
        results (atom {})
        result-collector (proxy [IResultCollector] []
                           (appendResult [_parentComponentUid, path, node, newValue]
                             (swap! results (fn [r]
                                              (if (not (or (= :children (.getPropertyId node)) (= :evolvers (.getPropertyId node))))
                                                (assoc r [path (.getPropertyId node)] newValue)
                                                r)))
                             )
                           (componentAdded [_parentComponentUid _componentUid])
                           (postProcessAfterEvolveCycle [_a _m]))
        container-engine (Container.
                           "accessor-call-test"
                            (ClojureContainerParser.)
                            result-collector
                            container)
        _ (.evolve container-engine [:main] {:x 1})]
    (test/is (= 9 (get @results [[:main] :a])))))

(test/deftest evolver-call-test
  (let [_ (core/defevolverfn tfn :a (+
                                      (get (get-reason) :y)
                                      2
                                      (:a {:a (first [(get (get-reason) :y) 1 2])})
                                      (get-property [:this] :t)))
        _ (core/defevolverfn :a (if (not (nil? (get-reason)))
                                  (+ (:x (get-reason)) (tfn component))
                                  old-a))
        container (core/defroot
                    {:id :main
                     :a 0
                     :t 3
                     :evolvers {:a a-evolver}})
        results (atom {})
        result-collector (proxy [IResultCollector] []
                           (appendResult [_parentComponentUid, path, node, newValue]
                             (swap! results (fn [r]
                                              (if (not (or (= :children (.getPropertyId node)) (= :evolvers (.getPropertyId node))))
                                                (assoc r [path (.getPropertyId node)] newValue)
                                                r)))
                             )
                           (componentAdded [_parentComponentUid _componentUid])
                           (postProcessAfterEvolveCycle [_a _m]))
        container-engine (Container.
                           "evolver-call-test"
                           (ClojureContainerParser.)
                           result-collector
                           container)
        _ (.evolve container-engine [:main] {:x 1 :y 3})]
    (test/is (= 12 (get @results [[:main] :a])))))

(test/deftest rebuild-look-test
  (let [_ (core/defevolverfn :a (* (get-property [:this] :b) 2))
        _ (fgp/deflookfn test-look (:a) (awt/fillRect 0 0 a a))
        container (core/defroot
                    {:id :main
                     :a nil
                     :b 2
                     :popup false
                     :look test-look
                     :look-vec []
                     :position-matrix nil
                     :viewport-matrix nil
                     :clip-size nil
                     :evolvers {:a a-evolver}})
        ui-app (FGAppContainer. "c1" container (FGAWTInteropUtil. 64))
        _ (.initialize ui-app)
        container-accessor (.getContainerAccessor ui-app)]
    (test/is (= [["fillRect" 0 0 4 4]] (.get (.getComponent container-accessor 0) :look-vec)))))

(test/deftest paint-all-test
  (let [_ (core/defevolverfn :a (* (get-property [:this] :b) 2))
        _ (fgp/deflookfn test-look (:a) (awt/fillRect 0 0 a a))
        _ (fgp/deflookfn child-look () (awt/fillRect 0 0 1 1))
        container (core/defroot
                    {:id :main
                     :a nil
                     :b 2
                     :popup false
                     :look test-look
                     :look-vec []
                     :position-matrix (m/translation 2 1)
                     :viewport-matrix m/IDENTITY-MATRIX
                     :clip-size (m/defpoint 10 10)
                     :visible true
                     :evolvers {:a a-evolver}
                     :children {:c1 {:id :c1
                                     :popup false
                                     :look child-look
                                     :look-vec []
                                     :clip-size (m/defpoint 5 5)
                                     :position-matrix (m/translation 3 2)
                                     :viewport-matrix m/IDENTITY-MATRIX
                                     :visible 1}}})
        ui-app (FGAWTAppContainer. container 1)
        _ (.initialize ui-app)
        ;container-accessor (.getContainerAccessor ui-app)
        paint-all-vec (ArrayList.)
        primitive-painter (proxy [Consumer] []
                            (accept [look-vec] (.add paint-all-vec (vec look-vec))))
        _ (.paintAllFromRoot ui-app primitive-painter)]
    (test/is (= [["pushCurrentClip"]
                 ["transform" (AffineTransform. 1.0 0.0 0.0 1.0 2.0 1.0)]
                 ["clipRect" 0 0 10.0 10.0]
                 ["transform" (AffineTransform. 1.0 0.0 0.0 1.0 0.0 0.0)]
                 [["fillRect" 0 0 4 4]]
                     ["pushCurrentClip"]
                     ["transform" (AffineTransform. 1.0 0.0 0.0 1.0 3.0 2.0)]
                     ["clipRect" 0 0 5.0 5.0]
                     ["transform" (AffineTransform. 1.0 0.0 0.0 1.0 0.0 0.0)]
                     [["fillRect" 0 0 1 1]]
                     ["transform" (AffineTransform. 1.0 0.0 0.0 1.0 0.0 0.0)]
                     ["transform" (.createInverse (AffineTransform. 1.0 0.0 0.0 1.0 3.0 2.0))]
                     ["popCurrentClip"]
                 ["transform" (AffineTransform. 1.0 0.0 0.0 1.0 0.0 0.0)]
                 ["transform" (.createInverse (AffineTransform. 1.0 0.0 0.0 1.0 2.0 1.0))]
                 ["popCurrentClip"]] paint-all-vec))))

(test/deftest add-children-test
  (let [_ (core/defevolverfn evolver-res :res (if (= (get-reason) {:do :res})
                                                (let [child-list (list :c1 :c2 :c3)]
                                                  (apply + (map
                                                             (fn [e] (nil? e) 0 e)
                                                             (map #(get-property [:this %] :a) child-list))))
                                                old-res))
        _ (core/defevolverfn :children (if (= (get-reason) {:do :children})
                                         (let [c1 (:c1 old-children)]
                                           (assoc
                                             old-children
                                             :c2 (assoc c1 :id :c2 :a 6)
                                             :c3 (assoc c1 :id :c3 :a 7)))
                                         old-children))
        container (core/defroot
                    {:id :main
                     :src 1
                     :res nil
                     :x 11
                     :y 22
                     :evolvers {:res evolver-res
                                :children children-evolver}
                     :children {:c1 {:id :c1
                                     :a 5}}})
        results (atom {})
        result-collector (proxy [IResultCollector] []
                           (appendResult [_parentComponentUid, _path, node, newValue]
                             (swap! results (fn [r]
                                              (if (not (or (= :children (.getPropertyId node)) (= :evolvers (.getPropertyId node))))
                                                (assoc r (.getPropertyId node) newValue)
                                                r)))
                             )
                           (componentAdded [_parentComponentUid _componentUid])
                           (postProcessAfterEvolveCycle [_a _m]))
        container-engine (Container.
                           "add-children-test"
                           (ClojureContainerParser.)
                           result-collector
                           container)
        _ (.evolve container-engine [:main] {:do :children})
        _ (.evolve container-engine [:main] {:do :res})]
    (test/is (= (+ 5 6 7) (get @results :res)))))

(test/deftest add-children-test1
  (let [_ (core/defevolverfn evolver-res :res (if (= (get-reason) {:do :res})
                                                (let [child-list (list :c1 :c2 :c3)]
                                                  (apply + (map
                                                             (fn [e] (nil? e) 0 e)
                                                             (map #(get-property [:this %] :a) child-list))))
                                                old-res))
        _ (core/defevolverfn :children (if (= (get-reason) {:do :children})
                                         (let [c1 (:c1 old-children)]
                                           (assoc
                                             old-children
                                             :c2 (assoc c1 :id :c2 :a 6)
                                             :c3 (assoc c1 :id :c3 :a 7)))
                                         old-children))
        container (core/defroot
                    {:id :main
                     :src 1
                     :res nil
                     :x 11
                     :y 22
                     :evolvers {:res evolver-res
                                :children children-evolver}
                     :children {:c1 {:id :c1
                                     :a 5}}})
        results (atom {})
        result-collector (proxy [IResultCollector] []
                           (appendResult [_parentComponentUid, _path, node, newValue]
                             (swap! results (fn [r]
                                              (if (not (or (= :children (.getPropertyId node)) (= :evolvers (.getPropertyId node))))
                                                (assoc r (.getPropertyId node) newValue)
                                                r)))
                             )
                           (componentAdded [_parentComponentUid _componentUid])
                           (postProcessAfterEvolveCycle [_a _m]))
        container-engine (Container.
                           "add-children-test1"
                           (ClojureContainerParser.)
                           result-collector
                           container)
        _ (.evolve container-engine [:main] {:do :children})
        _ (.evolve container-engine [:main] {:do :res})]
    (test/is (= (+ 5 6 7) (get @results :res)))))

(test/deftest add-children-dependency-test
  (let [_ (core/defevolverfn evolver-res :res (if (= (count (get-property [:this] :children)) 3)          ;(and (vector? (get-reason)) (= (count (get-reason)) 2))
                                                (let [child-list (list :c1 :c2 :c3)]
                                                  (apply + (map
                                                             (fn [e] (nil? e) 0 e)
                                                             (map #(get-property [:this %] :a) child-list))))
                                                old-res))
        _ (core/defevolverfn :children (if (= (get-reason) {:do :children})
                                         (let [c1 (:c1 old-children)]
                                           (assoc
                                             old-children
                                             :c2 (assoc c1 :id :c2 :a 6)
                                             :c3 (assoc c1 :id :c3 :a 7)))
                                         old-children))
        _ (core/defevolverfn :a (if (= (get-reason) {:do :a})
                                  (inc old-a)
                                  old-a))
        container (core/defroot
                    {:id :main
                     :src 1
                     :res nil
                     :x 11
                     :y 22
                     :evolvers {:res evolver-res
                                :children children-evolver}
                     :children {:c1 {:id :c1
                                     :a 5
                                     :evolvers {:a a-evolver}}}})
        results (atom {})
        result-collector (proxy [IResultCollector] []
                           (appendResult [_parentComponentUid, _path, node, newValue]
                             (swap! results (fn [r]
                                              (if (not (or (= :children (.getPropertyId node)) (= :evolvers (.getPropertyId node))))
                                                (assoc r (.getPropertyId node) newValue)
                                                r)))
                             )
                           (componentAdded [_parentComponentUid _componentUid])
                           (postProcessAfterEvolveCycle [_a _m]))
        container-engine (Container.
                           "add-children-dependency-test"
                           (ClojureContainerParser.)
                           result-collector
                           container)
        _ (.evolve container-engine [:main] {:do :children})
        _ (.evolve container-engine [:main :c3] {:do :a})]
    (test/is (= (+ 5 6 7 1) (get @results :res)))))

(test/deftest change-remove-children-test
  (let [_ (core/defevolverfn evolver-res :res (if (and (vector? (get-reason)) (= (count (get-reason)) 2))
                                                (let [c-id (second (get-reason))]
                                                  (assoc old-res c-id (get-property [:this c-id] :a)))
                                                old-res))
        _ (core/defevolverfn :children (if (and (map? (get-reason)) (= :children (:do (get-reason))))
                                         (let [c1 (:c1 old-children)
                                               cmd (:cmd (get-reason))]
                                           (cond
                                             (= :add cmd)
                                             (assoc
                                               old-children
                                               :c2 (assoc c1 :id :c2 :a "c2_1")
                                               :c3 (assoc c1 :id :c3 :a "c3_1"))
                                             (= :change cmd)
                                             (assoc-in old-children [:c2 :a] "c2_2")
                                             :else
                                             (dissoc old-children :c3)))
                                         old-children))
        container (core/defroot
                    {:id :main
                     :src 1
                     :res {}
                     :x 11
                     :y 22
                     :evolvers {:res evolver-res
                                :children children-evolver}
                     :children {:c1 {:id :c1
                                     :a "c1_1"}}})
        results (atom {})
        removed-res (atom #{})
        result-collector (proxy [IResultCollector] []
                           (appendResult [_parentComponentUid, _path, node, newValue]
                             (swap! results (fn [r] (assoc r (.getPropertyId node) newValue)))
                             )
                           (componentAdded [_parentComponentUid _componentUid])
                           (componentRemoved [componentUid] (swap! removed-res (fn [r] (conj r componentUid))))
                           (postProcessAfterEvolveCycle [_a _m]))
        container-engine (Container.
                           "change-remove-children-test"
                           (ClojureContainerParser.)
                           result-collector
                           container)
        _ (.evolve container-engine [:main] {:do :children :cmd :add})
        _ (.evolve container-engine [:main] {:do :children :cmd :change})
        _ (.evolve container-engine [:main] {:do :children})]
    (test/is (= {:c1 "c1_1" :c2 "c2_2" :c3 "c3_1"} (get @results :res)))
    (test/is (= 2 (count (get @results :children))))
    (test/is (= 2 (count @removed-res)))))

(test/deftest change-remove-children-test1
  (let [_ (core/defevolverfn evolver-res :res (if (and (vector? (get-reason)) (= (count (get-reason)) 2))
                                                (let [c-id (second (get-reason))]
                                                  (assoc old-res c-id (get-property [:this c-id] :a)))
                                                old-res))
        _ (core/defevolverfn :children (if (and (map? (get-reason)) (= :children (:do (get-reason))))
                                         (let [c1 (:c1 old-children)
                                               cmd (:cmd (get-reason))]
                                           (cond
                                             (= :add cmd)
                                             (assoc
                                               old-children
                                               :c2 (assoc c1 :id :c2 :a "c2_1")
                                               :c3 (assoc c1 :id :c3 :a "c3_1"))
                                             (= :change cmd)
                                             (assoc-in old-children [:c2 :a] "c2_2")
                                             :else
                                             (dissoc old-children :c3)))
                                         old-children))
        container (core/defroot
                    {:id :main
                     :src 1
                     :res {}
                     :x 11
                     :y 22
                     :evolvers {:res evolver-res
                                :children children-evolver}
                     :children {:c1 {:id :c1
                                     :a "c1_1"}}})
        results (atom {})
        removed-res (atom #{})
        result-collector (proxy [IResultCollector] []
                           (appendResult [_parentComponentUid, _path, node, newValue]
                             (swap! results (fn [r] (assoc r (.getPropertyId node) newValue)))
                             )
                           (componentAdded [_parentComponentUid _componentUid])
                           (componentRemoved [componentUid] (swap! removed-res (fn [r] (conj r componentUid))))
                           (postProcessAfterEvolveCycle [_a _m]))
        container-engine (Container.
                           "change-remove-children-test1"
                           (ClojureContainerParser.)
                           result-collector
                           container)
        _ (.evolve container-engine [:main] {:do :children :cmd :add})
        _ (.evolve container-engine [:main] {:do :children :cmd :change})
        _ (.evolve container-engine [:main] {:do :children})]
    (test/is (= {:c1 "c1_1" :c2 "c2_2" :c3 "c3_1"} (get @results :res)))
    (test/is (= 2 (count (get @results :children))))
    (test/is (= 2 (count @removed-res)))))

(test/deftest add-remove-with-children-test
  (let [c1-prototype {:id :c1
                      :children {:c1_1 {:id :c1_1}
                                 :c1_2 {:id :c1_2}}}
        _ (core/defevolverfn :children (if (and (map? (get-reason)) (= :children (:do (get-reason))))
                                         (let [cmd (:cmd (get-reason))]
                                           (cond
                                             (= :add cmd)
                                             (assoc old-children :c1 c1-prototype)
                                             :else
                                             (dissoc old-children :c1)))
                                         old-children))
        container (core/defroot
                    {:id :main
                     :evolvers {:children children-evolver}
                     :children {:c1 c1-prototype}})
        added-res (atom {0 0 1 0 2 0 3 0})
        removed-res (atom {0 0 1 0 2 0 3 0})
        result-collector (proxy [IResultCollector] []
                           (appendResult [_parentComponentUid, _path, _node, _newValue])
                           (componentAdded [_parentComponentUid componentUid] (swap! added-res (fn [r] (assoc r componentUid (inc (get r componentUid))))))
                           (componentRemoved [componentUid] (swap! removed-res (fn [r] (assoc r componentUid (inc (get r componentUid))))))
                           (postProcessAfterEvolveCycle [_a _m]))
        container-engine (Container.
                           "add-remove-with-children-test"
                           (ClojureContainerParser.)
                           result-collector
                           container)
        _ (.evolve container-engine [:main] {:do :children})
        _ (.evolve container-engine [:main] {:do :children :cmd :add})]
    (test/is (= {0 0 1 1 2 1 3 1} @removed-res))
    (test/is (= {0 1 1 2 2 2 3 2} @added-res))))

(test/deftest add-remove-with-children-dep-test
  (let [_ (core/defevolverfn :a (if (= [:_] (get-reason)) (inc (get-property [:_] :x)) old-a))
        _ (core/defevolverfn :b (if (= [] (get-reason)) (inc (get-property [] :x)) old-b))
        _ (core/defevolverfn :x (if-let [x (:x (get-reason))] x old-x))
        _ (core/defevolverfn :y (if (and (vector? (get-reason)) (= 3 (count (get-reason))))
                                  (inc (get-property [:this :c1 (nth (get-reason) 2)] :a)) old-y))
        _ (core/defevolverfn :z (if (and (vector? (get-reason)) (= 2 (count (get-reason))))
                                  (inc (get-property [:this (nth (get-reason) 1)] :b)) old-z))
        c1-prototype {:id :c1
                      :b 0
                      :evolvers {:b b-evolver}
                      :children {:c1_1 {:id :c1_1
                                        :a 0
                                        :evolvers {:a a-evolver}}}}
        _ (core/defevolverfn :children (if (and (map? (get-reason)) (= :children (:do (get-reason))))
                                         (let [cmd (:cmd (get-reason))]
                                           (cond
                                             (= :add cmd)
                                             (assoc old-children :c1 c1-prototype)
                                             :else
                                             (dissoc old-children :c1)))
                                         old-children))
        container (core/defroot
                    {:id :main
                     :x 0
                     :y 0
                     :z 0
                     :evolvers {:children children-evolver
                                :x x-evolver
                                :y y-evolver
                                :z z-evolver}
                     :children {}})
        res (atom {})
        result-collector (proxy [IResultCollector] []
                           (appendResult [_parentComponentUid, path, node, newValue]
                             (swap! res (fn [a] (assoc-in a [(last path) (.getPropertyId node)] newValue))))
                           (componentAdded [_parentComponentUid _componentUid])
                           (componentRemoved [_componentUid])
                           (postProcessAfterEvolveCycle [_a _m]))
        container-engine (Container.
                           "add-remove-with-children-dep-test"
                           (ClojureContainerParser.)
                           result-collector
                           container)
        _ (reset! res {})
        _ (.evolve container-engine [:main] {:do :children :cmd :add})
        _ (.evolve container-engine [:main] {:x 4})]
    (test/is (= 5 (get-in @res [:c1_1 :a])))
    (test/is (= 5 (get-in @res [:c1 :b])))
    (test/is (= 6 (get-in @res [:main :y])))
    (test/is (= 6 (get-in @res [:main :z])))))

(test/deftest remove-add-dependent-test
  (let [_ (core/defevolverfn :a (if (= (get-reason) []) (get-property [] :res) old-a))
        c1-prototype {:id :c1
                      :a 0
                      :evolvers {:a a-evolver}}
        _ (core/defevolverfn evolver-res :res (if (= (get-reason) :res)
                                                (inc old-res)
                                                old-res))
        _ (core/defevolverfn :children (if (and (map? (get-reason)) (= :children (:do (get-reason))))
                                         (let [cmd (:cmd (get-reason))]
                                           (cond
                                             (= :add cmd)
                                             (assoc old-children :c1 c1-prototype)
                                             :else
                                             (dissoc old-children :c1)))
                                         old-children))
        container (core/defroot
                    {:id :main
                     :res 0
                     :evolvers {:res evolver-res
                                :children children-evolver}
                     :children {:c1 c1-prototype}})
        results (atom #{})
        result-collector (proxy [IResultCollector] []
                           (appendResult [_parentComponentUid, _path, node, newValue]
                             (swap! results (fn [r]
                                              (if (= :a (.getPropertyId node))
                                                (conj r newValue)
                                                r))))
                           (componentAdded [_parentComponentUid _componentUid])
                           (componentRemoved [_componentUid])
                           (postProcessAfterEvolveCycle [_a _m]))
        container-engine (Container.
                           "remove-add-dependent-test"
                           (ClojureContainerParser.)
                           result-collector
                           container)
        _ (.evolve container-engine [:main] :res)                       ; :res and :c1 :a become 1
        _ (.evolve container-engine [:main] {:do :children})            ; :c1 is removed
        _ (.evolve container-engine [:main] :res)                       ; :res is evolved with no dependents and becomes 2
        _ (.evolve container-engine [:main] {:do :children :cmd :add})  ; fresh :c1 is added
        _ (.evolve container-engine [:main] :res)                       ; :res and :c1 :a become 3
        ]
    (test/is (= #{0 1 3} @results))))

(test/deftest remove-dependency-test
  (let [_ (core/defevolverfn :a (if (map? (get-reason))
                                  (:a (get-reason))
                                  old-a))
        c1-prototype {:id :c1
                      :a 0
                      :evolvers {:a a-evolver}}
        _ (core/defevolverfn evolver-res :res (get-property [:this :c1] :a))
        ambiguous-child-id :c1
        _ (core/defevolverfn evolver-res1 :res1 (get-property [:this ambiguous-child-id] :a))
        _ (core/defevolverfn :children (if (and (map? (get-reason)) (= :children (:do (get-reason))))
                                         (let [cmd (:cmd (get-reason))]
                                           (cond
                                             (= :add cmd)
                                             (assoc old-children :c1 c1-prototype)
                                             :else
                                             (dissoc old-children :c1)))
                                         old-children))
        container (core/defroot
                    {:id :main
                     :res 0
                     :res1 0
                     :evolvers {:res evolver-res
                                :res1 evolver-res1
                                :children children-evolver}
                     :children {:c1 c1-prototype}})
        results (atom #{})
        results1 (atom #{})
        result-collector (proxy [IResultCollector] []
                           (appendResult [_parentComponentUid, _path, node, newValue]
                             (cond
                               (= :res (.getPropertyId node))
                               (swap! results (fn [r] (conj r newValue)))
                               (= :res1 (.getPropertyId node))
                               (swap! results1 (fn [r] (conj r newValue)))))
                           (componentAdded [_parentComponentUid _componentUid])
                           (componentRemoved [_componentUid])
                           (postProcessAfterEvolveCycle [_a _m]))
        container-engine (Container.
                           "remove-dependency-test"
                           (ClojureContainerParser.)
                           result-collector
                           container)
        _ (.evolve container-engine [:main :c1] {:a 1})                 ; :res, :res1 and :c1 :a become 1
        _ (.evolve container-engine [:main] {:do :children})            ; :c1 is removed
        _ (.evolve container-engine [:main] {})                         ; :res, :res1 are evolved, their dependenies obtain nil
        _ (.evolve container-engine [:main] {:do :children :cmd :add})  ; fresh :c1 is added
        _ (.evolve container-engine [:main :c1] {:a 2})                 ; :c1 :a become 2; :res does not depend on :c1 any more but :res1 does again and also becomes 2
        ]
    (test/is (= #{0 nil 1} @results))
    (test/is (= #{0 nil 1 2} @results1))))

(test/deftest evolve-consumer-test
  (let [_ (core/defevolverfn :a (if (not (nil? (get-reason)))
                                  (+ (:x (get-reason)) 5)
                                  old-a))
        container (core/defroot
                    {:id :main
                     :a 0
                     :evolvers {:a a-evolver}})
        results (atom 1)
        consumed-results (atom 2)
        result-collector (proxy [IResultCollector] []
                           (appendResult [_parentComponentUid, _path, node, newValue]
                             (swap! results (fn [r] (if (= :a (.getPropertyId node))
                                                      [newValue (.getName (Thread/currentThread))]
                                                      r))))
                           (componentAdded [_parentComponentUid _componentUid])
                           (postProcessAfterEvolveCycle [_a _m]))
        lock (ReentrantLock.)
        condition (.newCondition lock)
        evolve-consumer (proxy [IFGEvolveConsumer] []
                          (getTargetPath [] [:main])
                          (getTargetProperties [] [:a])
                          (acceptEvolveResult [_session-id component _reason]
                            (do
                              (.lock lock)
                              (swap! consumed-results (fn [_] [(:a component) (.getName (Thread/currentThread))]))
                              (.signal condition)
                              (.unlock lock))))
        container-engine (Container.
                           "evolve-consumer-test"
                           (ClojureContainerParser.)
                           result-collector
                           container)
        _ (.addEvolveConsumer container-engine evolve-consumer)
        _ (.lock lock)
        _ (.evolve container-engine [:main] {:x 2})
        _ (.await condition 5 TimeUnit/SECONDS)
        _ (.unlock lock)]
    (test/is (= 7 (first @results)))
    (test/is (= 7 (first @consumed-results)))
    (test/is (not= (second @results) (second @consumed-results)))))

(test/deftest input-dep-test
  (let [_ (core/defevolverfn :a (cond
                                  (mouse/mouse-event? component) (mouse/get-mouse-rel-x component)
                                  (mousewheel/mouse-wheel? component) (mousewheel/get-wheel-rotation component)
                                  (keyboard/key-event? component) (keyboard/get-key-char component)
                                  (host/host-event? component) "host"
                                  (clipboard/clipboard-paste? component) "paste"
                                  (timer/timer-event? component) "timer"))]
    (test/is (= #{:mouse :mousewheel :keyboard :host :clipboard :timer} (set (:input-channel-dependencies (meta a-evolver)))))))