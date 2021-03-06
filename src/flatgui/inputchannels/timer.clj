; Copyright (c) 2015 Denys Lebediev and contributors. All rights reserved.
; The use and distribution terms for this software are covered by the
; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
; which can be found in the file LICENSE at the root of this distribution.
; By using this software in any fashion, you are agreeing to be bound by
; the terms of this license.
; You must not remove this notice, or any other, from this software.

(ns ^{:doc    "Timer input channel"
      :author "Denys Lebediev"}
flatgui.inputchannels.timer
  (:require [flatgui.inputchannels.channelbase :as cb])
  (:import (flatgui.core FGTimerEvent)))


(cb/definputparser timer-event? FGTimerEvent true)

(cb/definputparser get-time FGTimerEvent (.getTimestamp repaint-reason))

;;; Dependency check

;; TODO timer id
(defn find-timer-dependency [s-expr]
  (cb/find-channel-dependency s-expr 'flatgui.inputchannels.timer :timer))
