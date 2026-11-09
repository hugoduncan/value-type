(ns value-type.readme
  "Pure rendering of maintained release coordinates in README text."
  (:require [clojure.string :as str]))

(defn- region [text label]
  (let [start (str "<!-- release-" label ":start -->")
        end (str "<!-- release-" label ":end -->")
        begins (str/index-of text start)
        ends (str/index-of text end)]
    (when-not (and begins ends
                   (< begins ends)
                   (nil? (str/index-of text start (inc begins)))
                   (nil? (str/index-of text end (inc ends))))
      (throw (ex-info "README requires one ordered marker pair per release region"
                      {:region label})))
    {:start begins :body (+ begins (count start)) :end ends}))

(defn render
  "Validate both non-overlapping regions and render prepared Clojars coordinates.
   Preserve all text outside region bodies. Do not claim publication or testing."
  [text version]
  (when-not (and (string? version) (re-matches #"0\.1\.[1-9][0-9]*" version))
    (throw (ex-info "Invalid release version" {:version version})))
  (let [status (region text "status")
        install (region text "install")
        ordered (sort-by :start [status install])]
    (when-not (< (:end (first ordered)) (:start (second ordered)))
      (throw (ex-info "README release regions must not overlap" {})))
    (reduce (fn [result [bounds body]]
              (str (subs result 0 (:body bounds)) "\n" body "\n"
                   (subs result (:end bounds))))
            text
            (sort-by (comp :start first) >
                     [[status (str "Release " version " coordinates are prepared. Use them only after this\n"
                                   "version is published by the release workflow.")]
                      [install (str "After version " version " is published, add these Clojars coordinates to\n"
                                    "your application's `deps.edn`:\n\n```clojure\n"
                                    "{:deps {org.hugoduncan/value-type {:mvn/version \"" version "\"}}\n"
                                    " :aliases {:dev {:jvm-opts [\"--enable-preview\"]}}}\n```")]]))))
