; Copyright (c) 2018 Denys Lebediev and contributors. All rights reserved.
; The use and distribution terms for this software are covered by the
; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
; which can be found in the file LICENSE at the root of this distribution.
; By using this software in any fashion, you are agreeing to be bound by
; the terms of this license.
; You must not remove this notice, or any other, from this software.

(ns flatgui.util.vectorutil-test
  (:require
    [clojure.test :as test]
    [flatgui.util.vectorutil :as vu]))

(test/deftest firstv-test
  (test/is (nil? (vu/firstv nil)))
  (test/is (nil? (vu/firstv [])))
  (test/is (= 1 (vu/firstv [1])))
  (test/is (= 1 (vu/firstv [1 2])))
  (test/is (= 1 (vu/firstv [1 2 3]))))

(test/deftest secondv-test
  (test/is (nil? (vu/secondv nil)))
  (test/is (nil? (vu/secondv [])))
  (test/is (nil? (vu/secondv [1])))
  (test/is (= 2 (vu/secondv [1 2])))
  (test/is (= 2 (vu/secondv [1 2 3])))
  (test/is (= 2 (vu/secondv [1 2 3 4]))))

(test/deftest take-lastv-test
  (test/is (nil? (vu/take-lastv 2 nil)))
  (test/is (nil? (vu/take-lastv 2 [])))
  (test/is (= [1] (vu/take-lastv 2 [1])))
  (test/is (= [1 2] (vu/take-lastv 2 [1 2])))
  (test/is (= [2 3] (vu/take-lastv 2 [1 2 3])))
  (test/is (= [2 3 4] (vu/take-lastv 3 [1 2 3 4]))))

(test/deftest takev-test
  (test/is (= [] (vu/takev 2 nil)))
  (test/is (= [] (vu/takev 2 [])))
  (test/is (= [1] (vu/takev 2 [1])))
  (test/is (= [1 2] (vu/takev 2 [1 2])))
  (test/is (= [1 2] (vu/takev 2 [1 2 3])))
  (test/is (= [1 2 3] (vu/takev 3 [1 2 3 4]))))

(test/deftest dropv-test
  (test/is (= [] (vu/dropv 2 nil)))
  (test/is (= [] (vu/dropv 2 [])))
  (test/is (= [] (vu/dropv 2 [1])))
  (test/is (= [] (vu/dropv 2 [1 2])))
  (test/is (= [3] (vu/dropv 2 [1 2 3])))
  (test/is (= [3 4] (vu/dropv 2 [1 2 3 4]))))

(test/deftest emptyv?-test
  (test/is (true? (vu/emptyv? nil)))
  (test/is (true? (vu/emptyv? [])))
  (test/is (false? (vu/emptyv? [1])))
  (test/is (false? (vu/emptyv? [1 2])))
  (test/is (false? (vu/emptyv? [1 2 3]))))