(ns louhi.server.jetty-test
  (:require [clojure.test :refer [deftest is]]
            [matcher-combinators.test]
            [matcher-combinators.matchers :as m]
            [louhi.http.client :as http]
            [louhi.server.jetty :as server]))


(deftest jetty-test
  (with-open [server (server/create-server (constantly {:status 200
                                                        :body   "hello"})
                                           nil)]
    (let [url (str "http://127.0.0.1:" (:port server) "/")]
      (is (match? {:status 200
                   :body   (m/via slurp "hello")}
                  @(http/GET url))))))

