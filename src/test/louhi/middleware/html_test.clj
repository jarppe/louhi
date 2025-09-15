(ns louhi.middleware.html-test
  (:require [clojure.test :refer [deftest testing is]]
            [matcher-combinators.test]
            [ring.mock.request :refer [request]]
            [louhi.middleware.html :as html]))


(deftest wrap-html-response-test
  (testing "with no response returns nil"
    (let [wrapper (html/wrap-html-response (constantly nil) nil)]
      (is (nil? (wrapper (request :get "/"))))))

  (testing "response can be wrapped into full page"
    (let [wrapper (html/wrap-html-response (constantly {:status 200
                                                        :body   [:h1 "hello"]})
                                           (fn [body]
                                             [:html body]))]
      (testing "with full body"
        (is (match? {:body "<html><h1>hello</h1></html>"}
                    (wrapper (request :get "/")))))
      
      (testing "with partial body"
        (is (match? {:body "<h1>hello</h1>"}
                    (wrapper (-> (request :get "/")
                                 (update :headers assoc "hx-request" "true")))))
        (is (match? {:body "<h1>hello</h1>"}
                    (wrapper (-> (request :get "/")
                                 (update :headers assoc "x-alpine-request" "true")))))))))
