(ns clojure-workshop.core
  (:require [compojure.core :as compojure]
            [ring.adapter.jetty :as jetty]
            [hiccup.core :as hiccup]
            [net.cgrand.enlive-html :as enlive]
            [clojure.string :as str]
            [clojure-workshop.db :as db]
            [ring.middleware.defaults :refer [wrap-defaults site-defaults]]))

(defn render-html [body]
  {:status 200
   :body body
   :headers {"Content-Type" "text/html"}})

(defn parse-card [url]
  (-> (enlive/html-resource (java.net.URL. url))
      (enlive/select [:body :div.catalogCard])))

(defn title [card]
  (-> (enlive/select card [:div.catalogCard-title :a]) first :content first))

(defn price->int [price-str]
  (Integer/parseInt
   (str/join
    (re-seq #"\d+" price-str))))

(defn price [card]
  (-> (enlive/select card [:div.catalogCard-price])
      first
      :content
      first))

(defn price-title [url]
  (let [cards (parse-card url)]
    (doall
     (map (fn [item]
            {:title (title item)
             :price (price->int (price item))})
          cards))))

(defn fetch-save-db [url]
  (db/save (price-title url)))

(defn form []
  [:form {:method :post}
   [:label
    [:span "URL: "]
    [:input {:type "text" :name :url}]]
   [:input {:type :submit :value "Добавить URL"}]])

(defn render-item [item]
 (let [{:keys [title price]} item]
  [:dl
   [:dt title]
   [:dd price]]))

(defn render-db []
  (let [db (db/read)]
    (hiccup/html
     (form)
     [:form {:action "/db/clean" :method :post}
      [:input {:type :submit :value "Очистить"}]
      (map render-item db)])))

(defn redirect-with [callback redirect-url]
  (do
    (when callback (callback))
    {:status 302
     :headers {"Location" redirect-url}}))

(defn query-form [query-str]
  (hiccup/html
   [:form
    [:label
     [:span "Поиск: "]
     [:input {:name "q" :value query-str}]
     [:input {:type :submit :value "Искать"}]]]))

(defn query-results [query-str]
  (let [results (db/query query-str)]
    (hiccup/html
     (query-form query-str)
     (map render-item results))))

(compojure/defroutes app
  (compojure/GET "/" [] (redirect-with nil "/db"))
  (compojure/GET "/query" [q] (render-html (query-results q)))
  (compojure/POST "/db/clean" []
                  (redirect-with (db/clean) "/db"))
  (compojure/POST "/db" [url]
                  (redirect-with (fetch-save-db url) "/db"))
  (compojure/GET "/db" [] (render-html (render-db))))

(def wrapped-app (-> app
                     (wrap-defaults (assoc-in site-defaults [:security :anti-forgery] false))))

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
