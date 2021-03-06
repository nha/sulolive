(ns eponai.web.ui.photo
  (:require
    [om.next :as om :refer [defui]]
    [eponai.common.ui.dom :as dom]
    [eponai.common.photos :as photos]
    [eponai.common.ui.elements.css :as css]
    [taoensso.timbre :refer [debug warn]]
    [eponai.common.shared :as shared]))

#?(:cljs
   (let [loaded-url-cache (atom nil)]
     (defn is-loaded? [photo-url]
       (let [img (js/Image.)
             _ (set! (.-src img) photo-url)
             loaded? (.-complete img)]
         (if loaded?
           (if (contains? @loaded-url-cache photo-url)
             ;; It's loaded and it's not the first time we check this.
             true
             ;; Return false the first time to let the animation happen.
             (do (swap! loaded-url-cache (fnil conj #{}) photo-url)
                 false))
           (do
             (swap! loaded-url-cache (fnil disj #{}) photo-url)
             false))))))

(defui Photo
  Object
  (large-image-url [this]
    (let [{:keys [photo-id transformation ext]} (om/props this)]
      (photos/main (shared/by-key this :shared/photos) photo-id {:transformation (or transformation :transformation/preview)
                                                                 :ext            ext})))
  (initLocalState [this]
    {:loaded-main? #?(:clj  false
                      :cljs (when (string? (:photo-id (om/props this)))
                              (is-loaded? (.large-image-url this))))})
  (componentDidMount [this]
    (let [{:keys [photo-id]} (om/props this)]
      #?(:cljs
         (when (some? photo-id)
           (let [image-large (js/Image.)
                 url (.large-image-url this)]
             (set! (.-onload image-large) #(do
                                            (om/update-state! this assoc :loaded-main? true)))
             (set! (.-src image-large) url))))))

  (render [this]
    (let [{:keys [content src photo-id classes ext alt background? id onMouseMove onMouseOut]} (om/props this)]
      (cond (some? photo-id)
            (let [{:keys [loaded-main?]} (om/get-state this)
                  url-small (photos/mini (shared/by-key this :shared/photos) photo-id {:ext ext})
                  url (.large-image-url this)]
              (if-not (string? photo-id)
                (warn "Ignoring invalid photo src type, expecting a URL string. Got src: " photo-id)
                (dom/div
                  {:classes (conj classes ::css/photo)}
                  (when background?
                    (dom/div
                      (cond-> (css/add-class :background {:style {:backgroundImage (str "url(" url ")")}
                                                          :id    (when id (str id "-background"))})
                              ;loaded-main?
                              ;(assoc :style {:backgroundImage (str "url(" url ")")})
                              loaded-main?
                              (update :classes conj :loaded))))
                  ;[
                  ;(dom/div (css/add-class :content)
                  ;         content)
                  ;]
                  (when url-small
                    (dom/img
                      {
                       ;:data-src url-small
                       :src     url-small
                       :alt     (or alt "")
                       :classes ["small"]}))
                  (dom/img
                    (cond->> {
                              ;:data-src (when loaded-main? url)
                              :onMouseMove onMouseMove
                              :onMouseOut  onMouseOut
                              :src         url
                              :classes     ["main"]
                              :onLoad      #(om/update-state! this assoc :loaded-main? true)
                              ;:itemProp    "image"
                              :alt         (or alt "")
                              }
                             loaded-main?
                             (css/add-class :loaded))))))

            (some? src)
            (dom/div
              {:classes (conj classes ::css/photo)
               ;:style   {:backgroundImage (str "url(" src ")")}
               }
              (dom/img
                {
                 ;:data-src src
                 :alt     (or alt "")
                 :src     src
                 :classes ["main loaded"]})

              (when (some? content)
                (dom/div (css/add-class :content)
                         content)))

            :else
            (warn "Photo component got no data source, expecing a photo key or a src.")))))

(def ->Photo (om/factory Photo))

(defn overlay [opts & content]
  (dom/div
    (css/add-class ::css/overlay opts)
    (dom/div
      (css/add-class ::css/photo-overlay-content)
      content)))

(defn photo [{:keys [status classes id] :as props} & content]
  (dom/div
    {:classes (disj (set (conj classes ::css/photo-container status)) :thumbnail)
     :id      (when id (str id "-container"))}
    ;(update props :classes into [::css/photo-container status]) ;(css/add-classes [::css/photo-container status])
    (->Photo props)
    (cond (= status :edit)
          (overlay nil (dom/i {:classes ["fa fa-camera fa-fw"]}))

          (= status :loading)
          (overlay nil (dom/i {:classes ["fa fa-spinner fa-spin"]}))
          :else
          content)))

