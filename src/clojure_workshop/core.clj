(ns clojure-workshop.core
  (:require [compojure.core :as compojure]
            [ring.adapter.jetty :as jetty]
            [net.cgrand.enlive-html :as enlive]
            [cheshire.core :as cheshire]
            [hiccup.core]
            [clojure.edn]
            [ring.middleware.defaults :refer [wrap-defaults site-defaults]]
            [clojure.string :as str]))


(def ^:dynamic *titles* [:body :*> :h1#j-catalog-header])

(def ^:dynamic *item* [:body :*> :div.catalogCard-info])

(def ^:dynamic *item-price* [:div.catalogCard-price])

(def ^:dynamic *item-title* [:div.catalogCard-title :a])


(def ^:dynamic *urls* ["https://super-truper.com.ua/multituly/"
                       "https://super-truper.com.ua/keysy/"
                       "https://super-truper.com.ua/generatory/"])

(defn read-db "reads from data.edn or returns empty list" []
  (or (clojure.edn/read-string (slurp "data.edn")) (list)))

(defn scrap-uri
  "fetches document from URL and returns parse DOM as map"
  [url]
  (enlive/html-resource (java.net.URL. url)))

(defn transform-price
  "transforms string to number"
  [price-str]
  (Float/parseFloat (str/join (re-seq #"[\d\.]" price-str))))


(defn parse-titles
  ([res] (->> (enlive/select res *titles*)
              (map :content)
              ffirst))
  ([res arr] (enlive/select res arr)))

(defn parse-items
  "parses DOM map and returns list of items with title and price"
  [res]
  (let [cat-content #(first (mapcat :content %))
        parse-title (fn [item] {:title (cat-content (enlive/select item *item-title*))})
        parse-price (fn [item] {:price (transform-price (cat-content (enlive/select item *item-price*)))})
        parse-title-price (fn [item] (into {} [(parse-title item) (parse-price item)]))]
    (->> (enlive/select res *item*)
         (map parse-title-price))))

(defn parse-title-items [res]
  {:header (parse-titles res)
   :items (parse-items res)})

;; (map #(->> (scrap-uri %) parse-title-items) *urls*)
(defn parse-uris []
  (map #(->> (scrap-uri %) parse-title-items) *urls*))

(defn parse-and-save-uri [uri]
  (let [parsed-uri (->> (scrap-uri uri)
                        parse-title-items)
        db (read-db)]
     (spit "data.edn" (pr-str (conj db parsed-uri)))))


(defn titles-from-db "returns only headers from DB"
  []
  (let [db (read-db)
        f (fn [{:keys [header items]}] {:header header :items (count items)})] 
     (map f db)))

(defn find-most-expensive-item []
  (let [db (clojure.edn/read-string (slurp "data.edn"))
        items (mapcat :items db)]
    (-> (sort-by :price items)
        last)))

(defn search-item-by-title [query]
  (let [db (read-db)
        items (mapcat :items db)
        parts (str/join ".*" (str/split query #" ")) ;; split by space
        pattern (re-pattern (str ".*" "(?u)(?i)" parts ".*"))
        f (fn [item] (re-matches pattern (:title item)))]
   (filter f items)))

(compojure/defroutes app
  (compojure/GET "/" [] {:body "Hello World!"
                         :status 200
                         :headers {"Content-Type" "text/plain"}})
  (compojure/GET "/form" [q] {:status 200
                              :headers {"Content-Type" "text/html"}
                              :body (hiccup.core/html
                                     [:html
                                      [:body
                                       [:h2 "Scrapped pages"]
                                       [:ul
                                        (for [{title :header items-count :items} (titles-from-db)]
                                          [:li title " " items-count])]
                                       [:h2 "Most expensive item"]
                                       (let [{title :title price :price} (find-most-expensive-item)]
                                         [:dl
                                          [:dt "Title"]
                                          [:dd title]
                                          [:dt "Price"]
                                          [:dd price]])
                                       [:h2 "Find"]
                                       [:form {:method :get}
                                        [:label
                                         "Search by title"
                                         [:br]
                                         [:input {:name "q" :type :text}]]]
                                       (when q
                                         [:h4 q]
                                         [:ul
                                          (for [{title :title price :price} (search-item-by-title q)]
                                            [:li title])])
                                       [:form {:action "/parse" :method :get}
                                        [:h2 "Scrap"]
                                        [:input {:type "text" :name "uri"}]
                                        [:input {:type "submit" :value "Scrap!"}]]]])})

  (compojure/GET "/parse" [uri] (do (parse-and-save-uri uri)
                                    {:status 302
                                     :headers {"Location" "/form"}})))


(def wrapped-app (-> app
                     (wrap-defaults site-defaults)))

(def server (atom nil))

(defn start []
  (reset! server (jetty/run-jetty #'wrapped-app {:join? false :port 8080})))

(defn stop []
  (.stop @server))

(defn restart []
  (when @server (stop))
  (start))

(defn -main []
  (jetty/run-jetty #'wrapped-app {:join? false :port 8080}))

