(ns eponai.server.parser.read
  (:require
    [eponai.common.database :as db]
    [eponai.common.datascript :as eponai.datascript]
    [eponai.common.parser :as parser]
    [eponai.server.datomic.query :as query]
    [environ.core :as env]
    [datomic.api :as datomic]
    [taoensso.timbre :refer [error debug trace warn]]
    [eponai.common.auth :as auth]
    [eponai.server.external.stripe :as stripe]
    [eponai.server.external.wowza :as wowza]
    [eponai.server.external.chat :as chat]
    [eponai.server.external.product-search :as product-search]
    [eponai.server.external.client-env :as client-env]
    [eponai.server.api.store :as store]
    [eponai.common.api.products :as products]
    [eponai.server.api.user :as user]
    [eponai.common.browse :as browse]
    [taoensso.timbre :as timbre]
    [clojure.data.json :as json]
    [clojure.java.io :as io]
    [eponai.common.format.date :as date]
    [eponai.common.search :as common.search]
    [cemerick.url :as url]
    [eponai.common.format :as f]
    [eponai.server.external.taxjar :as taxjar]
    [eponai.common :as c]
    [eponai.common.database.rules :as db.rules]
    [medley.core :as medley])
  (:import (datomic.db Db)))

(defmacro defread
  ""
  [read-sym args auth-and-basis-params mutate-body]
  (assert (and (map? auth-and-basis-params) (contains? auth-and-basis-params :auth))
          (str "defreads's auth and basis-params requires an :auth key"
               " was: " auth-and-basis-params))
  (assert (== 3 (count args))
          (str "defread needs argument vector to take 3 arguments, was: " args))
  (let [read-key# (keyword (namespace read-sym) (name read-sym))
        basis-params-body# (when-let [bp# (:uniq-by auth-and-basis-params)]
                             `(defmethod parser/read-basis-params ~read-key# ~args ~bp#))
        ;; For return values of :log, see comment in parser/log-param-keys
        log-param-key# (when-let [pk# (:log auth-and-basis-params)]
                         `(defmethod parser/log-param-keys ~read-key# ~args ~pk#))]
    `(do
       ~basis-params-body#
       ~log-param-key#
       (defmethod parser/server-auth-role ~read-key# ~args ~(:auth auth-and-basis-params))
       (defmethod parser/server-read ~read-key# ~args ~mutate-body))))

(defread datascript/schema
  [{:keys [db db-history]} _ _]
  {:auth ::auth/public}
  {:value (-> (query/schema db db-history)
              (eponai.datascript/schema-datomic->datascript))})

(defread query/cart
  [{:keys [db db-history query auth]} _ {:keys [items]}]
  {:auth ::auth/any-user}
  {:value (query/one db db-history query {:where   '[[?u :user/cart ?e]]
                                          :symbols {'?u (:user-id auth)}})})

(defread query/checkout
  [{:keys [db db-history query auth route-params]} _ _]
  {:auth ::auth/any-user}
  {:value (db/pull-all-with db query {:where   '[[?u :user/cart ?c]
                                                 [?c :user.cart/items ?e]
                                                 [?i :store.item/skus ?e]
                                                 [?s :store/items ?i]]
                                      :symbols {'?s (:store-id route-params)
                                                '?u (:user-id auth)}})})

(defread query/taxes
  [{:keys [db db-history query auth system state route-params] :as env} _ {:keys [destination subtotal shipping]}]
  {:auth ::auth/any-user}
  {:value (let [store-id (:store-id route-params)]
            (debug "Query/taxes")
            (when-let [destination-address (:shipping/address destination)]
              (let [taxes (taxjar/calculate (:system/taxjar system)
                                            store-id
                                            {:destination-address destination-address
                                             :source-address      (store/address env store-id)
                                             :amount              subtotal
                                             :shipping            shipping})]
                (debug "Got taxes: " taxes)
                taxes)))})

;; ############ Browsing reads

(defread query/stores
  [{:keys [db db-history query locations]} _ _]
  {:auth ::auth/public}
  {:value (do
            (debug "Read stores: " locations)
            (when (some? (:db/id locations))
              (query/all db db-history query {:where   '[[?e :store/locality ?l]
                                                         [?status :status/type :status.type/open]
                                                         [?e :store/status ?status]
                                                         [?s :stream/state _]
                                                         [?e :store/profile ?p]
                                                         [?p :store.profile/photo _]
                                                         [?s :stream/store ?e]]
                                              :symbols {'?l (:db/id locations)}})))})

(defread query/streams
  [{:keys [db db-history query locations]} _ _]
  {:auth ::auth/public}
  {:value (when (some? (:db/id locations))
            (query/all db db-history query {:where   '[[?s :store/locality ?l]
                                                       [?st :status/type :status.type/open]
                                                       [?s :store/status ?st]
                                                       [?e :stream/store ?s]
                                                       [?e :stream/state :stream.state/live]]
                                            :symbols {'?l (:db/id locations)}}))})

(defread query/browse-items
  [{db                                                 :db
    db-history                                         :db-history
    query                                              :query
    locations                                          :location
    {:keys [top-category sub-category] :as categories} :route-params
    {:keys [search]}                                   :query-params} _ _]
  {:auth    ::auth/public
   :uniq-by {:custom [[:filter (cond
                                 (seq search)
                                 [:search search]
                                 (some? top-category)
                                 [:tc top-category]
                                 (some? sub-category)
                                 [:sc sub-category])]]}}
  {:value (when (some? (:db/id locations))
            (query/all db db-history query (cond
                                             (seq search)
                                             (products/find-with-search locations search)
                                             (some? top-category)
                                             (products/find-with-category-names locations (select-keys categories [:top-category]))
                                             (some? sub-category)
                                             (products/find-with-category-names locations (select-keys categories [:sub-category]))
                                             :else
                                             (products/find-all locations))))})

;; ------ Featured
(defn feature-all [namespace coll]
  (into []
        (map #(assoc % (keyword (name namespace) "featured") true))
        coll))

(defread query/featured-streams
  [{:keys [db db-history query locations]} _ _]
  {:auth ::auth/public}
  {:value (when (some? (:db/id locations))
            (when-not db-history
              (->> (query/all db db-history query {:where   '[[?s :store/locality ?l]
                                                              [?st :status/type :status.type/open]
                                                              [?s :store/status ?st]
                                                              [?e :stream/store ?s]
                                                              [?s :store/profile ?p]
                                                              [?p :store.profile/photo _]]
                                                   :symbols {'?l (:db/id locations)}})
                   (feature-all :stream))))})

(defread query/featured-items
  [{:keys [db db-history query locations]} _ _]
  {:auth ::auth/public}
  {:value (when (some? (:db/id locations))
            (letfn [(add-time-to-all [time items]
                      (map #(if (nil? (:store.item/created-at %))
                             (assoc % :store.item/created-at time)
                             %)
                           items))]
              (->> (db/pull-all-with db query {:where   '[[?s :store/locality ?l]
                                                          [?st :status/type :status.type/open]
                                                          [?s :store/status ?st]
                                                          [?s :store/items ?e]
                                                          [?s :store/profile ?profile]
                                                          [?profile :store.profile/photo _]
                                                          [?e :store.item/photos ?p]
                                                          [?p :store.item.photo/photo _]]
                                               :symbols {'?l (:db/id locations)}})
                   (add-time-to-all 0)
                   (sort-by :store.item/created-at #(compare %2 %1))
                   (take 10)
                   (feature-all :store.item))))})

(defread query/featured-stores
  [{:keys [db db-history query locations]} _ _]
  {:auth ::auth/public}
  ;; TODO: Come up with a way to feature stores.
  {:value (when (some? (:db/id locations))
            (letfn [(add-time-to-all [time items]
                      (map #(if (nil? (:store/created-at %))
                             (assoc % :store/created-at time)
                             %)
                           items))]
              (->> (db/pull-all-with db query {:where   '[[?e :store/locality ?l]
                                                          [?st :status/type :status.type/open]
                                                          [?e :store/status ?st]
                                                          [?e :store/profile ?p]
                                                          [?p :store.profile/photo _]]
                                               :symbols {'?l (:db/id locations)}})
                   (add-time-to-all 0)
                   (sort-by :store/created-at #(compare %2 %1))
                   (take 4)
                   (feature-all :store))))})

;; ##############

(defread query/store
  [{:keys [db db-history query auth route-params]} _ _]
  {:auth    ::auth/public
   :uniq-by {:route-params [:store-id]}}
  {:value (let [store-id (:store-id route-params)
                {:store/keys [status owners]} (db/pull db
                                                       [{:store/status [:status/type]}
                                                        {:store/owners [{:store.owner/user [:user/email]}]}]
                                                       store-id)]
            (debug "AUTH: " auth)
            (if (or (= (:status/type status)
                       :status.type/open)
                    (= (:email auth) (get-in owners [:store.owner/user :user/email])))
              (query/one db db-history query {:where   '[[?e _ _]]
                                              :symbols {'?e store-id}})
              {:db/id            store-id
               :store/not-found? true}))})

(defread query/store-items
  [{:keys [db db-history query route-params]} _ _]
  {:auth    ::auth/public
   :uniq-by {:route-params [:store-id :navigation]}}
  {:value (let [{:keys [store-id navigation]} route-params
                params (if (not-empty navigation)
                         {:where   '[[?s :store/items ?e]
                                     [?e :store.item/section ?n]
                                     [?n :store.section/path ?p]]
                          :symbols {'?s store-id
                                    '?p navigation}}

                         {:where   '[[?s :store/items ?e]]
                          :symbols {'?s store-id}}
                         )]
            (query/all db db-history query params))})

;; TODO: Separate out this query?
;; making it query/user-orders
;;           query/store-orders
(defread query/orders
  [{:keys              [db query auth]
    {:keys [store-id]} :route-params} _ _]
  {:auth    (if (some? store-id)
              {::auth/store-owner {:store-id store-id}}
              ::auth/any-user)
   :uniq-by {:custom [[:val (if (some? store-id)
                              [:store-id store-id]
                              [:user-id (hash (:email auth))])]]}}
  {:value (if (some? store-id)
            (db/pull-all-with db query {:where   '[[?e :order/store ?s]]
                                        :symbols {'?s store-id}})
            (db/pull-all-with db query {:where   '[[?e :order/user ?u]]
                                        :symbols {'?u (:user-id auth)}}))})

(defread query/inventory
  [{:keys              [query db]
    {:keys [store-id]} :route-params} _ _]
  {:auth    {::auth/store-owner {:store-id store-id}}
   :uniq-by {:route-params [:store-id]}}
  {:value (let [items (db/pull-all-with db query {:where   '[[?s :store/items ?e]]
                                                  :symbols {'?s store-id}})]
            ;(debug "Found items: " (into [] items))
            items)})

(defread query/order
  [{:keys [query db auth]
    {:keys [store-id order-id]} :route-params} _ _]
  {:auth    ::auth/any-user
   :uniq-by {:custom [[:val (if (some? store-id)
                              [:store-id store-id]
                              [:user-id (hash (:email auth))])]
                      [:order-id order-id]]}}
  {:value (if (some? store-id)
            (db/pull-one-with db query {:where   '[[?e :order/store ?s]]
                                        :symbols {'?e order-id
                                                  '?s store-id}})
            (db/pull-one-with db query {:where   '[[?e :order/user ?u]]
                                        :symbols {'?e order-id
                                                  '?u (:user-id auth)}}))})

(defread query/order-payment
  [{:keys                       [query db auth system]
    {:keys [store-id order-id]} :route-params} _ _]
  {:auth    ::auth/any-user
   :uniq-by {:custom [[:val (if (some? store-id)
                              [:store-id store-id]
                              [:user-id (hash (:email auth))])]
                      [:order-id order-id]]}}
  {:value (when-let [charge (if (some? store-id)
                              (db/pull-one-with db [:db/id :charge/id] {:where   '[[?o :order/store ?s]
                                                                                   [?o :order/charge ?e]]
                                                                        :symbols {'?o order-id
                                                                                  '?s store-id}})
                              (db/pull-one-with db [:db/id :charge/id] {:where   '[[?o :order/user ?u]
                                                                                   [?o :order/charge ?e]]
                                                                        :symbols {'?o order-id
                                                                                  '?u (:user-id auth)}}))]
            (assoc (stripe/get-charge (:system/stripe system) (:charge/id charge)) :db/id (:db/id charge)))})

(defread query/stripe-account
  [{:keys [db route-params]
    {:keys [store-id]} :route-params
    :as env} _ _]
  {:auth    {::auth/store-owner {:store-id store-id}}
   :uniq-by {:route-params [:store-id]}}
  {:value (store/account env store-id)})

(defread query/stripe-balance
  [{:keys [db]
    {:keys [store-id]} :route-params
    :as env} _ _]
  {:auth    {::auth/store-owner {:store-id store-id}}
   :uniq-by {:route-params [:store-id]}}
  {:value (store/balance env store-id)})

(defread query/stripe-customer
  [env _ _]
  {:auth ::auth/any-user}
  {:value (let [c (user/customer env)]
            (debug "Query/stripe-customer " c)
            c)})

(defread query/stripe-country-spec
  [{:keys [system ast target db query]} _ _]
  {:auth ::auth/any-store-owner}
  {:value (stripe/get-country-spec (:system/stripe system) "CA")})

(defread query/skus
  [{:keys [db db-history query]} _ {:keys [sku-ids]}]
  {:auth    ::auth/public
   :log     [:sku-ids]
   :uniq-by {:params [:sku-ids]}}
  {:value (query/all db db-history query
                     {:where   '[[_ :store.item/skus ?e]]
                      :symbols {'[?e ...] sku-ids}})})

(defread query/item
  [{:keys [db db-history query route-params]} _ _]
  {:auth    ::auth/public
   :uniq-by {:route-params [:product-id]}}
  {:value (query/one db db-history query
                     {:where   '[[?e _ _]]
                      :symbols {'?e (:product-id route-params)}})})

(defread query/auth
  [{:keys [auth query db]} _ _]
  {:auth ::auth/any-user}
  {:value (let [can-open-store? (boolean (get-in auth [:user_metadata :can_open_store]))
                authed-user (db/pull db query (:user-id auth))]
            (when authed-user
              (assoc authed-user :user/can-open-store? can-open-store?)))})

(defread query/auth0-info
  [{:keys [auth query db] :as env} _ _]
  {:auth ::auth/public}
  {:value (let [user-info (user/user-info env)]
            (debug "SERVER QUERY/AUTH0 : " user-info)
            user-info)})

(defread query/stream
  [{:keys [db db-history query route-params]} _ _]
  {:auth    ::auth/public
   :uniq-by {:route-params [:store-id]}}
  {:value (when-let [store-id (:store-id route-params)]
            (query/one db db-history query {:where   '[[?e :stream/store ?store-id]]
                                            :symbols {'?store-id store-id}}))})

(defread query/locations
  [{:keys [db db-history query locations]} _ _]
  {:auth ::auth/public}
  {:value (when (:sulo-locality/path locations)
            (db/pull db [:db/id
                         :sulo-locality/path
                         :sulo-locality/title {:sulo-locality/photo [:photo/id]}] [:sulo-locality/path (:sulo-locality/path locations)]))})


(defread query/stream-config
  [{:keys [db db-history query system]} k _]
  {:auth ::auth/public}
  ;; Only query once. User will have to refresh the page to get another stream config
  ;; (For now).
  (when (nil? db-history)
    (let [wowza (:system/wowza system)]
      {:value {:ui/singleton                              :ui.singleton/stream-config
               :ui.singleton.stream-config/subscriber-url (wowza/subscriber-url wowza)
               :ui.singleton.stream-config/publisher-url  (wowza/publisher-url wowza)}})))

(defread query/chat
  [{:keys         [db query system route-params]
    ::parser/keys [read-basis-t-for-this-key chat-update-basis-t]} k _]
  {:auth    ::auth/public
   :log     ::parser/no-logging
   :uniq-by {:route-params [:store-id]}}
  (if-some [store-id (:store-id route-params)]
    (let [store (db/entity db store-id)
          chat (:system/chat system)
          _ (when chat-update-basis-t
              (chat/sync-up-to! chat chat-update-basis-t))
          chat-reader (chat/store-chat-reader chat (when (datomic/is-filtered db)
                                                     (.getFilter ^Db db)))]
      {:value (-> (if (nil? read-basis-t-for-this-key)
                    (chat/initial-read chat-reader store query)
                    (chat/read-messages chat-reader store query read-basis-t-for-this-key))
                  (parser/value-with-basis-t (chat/last-read chat-reader)))})
    (do (warn "No store :db/id passed in params for read: " k
              " with route-params: " route-params)
        {:value (parser/value-with-basis-t {} {:chat-db (:chat-db read-basis-t-for-this-key)
                                               :sulo-db (datomic/basis-t db)})})))

;; Get all categories.
(defread query/navigation
  [{:keys [db db-history query]} _ _]
  {:auth ::auth/public}
  {:value (let [children-keys #{:category/children :category/_children}]
            (query/all db db-history
                       (into [{:category/children [:db/id]}]
                             (comp (remove children-keys)
                                   (remove #(and (map? %) (some children-keys (keys %)))))
                             query)
                       {:where '[[?e :category/path]]}))})

(defread query/categories
  [{:keys [db db-history query]} _ _]
  {:auth ::auth/public}
  {:value (query/all db db-history query {:where '[[?e :category/path _]
                                                   [(missing? $ ?e :category/_children)]]})})

(defread query/owned-store
  [{:keys [db db-history auth query]} _ _]
  {:auth ::auth/any-user}
  {:value (query/one db db-history query
                     {:where   '[[?owners :store.owner/user ?user]
                                 [?e :store/owners ?owners]]
                      :symbols {'?user (:user-id auth)}})})

(defread query/countries
  [{:keys [db db-history auth query]} _ _]
  {:auth ::auth/any-user}
  {:value (query/all db db-history query {:where '[[?e :country/code _]]})})

(defread query/product-search
  [{:keys [db-history system]} _ _]
  {:auth ::auth/public}
  {:value (if-not db-history
            (db/db (:search-conn (:system/product-search system)))
            (product-search/changes-since db-history :store.item/name))})

(defread query/client-env
  [{:keys [db-history system]} _ _]
  {:auth ::auth/public}
  {:value (when-not db-history
            (client-env/env-map (:system/client-env system)))})

(defread query/sulo-localities
  [{:keys [target db query]} _ _]
  {:auth ::auth/public}
  {:value (db/pull-all-with db query {:where '[[?e :sulo-locality/title _]]})})

(defn browse-products-uniqueness [query-params]
  (let [v [:price-range :order]]
    (if (some? (:search query-params))
      (conj v :search)
      (conj v :categories))))

(defread query/browse-products-2
  [{:keys         [db locations query-params route-params query]
    ::parser/keys [read-basis-t-for-this-key]} _ _]
  {:auth ::auth/public}
  (let [browse-params (browse/make-browse-params locations route-params query-params)
        uniqueness (select-keys browse-params (browse-products-uniqueness query-params))]
    (if (= read-basis-t-for-this-key uniqueness)
      {:value (parser/value-with-basis-t {}
                                         read-basis-t-for-this-key)}
      (let [browse-result (browse/find-items db browse-params)
            initial-pull (db/pull-many db query (seq (browse/page-items db
                                                                        browse-result
                                                                        (:page-range browse-params)
                                                                        (:categories browse-params))))]
        {:value (parser/value-with-basis-t
                  {:browse-result browse-result
                   :browse-params browse-params
                   :initial-pull  initial-pull}
                  uniqueness)}))))

(defread query/browse-product-items
  [{:keys [db db-history query route-params]} _ {:keys [product-items]}]
  {:auth ::auth/public
   :log  [:product-items]}
  {:value (when (seq product-items)
            (db/pull-many db query product-items))})
