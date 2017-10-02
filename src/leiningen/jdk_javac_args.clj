(ns leiningen.jdk-javac-args
  (:require [clojure.string :as string]
            [leiningen.jdk-javac :as jdk-javac]))

(defn jdk-javac-args
  "Display options of jdk javac"
  [project & args]
    (print
      (string/join
        " "
        (vec (jdk-javac/javac-args-array
               project args))))
    (flush))
