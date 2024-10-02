(ns simple.app.html
  (:require [ring.util.http-response :as resp]
            [louhi.util.html :as html]))


(defn html-head [env]
  (let [dev?           (-> env :config :mode (= :dev))
        resources-repo (-> env :resources-repo)]
    [:head
     [:meta {:charset "utf-8"}]
     [:title "Louhi Demo"]
     [:meta {:name    "viewport"
             :content "width=device-width, initial-scale=1.0"}]
     ;; TODO: Self host this
     [:link {:href "https://fonts.googleapis.com/css2?family=Material+Symbols+Outlined:opsz,wght,FILL,GRAD@20..48,100..700,0..1,-50..200"
             :type "text/css"
             :rel  "stylesheet"}]
     (html/script resources-repo "/s/louhi/htmx.js")
     (html/script resources-repo "/s/louhi/alpine.js")
     (when dev?
       (html/script resources-repo "/dev/watch"))
     (html/style resources-repo "/s/styles.css")]))



(defn html-page [env & body]
  (apply html/html-page
         (html-head env)
         body))


(def pages
  {:home {:href "/"}
   :foo  {:label "Foo"
          :href  "/foo"}
   :bar  {:label "Bar"
          :href  "/bar"}
   :boz  {:label "Boz"
          :href  "/foo/boz"}})


(defn page-nav [page-id & content]
  (let [href              (-> page-id pages :href)
        [attrs & content] (if (-> content (first) (map?))
                            content
                            (cons nil content))]
    [:a (merge {:href      href
                :hx-get    href
                :hx-target "body"
                :hx-swap   "innerHTML transition:true"}
               attrs)
     content]))


(def nav-links (mapv pages [:foo :bar]))


(defn page-header [req]
  (let [current  (-> req :uri)
        username (-> req :session/session :username)]
    [:header
     [:nav {:hx-boost "true"}
      [:a.logo {:href         (when (not= current "/") "/")
                :aria-current (when (= current "/") "page")
                :hx-swap      "innerHTML transition:true"}
       "Demo"]
      [:div.spacer]
      (for [{:keys [label href]} nav-links]
        [:a {:href         (when (not= href current) href)
             :aria-current (when (= href current) "page")
             :hx-swap      "innerHTML transition:true"}
         label])
      [:button {:popovertarget "user-menu-popover"}
       [:i "expand_more"]
       username]]

     ; User-menu pop-over:

     [:div {:id      "user-menu-popover"
            :popover true}
      [:div.user-menu
       [:a [:i "settings"] [:span "Settings..."]]
       [:a [:i "info"] [:span "Info..."]]
       [:a [:span "What ever..."]]
       [:hr]
       [:a {:href "/session/logout"}
        [:i "logout"] [:span "Logout"]]]]]))


(defn main-page [_env]
  [:body
   [:main
    [:article
     [:h1 "Hullo!"]]]])


(defn main-page-handler [env]
  (fn [req]
    (-> (html-page env
                   (page-header req)
                   (main-page env))
        (resp/ok)
        (update :headers assoc "cache-control" "no-cache"))))

