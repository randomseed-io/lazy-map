(ns user
  (:require
   [clojure.spec.alpha               :as                  s]
   [orchestra.spec.test              :as                 st]
   [clojure.spec.test.alpha          :as                cst]
   [clojure.spec.gen.alpha           :as                gen]
   [clojure.string                   :as                str]
   [clojure.repl                     :refer            :all]
   [clojure.test                     :refer [run-tests
                                             run-all-tests]]
   [clojure.tools.namespace.repl     :refer   [refresh
                                               refresh-all]]
   [expound.alpha                    :as            expound]
   [taoensso.nippy                   :as              nippy]
   [io.randomseed.lazy-map           :as           lazy-map]
   [puget.printer                    :as              puget]
   [puget.printer                    :refer        [cprint]]
   [kaocha.repl                      :refer            :all]))

(set! *warn-on-reflection* true)

(alter-var-root
 #'s/*explain-out*
 (constantly
  (expound/custom-printer {:show-valid-values? false
                           :print-specs?        true
                           :theme    :figwheel-theme})))

(when (System/getProperty "nrepl.load")
  (require 'nrepl)
  ;;(require 'infra)
  )

(st/instrument)

(defn test-all []
  (refresh)
  (cst/with-instrument-disabled
    (run-all-tests)))

(comment 
  (refresh-all)
  (cst/with-instrument-disabled (test-all))
  (cst/with-instrument-disabled (run-all))
  )
