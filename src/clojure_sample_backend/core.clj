(ns clojure-sample-backend.core
  (:require [compojure.core :as compojure]
            [ring.adapter.jetty :as jetty]
            [net.cgrand.enlive-html :as enlive]
            [cheshire.core :as cheshire]
            [hiccup.core]
            [ring.middleware.defaults :refer [wrap-defaults site-defaults]]
            [clojure.string :as str]))


(def ^:dynamic *titles* [:body :*> :div.catalogCard-title :a])

;; try "https://super-truper.com.ua/keysy/"
;; try "https://super-truper.com.ua/multituly/"

(defn scrap-uri [url]
  (enlive/html-resource (java.net.URL. url)))


(defn parse-titles
  ([res] (enlive/select res *titles*))
  ([res arr] (enlive/select res arr)))

(defn map-href-title [coll]
  (let [f (fn [{{href :href} :attrs content :content}] {:link href :title (first content)})]
    (map f coll)))

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
                                            parse-titles
                                            map-href-title
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

