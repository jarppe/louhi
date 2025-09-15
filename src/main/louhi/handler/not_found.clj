(ns louhi.handler.not-found
  (:require [louhi.http.status :as status]))

(def not-found-handler
  (constantly (constantly {:status status/not-found
                           :body   "Not found"})))
