; Copyright (c) 2017 Denys Lebediev and contributors. All rights reserved.
; The use and distribution terms for this software are covered by the
; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
; which can be found in the file LICENSE at the root of this distribution.
; By using this software in any fashion, you are agreeing to be bound by
; the terms of this license.
; You must not remove this notice, or any other, from this software.

(ns flatgui.util.vecmath-test
  (:require
    [clojure.test :as test]
    [flatgui.util.vecmath :as v]
    [flatgui.util.matrix :as m]))

(test/deftest vec-comparenum-test
  (test/is (= [-1 0 1] (v/vec-comparenum [3 4 5] [1 4 6]))))

(test/deftest m->v-test
  (let [m (m/translation 3 -5 1)]
    (test/is (= [3 -5 1] (v/mxtransf->vec m)))))

(test/deftest m->v-test2
  (let [m (m/translation -3.5 -2.5)]
    (test/is (= [-3.5 -2.5] (v/mxtransf->vec m 2)))))

(test/deftest -m+p->v-test
  (let [m (m/translation 3 5 7)
        p (m/defpoint -2 3 -4)]
    (test/is (= [-5 -2 -11] (v/-mxtransf+point->vec m p)))))

(test/deftest -m+p->v-test2
  (let [m (m/translation -2 -3)
        p (m/defpoint 4 3)]
    (test/is (= [6 6] (v/-mxtransf+point->vec m p 2)))))

(test/deftest subranges-test
  ;;        0 1 2 3 4 5 6 7 8 9 A B C D E F
  (let [tv [1 2 3 3 3 4 4 5 5 5 5 6 7 7 8 8 9 10 10]]
    (test/is (= [[0 1] [1 1] [2 3] [5 2] [7 4] [11 1] [12 2] [14 2] [16 1] [17 2]] (v/find-subranges tv)))))
