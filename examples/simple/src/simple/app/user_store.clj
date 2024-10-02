(ns simple.app.user-store
  (:require [integrant.core :as ig]))


(defmethod ig/init-key :simple/user-store [_ _]
  (->> [{:id       1
         :username "Foo"
         :email    "foo"
         :password "bar"
         :role     :admin}
        {:id       2
         :username "hunter by the name"
         :email    "tiger"
         :password "hunter2"
         :role     :user}]
       (reduce (fn [acc user-info]
                 (assoc acc (:email user-info) user-info))
               {})))


(defn get-user-info [user-store {:keys [email password]}]
  (let [user-info (get user-store email)]
    (when (and user-info (= (:password user-info) password))
      (dissoc user-info :password))))
