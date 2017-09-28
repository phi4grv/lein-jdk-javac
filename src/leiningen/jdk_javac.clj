(ns leiningen.jdk-javac
  (:require [clojure.string :as string]
            [leiningen.classpath :as classpath]
            [leiningen.core.eval :as eval]
            [leiningen.core.main :as main]
            [leiningen.core.project :as project])
  (:import javax.tools.ToolProvider))

(defn- javac-args-array
  [project args]
  (let [source-path (string/join
                      (System/getProperty "path.separator")
                      (:java-source-paths project))]
    (into-array
      String
      (concat
        (:javac-options project)
        ["-cp" (classpath/get-classpath-string project)
         "-d" (:compile-path project)
         "-sourcepath" source-path]
        args))))

(defn- subprocess-form
  "Creates a form for running javac in a subprocess."
  [compile-path javac-args]
  (main/debug "Running javac with" javac-args)
  `(let [abort# (fn [& msg#]
                  (.println java.lang.System/err (apply str msg#))
                  (java.lang.System/exit 1))]
     (if-let [compiler# (javax.tools.ToolProvider/getSystemJavaCompiler)]
       (do
         (.mkdirs (clojure.java.io/file ~compile-path))
         (when-not (zero?
                     (.run compiler# nil nil nil
                           (into-array java.lang.String ~javac-args)))
           (abort# "Compilation of Java sources(lein jdk-javac) failed.")))
       (abort# "Java compiler not found; Be sure to use java from a JDK\n"
               "rather than a JRE by modifying PATH or setting JAVA_CMD."))))

;; Pure java projects will not have Clojure on the classpath. As such, we need
;; to add it if it's not already there.
(def subprocess-profile
  {:dependencies [^:displace ['org.clojure/clojure (clojure-version)]]
   :eval-in :subprocess})

(defn javac-project-for-subprocess
  "Merge profiles to create project appropriate for javac subprocess.  This
  function is mostly extracted to simplify testing, to validate that settings
  like `:local-repo` and `:mirrors` are respected."
  [project subprocess-profile]
  (-> (project/merge-profiles project [subprocess-profile])
      (project/retain-whitelisted-keys project)))

(defn jdk-javac
  "Run javac of jdk with options and args."
  [project & args]
  (let [javac-args (vec (javac-args-array project args))
        compile-path (:compile-path project)
        form (subprocess-form compile-path javac-args)]
    (try
      (binding [eval/*pump-in* false]
        (eval/eval-in
          (javac-project-for-subprocess project subprocess-profile)
          form))
      (catch Exception e
        (if-let [exit-code (:exit-code (ex-data e))]
          (main/exit exit-code)
          (throw e))))))
