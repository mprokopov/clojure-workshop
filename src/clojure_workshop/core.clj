(ns clojure-workshop.core
  (:require [compojure.core :as compojure]
            [ring.adapter.jetty :as jetty]
            [ring.middleware.defaults :refer [wrap-defaults site-defaults]]
            [ring.middleware.reload :refer [wrap-reload]]))

(compojure/defroutes app
  (compojure/GET "/" [] {:body "Hello World!"
                         :status 200
                         :headers {"Content-Type" "text/plain"}}))

(def wrapped-app (-> app
                     (wrap-defaults site-defaults)))

(defonce server (atom nil))

(defn start []
  (reset! server (jetty/run-jetty (wrap-reload #'wrapped-app) {:join? false :port 8080})))

(defn stop []
  (.stop @server))

(defn restart []
  (when @server (stop))
  (start))

(defn -main []
  (jetty/run-jetty #'wrapped-app {:join? false :port 8080}))
