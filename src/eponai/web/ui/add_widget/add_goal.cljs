(ns eponai.web.ui.add-widget.add-goal
  (:require
    [cljs-time.coerce :as c]
    [cljs-time.core :as t]
    [eponai.client.ui :refer-macros [opts]]
    [eponai.web.ui.datepicker :refer [->Datepicker]]
    [eponai.web.ui.utils.filter :as filter]
    [om.next :as om :refer-macros [defui]]
    [sablono.core :refer-macros [html]]
    [datascript.core :as d]
    [eponai.common.format :as f]
    [taoensso.timbre :refer-macros [debug]]))

(defui NewGoal

  Object
  (set-input [this props]
    (let [{:keys [widget]} (::om/computed props)
          goal (get-in widget [:widget/report :report/goal])]
      (when widget
        (om/update-state! this assoc
                          :daily-limit (:goal/value goal)
                          :start (c/from-long (get-in goal [:goal/cycle :cycle/start]))))))
  (componentDidMount [this]
    (.set-input this (om/props this)))
  (componentWillReceiveProps [this new-props]
    (.set-input this new-props))

  (render [this]
    (let [{:keys [widget
                  on-change]} (om/get-computed this)
          {:keys [daily-limit
                  filtered-tags
                  start
                  end]} (om/get-state this)]
      (html
        [:div
         [:ul.breadcrumbs
          [:li
           [:a.disabled
            "New Goal"]]]
         [:div.row.column.small-12.medium-6
          [:div.row
           [:div.columns.small-3.text-right
            [:label "Daily limit:"]]
           [:div.columns.small-9.end
            [:input
             {:value     (or daily-limit "")
              :type      "number"
              :on-change #(do
                           (let [value (.-value (.-target %))]
                             (om/update-state! this assoc :daily-limit value)
                             (when on-change
                               (on-change (assoc-in widget [:widget/report :report/goal :goal/value] value)))))}]]]

          [:div.row
           [:div.columns.small-3.text-right
            [:label "End Date:"]]
           [:div.columns.small-9.end
            (->Datepicker
              (opts {:key       [::date-picker]
                     :value     end
                     :on-change #(do
                                  (om/update-state! this assoc :end %)
                                  (when on-change
                                    (on-change (assoc-in widget [:widget/report :report/goal :goal/cycle :cycle/period-count]
                                                         (t/in-days (t/interval start (c/from-date %)))))))}))]]

          (filter/->TagFilter {:tags filtered-tags}
                              {:on-change #(om/update-state! this assoc :filtered-tags %)})]))))

(def ->NewGoal (om/factory NewGoal))