(defn square [props & content]
  (photo (css/add-class :square props) content))

(defn circle [{:keys [status] :as props} & content]
  (dom/div
    (css/add-classes [::css/photo-container :circle status])
    (->Photo (css/add-class :circle props))
    (cond (= status :edit)
          (overlay nil (dom/i {:classes ["fa fa-camera fa-fw"]}))

          (= status :loading)
          (overlay nil (dom/i {:classes ["fa fa-spinner fa-spin"]}))
          :else
          content)))

(defn cover [{:keys [photo-id src transformation] :as props} & content]
  (let [photo-key (when-not src (or photo-id "static/storefront-cover"))]
    (photo (-> (css/add-class :cover props)
               (assoc :style :style/cover)
               (assoc :photo-id photo-key)
               (assoc :transformation (or transformation :transformation/cover)))
           (overlay nil content))))

;(defn edit-cover [{:keys [photo-id] :as props} & content]
;  (cover (merge props {:placeholder? true
;                       :transformation :transformation/preview})
;         (overlay nil (dom/i {:classes ["fa fa-camera fa-fw"]}))))

(defn product-photo [product & [{:keys [index transformation classes background?] :as opts} & content]]
  (let [{:store.item/keys [photos]} product
        {item-photo :store.item.photo/photo} (get (into [] (sort-by :store.item.photo/index photos)) (or index 0))
        photo-id (:photo/id item-photo "static/storefront")]
    (photo (merge opts {:photo-id    photo-id
                        :alt         (str (:store.item/name product))
                        :classes     (conj classes :product-photo)
                        :background? (if (some? background?) background? true)})
           content)))

(defn product-preview [product opts & content]
  (let [params (assoc opts :background? false)]
    (product-photo product (css/add-class :square params)
                   content)))

(defn product-thumbnail [product & [opts]]
  (product-preview product
                   (->> (assoc opts :background? false)
                        (css/add-class :thumbnail))))

(defn store-photo [store props & content]
  (let [photo (get-in store [:store/profile :store.profile/photo])
        photo-id (:photo/id photo "static/storefront-2")]
    (circle (->> (merge props {:photo-id photo-id
                               :alt      (str (-> store :store/profile :store.profile/name))})
                 (css/add-class :store-photo))
            content)))

(defn store-cover [store props & content]
  (let [{cover-photo :store.profile/cover} (:store/profile store)]
    (cover (merge props {:photo-id       (:photo/id cover-photo)
                         :transformation :transformation/cover})
           content)))

(defn user-photo [user props & content]
  (let [photo (get-in user [:user/profile :user.profile/photo])
        p (if (:photo/id photo)
            {:photo-id (:photo/id photo)
             :alt      (-> user :user/profile :user.profile/name)}
            {:photo-id "static/cat-profile"
             :ext      "png"
             :alt      (-> user :user/profile :user.profile/name)})]
    (dom/div
      (css/add-class :user-profile-photo)
      (circle
        (merge props p)
        content))))

(defn video-thumbnail [opts & content]
  (photo
    (css/add-class :video-thumbnail opts)
    content))

(defn stream-photo [thumbnail-url store opts & content]
  (let [photo (get-in store [:store/profile :store.profile/photo])
        photo-id (:photo/id photo "static/storefront-2")]
    (dom/div
     (css/add-class :stream-photo)
     (video-thumbnail
       {:photo-id photo-id
        :alt      (str (get-in store [:store/profile :store.profile/name]) " live stream")}
       (overlay
         nil
         (dom/div (css/add-class :video)
                  (dom/i {:classes ["fa fa-play fa-fw"]}))
         content)))))

(defn vod-photo [thumbnail-url store opts & content]
  (let [photo (get-in store [:store/profile :store.profile/photo])
        photo-id (:photo/id photo "static/storefront-2")]
    (dom/div
     (css/add-class :stream-photo)
     (video-thumbnail
       {:photo-id photo-id
        :alt      (str (get-in store [:store/profile :store.profile/name]) " recorded live stream")}
       (overlay
         nil
         (dom/div (css/add-class :video)
                  (dom/i {:classes ["fa fa-play fa-fw"]}))
         content)))))