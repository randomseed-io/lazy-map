(ns io.randomseed.lazy-map.build.main

  (:require [io.randomseed.lazy-map.build.pom-sync :as pom-sync]
            [clojure.java.io                       :as       io]
            [clojure.string                        :as      str]
            [clojure.tools.build.api               :as        b]
            [juxt.pack.api                         :as     pack]))

(def ^:private LIB 'io.randomseed/lazy-map)
(def ^:private AOT_NS ['io.randomseed.lazy-map])

(defn- first-nonblank-line
  [path]
  (with-open [r (io/reader path)]
    (or (some (fn [^String line]
                (let [s (str/trim line)]
                  (when-not (str/blank? s) s)))
              (line-seq r))
        (throw (ex-info "VERSION file contains no non-blank lines"
                        {:path path})))))

(def version
  (first-nonblank-line "VERSION"))

(def ^:private DEFAULT_JAR
  (str "target/lazy-map-" version ".jar"))

(defn sync-pom
  "Sync deps.edn -> pom.xml (dependency section).
   - clojure -T:build sync-pom
   - clojure -T:build sync-pom :local-root-version \"${project.version}\""
  [{:keys [local-root-version]
    :or   {local-root-version "${project.version}"}}]
  (pom-sync/sync-pom-deps! "deps.edn" "pom.xml"
                           {:name LIB
                            :local-root-version local-root-version}))

(defn jar-old
  "Sync POM deps and build jar.
   - clojure -T:build jar
   - clojure -T:build jar :jar \"target/custom.jar\""
  [{:keys [jar local-root-version]
    :or   {jar                DEFAULT_JAR
           local-root-version "${project.version}"}}]
  (io/make-parents (io/file jar))
  ;; (sync-pom {:local-root-version local-root-version}) ; moved to Makefile as separate target
  (pack/library {:basis (b/create-basis {:project "deps.edn"})
                 :path  jar
                 :lib   LIB
                 :pom   (io/input-stream (io/file "pom.xml"))}))

(defn jar [_]
  (let [class-dir "target/classes"]
    (b/delete {:path "target"})
    (b/copy-dir    {:src-dirs   ["src" "resources"]
                    :target-dir class-dir})
    (b/compile-clj {:basis      (b/create-basis {:project "deps.edn"})
                    :class-dir  class-dir
                    :ns-compile AOT_NS})
    (b/jar         {:class-dir class-dir
                    :jar-file  (str "target/lazy-map-" version ".jar")})))

(defn -main [& _]
  (jar nil))
