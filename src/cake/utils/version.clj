(ns cake.utils.version
  (:use [clojure.string :only [join split]]))

(def version-levels [:major :minor :patch])

(defn version-map [version]
  (if-not (string? version)
    version
    (let [[version snapshot] (split version #"-")]
      (into {:snapshot snapshot}
            (map vector
                 version-levels
                 (map #(Integer/parseInt %)
                      (split version #"\.")))))))

(defn version-str [version]
  (str (join "." (map #(or (version %) 0) version-levels))
       (when (version :snapshot) "-SNAPSHOT")))

(defn version-mismatch? [expected-version actual-version]
  (let [expected (version-map expected-version)
        actual   (version-map actual-version)]
    (not (and (= (:major expected) (:major actual))
              (>= 0 (compare [(:minor expected) (:patch expected)]
                             [(:minor actual)   (:patch actual)]))))))
