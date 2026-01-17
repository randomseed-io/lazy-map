(ns io.randomseed.lazy-map.build.main

  (:require [io.randomseed.lazy-map.build.pom-sync   :as pom-sync]
            [io.randomseed.lazy-map.build.maven-meta :as    mmeta]
            [clojure.java.io                         :as       io]
            [clojure.tools.build.api                 :as        b]))

(defn kw->name
  [n]
  (not-empty
   (cond (ident?  n) (name n)
         (string? n) n
         (nil?    n) nil
         :else       (str n))))

(defn kw->symbol
  [n]
  (cond (nil?           n) nil
        (simple-symbol? n) n
        :else              (symbol (kw->name n))))

(defn description [opts] (kw->name   (:description opts)))
(defn app-version [opts] (kw->name   (:version     opts)))
(defn app-group   [opts] (kw->name   (:group       opts)))
(defn app-name    [opts] (kw->name   (:name        opts)))
(defn app-scm     [opts] (kw->name   (:scm         opts)))
(defn app-url     [opts] (kw->name   (:url         opts)))
(defn aot-ns      [opts] (kw->symbol (:aot-ns      opts)))
(defn lib-name    [opts] (str (app-group opts) "/" (app-name    opts)))
(defn jar-name    [opts] (str (app-name  opts) "-" (app-version opts) ".jar"))
(defn jar-file    [opts] (str "target/" (jar-name opts)))
(defn class-dir   [opts] (str (or (:class-dir opts) "target/classes")))

(defn- pom-stream
  [pom-file]
  (io/input-stream (io/file pom-file)))

(defn sync-pom
  "Sync deps.edn -> pom.xml (dependency section).
   - clojure -T:build sync-pom
   - clojure -T:build sync-pom :local-root-version \"${project.version}\""
  [{:keys [local-root-version]
    :or   {local-root-version "${project.version}"}
    :as   opts}]
  (pom-sync/sync-pom-deps! "deps.edn" "pom.xml"
                           {:name               (app-name    opts)
                            :group              (app-group   opts)
                            :lib-name           (lib-name    opts)
                            :version            (app-version opts)
                            :local-root-version local-root-version
                            :description        (description opts)
                            :url                (app-url     opts)
                            :scm                (app-scm     opts)}))

(defn jar [opts]
  (let [class-dir "target/classes"]
    (b/delete                      {:path "target"})
    (b/copy-dir                    {:src-dirs   ["src" "resources"]
                                    :target-dir class-dir})
    (b/compile-clj                 {:basis      (b/create-basis {:project "deps.edn"})
                                    :class-dir  class-dir
                                    :ns-compile (aot-ns opts)})
    (mmeta/install-maven-metadata! {:class-dir   class-dir
                                    :artifact-id (app-name    opts)
                                    :group-id    (app-group   opts)
                                    :version     (app-version opts)
                                    :pom         (pom-stream  "pom.xml")})
    (b/jar                         {:class-dir class-dir
                                    :jar-file  (jar-file opts)})))

(defn -main [& _]
  (jar nil))
