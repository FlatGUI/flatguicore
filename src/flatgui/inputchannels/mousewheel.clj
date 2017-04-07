; Copyright (c) 2015 Denys Lebediev and contributors. All rights reserved.
; The use and distribution terms for this software are covered by the
; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
; which can be found in the file LICENSE at the root of this distribution.
; By using this software in any fashion, you are agreeing to be bound by
; the terms of this license.
; You must not remove this notice, or any other, from this software.

(ns ^{:doc "Mouse wheel input channel"
      :author "Denys Lebediev"}
  flatgui.inputchannels.mousewheel
  (:import [java.awt.event MouseWheelEvent])
  (:require [flatgui.inputchannels.channelbase :as channelbase]))


(channelbase/definputparser mouse-wheel? MouseWheelEvent true)

(channelbase/definputparser get-wheel-rotation MouseWheelEvent (.getWheelRotation repaint-reason))

;;; Dependency check

(defn find-mousewheel-dependency [s-expr]
  (channelbase/find-channel-dependency s-expr 'flatgui.inputchannels.mousewheel :mousewheel))
