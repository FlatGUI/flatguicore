; Copyright (c) 2015 Denys Lebediev and contributors. All rights reserved.
; The use and distribution terms for this software are covered by the
; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
; which can be found in the file LICENSE at the root of this distribution.
; By using this software in any fashion, you are agreeing to be bound by
; the terms of this license.
; You must not remove this notice, or any other, from this software.


(ns flatgui.run
  (:import (flatgui.run FGRunUtil FGDesktopRunner)
           (java.util.function Consumer)))

(defn- create-java-exception-handler [exception-handler]
  (if exception-handler
    (reify Consumer
      (accept [_this ex] (exception-handler ex)))))

(defn run-desktop
  ([container-var win-title icon-resource exception-handler]
    (let [java-ex-handler (create-java-exception-handler exception-handler)
          icon (FGRunUtil/loadIconFromClasspath icon-resource java-ex-handler)]
      (FGDesktopRunner/runDesktop container-var win-title icon, java-ex-handler)))
  ([container-var exception-handler] (run-desktop container-var (str container-var) nil exception-handler))
  ([container-var] (run-desktop container-var nil)))