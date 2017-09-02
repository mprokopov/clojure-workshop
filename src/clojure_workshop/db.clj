(ns clojure-workshop.db)

(def ^:dynamic *db-name* "data.edn")

(defn read []
  (try
    (clojure.edn/read-string (slurp *db-name*))
    (catch Exception e)))

(defn save
  [body]
  {:pre [(seq? body)]}
  (let [db (or (read) (list))]
    (spit *db-name*
          (into db body))))

(defn clean []
  (spit *db-name* (list)))

(defn filter-regex-fn [item query-str]
  (let [re-pat (re-pattern (str ".*" "(?u)(?i)" query-str ".*"))]
    (re-matches re-pat (:title item))))

(defn query [query-str]
  (let [db (read)]
    (filter #(filter-regex-fn % query-str) db)))
