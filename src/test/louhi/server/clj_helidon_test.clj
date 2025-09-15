(ns louhi.server.clj-helidon-test
  (:require [clojure.test :refer [deftest is]]
            [matcher-combinators.test]
            [matcher-combinators.matchers :as m]
            [louhi.http.client :as http]
            [louhi.server.clj-helidon :as server]))


(deftest hirundo-test
  (with-open [server (server/make-server (constantly {:status 200
                                                      :body   "hello"})
                                         nil)]
    (let [url (str "http://127.0.0.1:" (-> server (meta) :port) "/")]
      (is (match? {:status 200
                   :body   (m/via slurp "hello")}
                  @(http/GET url))))))

