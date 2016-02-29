(ns eponai.server.parser.response
  (:require [clojure.core.async :refer [go <!]]
            [datomic.api :as d]
            [eponai.common.database.transact :as t]
            [eponai.server.middleware :as m]
            [taoensso.timbre :refer [debug error trace]]
            [eponai.server.datomic.format :as f]
            [eponai.common.database.pull :as p]))

;; function to use with eponai.common.parser/post-process-parse
(defmulti response-handler (fn [_ k _] k))
(defmethod response-handler :default
  [_ k _]
  (trace "no response-handler for key:" k)
  (cond
    (= "proxy" (namespace k)) :call

    :else
    nil))

(defmethod response-handler 'transaction/create
  [{:keys [state ::m/currency-rates-fn]} _ response]
  (when-let [chan (get-in response [:result :currency-chan])]
    (go
      (let [date (<! chan)]
        (when-not (p/pull (d/db state) '[:conversion/_date] [:date/ymd (:date/ymd date)])
          (let [rates (f/currency-rates (currency-rates-fn (:date/ymd date)))]
            (t/transact state rates))))))
  (update response :result dissoc :currency-chan))

(defmethod response-handler 'signup/email
  [{:keys [::m/send-email-fn]} _ response]
  (when-let [chan (get-in response [:result :email-chan])]
    (go
      (send-email-fn (<! chan) (get-in response [:result :status]))))
  (-> response
      (update :result dissoc :email-chan)
      (update :result dissoc :status)))
