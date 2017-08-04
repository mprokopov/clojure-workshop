(ns clojure-workshop.core
  (:require [compojure.core :as compojure]
            [ring.adapter.jetty :as jetty]
            [net.cgrand.enlive-html :as enlive]
            [cheshire.core :as cheshire]
            [hiccup.core]
            [ring.middleware.defaults :refer [wrap-defaults site-defaults]]
            [clojure.string :as str]))


(def ^:dynamic *titles* [:body :*> :h1#j-catalog-header])

(def ^:dynamic *item* [:body :*> :div.catalogCard-info])

(def ^:dynamic *item-price* [:div.catalogCard-price])

(def ^:dynamic *item-title* [:div.catalogCard-title :a])


(def ^:dynamic *urls* ["https://super-truper.com.ua/multituly/"
                       "https://super-truper.com.ua/keysy/"])

(def data (atom nil))

(defn scrap-uri
  "fetches document from URL and returns parse DOM as map"
  [url]
  (enlive/html-resource (java.net.URL. url)))

(defn transform-price
  "transforms string to number"
  [price-str]
  (Integer/parseInt (str/join (re-seq #"\d" price-str))))


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
        db (or (clojure.edn/read-string (slurp "data.edn")) (list))]
     (spit "data.edn" (pr-str (conj db parsed-uri)))))

(defn find-most-expensive-item []
  (let [db (clojure.edn/read-string (slurp "data.edn"))
        items (mapcat :items db)]
    (-> (sort-by :price items)
        last)))


(compojure/defroutes app
  (compojure/GET "/" [] {:body "Hello World!"
                         :status 200
                         :headers {"Content-Type" "text/plain"}})
  (compojure/GET "/form" [] {:status 200
                             :headers {"Content-Type" "text/html"}
                             :body (hiccup.core/html
                                    [:html
                                      [:body [:form {:action "/parse" :method :get}
                                              [:input {:type "text" :name "uri"}]
                                              [:input {:type "submit" :value "Scrap!"}]]]])})

  (compojure/GET "/parse" [uri] {:status 200
                                 :headers {"Content-Type" "application/json"}
                                 :body (->> (scrap-uri uri)
                                            parse-title-items
                                            cheshire/generate-string)}))


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

