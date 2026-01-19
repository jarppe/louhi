(ns louhi.middleware.htmx-test
  (:require [clojure.test :refer [deftest testing is]]
            [matcher-combinators.test]
            [ring.mock.request :refer [request]]
            [louhi.middleware.htmx :as htmx]))


(def wrap-htmx ((-> htmx/wrap-htmx :compile) {:htmx true} nil))


(deftest wrap-html-response-test
  (testing "with no response returns nil"
    (let [wrapper (wrap-htmx (constantly nil) {:to-full-page (fn [body] [:html body])})]
      (is (nil? (wrapper (request :get "/"))))))

  (testing "response can be wrapped into full page"
    (let [wrapper (wrap-htmx (constantly {:status 200
                                          :body   [:h1 "hello"]})
                             {:to-full-page (fn [body] [:html body])})]
      (testing "with full body"
        (is (match? {:body [:html [:h1 "hello"]]}
                    (wrapper (request :get "/")))))

      (testing "with partial body"
        (is (match? {:body [:h1 "hello"]}
                    (wrapper (-> (request :get "/")
                                 (update :headers assoc "hx-request" "true")))))
        (is (match? {:body [:h1 "hello"]}
                    (wrapper (-> (request :get "/")
                                 (update :headers assoc "x-alpine-request" "true")))))))))
