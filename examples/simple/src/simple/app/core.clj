(ns simple.app.core
  (:require [ring.util.http-response :as resp]
            [louhi.util.html :as html]))


(defn page-head [dev? resources-repo]
  [:head
   [:meta {:charset "utf-8"}]
   [:title "HTMX Demo"]
   [:meta {:name    "viewport"
           :content "width=device-width, initial-scale=1.0"}]
  ;; TODO: Self host this
   [:link {:href "https://fonts.googleapis.com/css2?family=Material+Symbols+Outlined:opsz,wght,FILL,GRAD@20..48,100..700,0..1,-50..200"
           :type "text/css"
           :rel  "stylesheet"}]
   (html/style resources-repo "/s/styles.css")
   (html/script resources-repo "/s/louhi/htmx.js")
   (html/script resources-repo "/s/louhi/alpine.js")
   (when dev?
     (html/script resources-repo "/s/louhi/dev-watcher.js"))])



(defn html-page [dev? resources-repo body]
  (html/html-page
   (page-head dev? resources-repo)
   body))


(def pages
  {:home {:href "/demo/"}
   :foo  {:label "Foo"
          :href  "/demo/foo"}
   :bar  {:label "Bar"
          :href  "/demo/bar"}
   :boz  {:label "Boz"
          :href  "/demo/boz"}})


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


(def nav-links (map pages [:foo :bar]))


(defn page-header [req]
  (let [current  (-> req :uri)
        username (-> req :session/session :username)]
    [:header
     [:nav {:hx-boost "true"}
      [:a.logo {:href         (when (not= current "/kuvatus/") "/kuvatus/")
                :aria-current (when (= current "/kuvatus/") "page")
                :hx-swap      "innerHTML transition:true"}
       "Demo"]
      [:div.spacer]
      (for [{:keys [label href]} nav-links]
        [:a {:href         (when (not= href current) href)
             :aria-current (when (= href current) "page")
             :hx-swap      "innerHTML transition:true"}
         label])
      [:button {:popovertarget "user-menu-popover"
                :hx-on:click   "setUserMenuPos()"}
       [:i "expand_more"]
       username]]

     ; User-menu pop-over:

     [:div {:id      "user-menu-popover"
            :popover true}
      [:div.user-menu
       [:a [:i "settings"] [:span "Asetukset..."]]
       [:a [:i "info"] [:span "Tietoja..."]]
       [:a [:span "Jotain muuta..."]]
       [:hr]
       [:a {:href "/session/logout"}
        [:i "logout"] [:span "Kirjaudu ulos"]]]]]))


(defn main-page-handler [env]
  (fn [req]
    (-> [[:main.main
          [:article
           [:h1 "Hello!!!"]
           #_#_#_#_[:div
                    [:span "Hello, world! "]
                    [:i "face"]]
                 [:div
                  [:i "refresh"]
                  [:span " Does this work"]]
               [:div
                (page-nav :foo "Go to foo")]
             [:div
              (page-nav :boz "Go to boz")]]]]
        (resp/ok))))


(defn app-routes [env]
  [""
   ["/" {:get {:name    :app/index
               :handler (main-page-handler env)}}]
   ["/foo" {:get {:name    :app/foo
                  :handler (fn [req]
                             (-> [(page-header req)
                                  [:main
                                   [:article
                                    [:h1 "Foo"]]]]
                                 (resp/ok)))}}]
   ["/bar" {:get {:name    :app/bar
                  :handler (fn [req]
                             (-> [(page-header req)
                                  [:main
                                   [:article
                                    [:h1 "Bar"]]]]
                                 (resp/ok)))}}]])
