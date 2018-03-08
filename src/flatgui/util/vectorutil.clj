; Copyright (c) 2018 Denys Lebediev and contributors. All rights reserved.
; The use and distribution terms for this software are covered by the
; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
; which can be found in the file LICENSE at the root of this distribution.
; By using this software in any fashion, you are agreeing to be bound by
; the terms of this license.
; You must not remove this notice, or any other, from this software.

(ns flatgui.util.vectorutil)

(defn firstv [v] (if (> (count v) 0) (nth v 0)))

(defn secondv [v] (if (> (count v) 1) (nth v 1)))

(defn take-lastv [n v] (subvec v (- (count v) n)))
