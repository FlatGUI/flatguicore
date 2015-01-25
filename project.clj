; Copyright (c) 2015 Denys Lebediev and contributors. All rights reserved.
; The use and distribution terms for this software are covered by the
; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
; which can be found in the file LICENSE at the root of this distribution.
; By using this software in any fashion, you are agreeing to be bound by
; the terms of this license.
; You must not remove this notice, or any other, from this software.

;breaks IDE (load-string (slurp "../projectcommon.clj"))
(def flatgui-version "0.1.0-SNAPSHOT")

(def jetty-version "9.2.7.v20150116")

(defproject org.flatgui/flatguicore flatgui-version
  :description "FlatGUI Core"
  :url "http://flatgui.org"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.json/json "20141113"] ; TODO Get rid of JSON dependency
                 [org.eclipse.jetty/jetty-server ~jetty-version]
                 [org.eclipse.jetty/jetty-servlet ~jetty-version]
                 [org.eclipse.jetty.websocket/websocket-servlet ~jetty-version]
                 [org.eclipse.jetty/jetty-util ~jetty-version]
                 [junit/junit "4.12"]]
  :java-source-paths ["src/flatgui/core"
                      "src/flatgui/controlcenter"]
  :omit-source true
  :aot :all)
