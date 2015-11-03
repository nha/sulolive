(ns flipmunks.budget.validate
  (:require [flipmunks.budget.http :as e]))

(defn- validate
  [msg f & args]
  (if (apply f args)
    true
    (let [message (str "Validation failed, " msg)]
      (throw (ex-info message {:cause   ::validation-error
                               :status  ::e/unprocessable-entity
                               :data    {:fn     f
                                         :params args}
                               :message message})))))

(defn- valid-user-tx? [user-tx]
  (let [required-fields #{:transaction/uuid
                          :transaction/name
                          :transaction/date
                          :transaction/amount
                          :transaction/currency
                          :transaction/created-at}]
    (validate (str "user tx: " user-tx) every? #(contains? user-tx %) required-fields)))


(defn valid-user-txs?
  "Validate the user transactions to be posted. Verifies that the required attributes
  are included en every transaction, and throws an ExceptionInfo if validation fails."
  [user-txs]
  (validate "" every? #(valid-user-tx? %) user-txs))

(defn valid-signup?
  "Validate the signup parameters. Checks that username and password are not empty,
  and that the password matches the repeated password. Throws an ExceptionInfo if validation fails."
  [{:keys [request-method params]}]
  (let [{:keys [password username repeat]} params]
    (validate "request method not POST" = request-method :post)
    (validate "contains empty fields" (every-pred not-empty) username password repeat)
    (validate "passwords don't match" = password repeat)))