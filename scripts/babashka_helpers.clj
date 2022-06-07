(ns babashka-helpers
  (:require [babashka.fs :as fs]
            [babashka.tasks :refer [shell current-task run]]))



(defn headline []
  (println "
 +----------------------------------------------------------------------------+
 |                                                                            |
 |                                 AW  ,,                                     |
 |                                ,M'`7MM                                     |
 |                                MV   MM                                     |
 |                  MM    MM     AW    MM  ,pW\"Wq.   .P\"Ybmmm                 |
 |                  MM    MM    ,M'    MM 6W'   `Wb :MI  I8                   |
 |                  MM    MM    MV     MM 8M     M8  WmmmP\"                   |
 |                  MM    MM   AW      MM YA.   ,A9 8M                        |
 |                  MVbgd\"'Mb ,M'    .JMML.`Ybmd9'   YMMMMMb                  |
 |                  M.        MV                    6'     dP                 |
 |                  M8       AW                     Ybmmmd'                   |
 |                                                                            |
 +----------------------------------------------------------------------------+

"))


(defn print-public-task [k]
  (let [{:keys [:private :name]} (current-task)]
    (when-not private
      (println (case k :enter "☐" "✓") name))))



(defn clean-target [module]
  (println "Removing target folder on" module)
  (fs/delete-tree (format "%s/target" module)))



(defn format-source [module]
  (println "Formatting source code for module:" module)
  (shell {:dir module} "lein with-profile tools cljfmt fix"))



(defn format-source [module]
  (println "Formatting source code for module:" module)
  (shell {:dir module} "lein with-profile tools cljfmt fix"))



(defn update-dependencies [module]
  (println "Updating dependencies for module" module)
  (shell {:dir module} "lein with-profile tools ancient upgrade"))



(defn publish-jar [module]
  (println "Publish jar for module" module)
  (shell {:dir module} "lein deploy clojars"))



(defn glob [module dir pattern]
  (try
    (fs/glob (format "%s/%s" module dir) pattern)
    (catch Exception _ [])))



(defn build [module & args]
  (when (seq (fs/modified-since (or (first (glob module "target" "**.jar")) "a.jar")
               (concat
                 (glob module "src" "**.clj")
                 (glob module "resources" "**")
                 (glob module "java" "**.java")
                 (glob module "" "project.clj"))))
    (println "------------------------------------------------------------")
    (println "Building module" module)
    (println "------------------------------------------------------------")
    (if ((set args) ":skip-tests")
      (shell {:dir module} "lein do check, install")
      (shell {:dir module} "lein do check, test, install"))))
