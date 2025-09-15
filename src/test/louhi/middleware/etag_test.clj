(ns louhi.middleware.etag-test
  (:require [clojure.test :refer [deftest testing is]]
            [matcher-combinators.test]
            [ring.mock.request :refer [request]]
            [louhi.http.status :as status]
            [louhi.middleware.etag :as etag]))


(deftest etag-test
  (testing "with no response returns nil"
    (let [wrapper (etag/wrap-etag (constantly nil))]
      (is (nil? (wrapper (request :get "/"))))))

  (testing "does nothing when"
    (testing "response is not 200-299"
      (let [non-ok-resp {:status 300}
            wrapper     (etag/wrap-etag (constantly non-ok-resp))]
        (is (identical? non-ok-resp (wrapper (request :get "/"))))))
    (testing "request does not have if-none-match"
      (let [ok-resp {:status 200}
            wrapper (etag/wrap-etag (constantly ok-resp))]
        (is (identical? ok-resp (wrapper (request :get "/")))))))

  (testing "change response to not-modified if etag matches"
    (let [resp    {:status  200
                   :headers {"etag" "magic"}
                   :body    "body"}
          wrapper (etag/wrap-etag (constantly resp))
          resp    (wrapper (-> (request :get "/")
                               (update :headers assoc "if-none-match" "magic")))]
      (is (match? {:status  status/not-modified
                   :headers {"etag" "magic"}}
                  resp))
      (is (not (contains? resp :body)))))

  (testing "calculate etag if not provided"
    (let [resp      {:status 200
                     :body   "body"}
          body-etag (#'etag/body-etag (:body resp))
          wrapper   (etag/wrap-etag (constantly resp))
          resp      (wrapper (-> (request :get "/")
                                 (update :headers assoc "if-none-match" body-etag)))]
      (is (match? {:status  status/not-modified
                   :headers {"etag" body-etag}}
                  resp))
      (is (not (contains? resp :body))))))