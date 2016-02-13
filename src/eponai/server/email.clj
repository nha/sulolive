(ns eponai.server.email
  (:require [eponai.server.http :as h]
            [postal.core :as email]
            [datomic.api :as d]
            [environ.core :refer [env]]
            [taoensso.timbre :refer [debug error info]]
            [eponai.common.database.pull :as p]
            [hiccup.page :refer [xhtml]]
            [garden.core :refer [css]]
            [garden.stylesheet :as g]))

(declare html-content)
(declare text-content)

(defn subject [user-status]
  (if (= user-status :user.status/new)
    "Create your account on JourMoney"
    "Sign in to JourMoney"))

(defn- smtp []
  {:host (env :smtp-host)
   :user (env :smtp-user)
   :pass (env :smtp-pass)
   :tls  (env :smtp-tls)
   :port (env :smtp-port)})

(defn- send-email
  "Send a verification email to the provided address with the given uuid.
  Will send a link with the path /verify/:uuid that will verify when the user opens the link."
  [smtp address uuid user-status]
  (let [link (str "http://localhost:3000/verify/" uuid)
        body {:from    "info@gmail.com"
              :to      "info@jourmoney.com"
              :subject (subject user-status)
              :body    [:alternative
                        {:type    "text/plain"
                         :content (text-content link user-status)}
                        {:type    "text/html"
                         :content (html-content link user-status)}
                        ]}
        status (email/send-message smtp body)]

    (debug "Sent verification email to uuid: " uuid "with status:" status)
    (if (= 0 (:code status))
      status
      (throw (ex-info (:message status) {:cause   ::email-error
                                         :status  ::h/service-unavailable
                                         :message (:message status)
                                         :data    {:email address
                                                   :uuid  uuid
                                                   :error (:error status)}})))))
(defn send-email-fn
  "Function that checks if there's any pending verification for the provided user emal,
  and sends a verification email if so.

  Conn is the connection to the database."
  [conn]
  (fn [verification user-status]
    (let [db (d/db conn)
          uuid (:verification/uuid verification)]

      (cond (p/lookup-entity db [:verification/uuid uuid])
            (send-email (smtp)
                        (:verification/value verification)
                        uuid
                        user-status)))))

(defn text-content [link user-status]
  (if (= user-status :user.status/new)
    (str "Click and confirm that you want to create an account on JourMoney. This link will expire in 15 minutes and can only be used once.\n" link)
    (str "Sign in to JourMoney. This link will expire in 15 minutes and can only be used once" link)))

(defn html-content [link user-status]
  (xhtml
    [:head
     [:meta
      {:content "text/html; charset=UTF-8", :http-equiv "Content-Type"}]
     [:meta
      {:content "width=device-width, initial-scale=1.0",
       :name    "viewport"}]
     [:title
      (subject user-status)]]
    [:body
     {:style "margin: 0; padding: 0;"
      :bgcolor "#FDFFFC"}
     [:table
      {:align   "center",
       :style   "color:01213d;"}
      [:tr
       [:td {:align "center"}
        [:table
         [:tr
          [:td {:align "center"}
           [:p
            {:style
             "background-color:#01213d;border-radius:50%;height:30px;width:30px;display:inline-block;"}]
           [:p
            {:style
             "background-color:#01213d;border-radius:50%;height:30px;width:30px;display:inline-block;"}]
           [:p
            {:style
             "background-color:#01213d;border-radius:50%;height:30px;width:30px;display:inline-block;"}]]]
         [:tr
          [:td
           {:align "center"}
           [:p {:style "font-size:18px;border-top:1px solid #e5e5e5;padding-top:1em;"}
            (if (= user-status :user.status/new)
              "Click and confirm that you want to create an account on JourMoney."
              "Sign in to JourMoney.")]]
          [:tr
           [:td
            {:align "center"}
            [:p {:style "font-size:16px;color:#b3b3b1;"}
             "This link will expire in 15 minutes and can only be used once."]]]]
         [:tr
          [:td
           {:align "center"}
           [:p
            [:a
                {:href link
                 :style
                       "text-decoration:none;display:inline-block; border-radius:10px; padding:16px 20px;font-size:16px;border:1px solid transparent;background-color:#2EC4B6;color:#fff;font-weight:bold;"}
                (if (= user-status :user.status/new)
                  "Create account"
                  "Sign in")]]]]
         [:tr
          [:td
           {:align "center"}
           [:p
            (if (= user-status :user.status/new)
              "Or create an account using this link:"
              "Or sign in using this link:")
            [:br]
            [:a {:href link} link]]]]
         [:tr
          [:td
           {:align  "center"}
           [:p {:style "color:#b3b3b1;border-top:1px solid #e5e5e5;padding:1em;"}
            "This email was sent by JourMoney."
            [:br]
            "eponai hb"
            [:br]
            "Stockholm, Sweden"]]]]]]]]))